<script setup lang="ts">
import type { Component } from 'vue'

import { Download, Eye, FileArchive, FileImage, FileSpreadsheet, FileText, Music, Presentation, Trash2, Video } from '@lucide/vue'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import type { PreviewFile, PreviewKind } from '@/sail/file-preview'

import { Button } from '@/components/ui/button'
import { previewBlob, previewKind } from '@/sail/file-preview'
import { attachmentSize, saveMessageBlob } from '@/sail/message-center'

const props = defineProps<{ file: PreviewFile, removable?: boolean, downloadable?: boolean, disabled?: boolean }>()
const emit = defineEmits<{ preview: [file: PreviewFile], remove: [] }>()
const thumb = ref('')
const failure = ref('')
let generation = 0
const kind = computed(() => previewKind(props.file.name))
const icons: Partial<Record<PreviewKind, Component>> = { 'image': FileImage, 'sheet': FileSpreadsheet, 'word': FileText, 'office-text': Presentation, 'audio': Music, 'video': Video, 'unsupported': FileArchive }
const icon = computed(() => icons[kind.value] ?? FileText)
/** 图片附件呈现真实缩略图；切换引用或卸载时释放私有对象地址。 */
async function thumbnail() {
  const current = ++generation
  if (thumb.value)
    URL.revokeObjectURL(thumb.value)
  thumb.value = ''
  failure.value = ''
  if (kind.value !== 'image')
    return
  try {
    const result = await previewBlob(props.file)
    if (current !== generation)
      return
    thumb.value = URL.createObjectURL(new Blob([result], { type: props.file.contentType || result.type }))
  }
  catch {
    if (current === generation)
      failure.value = '图片暂不可用'
  }
}
/** 原文件下载保持鉴权，不暴露令牌到图片或链接 URL。 */
async function download() {
  try {
    saveMessageBlob(await previewBlob(props.file), props.file.name)
  }
  catch (error) { failure.value = error instanceof Error ? error.message : '下载失败' }
}
watch(() => [props.file.id, props.file.taskId, props.file.local, props.file.name], thumbnail, { immediate: true })
onBeforeUnmount(() => {
  generation++
  if (thumb.value)
    URL.revokeObjectURL(thumb.value)
})
</script>

<template>
  <div class="flex min-w-0 items-center gap-3 rounded border bg-background p-2.5 transition-colors hover:border-primary/40">
    <button type="button" class="flex size-12 shrink-0 items-center justify-center overflow-hidden rounded bg-muted/60 text-primary" :aria-label="`预览${file.name}`" :disabled="disabled" @click="emit('preview', file)">
      <img v-if="thumb" :src="thumb" alt="" class="size-full object-cover"><component :is="icon" v-else class="size-6 stroke-1.5" />
    </button>
    <div class="min-w-0 flex-1">
      <button type="button" class="block max-w-full truncate text-left text-sm hover:text-primary" :title="file.name" :disabled="disabled" @click="emit('preview', file)">
        {{ file.name }}
      </button><p class="mt-1 text-xs text-muted-foreground">
        {{ failure || (file.size !== undefined ? attachmentSize(file.size) : '') }}<span v-if="!failure" class="ml-2 uppercase">{{ file.name.split('.').pop() }}</span>
      </p>
    </div>
    <Button type="button" variant="ghost" size="icon" class="size-7" :aria-label="`预览${file.name}`" :disabled="disabled" @click="emit('preview', file)">
      <Eye class="size-4" />
    </Button>
    <Button v-if="downloadable" type="button" variant="ghost" size="icon" class="size-7" :aria-label="`下载${file.name}`" :disabled="disabled" @click="download">
      <Download class="size-4" />
    </Button>
    <Button v-if="removable" type="button" variant="ghost" size="icon" class="size-7 text-muted-foreground hover:text-destructive" :aria-label="`移除附件${file.name}`" :disabled="disabled" @click="emit('remove')">
      <Trash2 class="size-4" />
    </Button>
  </div>
</template>
