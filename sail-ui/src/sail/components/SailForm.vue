<script setup lang="ts">
import { nextTick, onBeforeUnmount } from 'vue'

const messages = new Map<HTMLElement, HTMLElement>()
/** 统一约束反馈为字段下方中文提示，保留浏览器原生有效性判断。 */
function invalid(event: Event) {
  const input = event.target as HTMLInputElement
  event.preventDefault()
  clear(input)
  const state = input.validity
  const message = state.valueMissing ? '请填写或选择此项' : state.typeMismatch ? '请输入正确的格式' : state.tooShort ? `至少输入 ${input.minLength} 个字符` : state.tooLong ? `最多输入 ${input.maxLength} 个字符` : state.rangeUnderflow ? `不能小于 ${input.min}` : state.rangeOverflow ? `不能大于 ${input.max}` : state.patternMismatch ? '输入格式不正确，请按字段要求填写' : state.stepMismatch ? '请输入有效的数值' : input.validationMessage
  const feedback = document.createElement('p')
  feedback.className = 'sail-field-error'
  feedback.textContent = message
  feedback.id = `sail-error-${input.id || input.name || messages.size}`
  const control = input.tagName === 'SELECT' ? input.parentElement?.querySelector<HTMLElement>('[role=combobox]') ?? input : input
  control.setAttribute('aria-invalid', 'true')
  control.setAttribute('aria-errormessage', feedback.id)
  input.insertAdjacentElement('afterend', feedback)
  messages.set(input, feedback)
  // 一次验证只聚焦第一个无效字段，不逐个跳动焦点。
  if (messages.size === 1)
    void nextTick(() => control.focus())
}
/** 用户修改后重新判断该字段，修正完成即清除错误。 */
function updated(event: Event) {
  const input = event.target as HTMLInputElement
  if (messages.has(input) && input.validity?.valid)
    clear(input)
}
/** 清理仅由表单建立的提示，不修改业务校验条件。 */
function clear(input: HTMLElement) {
  messages.get(input)?.remove()
  messages.delete(input)
  const control = input.tagName === 'SELECT' ? input.parentElement?.querySelector<HTMLElement>('[role=combobox]') ?? input : input
  control.removeAttribute('aria-invalid')
  control.removeAttribute('aria-errormessage')
}
onBeforeUnmount(() => messages.forEach((_, input) => clear(input)))
</script>

<template>
  <form class="sail-form" @invalid.capture="invalid" @input="updated" @change="updated">
    <slot />
  </form>
</template>
