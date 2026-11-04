<script setup lang="ts">
import { Bold, Heading2, Italic, Link2, List, ListOrdered, Quote, Redo2, Strikethrough, Underline, Undo2 } from '@lucide/vue'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import StarterKit from '@tiptap/starter-kit'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import { Input as AInput, Modal } from 'ant-design-vue'
import { onBeforeUnmount, ref, watch } from 'vue'

import { Button } from '@/components/ui/button'
import { useMessageError } from '@/composables/use-message-error'
import { sanitizeMessageHtml } from '@/sail/message-center'

const props = withDefaults(defineProps<{ modelValue: string, disabled?: boolean, placeholder?: string }>(), { placeholder: '填写正文' })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const linkOpen = ref(false)
const link = ref('')
const linkError = useMessageError()
// 持久化图片只保留文件编号，渲染地址为当前会话鉴权后生成的 Blob。
const AttachmentImage = Image.extend({ parseHTML() {
  return [{ tag: 'img[data-file-id]' }]
}, addAttributes() {
  return { ...this.parent?.(), fileId: { default: null, parseHTML: element => element.getAttribute('data-file-id'), renderHTML: attributes => ({ 'data-file-id': attributes.fileId }) } }
} })
const editor = useEditor({ content: props.modelValue, editable: !props.disabled, extensions: [StarterKit.configure({ heading: { levels: [2, 3] }, link: { openOnClick: false, autolink: true, defaultProtocol: 'https', isAllowedUri: url => /^https?:\/\/\S+$/i.test(url) } }), AttachmentImage, Placeholder.configure({ placeholder: props.placeholder })], onUpdate: ({ editor }) => emit('update:modelValue', sanitizeMessageHtml(editor.getHTML())) })
watch(() => props.modelValue, (value) => {
  if (editor.value && sanitizeMessageHtml(editor.value.getHTML()) !== value)
    editor.value.commands.setContent(value, { emitUpdate: false })
})
watch(() => props.disabled, value => editor.value?.setEditable(!value))
/** 编辑超链接使用可校验对话框，禁止脚本和其他主动协议。 */
function editLink() {
  link.value = String(editor.value?.getAttributes('link').href ?? '')
  linkError.value = ''
  linkOpen.value = true
}
/** 验证链接协议后更新选区，空链接表示移除现有链接。 */
function applyLink() {
  if (link.value && !/^https?:\/\/\S+$/i.test(link.value)) {
    linkError.value = '请输入 http:// 或 https:// 开头的链接'
    return
  }
  if (link.value) {
    editor.value?.chain().focus().extendMarkRange('link').setLink({ href: link.value }).run()
  }
  else {
    editor.value?.chain().focus().unsetLink().run()
  }
  linkOpen.value = false
}
/** 插图由发布页完成上传，编辑器只插入经过验证的附件引用。 */
function insertImage(fileId: number, url: string, name: string) {
  editor.value?.chain().focus().insertContent({ type: 'image', attrs: { src: url, alt: name, fileId } }).run()
}
/** 删除附件时同步移除正文引用，避免发布时出现悬空图片。 */
function removeImage(fileId: number) {
  if (!editor.value)
    return
  const transaction = editor.value.state.tr
  editor.value.state.doc.descendants((node, position) => {
    if (node.type.name === 'image' && Number(node.attrs.fileId) === fileId) {
      const start = transaction.mapping.map(position)
      transaction.delete(start, start + node.nodeSize)
    }
  })
  editor.value.view.dispatch(transaction)
}
/** 草稿恢复时取编辑器规范化后的内容，避免格式整理被误判成用户修改。 */
function getContent() {
  return sanitizeMessageHtml(editor.value?.getHTML() ?? props.modelValue)
}
defineExpose({ insertImage, removeImage, getContent })
onBeforeUnmount(() => editor.value?.destroy())
</script>

<template>
  <div class="message-editor overflow-hidden rounded border bg-background" :class="{ 'opacity-60': disabled }">
    <div v-if="editor" class="flex flex-wrap items-center gap-0.5 border-b bg-muted/20 p-1.5" role="toolbar" aria-label="正文编辑工具">
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="加粗" :aria-pressed="editor.isActive('bold')" :disabled="disabled" @click="editor.chain().focus().toggleBold().run()">
        <Bold class="size-4" />
      </Button>
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="斜体" :aria-pressed="editor.isActive('italic')" :disabled="disabled" @click="editor.chain().focus().toggleItalic().run()">
        <Italic class="size-4" />
      </Button>
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="下划线" :aria-pressed="editor.isActive('underline')" :disabled="disabled" @click="editor.chain().focus().toggleUnderline().run()">
        <Underline class="size-4" />
      </Button>
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="删除线" :aria-pressed="editor.isActive('strike')" :disabled="disabled" @click="editor.chain().focus().toggleStrike().run()">
        <Strikethrough class="size-4" />
      </Button>
      <span class="mx-1 h-4 w-px bg-border" />
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="段落标题" :aria-pressed="editor.isActive('heading')" :disabled="disabled" @click="editor.chain().focus().toggleHeading({ level: 2 }).run()">
        <Heading2 class="size-4" />
      </Button>
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="无序列表" :aria-pressed="editor.isActive('bulletList')" :disabled="disabled" @click="editor.chain().focus().toggleBulletList().run()">
        <List class="size-4" />
      </Button>
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="有序列表" :aria-pressed="editor.isActive('orderedList')" :disabled="disabled" @click="editor.chain().focus().toggleOrderedList().run()">
        <ListOrdered class="size-4" />
      </Button>
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="引用" :aria-pressed="editor.isActive('blockquote')" :disabled="disabled" @click="editor.chain().focus().toggleBlockquote().run()">
        <Quote class="size-4" />
      </Button>
      <Button type="button" variant="ghost" size="icon" class="size-8" aria-label="设置链接" :aria-pressed="editor.isActive('link')" :disabled="disabled" @click="editLink">
        <Link2 class="size-4" />
      </Button>
      <span class="mx-1 h-4 w-px bg-border" /><Button type="button" variant="ghost" size="icon" class="size-8" aria-label="撤销" :disabled="disabled || !editor.can().undo()" @click="editor.chain().focus().undo().run()">
        <Undo2 class="size-4" />
      </Button><Button type="button" variant="ghost" size="icon" class="size-8" aria-label="重做" :disabled="disabled || !editor.can().redo()" @click="editor.chain().focus().redo().run()">
        <Redo2 class="size-4" />
      </Button>
    </div>
    <EditorContent :editor="editor" class="message-body" />
    <Modal v-model:open="linkOpen" :mask-closable="false" title="设置链接" :z-index="80" ok-text="保存链接" cancel-text="取消" @ok="applyLink">
      <label class="block space-y-2 py-3"><span>链接地址</span><AInput v-model:value="link" placeholder="https://" @press-enter="applyLink" /></label>
    </Modal>
  </div>
</template>

<style>
.message-editor .tiptap {
  min-height: 280px;
  padding: 18px;
  outline: none;
}
.message-editor [aria-pressed='true'] {
  color: var(--primary);
  background: var(--accent);
}
.message-editor .tiptap p.is-editor-empty:first-child::before {
  color: var(--muted-foreground);
  content: attr(data-placeholder);
  float: left;
  height: 0;
  pointer-events: none;
}
</style>
