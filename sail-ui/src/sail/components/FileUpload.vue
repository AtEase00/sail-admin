<script setup lang="ts">
import type { UploadFile, UploadProps } from 'ant-design-vue'

import { Inbox } from '@lucide/vue'
import { Upload } from 'ant-design-vue'
import { computed, nextTick, ref } from 'vue'

import type { PreviewFile } from '@/sail/file-preview'

import { createBrowserUuid } from '@/sail/browser-uuid'
import { FILE_ACCEPT } from '@/sail/file-preview'

import FilePreview from './FilePreview.vue'

const props = withDefaults(defineProps<{ disabled?: boolean, maxCount?: number }>(), { maxCount: 20 })
const files = defineModel<File[]>({ default: () => [] })
const preview = ref<PreviewFile>()
const error = ref('')
const ids = new WeakMap<File, string>()
let selection: File[] | undefined
/** 本地文件使用独立 UID，名称相同的两个文件不互相覆盖。 */
function uid(file: File) {
  if (!ids.has(file))
    ids.set(file, createBrowserUuid())
  return ids.get(file)!
}
const list = computed<UploadFile[]>(() => files.value.map(file => ({ uid: uid(file), name: file.name, size: file.size, type: file.type, originFileObj: file as UploadFile['originFileObj'] })))
/** 选择阶段仅管理待上传列表，真实请求由页面统一提交并显示实际结果。 */
const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (file.size > 20971520) {
    error.value = `${file.name} 超过 20 MiB`
    return Upload.LIST_IGNORE
  }
  const selected = selection ?? files.value
  if (selected.length >= props.maxCount) {
    error.value = `最多选择 ${props.maxCount} 个文件`
    return Upload.LIST_IGNORE
  }
  error.value = ''
  // 同一批 beforeUpload 在父组件回传新 props 前连续执行，累积本批文件避免覆盖。
  selection = [...selected, file]
  files.value = selection
  void nextTick(() => {
    selection = undefined
  })
  return false
}
/** 预览原始选择文件，上传前也能识别图片、PDF 和现代文档。 */
function open(file: UploadFile) {
  if (file.originFileObj)
    preview.value = { name: file.name, contentType: file.type, size: file.size, local: file.originFileObj }
}
/** 删除仅移除本次待上传选择，不删除远程文件。 */
function remove(file: UploadFile) {
  files.value = files.value.filter(item => uid(item) !== file.uid)
  return true
}
</script>

<template>
  <div>
    <Upload.Dragger :file-list="list" :before-upload="beforeUpload" :disabled="disabled" :accept="FILE_ACCEPT" multiple list-type="picture" @preview="open" @remove="remove">
      <Inbox class="mx-auto mb-3 size-8 text-primary stroke-1.5" /><p class="text-sm">
        点击选择或拖拽文件到此处
      </p><p class="mt-2 text-xs text-muted-foreground">
        支持图片、PDF、Word、表格、Markdown 等，每个不超过 20 MiB
      </p>
    </Upload.Dragger><p v-if="error" role="alert" class="mt-2 text-xs text-destructive">
      {{ error }}
    </p>
    <FilePreview :file="preview" @close="preview = undefined" />
  </div>
</template>
