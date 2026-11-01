import DOMPurify from 'dompurify'

import { apiFetch, request } from '@/lib/api-client'
import { createBrowserUuid } from '@/sail/browser-uuid'

/** 接收范围只保存真实编号，最终收件人由后端解析。 */
export interface MessageAudience { userIds: number[], deptIds: number[], roleIds: number[], postIds: number[], excludedIds: number[], includeChildren: boolean, all: boolean }
export interface MessagePerson { id: number, username: string, nickname: string, deptId: number | null, deptName: string | null }
export interface AudienceOption { id: number, name: string, parentId: number | null }
export interface AudienceOptions { departments: AudienceOption[], roles: AudienceOption[], posts: AudienceOption[] }
export interface MessageAttachment { fileId: number, name: string, contentType: string, fileSize: number }
export interface MessageStatistics { total: number, delivered: number, pending: number, failed: number, skipped: number, readCount: number, confirmed: number }
export interface MessageDraft {
  requestKey: string
  version: number
  title: string
  content: string
  category: string
  priority: string
  requireConfirmation: boolean
  displayMode: 'INBOX' | 'POPUP'
  pinned: boolean
  scheduledAt: string | null
  expiresAt: string | null
  actionPath: string | null
  audience: MessageAudience
  attachmentIds: number[]
  templateCode: string | null
}
export interface MessageTask extends MessageDraft { id: number, status: string, senderId: number, senderName: string | null, summary: string, createdAt: string, updatedAt: string, publishedAt: string | null, lastError: string | null, attachments: MessageAttachment[], statistics: MessageStatistics }
export interface InboxMessage { id: number, taskId: number | null, title: string, content: string | null, summary: string | null, category: string | null, priority: string | null, senderName: string | null, readFlag: boolean, readAt: string | null, confirmedAt: string | null, archived: boolean, requireConfirmation: boolean, pinned: boolean, createdAt: string, expiresAt: string | null, taskStatus: string | null, actionPath: string | null, attachments?: MessageAttachment[] }
export interface MessageReceipt { id: number, userId: number, username: string, nickname: string | null, deptName: string | null, status: string, errorMessage: string | null, deliveredAt: string | null, readAt: string | null, confirmedAt: string | null }
export interface MessageTemplate { id: number, code: string, name: string, title: string, content: string, enabled: boolean, createdAt: string }
export interface MessageSettings { maxRecipients: number, scheduleDays: number, maxAttachments: number, recallMinutes: number, reminderEnabled: number }
export interface AudiencePreview { count: number, fingerprint: string, version: number, sample: MessagePerson[] }

/** 创建新的幂等键；复制消息时必须生成新键。 */
export function newMessageDraft(): MessageDraft {
  return { requestKey: createBrowserUuid(), version: 0, title: '', content: '', category: 'NOTICE', priority: 'NORMAL', requireConfirmation: false, displayMode: 'INBOX', pinned: false, scheduledAt: null, expiresAt: null, actionPath: null, audience: { userIds: [], deptIds: [], roleIds: [], postIds: [], excludedIds: [], includeChildren: true, all: false }, attachmentIds: [], templateCode: null }
}
/** 复制只沿用可编辑内容，移除旧版本及客户端创建键。 */
export function copyMessageDraft(task: MessageTask): MessageDraft {
  return { ...newMessageDraft(), title: task.title, content: task.content, category: task.category, priority: task.priority, requireConfirmation: task.requireConfirmation, displayMode: task.displayMode, pinned: task.pinned, actionPath: task.actionPath, audience: cloneAudience(task.audience), attachmentIds: task.attachments.map(file => file.fileId), templateCode: task.templateCode }
}
/** Vue 响应式对象通过明确业务字段复制，避免 structuredClone 无法处理 Proxy。 */
export function cloneAudience(value: MessageAudience): MessageAudience {
  return { userIds: [...value.userIds], deptIds: [...value.deptIds], roleIds: [...value.roleIds], postIds: [...value.postIds], excludedIds: [...value.excludedIds], includeChildren: value.includeChildren, all: value.all }
}
/** 只输出受支持的正文结构；图片地址必须在鉴权读取后由组件填充。 */
export function sanitizeMessageHtml(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'ul', 'ol', 'li', 'blockquote', 'h2', 'h3', 'hr', 'a', 'img'], ALLOWED_ATTR: ['href', 'title', 'rel', 'data-file-id', 'alt'], ALLOW_DATA_ATTR: false })
}
/** 模板变量去重保留出现顺序，不执行表达式或替换缺失值。 */
export function templateVariables(source: string): string[] {
  return [...new Set([...source.matchAll(/\$\{([a-z]\w*)\}/gi)].map(match => match[1]!))]
}
/** 过期和撤回展示状态保留，正文及附件均不可访问。 */
export function messageUnavailable(message: Pick<InboxMessage, 'expiresAt' | 'taskStatus'>): boolean {
  return message.taskStatus === 'WITHDRAWN' || (!!message.expiresAt && new Date(message.expiresAt.replace(' ', 'T')).getTime() <= Date.now())
}
/** 附件下载使用消息授权路径，接收人无需拥有发布者的文件权限。 */
export async function messageAttachmentBlob(taskId: number, fileId: number): Promise<Blob> {
  return apiFetch<Blob, 'blob'>(`/messages/tasks/${taskId}/attachments/${fileId}`, { responseType: 'blob', query: { inline: true } })
}
/** 上传保持私有，由文件模块执行类型、容量与归属校验。 */
export async function uploadMessageAttachment(file: File): Promise<MessageAttachment> {
  const body = new FormData()
  body.append('file', file)
  const uploaded = await request<{ id: number, originalName: string, contentType: string, size: number }>('/file/objects', 'POST', body)
  return { fileId: uploaded.id, name: uploaded.originalName, contentType: uploaded.contentType, fileSize: uploaded.size }
}
/** Blob 仅在用户主动下载时创建，结束后释放浏览器对象地址。 */
export function saveMessageBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
/** 附件大小保留真实字节含义，不以缺失字段冒充。 */
export function attachmentSize(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1048576).toFixed(1)} MB`
}
