<script setup lang="ts">
import { CircleCheck, CircleX, Info, TriangleAlert } from '@lucide/vue'

import { messageItems } from '@/lib/message'

const icons = { success: CircleCheck, error: CircleX, warning: TriangleAlert, info: Info }
const labels = { success: '成功', error: '错误', warning: '警告', info: '信息' }
</script>

<template>
  <Teleport to="body">
    <div class="sail-global-message" aria-label="操作反馈" aria-live="polite" aria-relevant="additions" data-global-message>
      <TransitionGroup name="global-message" tag="div" class="sail-message-stack">
        <div v-for="item in messageItems" :key="item.id" class="sail-message-notice" :data-message-type="item.type" :role="item.type === 'error' || item.type === 'warning' ? 'alert' : 'status'" aria-atomic="true">
          <component :is="icons[item.type]" class="sail-message-icon" :aria-label="labels[item.type]" />
          <span>{{ item.content }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style>
.sail-global-message {
  position: fixed;
  inset: max(24px, env(safe-area-inset-top)) 12px auto;
  z-index: 10000;
  pointer-events: none;
}
.sail-message-stack {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 12px;
}
.sail-message-notice {
  display: flex;
  align-items: center;
  gap: 9px;
  max-width: min(520px, 100%);
  padding: 10px 16px;
  border-radius: 8px;
  background: var(--popover);
  color: var(--popover-foreground);
  box-shadow:
    0 6px 16px 0 #00000014,
    0 3px 6px -4px #0000001f,
    0 9px 28px 8px #0000000d;
  font-size: 14px;
  line-height: 22px;
  overflow-wrap: anywhere;
}
.sail-message-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}
[data-message-type='success'] .sail-message-icon {
  color: #52c41a;
}
[data-message-type='error'] .sail-message-icon {
  color: #ff4d4f;
}
[data-message-type='warning'] .sail-message-icon {
  color: #faad14;
}
[data-message-type='info'] .sail-message-icon {
  color: #1677ff;
}
.global-message-enter-active,
.global-message-leave-active,
.global-message-move {
  transition:
    transform 200ms ease,
    opacity 200ms ease;
}
.global-message-enter-from,
.global-message-leave-to {
  transform: translateY(-24px);
  opacity: 0;
}
.global-message-leave-active {
  position: absolute;
}
@media (prefers-reduced-motion: reduce) {
  .global-message-enter-active,
  .global-message-leave-active,
  .global-message-move {
    transition: none;
  }
}
</style>
