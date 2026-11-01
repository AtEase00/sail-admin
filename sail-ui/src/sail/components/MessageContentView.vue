<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

import { messageAttachmentBlob, sanitizeMessageHtml } from '@/sail/message-center'

import ImagePreview from './ImagePreview.vue'

const props = defineProps<{ content: string, taskId?: number | null }>()
const host = ref<HTMLElement | null>(null)
const error = ref('')
const html = ref('')
const urls: string[] = []
const preview = ref<{ src: string, alt: string }>()
let generation = 0
/** 先净化再鉴权读取图片，换消息和卸载都释放对象地址。 */
async function render() {
  const current = ++generation
  preview.value = undefined
  urls.splice(0).forEach(url => URL.revokeObjectURL(url))
  error.value = ''
  html.value = sanitizeMessageHtml(props.content)
  await nextTick()
  if (!props.taskId || !host.value)
    return
  const taskId = props.taskId
  await Promise.all(Array.from(host.value.querySelectorAll<HTMLImageElement>('img[data-file-id]')).map(async (image) => {
    const id = Number(image.dataset.fileId)
    if (!Number.isSafeInteger(id) || id <= 0) {
      image.remove()
      return
    }
    try {
      const blob = await messageAttachmentBlob(taskId, id)
      if (generation !== current)
        return
      if (!['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(blob.type)) {
        image.remove()
        return
      }
      const url = URL.createObjectURL(blob)
      urls.push(url)
      image.src = url
      image.tabIndex = 0
      image.setAttribute('role', 'button')
      image.setAttribute('aria-label', `预览图片：${image.alt || '正文图片'}`)
    }
    catch {
      if (generation === current) {
        image.remove()
        error.value = '部分图片未能读取，请刷新消息重试。'
      }
    }
  }))
}
/** 事件委托只打开已鉴权的正文图片，不接受正文提供的外部图片地址。 */
function openImage(event: MouseEvent | KeyboardEvent) {
  if (event instanceof KeyboardEvent && !['Enter', ' '].includes(event.key))
    return
  const image = event.target
  if (image instanceof HTMLImageElement && urls.includes(image.src)) {
    event.preventDefault()
    preview.value = { src: image.src, alt: image.alt }
  }
}
watch(() => [props.content, props.taskId, host.value], render, { immediate: true })
onBeforeUnmount(() => {
  generation++
  urls.forEach(url => URL.revokeObjectURL(url))
})
</script>

<template>
  <div>
    <div ref="host" class="message-body message-content-view" @click="openImage" @keydown="openImage" v-html="html" /><p v-if="error" role="alert" class="mt-3 text-sm text-destructive">
      {{ error }}
    </p>
    <ImagePreview v-if="preview" :src="preview.src" :alt="preview.alt" open @close="preview = undefined" />
  </div>
</template>

<style>
.message-content-view img[role='button'] {
  cursor: zoom-in;
}
</style>
