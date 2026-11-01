<script setup lang="ts">
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist'

import { ChevronLeft, ChevronRight, Download, FileQuestion, Minus, Plus, RotateCw } from '@lucide/vue'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'

import type { PreviewFile } from '@/sail/file-preview'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { cleanDocumentHtml, officePreview, previewBlob, previewKind } from '@/sail/file-preview'
import { saveMessageBlob } from '@/sail/message-center'

import ImagePreview from './ImagePreview.vue'

const props = defineProps<{ file?: PreviewFile }>()
const emit = defineEmits<{ close: [] }>()
const kind = computed(() => props.file ? previewKind(props.file.name) : 'unsupported')
const busy = ref(false)
const error = ref('')
const html = ref('')
const text = ref('')
const url = ref('')
const blob = shallowRef<Blob>()
const pdf = shallowRef<PDFDocumentProxy>()
const canvas = ref<HTMLCanvasElement>()
const page = ref(1)
const zoom = ref(1)
const rotation = ref(0)
const sheets = shallowRef<{ name: string, rows: string[][] }[]>([])
const sheet = ref(0)
let generation = 0
let renderRevision = 0
let rendering: RenderTask | undefined
let loadingPdf: PDFDocumentLoadingTask | undefined

/** 切换文件或关闭时清理私有对象地址和 PDF worker，过期响应不能覆盖下一份文件。 */
function release() {
  generation++
  renderRevision++
  rendering?.cancel()
  rendering = undefined
  if (url.value)
    URL.revokeObjectURL(url.value)
  url.value = ''
  void loadingPdf?.destroy()
  loadingPdf = undefined
  pdf.value = undefined
  blob.value = undefined
  html.value = ''
  text.value = ''
  sheets.value = []
  error.value = ''
}
/** 分页绘制 PDF，并按真实页数、缩放和旋转控制，不向外部 Office 服务发送文件。 */
async function renderPdf() {
  const document = pdf.value
  if (!document || !canvas.value)
    return
  const current = generation
  const renderRequest = ++renderRevision
  try {
    // 等待旧画布任务真正停止，快速缩放或翻页也不会同时复用同一 canvas。
    if (rendering) {
      rendering.cancel()
      await rendering.promise.catch((failure: Error) => {
        if (failure.name !== 'RenderingCancelledException')
          throw failure
      })
    }
    if (current !== generation || renderRequest !== renderRevision)
      return
    const pdfPage = await document.getPage(page.value)
    if (current !== generation || renderRequest !== renderRevision || !canvas.value)
      return
    const viewport = pdfPage.getViewport({ scale: zoom.value * Math.min(window.devicePixelRatio || 1, 2), rotation: rotation.value })
    const target = canvas.value
    target.width = viewport.width
    target.height = viewport.height
    target.style.width = `${viewport.width / Math.min(window.devicePixelRatio || 1, 2)}px`
    const task = pdfPage.render({ canvas: target, viewport })
    rendering = task
    await task.promise
  }
  catch (failure) {
    if (current === generation && renderRequest === renderRevision && (failure as Error).name !== 'RenderingCancelledException')
      error.value = 'PDF 页面读取失败，请下载原文件查看'
  }
}
/** 按格式按需加载阅读器，输出真实内容；不支持格式保留下载，不伪造预览。 */
async function load() {
  release()
  const file = props.file
  if (!file)
    return
  const current = generation
  busy.value = true
  page.value = 1
  zoom.value = 1
  rotation.value = 0
  sheet.value = 0
  try {
    if (kind.value === 'office-text') {
      const result = await officePreview(file)
      if (current === generation)
        text.value = result
      return
    }
    const result = await previewBlob(file)
    if (current !== generation)
      return
    blob.value = result
    if (kind.value === 'pdf') {
      const reader = await import('pdfjs-dist')
      reader.GlobalWorkerOptions.workerSrc = pdfWorkerUrl
      const loading = reader.getDocument({ data: new Uint8Array(await result.arrayBuffer()) })
      if (current !== generation) {
        void loading.destroy()
        return
      }
      loadingPdf = loading
      const document = await loading.promise
      if (current !== generation) {
        void loading.destroy()
        return
      }
      pdf.value = document
      busy.value = false
      await nextTick()
      await renderPdf()
    }
    else if (kind.value === 'word') {
      const mammoth = await import('mammoth')
      const document = await mammoth.convertToHtml({ arrayBuffer: await result.arrayBuffer() })
      if (current === generation)
        html.value = cleanDocumentHtml(document.value)
    }
    else if (kind.value === 'sheet') {
      const { Workbook } = await import('exceljs')
      const workbook = new Workbook()
      await workbook.xlsx.load(await result.arrayBuffer())
      const parsed = workbook.worksheets.slice(0, 20).map(worksheet => ({ name: worksheet.name, rows: Array.from({ length: Math.min(worksheet.rowCount, 1000) }, (_, row) => Array.from({ length: Math.min(worksheet.columnCount, 100) }, (_, column) => worksheet.getCell(row + 1, column + 1).text)) }))
      if (current === generation)
        sheets.value = parsed
    }
    else if (['markdown', 'text'].includes(kind.value)) {
      const source = (await result.text()).slice(0, 200000)
      if (kind.value === 'markdown') {
        const { marked } = await import('marked')
        const output = cleanDocumentHtml(await marked.parse(source))
        if (current === generation)
          html.value = output
      }
      else if (current === generation) {
        text.value = source
      }
    }
    else if (['image', 'audio', 'video'].includes(kind.value)) {
      // 下载接口可能为 octet-stream；栅格图用内容元数据，媒体使用明确格式映射。
      const mime = file.contentType && file.contentType !== 'application/octet-stream' ? file.contentType : result.type
      url.value = URL.createObjectURL(new Blob([result], { type: mime }))
    }
  }
  catch (failure) {
    if (current === generation)
      error.value = failure instanceof Error ? failure.message : '文件读取失败'
  }
  finally {
    if (current === generation)
      busy.value = false
  }
}
/** 用户主动下载时再次复用鉴权路径，预览状态不扩大读取权限。 */
async function download() {
  if (!props.file)
    return
  try {
    saveMessageBlob(blob.value ?? await previewBlob(props.file), props.file.name)
  }
  catch (failure) { error.value = failure instanceof Error ? failure.message : '下载失败' }
}
watch(() => props.file, load, { immediate: true })
watch([page, zoom, rotation], renderPdf)
onBeforeUnmount(release)
</script>

<template>
  <ImagePreview v-if="kind === 'image'" :src="url" :alt="file?.name" :loading="busy" :error="error" :open="!!file" @close="emit('close')" />
  <Dialog :open="!!file && kind !== 'image'" @update:open="!$event && emit('close')">
    <DialogContent class="z-[90] flex h-[90dvh] max-h-[960px] flex-col gap-0 overflow-hidden p-0 sm:max-w-[min(1200px,94vw)]" overlay-class="z-[90] bg-black/40">
      <DialogHeader class="border-b px-5 py-4 pr-14">
        <DialogTitle class="truncate text-base">
          {{ file?.name }}
        </DialogTitle>
        <DialogDescription>{{ kind === 'word' ? 'Word 内容预览，复杂版式以原文件为准' : kind === 'office-text' ? 'Office 文本预览，图片和原始版式请下载查看' : kind === 'sheet' ? '工作表预览，每张最多显示 1000 行、100 列' : kind === 'text' || kind === 'markdown' ? '文档预览，最多显示 200000 字符' : '文件预览' }}</DialogDescription>
      </DialogHeader>
      <div class="flex flex-wrap items-center gap-2 border-b bg-muted/20 px-4 py-2">
        <template v-if="kind === 'pdf' && pdf">
          <Button size="icon" variant="ghost" aria-label="上一页" :disabled="page <= 1" @click="page--">
            <ChevronLeft class="size-4" />
          </Button>
          <span class="text-xs">{{ page }} / {{ pdf.numPages }} 页</span>
          <Button size="icon" variant="ghost" aria-label="下一页" :disabled="page >= pdf.numPages" @click="page++">
            <ChevronRight class="size-4" />
          </Button>
        </template>
        <template v-if="kind === 'pdf'">
          <Button size="icon" variant="ghost" aria-label="缩小" :disabled="zoom <= .5" @click="zoom = Math.max(.5, zoom - .25)">
            <Minus class="size-4" />
          </Button><span class="w-12 text-center text-xs">{{ Math.round(zoom * 100) }}%</span><Button size="icon" variant="ghost" aria-label="放大" :disabled="zoom >= 3" @click="zoom = Math.min(3, zoom + .25)">
            <Plus class="size-4" />
          </Button>
          <Button size="icon" variant="ghost" aria-label="旋转" @click="rotation = (rotation + 90) % 360">
            <RotateCw class="size-4" />
          </Button>
        </template>
        <select v-if="sheets.length" v-model="sheet" aria-label="选择工作表" class="max-w-64 px-2">
          <option v-for="(item, index) in sheets" :key="item.name" :value="index">
            {{ item.name }}
          </option>
        </select>
        <Button class="ml-auto" size="sm" variant="outline" @click="download">
          <Download class="size-4" />下载原文件
        </Button>
      </div>
      <div class="min-h-0 flex-1 overflow-auto bg-muted/30 p-4 sm:p-6">
        <p v-if="busy" role="status" class="p-12 text-center text-sm text-muted-foreground">
          正在读取文件…
        </p>
        <p v-else-if="error" role="alert" class="p-8 text-center text-sm text-destructive">
          {{ error }}
        </p>
        <template v-else>
          <canvas v-if="kind === 'pdf'" ref="canvas" class="mx-auto max-w-none bg-white" />
          <article v-else-if="html" class="file-document mx-auto max-w-4xl bg-background p-6 sm:p-10" v-html="html" />
          <div v-else-if="kind === 'sheet' && sheets.length" class="overflow-auto bg-background">
            <table class="w-full border-collapse text-sm">
              <tbody>
                <tr v-for="(row, index) in sheets[sheet]?.rows" :key="index">
                  <th class="border bg-muted px-3 py-2 text-xs text-muted-foreground">
                    {{ index + 1 }}
                  </th><td v-for="(cell, column) in row" :key="column" class="min-w-24 border px-3 py-2 whitespace-pre-wrap">
                    {{ cell }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <pre v-else-if="['text', 'office-text'].includes(kind)" class="mx-auto max-w-5xl whitespace-pre-wrap break-words bg-background p-6 text-sm leading-7">{{ text || '此文件未提取到可显示的文本。' }}</pre>
          <audio v-else-if="kind === 'audio' && url" :src="url" controls class="mx-auto mt-12 w-full max-w-xl" />
          <video v-else-if="kind === 'video' && url" :src="url" controls class="mx-auto max-h-full max-w-full" />
          <div v-else class="flex flex-col items-center gap-4 p-12 text-sm text-muted-foreground">
            <FileQuestion class="size-12 stroke-1" />此格式暂不提供在线阅读，请下载原文件查看。
          </div>
        </template>
      </div>
    </DialogContent>
  </Dialog>
</template>

<style>
.file-document {
  overflow-wrap: anywhere;
  line-height: 1.8;
}
.file-document :is(h1, h2, h3, h4) {
  font-weight: 600;
  margin: 1em 0 0.6em;
}
.file-document h1 {
  font-size: 1.75rem;
}
.file-document h2 {
  font-size: 1.5rem;
}
.file-document h3 {
  font-size: 1.25rem;
}
.file-document p {
  margin: 0.7em 0;
}
.file-document img {
  max-width: 100%;
  height: auto;
}
.file-document :is(ul, ol) {
  padding-left: 1.5em;
}
.file-document ul {
  list-style: disc;
}
.file-document ol {
  list-style: decimal;
}
.file-document pre {
  white-space: pre-wrap;
  background: var(--muted);
  padding: 1rem;
}
.file-document code {
  font-family: monospace;
}
.file-document :is(td, th) {
  border: 1px solid var(--border);
  padding: 0.5rem;
}
.file-document table {
  border-collapse: collapse;
  max-width: 100%;
}
.file-document a {
  color: var(--primary-text);
  text-decoration: underline;
}
.file-document blockquote {
  background: var(--muted);
  padding: 0.5rem 1rem;
}
</style>
