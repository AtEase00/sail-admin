<script setup lang="ts">
import { CheckCheck } from '@lucide/vue'
import { Drawer, Modal } from 'ant-design-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { toast } from 'vue-sonner'

import type { PreviewFile } from '@/sail/file-preview'
import type { InboxMessage } from '@/sail/message-center'

import { Button } from '@/components/ui/button'
import { request } from '@/lib/api-client'
import { messageUnavailable } from '@/sail/message-center'
import { useDictionaryStore } from '@/stores/dictionaries'

import FileAttachment from './FileAttachment.vue'
import FilePreview from './FilePreview.vue'
import MessageContentView from './MessageContentView.vue'

const props = withDefaults(defineProps<{ id?: number, presentation?: 'drawer' | 'popup' }>(), { presentation: 'drawer' })
const emit = defineEmits<{ changed: [], presented: [id: number] }>()
const preview = ref<PreviewFile>()
const open = defineModel<boolean>('open', { default: false })
const dictionaries = useDictionaryStore()
const message = ref<InboxMessage>()
const busy = ref(false)
const error = ref('')
let revision = 0
let expiryTimer: number | undefined
watch(() => message.value?.taskStatus, () => {
  if (message.value && messageUnavailable(message.value))
    preview.value = undefined
})
const unavailable = computed(() => !!message.value && messageUnavailable(message.value))
/** 打开才读取正文并记录首次阅读，不预读整页消息。 */
async function load() {
  if (!open.value || !props.id)
    return
  const current = ++revision
  busy.value = true
  error.value = ''
  message.value = undefined
  try {
    const result = await request<InboxMessage>(`/messages/mine/${props.id}`)
    if (current !== revision)
      return
    message.value = result
    // 正文实际挂载后才记录展示，关闭或切换消息不能产生过期回执。
    await nextTick()
    if (current !== revision || !open.value)
      return
    if (props.presentation === 'popup' && !messageUnavailable(result))
      emit('presented', result.id)
    window.clearTimeout(expiryTimer)
    // 打开期间到期时重新取服务端状态，不继续展示已经失效的正文与附件。
    if (result.expiresAt && !messageUnavailable(result)) {
      const delay = new Date(result.expiresAt.replace(' ', 'T')).getTime() - Date.now()
      expiryTimer = window.setTimeout(load, Math.min(delay + 50, 2147483647))
    }
    window.dispatchEvent(new Event('sail:messages-read'))
    emit('changed')
  }
  catch (failure) {
    if (current === revision)
      error.value = failure instanceof Error ? failure.message : '消息读取失败'
  }
  finally {
    if (current === revision)
      busy.value = false
  }
}
/** 确认必须主动点击，更新后保留实际确认时间。 */
async function confirm() {
  if (!message.value || busy.value)
    return
  busy.value = true
  try {
    await request(`/messages/mine/${message.value.id}/confirm`, 'PUT')
    message.value = await request<InboxMessage>(`/messages/mine/${message.value.id}`)
    emit('changed')
    toast.success('已确认此消息')
  }
  catch (failure) {
    error.value = failure instanceof Error ? failure.message : '确认失败'
  }
  finally {
    busy.value = false
  }
}
watch(() => [open.value, props.id], () => {
  preview.value = undefined
  if (!open.value) {
    revision++
    window.clearTimeout(expiryTimer)
    busy.value = false
    return
  }
  void load()
}, { immediate: true })
onMounted(() => window.addEventListener('sail:messages-changed', load))
onBeforeUnmount(() => {
  revision++
  window.clearTimeout(expiryTimer)
  window.removeEventListener('sail:messages-changed', load)
})
</script>

<template>
  <component :is="presentation === 'popup' ? Modal : Drawer" v-model:open="open" :title="presentation === 'popup' ? '系统公告' : '消息详情'" :width="presentation === 'popup' ? 'min(760px,94vw)' : 'min(800px,96vw)'" :z-index="presentation === 'popup' ? 80 : 60" :root-style="{ zIndex: presentation === 'popup' ? 80 : 60 }" :footer="null" :mask-closable="false" :destroy-on-close="true">
    <FilePreview :file="preview" @close="preview = undefined" />
    <p v-if="busy && !message" class="p-12 text-center text-muted-foreground">
      正在读取消息…
    </p><p v-if="error" role="alert" class="mb-5 text-sm text-destructive">
      {{ error }}
    </p><article v-if="message" class="space-y-6">
      <header class="space-y-3 border-b pb-5">
        <div class="flex flex-wrap gap-2 text-xs">
          <span v-if="message.category" class="rounded bg-muted px-2 py-1">{{ dictionaries.label('message_category', message.category) }}</span><span v-if="message.priority && message.priority !== 'NORMAL'" class="rounded bg-primary/10 px-2 py-1 text-primary">{{ dictionaries.label('message_priority', message.priority) }}</span><span v-if="message.pinned" class="rounded bg-muted px-2 py-1">置顶</span>
        </div><h2 class="text-2xl leading-9 font-semibold break-words">
          {{ message.title }}
        </h2><p class="text-xs leading-6 text-muted-foreground">
          {{ message.senderName || '系统' }} · {{ message.createdAt }}<span v-if="message.expiresAt" class="block">有效期至 {{ message.expiresAt }}</span>
        </p>
      </header><div v-if="unavailable" class="rounded border bg-muted/30 p-8 text-center text-sm text-muted-foreground">
        {{ message.taskStatus === 'WITHDRAWN' ? '发布者已撤回此消息，正文与附件不再展示。' : '此消息已超过有效期，正文与附件不再展示。' }}
      </div><template v-else>
        <MessageContentView :content="message.content || ''" :task-id="message.taskId" /><section v-if="message.attachments?.length" class="border-t pt-5">
          <h3 class="mb-3 text-sm font-medium">
            附件（{{ message.attachments.length }}）
          </h3><div class="grid gap-2">
            <FileAttachment v-for="file in message.attachments" :key="file.fileId" :file="{ id: file.fileId, taskId: message.taskId, name: file.name, contentType: file.contentType, size: file.fileSize }" downloadable @preview="preview = $event" />
          </div>
        </section><div v-if="message.actionPath" class="border-t pt-5">
          <Button as-child variant="outline">
            <RouterLink :to="message.actionPath" @click="open = false">
              查看相关业务
            </RouterLink>
          </Button>
        </div><footer class="flex flex-wrap items-center justify-between gap-3 border-t pt-5">
          <span class="text-xs text-muted-foreground">{{ message.readAt ? `首次阅读 ${message.readAt}` : '尚未阅读' }}</span><template v-if="message.requireConfirmation">
            <span v-if="message.confirmedAt" class="text-sm text-primary">已确认 · {{ message.confirmedAt }}</span><Button v-else :disabled="busy" @click="confirm">
              <CheckCheck class="size-4" />确认已知悉
            </Button>
          </template>
        </footer>
      </template>
    </article>
  </component>
</template>
