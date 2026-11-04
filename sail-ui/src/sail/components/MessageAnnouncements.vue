<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage } from '@/lib/message'

import MessageDetail from './MessageDetail.vue'

const open = ref(false)
const current = ref<number>()
const error = useMessageError()
const shown = new Set<number>()
const pendingReceipts = new Set<number>()
let loading = false
let stopped = false
/** 展示回执持久化失败时保留待重试集合，当前会话不反复打断用户。 */
async function presented(id: number) {
  if (shown.has(id))
    return
  shown.add(id)
  pendingReceipts.add(id)
  await acknowledge()
}
/** 只重试确实已经成功展示的回执，不用已读批处理代替公告展示。 */
async function acknowledge() {
  for (const id of [...pendingReceipts]) {
    try {
      await request(`/messages/mine/${id}/presented`, 'PUT')
      pendingReceipts.delete(id)
      error.value = ''
    }
    catch (failure) { error.value = errorMessage(failure, '公告展示回执保存失败，将自动重试') }
  }
}
/** 每次仅展示一条，登录、重连和轮询从数据库补读，按服务端优先级依次处理。 */
async function refresh() {
  if (loading || stopped)
    return
  loading = true
  try {
    await acknowledge()
    if (open.value)
      return
    const rows = await request<{ id: number }[]>('/messages/announcements')
    if (stopped)
      return
    const next = rows.find(row => !shown.has(row.id))
    if (next) {
      current.value = next.id
      open.value = true
    }
  }
  catch { /* 断线期间保持收件箱入口，下一次实时信号或轮询补读。 */ }
  finally { loading = false }
}
/** 会话失效立即关闭公告，不沿用旧账号的接收上下文。 */
function stop() {
  stopped = true
  open.value = false
  current.value = undefined
  shown.clear()
  pendingReceipts.clear()
}
watch(open, (value) => {
  if (!value && !stopped) {
    if (current.value)
      shown.add(current.value)
    void refresh()
  }
})
onMounted(() => {
  void refresh()
  window.addEventListener('sail:messages-changed', refresh)
  window.addEventListener('sail:message-feed-check', refresh)
  window.addEventListener('sail:session-expired', stop)
})
onBeforeUnmount(() => {
  stop()
  window.removeEventListener('sail:messages-changed', refresh)
  window.removeEventListener('sail:message-feed-check', refresh)
  window.removeEventListener('sail:session-expired', stop)
})
</script>

<template>
  <MessageDetail :id="current" v-model:open="open" presentation="popup" @presented="presented" />
</template>
