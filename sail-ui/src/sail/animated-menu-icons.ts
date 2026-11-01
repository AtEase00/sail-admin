import type { Component } from 'vue'

import { defineComponent, h } from 'vue'

/** Vue 动效包装仅响应悬停和键盘焦点，不改变导航图标占位。 */
export function createAnimatedIcon(icon: Component, motion: 'ring' | 'rotate' | 'pulse' | 'send') {
  return defineComponent({ inheritAttrs: false, setup: (_, { attrs }) => () => h(icon, { ...attrs, class: [attrs.class, 'sail-animated-icon', `sail-icon-${motion}`] }) })
}

/** 参考 Lucide Animated 左对齐的三条线交互，使用 Vue SVG/CSS，无 React 运行时。 */
export const AnimatedAlignLeft = defineComponent({
  inheritAttrs: false,
  setup: (_, { attrs }) => () => h('svg', { ...attrs, 'class': [attrs.class, 'sail-animated-align'], 'viewBox': '0 0 24 24', 'fill': 'none', 'stroke': 'currentColor', 'stroke-width': 2, 'stroke-linecap': 'round', 'aria-hidden': true }, [h('path', { d: 'M3 6h18' }), h('path', { d: 'M3 12h12', class: 'sail-align-middle' }), h('path', { d: 'M3 18h14', class: 'sail-align-bottom' })]),
})
