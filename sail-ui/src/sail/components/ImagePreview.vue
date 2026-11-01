<script setup lang="ts">
import { FlipHorizontal2, FlipVertical2, Minus, Plus, RotateCcw, RotateCw, X } from '@lucide/vue'
import { FocusScope } from 'reka-ui'
import { defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue'

import { focusDialogContent } from '@/lib/dialog-focus'
import { imagePreviewCount } from '@/sail/image-preview-state'

const props = defineProps<{ src: string, alt?: string, open: boolean, loading?: boolean, error?: string }>()
const emit = defineEmits<{ close: [] }>()
// 阅读引擎按需下载，复用其缩放、旋转、拖动、滚轮和触屏手势。
const Lightbox = defineAsyncComponent(() => import('vue-easy-lightbox'))
const flipX = ref(1)
const flipY = ref(1)
let registered = false
/** 多个预览实例独立登记，避免关闭其中一个时提前恢复底层弹窗锁。 */
function register(open: boolean) {
  if (registered === open)
    return
  imagePreviewCount.value += open ? 1 : -1
  registered = open
  flipX.value = 1
  flipY.value = 1
}
watch(() => props.open, register, { immediate: true })
// 同一预览实例切换文件时复位镜像状态，不影响新图片的阅读方向。
watch(() => props.src, () => {
  flipX.value = 1
  flipY.value = 1
})
onBeforeUnmount(() => register(false))
</script>

<template>
  <Teleport to="body">
    <FocusScope v-if="open" trapped loop as-child @mount-auto-focus="focusDialogContent">
      <div class="sail-image-preview" role="dialog" aria-modal="true" :aria-label="alt || '图片预览'" tabindex="-1" :style="{ '--preview-flip-x': flipX, '--preview-flip-y': flipY }" @keydown.esc.stop.prevent="emit('close')">
        <Lightbox v-if="src && !error" :visible="open" :imgs="[{ src, alt }]" :zoom-scale="0.25" :max-zoom="10" :min-zoom="0.25" :mask-closable="false" title-disabled scroll-disabled @hide="emit('close')">
          <template #close-btn="{ close }">
            <button type="button" class="sail-preview-close" aria-label="关闭图片预览" title="关闭" @click="close">
              <X />
            </button>
          </template>
          <template #toolbar="{ toolbarMethods }">
            <div class="sail-preview-toolbar" role="toolbar" aria-label="图片预览工具">
              <button type="button" aria-label="水平翻转" title="水平翻转" @click="flipX *= -1">
                <FlipHorizontal2 />
              </button>
              <button type="button" aria-label="垂直翻转" title="垂直翻转" @click="flipY *= -1">
                <FlipVertical2 />
              </button>
              <button type="button" aria-label="向左旋转" title="向左旋转" @click="toolbarMethods.rotateLeft">
                <RotateCcw />
              </button>
              <button type="button" aria-label="向右旋转" title="向右旋转" @click="toolbarMethods.rotateRight">
                <RotateCw />
              </button>
              <span class="sail-preview-divider" />
              <button type="button" aria-label="缩小图片" title="缩小" @click="toolbarMethods.zoomOut">
                <Minus />
              </button>
              <button type="button" aria-label="放大图片" title="放大" @click="toolbarMethods.zoomIn">
                <Plus />
              </button>
            </div>
          </template>
          <template #onerror>
            <p role="alert" class="text-white">
              图片读取失败，请下载原文件查看。
            </p>
          </template>
        </Lightbox>
        <div v-else class="sail-preview-status" @keydown.esc.stop.prevent="emit('close')">
          <p :role="error ? 'alert' : 'status'">
            {{ error || (loading ? '正在读取图片…' : '图片不可用') }}
          </p>
          <button type="button" class="sail-preview-close" aria-label="关闭图片预览" @click="emit('close')">
            <X />
          </button>
        </div>
      </div>
    </FocusScope>
  </Teleport>
</template>

<style>
/* 保留轻量开源阅读引擎，外观按 Ant Design 的全屏预览组织。 */
.sail-image-preview .vel-modal {
  z-index: 90;
  background: rgb(0 0 0 / 65%);
  pointer-events: auto;
}
.sail-image-preview {
  position: fixed;
  inset: 0;
  z-index: 90;
  pointer-events: auto;
  outline: none;
}
.sail-preview-status {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: white;
  background: rgb(0 0 0 / 65%);
}
.sail-image-preview .vel-img {
  scale: var(--preview-flip-x) var(--preview-flip-y);
  background: transparent;
  box-shadow: none;
}
.sail-preview-toolbar {
  position: fixed;
  inset: auto 0 24px;
  display: flex;
  align-items: center;
  width: max-content;
  padding: 4px 12px;
  border-radius: 4px;
  background: rgb(0 0 0 / 55%);
  margin-inline: auto;
}
.sail-preview-close {
  position: fixed;
  top: 24px;
  right: 24px;
}
.sail-image-preview button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: white;
  opacity: 0.8;
  border-radius: 4px;
  cursor: pointer;
}
.sail-image-preview button:is(:hover, :focus-visible) {
  background: rgb(255 255 255 / 15%);
  opacity: 1;
}
.sail-image-preview button:focus-visible {
  outline: 2px solid white;
  outline-offset: 2px;
}
.sail-image-preview button svg {
  width: 20px;
  height: 20px;
}
.sail-preview-divider {
  width: 1px;
  height: 18px;
  margin-inline: 8px;
  background: rgb(255 255 255 / 25%);
}
@media (prefers-reduced-motion: reduce) {
  .sail-image-preview * {
    transition: none !important;
    animation: none !important;
  }
}
</style>
