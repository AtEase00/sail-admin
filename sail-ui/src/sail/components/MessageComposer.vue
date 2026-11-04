<script setup lang="ts">
import { FileText, ImagePlus, Paperclip, Save, Send } from '@lucide/vue'
import { Input as AInput, Select as ASelect, Switch as ASwitch, DatePicker, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'

import type { PreviewFile } from '@/sail/file-preview'
import type { AudiencePreview, MessageAttachment, MessageDraft, MessageSettings, MessageTask, MessageTemplate } from '@/sail/message-center'

import { Button } from '@/components/ui/button'
import { useMessageError } from '@/composables/use-message-error'
import { apiFetch, request } from '@/lib/api-client'
import { errorMessage, message as feedback } from '@/lib/message'
import FileAttachment from '@/sail/components/FileAttachment.vue'
import FilePreview from '@/sail/components/FilePreview.vue'
import MessageContentView from '@/sail/components/MessageContentView.vue'
import MessageEditor from '@/sail/components/MessageEditor.vue'
import MessageRecipients from '@/sail/components/MessageRecipients.vue'
import MessageTheme from '@/sail/components/MessageTheme.vue'
import { FILE_ACCEPT } from '@/sail/file-preview'
import { cloneAudience, copyMessageDraft, newMessageDraft, templateVariables, uploadMessageAttachment } from '@/sail/message-center'
import { canAccessPage, navigation } from '@/sail/navigation'
import { useAuthStore } from '@/stores/auth'
import { useDictionaryStore } from '@/stores/dictionaries'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const dictionaries = useDictionaryStore()
const draft = reactive<MessageDraft>(newMessageDraft())
const taskId = ref<number>()
const attachmentPreview = ref<PreviewFile>()
const attachments = ref<MessageAttachment[]>([])
const templates = ref<MessageTemplate[]>([])
const selectedTemplate = ref<number>()
const templateOpen = ref(false)
const variables = reactive<Record<string, string>>({})
const settings = ref<MessageSettings>()
const busy = ref(false)
const uploading = ref(false)
const error = useMessageError()
const savedAt = ref('')
const scheduled = ref(false)
const expiring = ref(false)
const preview = ref<AudiencePreview>()
const previewOpen = ref(false)
const baseline = ref('')
const editor = ref<InstanceType<typeof MessageEditor>>()
const fileInput = ref<HTMLInputElement>()
const imageInput = ref<HTMLInputElement>()
const urls: string[] = []
let loadingVersion = 0
let submitted = false
const template = computed(() => templates.value.find(item => item.id === selectedTemplate.value))
const variableNames = computed(() => template.value ? templateVariables(`${template.value.title} ${template.value.content}`) : [])
const dirty = computed(() => baseline.value !== JSON.stringify(draft))
const links = computed(() => navigation.filter(item => canAccessPage(item, auth)).map(item => ({ value: item.path, label: item.title })))
/** 只提交可编辑字段，正文和收件规则的版本由服务端返回后更新。 */
function payload(): MessageDraft {
  return { requestKey: draft.requestKey, version: draft.version, title: draft.title, content: draft.content, category: draft.category, priority: draft.priority, requireConfirmation: draft.requireConfirmation, displayMode: draft.displayMode, pinned: draft.pinned, actionPath: draft.actionPath || null, audience: cloneAudience(draft.audience), attachmentIds: attachments.value.map(file => file.fileId), templateCode: draft.templateCode, scheduledAt: scheduled.value ? draft.scheduledAt : null, expiresAt: expiring.value ? draft.expiresAt : null }
}
/** 编辑已有草稿或复制自己的历史消息，不沿用原发布任务和时间。 */
async function loadDraft() {
  if (route.path !== '/messages/history')
    return
  if (!route.query.draft && !route.query.copy && submitted) {
    Object.assign(draft, newMessageDraft())
    taskId.value = undefined
    attachments.value = []
    scheduled.value = false
    expiring.value = false
    savedAt.value = ''
    selectedTemplate.value = undefined
    error.value = ''
    Object.keys(variables).forEach(key => delete variables[key])
    urls.splice(0).forEach(url => URL.revokeObjectURL(url))
    baseline.value = JSON.stringify(draft)
    submitted = false
  }
  const id = Number(route.query.draft || route.query.copy)
  if (!id || (id === taskId.value && !route.query.copy))
    return
  const current = ++loadingVersion
  busy.value = true
  error.value = ''
  try {
    const task = await request<MessageTask>(`/messages/tasks/${id}`)
    if (current !== loadingVersion)
      return
    if (task.senderId !== auth.identity?.user.id)
      throw new Error('只能编辑或复制本人发布的消息')
    const copy = !!route.query.copy
    if (!copy && !['DRAFT', 'CANCELLED'].includes(task.status))
      throw new Error('此消息已经发布，请从发布记录复制为新消息')
    const input = copy ? copyMessageDraft(task) : { ...newMessageDraft(), requestKey: task.requestKey, version: task.version, title: task.title, content: task.content, category: task.category, priority: task.priority, requireConfirmation: task.requireConfirmation, displayMode: task.displayMode, pinned: task.pinned, scheduledAt: task.scheduledAt, expiresAt: task.expiresAt, actionPath: task.actionPath, templateCode: task.templateCode, attachmentIds: task.attachments.map(file => file.fileId), audience: cloneAudience(task.audience) }
    Object.assign(draft, input)
    taskId.value = copy ? undefined : id
    attachments.value = task.attachments
    scheduled.value = !!draft.scheduledAt
    expiring.value = !!draft.expiresAt
    savedAt.value = copy ? '' : task.updatedAt
    baseline.value = JSON.stringify(draft)
    // 编辑图片只使用当前会话生成的私有预览地址，不持久化 Blob 地址。
    await nextTick()
    draft.content = editor.value?.getContent() ?? draft.content
    baseline.value = JSON.stringify(draft)
    for (const file of task.attachments.filter(file => ['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(file.contentType))) {
      const blob = await apiFetch<Blob, 'blob'>(`/file/objects/${file.fileId}/download`, { responseType: 'blob' })
      if (current !== loadingVersion)
        return
      const url = URL.createObjectURL(new Blob([blob], { type: file.contentType }))
      urls.push(url)
      const image = document.querySelector<HTMLImageElement>(`.message-editor img[data-file-id="${file.fileId}"]`)
      if (image)
        image.src = url
    }
  }
  catch (failure) {
    error.value = errorMessage(failure, '草稿加载失败')
  }
  finally {
    if (current === loadingVersion)
      busy.value = false
  }
}
/** 保存与预览共享一次写入，发布确认使用刚返回的版本。 */
async function saveDraft(showToast = true): Promise<MessageTask> {
  const input = payload()
  const result = await request<MessageTask>(taskId.value ? `/messages/tasks/${taskId.value}` : '/messages/tasks', taskId.value ? 'PUT' : 'POST', input as unknown as Record<string, unknown>)
  taskId.value = result.id
  Object.assign(draft, { ...input, version: result.version })
  baseline.value = JSON.stringify(draft)
  savedAt.value = result.updatedAt
  await router.replace({ query: { compose: '1', draft: result.id } })
  if (showToast)
    feedback.success('草稿已保存')
  return result
}
/** 保存失败保持输入，防止用户内容丢失。 */
async function save() {
  if (busy.value || uploading.value)
    return
  busy.value = true
  error.value = ''
  try {
    await saveDraft()
  }
  catch (failure) {
    error.value = errorMessage(failure, '保存失败')
  }
  finally {
    busy.value = false
  }
}
/** 发布前保存并解析真实收件人数，确认后才能冻结快照。 */
async function prepare() {
  if (busy.value || uploading.value)
    return
  if (!draft.title.trim() || (!draft.content.replace(/<[^>]*>/g, '').trim() && !draft.content.includes('data-file-id'))) {
    error.value = '请填写消息标题和正文'
    return
  }
  if ((scheduled.value && !draft.scheduledAt) || (expiring.value && !draft.expiresAt)) {
    error.value = '请填写已开启的发送时间与有效期'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const task = await saveDraft(false)
    preview.value = await request<AudiencePreview>(`/messages/tasks/${task.id}/preview`, 'POST')
    previewOpen.value = true
  }
  catch (failure) {
    error.value = errorMessage(failure, '预览失败')
  }
  finally {
    busy.value = false
  }
}
/** 使用确认指纹提交；服务端在事务内重新验证人数、版本和权限。 */
async function publish() {
  if (busy.value || !preview.value || !taskId.value)
    return
  busy.value = true
  try {
    const result = await request<MessageTask>(`/messages/tasks/${taskId.value}/publish`, 'POST', { version: preview.value.version, fingerprint: preview.value.fingerprint })
    submitted = true
    previewOpen.value = false
    feedback.success(result.status === 'SCHEDULED' ? '已加入定时发布' : '消息已进入发送队列')
    await router.push('/messages/history')
  }
  catch (failure) {
    error.value = errorMessage(failure, '发布失败')
    previewOpen.value = false
  }
  finally {
    busy.value = false
  }
}
/** 图片与普通附件共用私有上传，超量时不产生多余文件。 */
async function upload(event: Event, image: boolean) {
  const input = event.target as HTMLInputElement
  const files = [...(input.files ?? [])]
  input.value = ''
  if (!files.length || !settings.value)
    return
  if (attachments.value.length + files.length > settings.value.maxAttachments) {
    error.value = `每条消息最多 ${settings.value.maxAttachments} 个附件（含正文图片）`
    return
  }
  if (image && files.some(file => !['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(file.type))) {
    error.value = '正文图片仅支持 PNG、JPEG、GIF、WebP'
    return
  }
  uploading.value = true
  error.value = ''
  try {
    for (const file of files) {
      const saved = await uploadMessageAttachment(file)
      attachments.value.push(saved)
      draft.attachmentIds = attachments.value.map(item => item.fileId)
      if (image) {
        const url = URL.createObjectURL(file)
        urls.push(url)
        editor.value?.insertImage(saved.fileId, url, saved.name)
      }
    }
  }
  catch (failure) {
    error.value = errorMessage(failure, '附件上传失败')
  }
  finally {
    uploading.value = false
  }
}
/** 移除文件时同步清理正文图片引用及待保存附件编号。 */
function removeAttachment(fileId: number) {
  editor.value?.removeImage(fileId)
  attachments.value = attachments.value.filter(file => file.fileId !== fileId)
  draft.attachmentIds = attachments.value.map(file => file.fileId)
}
/** 变量由服务端转义和渲染，不使用浏览器正则拼接 HTML。 */
async function applyTemplate() {
  if (!template.value)
    return
  busy.value = true
  try {
    const rendered = await request<{ title: string, content: string }>(`/messages/templates/${template.value.id}/preview`, 'POST', { variables: Object.fromEntries(variableNames.value.map(key => [key, variables[key] ?? ''])) })
    draft.title = rendered.title
    draft.content = rendered.content
    draft.templateCode = template.value.code
    templateOpen.value = false
  }
  catch (failure) {
    error.value = errorMessage(failure, '模板应用失败')
  }
  finally {
    busy.value = false
  }
}
/** 未保存离开使用明确确认，避免误操作清空正文。 */
function confirmLeave() {
  if (!dirty.value || submitted)
    return true
  return new Promise<boolean>(resolve => Modal.confirm({ title: '有尚未保存的修改', content: '离开后未保存的正文和接收范围将丢失。', okText: '放弃修改并离开', cancelText: '继续编辑', onOk: () => resolve(true), onCancel: () => resolve(false) }))
}
onBeforeRouteLeave(confirmLeave)
onBeforeRouteUpdate((to, from) => {
  if (to.query.compose !== from.query.compose || to.query.copy !== from.query.copy || (to.query.draft !== from.query.draft && String(taskId.value) !== to.query.draft))
    return confirmLeave()
})
/** 浏览器关闭或刷新也保护未保存内容。 */
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value && !submitted) {
    event.preventDefault()
    event.returnValue = ''
  }
}
watch(() => [route.path, route.query.draft, route.query.copy], loadDraft)
onMounted(async () => {
  baseline.value = JSON.stringify(draft)
  window.addEventListener('beforeunload', beforeUnload)
  try {
    const result = await Promise.all([request<MessageSettings>('/messages/settings'), request<MessageTemplate[]>('/messages/templates/available')])
    settings.value = result[0]
    templates.value = result[1]
    await loadDraft()
  }
  catch (failure) {
    error.value = errorMessage(failure, '发布页面加载失败')
  }
})
onBeforeUnmount(() => {
  loadingVersion++
  window.removeEventListener('beforeunload', beforeUnload)
  urls.forEach(url => URL.revokeObjectURL(url))
})
</script>

<template>
  <MessageTheme>
    <FilePreview :file="attachmentPreview" @close="attachmentPreview = undefined" />
    <form class="message-compose mx-auto max-w-7xl space-y-4" @submit.prevent="prepare">
      <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <section class="min-w-0 rounded-lg border bg-background">
          <div class="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
            <h2 class="flex items-center gap-2 font-semibold">
              <FileText class="size-4 text-primary" />消息内容
            </h2><div class="flex min-w-0 items-center gap-2">
              <ASelect v-model:value="selectedTemplate" class="!w-44" :options="templates.map(item => ({ value: item.id, label: item.name }))" placeholder="选择消息模板" allow-clear /><Button type="button" size="sm" variant="outline" :disabled="!template || busy" @click="templateOpen = true">
                应用模板
              </Button>
            </div>
          </div>
          <div class="space-y-5 p-5">
            <label class="block space-y-2"><span class="text-sm font-medium"><span class="mr-1 text-destructive">*</span>消息标题</span><AInput v-model:value="draft.title" placeholder="消息标题" :maxlength="200" show-count :disabled="busy" /></label><div class="space-y-2">
              <span class="text-sm font-medium"><span class="mr-1 text-destructive">*</span>消息正文</span><MessageEditor :key="draft.requestKey" ref="editor" v-model="draft.content" :disabled="busy || uploading" />
            </div>
            <div class="space-y-3">
              <div class="flex flex-wrap items-center gap-2">
                <Button type="button" variant="outline" size="sm" :disabled="busy || uploading || !auth.can('file:upload')" @click="fileInput?.click()">
                  <Paperclip class="size-3.5" />添加附件
                </Button><Button type="button" variant="outline" size="sm" :disabled="busy || uploading || !auth.can('file:upload')" @click="imageInput?.click()">
                  <ImagePlus class="size-3.5" />插入图片
                </Button><span class="text-xs text-muted-foreground">{{ uploading ? '正在上传…' : settings ? `附件 ${attachments.length} / ${settings.maxAttachments}` : '' }}</span>
              </div><p v-if="!auth.can('file:upload')" class="text-xs text-muted-foreground">
                当前账号未分配文件上传权限。
              </p><input ref="fileInput" type="file" class="hidden" multiple :accept="FILE_ACCEPT" aria-label="选择消息附件" @change="upload($event, false)"><input ref="imageInput" type="file" class="hidden" multiple accept="image/png,image/jpeg,image/webp,image/gif" aria-label="选择正文图片" @change="upload($event, true)"><div v-if="attachments.length" class="grid gap-2 sm:grid-cols-2">
                <FileAttachment v-for="file in attachments" :key="file.fileId" :file="{ id: file.fileId, name: file.name, contentType: file.contentType, size: file.fileSize }" removable :disabled="busy || uploading" @preview="attachmentPreview = $event" @remove="removeAttachment(file.fileId)" />
              </div>
            </div>
          </div>
        </section>
        <aside class="space-y-4">
          <section class="rounded-lg border bg-background">
            <h2 class="border-b px-5 py-4 font-semibold">
              发布设置
            </h2><div class="space-y-5 p-5">
              <label class="block space-y-2"><span class="block text-sm">展示方式</span><ASelect v-model:value="draft.displayMode" class="w-full" :options="[{ value: 'INBOX', label: '消息通知' }, { value: 'POPUP', label: '弹窗公告' }]" :disabled="busy" /></label><label class="block space-y-2"><span class="block text-sm">消息分类</span><ASelect v-model:value="draft.category" class="w-full" :options="dictionaries.options('message_category')" :disabled="busy" /></label><label class="block space-y-2"><span class="block text-sm">重要程度</span><ASelect v-model:value="draft.priority" class="w-full" :options="dictionaries.options('message_priority')" :disabled="busy" /></label><div class="flex items-center justify-between">
                <span class="text-sm">要求接收人确认</span><ASwitch v-model:checked="draft.requireConfirmation" :disabled="busy" aria-label="要求接收人确认" />
              </div><div class="flex items-center justify-between">
                <span class="text-sm">在收件箱置顶</span><ASwitch v-model:checked="draft.pinned" :disabled="busy" aria-label="在收件箱置顶" />
              </div><div class="space-y-2 border-t pt-4">
                <div class="flex items-center justify-between">
                  <span class="text-sm">定时发送</span><ASwitch v-model:checked="scheduled" :disabled="busy || !auth.can('message:schedule')" aria-label="定时发送" />
                </div><DatePicker v-if="scheduled" :value="draft.scheduledAt || undefined" class="w-full" show-time format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择发送时间" :disabled="busy" :disabled-date="date => date.isBefore(dayjs().startOf('day')) || !!settings && date.isAfter(dayjs().add(settings.scheduleDays, 'day').endOf('day'))" @update:value="draft.scheduledAt = $event ? String($event) : null" />
              </div><div class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-sm">设置有效期</span><ASwitch v-model:checked="expiring" :disabled="busy" aria-label="设置有效期" />
                </div><DatePicker v-if="expiring" :value="draft.expiresAt || undefined" class="w-full" show-time format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择有效期截止时间" :disabled="busy" @update:value="draft.expiresAt = $event ? String($event) : null" />
              </div><label class="block space-y-2 border-t pt-4"><span class="block text-sm">关联业务入口</span><ASelect :value="draft.actionPath || undefined" class="w-full" :options="links" placeholder="不关联业务页面" allow-clear :disabled="busy" @update:value="draft.actionPath = $event ? String($event) : null" /></label>
            </div>
          </section>
        </aside>
      </div>
      <section class="rounded-lg border bg-background px-5 py-4">
        <h2 class="mb-3 font-semibold">
          接收范围
        </h2><MessageRecipients v-model="draft.audience" :disabled="busy" />
      </section>
      <div class="sticky bottom-0 z-10 flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-background px-5 py-3">
        <span v-if="dirty || savedAt" class="text-xs text-muted-foreground">{{ dirty ? '有未保存的修改' : `草稿已保存于 ${savedAt}` }}</span><div class="ml-auto flex gap-2">
          <Button type="button" variant="outline" :disabled="busy || uploading" @click="save">
            <Save class="size-4" />保存草稿
          </Button><Button type="submit" :disabled="busy || uploading || !settings">
            <Send class="size-4" />预览并发布
          </Button>
        </div>
      </div>
      <Modal v-model:open="templateOpen" :mask-closable="false" title="应用消息模板" :z-index="60" ok-text="填入消息内容" cancel-text="取消" :confirm-loading="busy" @ok="applyTemplate">
        <label v-for="name in variableNames" :key="name" class="mb-4 block space-y-2"><span>{{ name }}</span><AInput v-model:value="variables[name]" :maxlength="1000" :placeholder="`填写 ${name}`" /></label><p v-if="!variableNames.length" class="py-4 text-sm">
          此模板没有需要填写的变量。
        </p>
      </Modal>
      <Modal v-model:open="previewOpen" :mask-closable="false" title="确认发布" :width="760" :z-index="60" :ok-text="scheduled ? '确认定时发布' : '确认发布'" cancel-text="返回编辑" :confirm-loading="busy" @ok="publish">
        <div v-if="preview" class="space-y-5 py-3">
          <div class="flex flex-wrap justify-between gap-3 rounded bg-primary/8 p-4 text-sm">
            <span>实际接收 <strong class="text-lg text-primary">{{ preview.count }}</strong> 人，已合并去重</span><span>{{ scheduled ? draft.scheduledAt : '立即发送' }}</span>
          </div><div>
            <h2 class="text-xl font-semibold break-words">
              {{ draft.title }}
            </h2><p class="mt-2 text-xs text-muted-foreground">
              {{ dictionaries.label('message_category', draft.category) }} · {{ dictionaries.label('message_priority', draft.priority) }}{{ draft.requireConfirmation ? ' · 需要确认' : '' }}
            </p>
          </div><MessageContentView :content="draft.content" :task-id="taskId" /><div v-if="attachments.length" class="border-t pt-3 text-sm text-muted-foreground">
            附件 {{ attachments.length }} 个：{{ attachments.map(file => file.name).join('、') }}
          </div><p class="text-xs leading-6 text-muted-foreground">
            接收人预览：{{ preview.sample.map(person => person.nickname || person.username).join('、') }}{{ preview.count > preview.sample.length ? ' 等' : '' }}
          </p>
        </div>
      </Modal>
    </form>
  </MessageTheme>
</template>
