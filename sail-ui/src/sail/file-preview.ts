import DOMPurify from 'dompurify'

import { apiFetch, request } from '@/lib/api-client'

/** 本地待上传文件和已保存文件共用预览契约，消息附件保留任务授权上下文。 */
export interface PreviewFile { id?: number, taskId?: number | null, name: string, contentType?: string, size?: number, local?: File }
export type PreviewKind = 'image' | 'pdf' | 'word' | 'sheet' | 'office-text' | 'markdown' | 'text' | 'audio' | 'video' | 'unsupported'
export const FILE_ACCEPT = '.png,.jpg,.jpeg,.gif,.bmp,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.md,.markdown,.txt,.csv,.json,.log,.zip,.mp3,.wav,.ogg,.mp4,.webm'
/** 根据文件本身的扩展名选择对应阅读器，不把其他格式冒充 PDF 或图片。 */
export function previewKind(name: string): PreviewKind {
  const ext = name.split('.').pop()?.toLowerCase() ?? ''
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp'].includes(ext))
    return 'image'
  if (ext === 'pdf')
    return 'pdf'
  if (ext === 'docx')
    return 'word'
  if (ext === 'xlsx')
    return 'sheet'
  if (['doc', 'xls', 'ppt', 'pptx'].includes(ext))
    return 'office-text'
  if (['md', 'markdown'].includes(ext))
    return 'markdown'
  if (['txt', 'csv', 'json', 'log'].includes(ext))
    return 'text'
  if (['mp3', 'wav', 'ogg'].includes(ext))
    return 'audio'
  if (['mp4', 'webm'].includes(ext))
    return 'video'
  return 'unsupported'
}
/** 所有远程读取携带当前身份，消息附件使用消息授权接口而非通用文件编号。 */
export async function previewBlob(file: PreviewFile): Promise<Blob> {
  if (file.local)
    return file.local
  if (!file.id)
    throw new Error('缺少文件编号，无法读取')
  return apiFetch<Blob, 'blob'>(file.taskId ? `/messages/tasks/${file.taskId}/attachments/${file.id}` : `/file/objects/${file.id}/download`, { responseType: 'blob', query: { inline: true } })
}
/** 二进制旧版 Office 由服务端读取真实文本，未上传的文件明确提示先上传。 */
export async function officePreview(file: PreviewFile): Promise<string> {
  if (!file.id)
    throw new Error('此格式需上传后才能进行文本预览')
  return request<string>(file.taskId ? `/messages/tasks/${file.taskId}/attachments/${file.id}/preview` : `/file/objects/${file.id}/preview`)
}
/** Markdown 与 Word 转换结果只保留文档排版和内嵌栅格图片，禁止脚本与外部资源加载。 */
export function cleanDocumentHtml(source: string): string {
  const safe = DOMPurify.sanitize(source, { ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'b', 'i', 'u', 's', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'blockquote', 'pre', 'code', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'hr', 'a', 'img', 'sup', 'sub'], ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'colspan', 'rowspan'], ALLOW_DATA_ATTR: false })
  const host = document.createElement('div')
  host.innerHTML = safe
  host.querySelectorAll('img').forEach((image) => {
    if (!/^data:image\/(?:png|jpeg|gif|webp);base64,/i.test(image.getAttribute('src') ?? ''))
      image.remove()
  })
  host.querySelectorAll('a').forEach((anchor) => {
    if (!/^https?:\/\//i.test(anchor.getAttribute('href') ?? ''))
      anchor.removeAttribute('href')
    anchor.target = '_blank'
    anchor.rel = 'noopener noreferrer'
  })
  return host.innerHTML
}
