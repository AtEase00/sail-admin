import { readonly, ref } from 'vue'

export type MessageType = 'success' | 'error' | 'warning' | 'info'
interface MessageItem { id: number, type: MessageType, content: string }
const items = ref<MessageItem[]>([])
const timers = new Map<number, ReturnType<typeof setTimeout>>()
let sequence = 0
export const messageItems = readonly(items)

/** 诊断字段留在 Error/HTTP 响应中，用户反馈只显示业务原因。 */
export function errorMessage(error: unknown, fallback = '操作失败，请重试'): string {
  const failure = error as { name?: string, data?: { message?: unknown } } | null
  const value = typeof failure?.data?.message === 'string' ? failure.data.message : failure?.name === 'FetchError' ? fallback : error instanceof Error ? error.message : typeof error === 'string' ? error : ''
  return value.replace(/[（(]追踪号\s[^）)]+[）)]/g, '').trim() || fallback
}
function dismiss(id: number) {
  clearTimeout(timers.get(id))
  timers.delete(id)
  items.value = items.value.filter(item => item.id !== id)
}
function show(type: MessageType, value: unknown, fallback?: string) {
  const content = errorMessage(value, fallback)
  const existing = items.value.find(item => item.type === type && item.content === content)
  // 同一请求被多个消费方处理时只保留一条反馈；重复反馈不延长驻留。
  if (existing)
    return existing.id
  const id = ++sequence
  if (items.value.length >= 3)
    dismiss(items.value[0]!.id)
  items.value.push({ id, type, content })
  timers.set(id, setTimeout(dismiss, type === 'error' ? 4500 : 3000, id))
  return id
}
export const message = {
  success: (value: string) => show('success', value),
  error: (value: unknown, fallback?: string) => show('error', value, fallback),
  warning: (value: string) => show('warning', value),
  info: (value: string) => show('info', value),
  dismiss,
  clear() {
    for (const id of timers.keys())
      dismiss(id)
  },
}
