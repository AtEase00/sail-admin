<script setup lang="ts">
import { nextTick, onBeforeUnmount } from 'vue'

import { message } from '@/lib/message'

const invalidFields = new Set<HTMLInputElement>()
let reporting = false
/** 原生约束保留在字段上，每次提交只通过全局 Message 报告第一个错误。 */
function invalid(event: Event) {
  const input = event.target as HTMLInputElement
  event.preventDefault()
  const control = input.tagName === 'SELECT' ? input.parentElement?.querySelector<HTMLElement>('[role=combobox]') ?? input : input
  control.setAttribute('aria-invalid', 'true')
  invalidFields.add(input)
  if (reporting)
    return
  reporting = true
  const state = input.validity
  const reason = state.valueMissing ? '请填写或选择此项' : state.typeMismatch ? '格式不正确' : state.tooShort ? `至少 ${input.minLength} 个字符` : state.tooLong ? `最多 ${input.maxLength} 个字符` : state.rangeUnderflow ? `不能小于 ${input.min}` : state.rangeOverflow ? `不能大于 ${input.max}` : '输入格式不正确'
  const label = input.labels?.[0]?.textContent?.replace(/\*/g, '').trim()
  message.warning(label ? `${label}：${reason}` : reason)
  void nextTick(() => {
    control.focus()
    reporting = false
  })
}
function clear(input: HTMLInputElement) {
  invalidFields.delete(input)
  const control = input.tagName === 'SELECT' ? input.parentElement?.querySelector<HTMLElement>('[role=combobox]') ?? input : input
  control.removeAttribute('aria-invalid')
}
function updated(event: Event) {
  const input = event.target as HTMLInputElement
  if (invalidFields.has(input) && input.validity?.valid)
    clear(input)
}
onBeforeUnmount(() => invalidFields.forEach(clear))
</script>

<template>
  <form class="sail-form" @invalid.capture="invalid" @input="updated" @change="updated">
    <slot />
  </form>
</template>
