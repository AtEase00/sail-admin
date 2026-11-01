<script setup lang="ts">
import { Mesh, Program, Renderer, Triangle } from 'ogl'
import { onMounted, onUnmounted, ref, watch } from 'vue'

/**
 * 基于用户提供的 Vue Bits GhostFibers，默认采用白天灰阶。
 * Copyright (c) 2025 David Haz，MIT + Commons Clause，原文见根目录 licenses/vue-bits-LICENSE.md。
 */
interface GhostFibersProps {
  lineColor?: string
  glowColor?: string
  speed?: number
  scale?: number
  rotation?: number
  rotationSpeed?: number
  layers?: number
  waveAmplitude?: number
  waveFrequency?: number
  waveSpeed?: number
  layerSpeed?: number
  twist?: number
  twistFrequency?: number
  twistSpeed?: number
  lineFrequency?: number
  lineSpacing?: number
  lineSharpness?: number
  glowFalloff?: number
  glowIntensity?: number
  brightness?: number
  blueBoost?: number
  vignette?: number
  grain?: number
  lightMode?: boolean
  dpr?: number
  fps?: number
  paused?: boolean
  className?: string
}

const props = withDefaults(defineProps<GhostFibersProps>(), {
  lineColor: '#737373',
  glowColor: '#a3a3a3',
  speed: 0.2,
  scale: 2,
  rotation: 0,
  rotationSpeed: 0.25,
  layers: 4,
  waveAmplitude: 0.015,
  waveFrequency: 3,
  waveSpeed: 0.15,
  layerSpeed: 0.08,
  twist: 0.1,
  twistFrequency: 5,
  twistSpeed: 1.2,
  lineFrequency: 5,
  lineSpacing: 2,
  lineSharpness: 16,
  glowFalloff: 10,
  glowIntensity: 1.6,
  brightness: 2,
  blueBoost: 1,
  vignette: 0.8,
  grain: 0.015,
  lightMode: true,
  dpr: 1,
  fps: 30,
  paused: false,
  className: '',
})

/** 解析明确的十六进制颜色，配置错误直接报错，不以其他颜色替代。 */
function hexToRgb(hex: string) {
  const value = hex.trim().replace(/^#/, '')
  const normalized = value.length === 3 ? value.replace(/./g, channel => channel + channel) : value
  if (!/^[a-f\d]{6}$/i.test(normalized))
    throw new TypeError(`GhostFibers 颜色格式无效: ${hex}`)
  return new Float32Array([0, 2, 4].map(offset => Number.parseInt(normalized.slice(offset, offset + 2), 16) / 255))
}

const vertex = `#version 300 es
in vec2 position;
void main() {
  gl_Position = vec4(position, 0.0, 1.0);
}
`

// 逐层累加纤维与光晕，浅色模式使用白底墨迹合成，避免深色发光背景。
const fragment = `#version 300 es
precision highp float;
uniform vec2 uResolution;
uniform float uTime;
uniform float uSpeed;
uniform float uScale;
uniform float uRotation;
uniform float uRotationSpeed;
uniform float uLayers;
uniform float uWaveAmplitude;
uniform float uWaveFrequency;
uniform float uWaveSpeed;
uniform float uLayerSpeed;
uniform float uTwist;
uniform float uTwistFrequency;
uniform float uTwistSpeed;
uniform float uLineFrequency;
uniform float uLineSpacing;
uniform float uLineSharpness;
uniform float uGlowFalloff;
uniform float uGlowIntensity;
uniform float uBrightness;
uniform float uBlueBoost;
uniform float uVignette;
uniform float uGrain;
uniform float uLightMode;
uniform vec3 uLineColor;
uniform vec3 uGlowColor;
out vec4 fragColor;
#define MAX_LAYERS 10

// 构造二维旋转矩阵，静态角度和时间角速度共同控制纤维方向。
mat2 rotate2d(float angle) {
  float sine = sin(angle);
  float cosine = cos(angle);
  return mat2(cosine, -sine, sine, cosine);
}

// 使用屏幕空间哈希生成确定性的细颗粒。
float grainHash(vec2 point) {
  point = floor(point);
  float hash = 52.9829189 * fract(dot(point, vec2(0.065, 0.005)));
  return fract(hash);
}

// 叠加不同频率的噪声，避免单一颗粒频率形成条纹。
float layeredGrain(vec2 fragmentPixel) {
  vec2 point = mod(fragmentPixel + vec2(uTime * 30.0, -uTime * 21.0), 1024.0);
  vec2 rotated = mat2(0.8, -0.5, 0.5, 0.8) * point;
  float grain = 0.0;
  grain += 0.40 * grainHash(rotated);
  grain += 0.25 * grainHash(rotated * 2.0 + 17.0);
  grain += 0.20 * grainHash(rotated * 4.0 + 47.0);
  grain += 0.10 * grainHash(rotated * 8.0 + 113.0);
  grain += 0.05 * grainHash(rotated * 16.0 + 191.0);
  return grain;
}

void main() {
  vec2 resolution = max(uResolution, vec2(1.0));
  vec2 uv = (2.0 * gl_FragCoord.xy - resolution) / resolution.y;
  float time = uTime * uSpeed;
  vec3 backdrop = mix(vec3(0.070588, 0.058824, 0.090196), vec3(1.0), step(0.5, uLightMode));
  vec3 centerTone = max(uLineColor * 0.85567 - uGlowColor * 0.06186, vec3(0.0));
  vec3 cloudTone = uLineColor * 0.19588 + uGlowColor * 0.2268;
  vec2 p = uv / max(uScale, 0.05);
  p = rotate2d(radians(uRotation) + time * uRotationSpeed) * p;
  vec3 color = vec3(0.0);
  float fiberField = 0.0;

  // 递归波动和径向扭曲形成每层纤维，层数限制与组件参数保持一致。
  for (int index = 0; index < MAX_LAYERS; index++) {
    float fi = float(index) + 1.0;
    if (fi > uLayers) break;
    p += uWaveAmplitude * sin(p.yx * fi * uWaveFrequency + time * (uWaveSpeed + fi * uLayerSpeed));
    float radius = length(p);
    float polarAngle = atan(p.y, p.x);
    polarAngle += sin(radius * uTwistFrequency - time * uTwistSpeed + fi) * uTwist;
    p = vec2(cos(polarAngle), sin(polarAngle)) * radius;
    float lines = abs(sin(p.x * (uLineFrequency + fi * uLineSpacing) + sin(p.y * 3.0 + time)));
    lines = pow(max(0.0, 1.0 - lines), uLineSharpness);
    fiberField += lines / fi;
    color += uLineColor * lines / fi;
    float glow = exp(-uGlowFalloff * abs(sin(p.x * 3.0 + time + fi)));
    color += uGlowColor * glow * uGlowIntensity / (fi * 2.0);
  }

  float center = exp(-2.2 * dot(uv, uv));
  color += centerTone * center;
  float cloud = exp(-1.5 * length(uv + vec2(sin(time * 0.3) * 0.25, cos(time * 0.25) * 0.18)));
  color += cloudTone * cloud;
  float vignette = 1.0 - smoothstep(0.35, 1.45, length(uv));
  color *= mix(1.0 - uVignette, 1.0, vignette);
  color = 1.0 - exp(-color * uBrightness);
  color.b *= uBlueBoost;

  // 白天模式保持白色基底，仅用纤维与空气层轻微着墨。
  vec3 outputColor;
  if (uLightMode > 0.5) {
    float edgeFade = mix(1.0 - uVignette, 1.0, vignette);
    float fibers = pow(smoothstep(0.12, 1.05, fiberField) * edgeFade, 1.5);
    float atmosphere = (center * 0.025 + cloud * 0.015) * edgeFade;
    vec3 fiberInk = mix(backdrop, uLineColor, 0.52);
    vec3 airColor = mix(backdrop, uGlowColor, 0.16);
    outputColor = mix(backdrop, airColor, atmosphere);
    outputColor = mix(outputColor, fiberInk, fibers * 0.3);
  } else {
    outputColor = backdrop + color;
  }
  float noise = (layeredGrain(gl_FragCoord.xy) - 0.5) * uGrain;
  outputColor = clamp(outputColor + noise, 0.0, 1.0);
  fragColor = vec4(outputColor, 1.0);
}
`

/** 从实际参数创建 uniform，首帧直接使用当前配置。 */
function createUniforms() {
  return {
    uResolution: { value: new Float32Array([1, 1]) },
    uTime: { value: 0 },
    uSpeed: { value: props.speed },
    uScale: { value: props.scale },
    uRotation: { value: props.rotation },
    uRotationSpeed: { value: props.rotationSpeed },
    uLayers: { value: Math.min(Math.max(Math.round(props.layers), 1), 10) },
    uWaveAmplitude: { value: props.waveAmplitude },
    uWaveFrequency: { value: props.waveFrequency },
    uWaveSpeed: { value: props.waveSpeed },
    uLayerSpeed: { value: props.layerSpeed },
    uTwist: { value: props.twist },
    uTwistFrequency: { value: props.twistFrequency },
    uTwistSpeed: { value: props.twistSpeed },
    uLineFrequency: { value: props.lineFrequency },
    uLineSpacing: { value: props.lineSpacing },
    uLineSharpness: { value: props.lineSharpness },
    uGlowFalloff: { value: props.glowFalloff },
    uGlowIntensity: { value: props.glowIntensity },
    uBrightness: { value: props.brightness },
    uBlueBoost: { value: props.blueBoost },
    uVignette: { value: props.vignette },
    uGrain: { value: props.grain },
    uLightMode: { value: props.lightMode ? 1 : 0 },
    uLineColor: { value: hexToRgb(props.lineColor) },
    uGlowColor: { value: hexToRgb(props.glowColor) },
  }
}

/** 保留当前渲染实例，用于参数热更新与统一资源释放。 */
interface RenderContext {
  uniforms: ReturnType<typeof createUniforms>
  refresh: () => void
}

const containerRef = ref<HTMLDivElement | null>(null)
let context: RenderContext | null = null
let teardown: (() => void) | null = null

/** 初始化 WebGL2；不支持时保留页面的纯白背景，登录仍可操作。 */
function setup() {
  const container = containerRef.value
  if (!container)
    return
  const canvas = document.createElement('canvas')
  if (!canvas.getContext('webgl2', { alpha: false, antialias: false }))
    return
  const renderer = new Renderer({ canvas, webgl: 2, alpha: false, antialias: false, dpr: Math.min(Math.max(props.dpr, 0.5), 2) })
  const gl = renderer.gl
  canvas.style.cssText = 'width:100%;height:100%;display:block'
  canvas.setAttribute('aria-hidden', 'true')
  container.appendChild(canvas)
  const uniforms = createUniforms()
  const geometry = new Triangle(gl)
  const program = new Program(gl, { vertex, fragment, uniforms })
  const mesh = new Mesh(gl, { geometry, program })
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  let frameId = 0
  let elapsed = 0
  let previousTime = performance.now()
  let lastRenderTime = 0
  let isVisible = true
  let contextLost = false

  /** 后台、离屏、暂停或减少动态效果时不持续占用 GPU。 */
  function canAnimate() {
    return isVisible && !document.hidden && !props.paused && !reducedMotion.matches && !contextLost
  }
  /** 绘制当前帧，暂停时也允许参数更新后重绘。 */
  function render() {
    if (!contextLost)
      renderer.render({ scene: mesh })
  }
  /** 释放已排队的动画帧，避免切换路由后继续运行。 */
  function stop() {
    cancelAnimationFrame(frameId)
    frameId = 0
  }
  /** 按目标帧率绘制，恢复页面时避免时间跳变。 */
  function loop(now: number) {
    frameId = 0
    if (!canAnimate())
      return
    elapsed += Math.min((now - previousTime) / 1000, 0.1)
    previousTime = now
    const fps = Math.min(Math.max(props.fps, 1), 120)
    if (now - lastRenderTime >= 1000 / fps - 0.5) {
      uniforms.uTime.value = elapsed
      render()
      lastRenderTime = now
    }
    frameId = requestAnimationFrame(loop)
  }
  /** 合并可见性、配置与系统偏好的变化，防止重复动画循环。 */
  function refresh() {
    if (!canAnimate()) {
      stop()
      render()
      return
    }
    if (frameId !== 0)
      return
    previousTime = performance.now()
    frameId = requestAnimationFrame(loop)
  }
  /** 按容器真实尺寸更新画布，分辨率使用实际绘图缓冲区。 */
  function setSize() {
    if (contextLost)
      return
    const rect = container!.getBoundingClientRect()
    renderer.setSize(Math.max(1, Math.floor(rect.width)), Math.max(1, Math.floor(rect.height)))
    uniforms.uResolution.value.set([gl.drawingBufferWidth, gl.drawingBufferHeight])
    render()
  }
  /** 显卡上下文丢失时暂停，等待浏览器恢复后重建资源。 */
  function handleContextLost(event: Event) {
    event.preventDefault()
    contextLost = true
    stop()
  }
  /** 上下文恢复后重新构建着色器和几何体。 */
  function handleContextRestored() {
    teardown?.()
    setup()
  }
  const resizeObserver = new ResizeObserver(setSize)
  const intersectionObserver = new IntersectionObserver(([entry]) => {
    // 回调使用浏览器提供的实际相交状态，不用其他状态代替。
    isVisible = entry!.isIntersecting
    refresh()
  })
  resizeObserver.observe(container)
  intersectionObserver.observe(container)
  document.addEventListener('visibilitychange', refresh)
  reducedMotion.addEventListener('change', refresh)
  canvas.addEventListener('webglcontextlost', handleContextLost)
  canvas.addEventListener('webglcontextrestored', handleContextRestored)
  context = { uniforms, refresh }
  setSize()
  refresh()

  /** 卸载时同时清理监听、观察器和 GPU 资源。 */
  teardown = () => {
    stop()
    resizeObserver.disconnect()
    intersectionObserver.disconnect()
    document.removeEventListener('visibilitychange', refresh)
    reducedMotion.removeEventListener('change', refresh)
    canvas.removeEventListener('webglcontextlost', handleContextLost)
    canvas.removeEventListener('webglcontextrestored', handleContextRestored)
    geometry.remove()
    program.remove()
    canvas.remove()
    gl.getExtension('WEBGL_lose_context')?.loseContext()
    context = null
    teardown = null
  }
}

onMounted(setup)
onUnmounted(() => {
  // 路由离开后停止渲染并释放上下文。
  teardown?.()
})
watch(() => ({ ...props }), (current, previous) => {
  // 像素密度需要重建画布，其他参数直接更新现有 uniform。
  if (current.dpr !== previous.dpr) {
    teardown?.()
    setup()
    return
  }
  if (!context)
    return
  const next = createUniforms()
  for (const key of Object.keys(next) as (keyof typeof next)[]) {
    if (key !== 'uTime' && key !== 'uResolution')
      context.uniforms[key].value = next[key].value
  }
  context.refresh()
})
</script>

<template>
  <div ref="containerRef" class="relative h-full w-full overflow-hidden" :class="className" />
</template>
