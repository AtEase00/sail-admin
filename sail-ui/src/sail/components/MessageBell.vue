<script setup lang="ts">
import { Bell, CheckCheck, Inbox } from '@lucide/vue'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { InboxMessage, MessageSettings } from '@/sail/message-center'
import type { PageResult } from '@/services/types/sail'

import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { useMessageError } from '@/composables/use-message-error'
import { request } from '@/lib/api-client'
import { errorMessage } from '@/lib/message'

const props = defineProps<{ unread: number }>()
const router = useRouter()
const open = ref(false)
const enabled = ref(false)
const rows = ref<InboxMessage[]>([])
const busy = ref(false)
const error = useMessageError()
/** 读取后台开关，不自行推测缺失配置。 */
async function settings() {
  try {
    enabled.value = (await request<MessageSettings>('/messages/settings')).reminderEnabled === 1
  }
  catch {
    enabled.value = false
  }
}
/** 展开时读取最近未读，复用外层唯一实时连接，不另建 WebSocket。 */
async function load() {
  busy.value = true
  error.value = ''
  try {
    rows.value = (await request<PageResult<InboxMessage>>('/messages/mine', 'GET', undefined, { folder: 'unread', page: 1, size: 5 })).records
  }
  catch (failure) {
    error.value = errorMessage(failure, '消息加载失败')
  }
  finally {
    busy.value = false
  }
}
/** 关闭提醒浮层并跳转个人收件箱或指定详情。 */
function visit(id?: number) {
  open.value = false
  void router.push({ path: '/messages', query: id ? { message: id } : {} })
}
/** 批量已读后通知布局同步未读数量。 */
async function readAll() {
  try {
    await request('/messages/read-all', 'PUT')
    window.dispatchEvent(new Event('sail:messages-read'))
    await load()
  }
  catch (failure) {
    error.value = errorMessage(failure, '标记失败')
  }
}
watch(open, (value) => {
  if (value)
    void load()
})
watch(() => props.unread, () => {
  if (open.value)
    void load()
})
onMounted(() => {
  void settings()
  window.addEventListener('sail:message-settings-changed', settings)
})
onBeforeUnmount(() => window.removeEventListener('sail:message-settings-changed', settings))
</script>

<template>
  <Popover v-if="enabled" v-model:open="open">
    <PopoverTrigger as-child>
      <Button variant="ghost" size="icon" class="relative size-8" :aria-label="`消息提醒，${unread} 条未读`">
        <Bell class="size-4" /><span v-if="unread" class="absolute -top-1 -right-1 min-w-4 rounded bg-destructive px-1 text-[10px] leading-4 text-white">{{ unread > 99 ? '99+' : unread }}</span>
      </Button>
    </PopoverTrigger><PopoverContent align="end" class="w-[min(380px,calc(100vw-24px))] p-0">
      <div class="flex items-center justify-between border-b px-4 py-3">
        <h2 class="text-sm font-semibold">
          未读消息 <span class="text-primary">{{ unread }}</span>
        </h2><Button variant="ghost" size="sm" :disabled="busy || !unread" @click="readAll">
          <CheckCheck class="size-3.5" />全部已读
        </Button>
      </div><div class="max-h-96 divide-y overflow-y-auto">
        <button v-for="message in rows" :key="message.id" class="block w-full px-4 py-3 text-left hover:bg-muted/40" @click="visit(message.id)">
          <p class="truncate text-sm font-medium">
            {{ message.title }}
          </p><p class="mt-1 truncate text-xs text-muted-foreground">
            {{ message.summary || '查看消息详情' }}
          </p><p class="mt-2 text-xs text-muted-foreground">
            {{ message.createdAt }}
          </p>
        </button><div v-if="!rows.length && !error" class="flex flex-col items-center gap-2 p-8 text-sm text-muted-foreground">
          <Inbox class="size-7 stroke-1" />{{ busy ? '正在读取…' : '暂无未读消息' }}
        </div>
      </div><button class="w-full border-t p-3 text-center text-sm text-primary hover:bg-muted/25" @click="visit()">
        查看全部消息
      </button>
    </PopoverContent>
  </Popover>
</template>
