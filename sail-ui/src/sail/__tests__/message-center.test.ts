// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'

import type { MessageTask } from '../message-center'

import { cloneAudience, copyMessageDraft, messageUnavailable, newMessageDraft, sanitizeMessageHtml, templateVariables } from '../message-center'

afterEach(() => vi.unstubAllGlobals())

describe('消息正文及发布边界', () => {
  it('普通 HTTP 环境缺少 randomUUID 时仍生成合法且独立的发布幂等键', () => {
    const getRandomValues = crypto.getRandomValues.bind(crypto)
    vi.stubGlobal('crypto', { getRandomValues })
    const first = newMessageDraft().requestKey
    const second = newMessageDraft().requestKey
    expect(first).toMatch(/^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/)
    expect(second).not.toBe(first)
  })
  it('阻断脚本、事件、主动链接与外部图片，保留私有附件引用和正常排版', () => {
    const html = sanitizeMessageHtml('<h2>通知</h2><p onclick="alert(1)">正文<script>alert(1)</script><a href="javascript:alert(1)">危险</a><a href="https://example.com">业务</a><img src="https://example.com/private" data-file-id="12" onerror="alert(1)"></p>')
    expect(html).toContain('<h2>通知</h2>')
    expect(html).toContain('https://example.com')
    expect(html).toContain('data-file-id="12"')
    expect(html).not.toMatch(/onclick|onerror|javascript:|<script|src=/)
  })
  it('响应式收件范围独立复制，取消选择不修改已保存表单', () => {
    const audience = reactive({ userIds: [1], deptIds: [2], roleIds: [], postIds: [], excludedIds: [3], all: false, includeChildren: true })
    const copy = cloneAudience(audience)
    copy.userIds.push(4)
    copy.excludedIds.splice(0)
    expect(audience.userIds).toEqual([1])
    expect(audience.excludedIds).toEqual([3])
  })
  it('复制发布只沿用正文和范围，重新生成幂等键并清空历史定时', () => {
    const draft = newMessageDraft()
    const task = { ...draft, id: 1, version: 5, scheduledAt: '2099-01-01 12:30:00', expiresAt: '2099-01-02 12:30:00', attachments: [{ fileId: 9 }], title: '通知', content: '<p>正文</p>' } as MessageTask
    const copy = copyMessageDraft(task)
    expect(copy.requestKey).not.toBe(draft.requestKey)
    expect(copy.version).toBe(0)
    expect(copy.scheduledAt).toBeNull()
    expect(copy.expiresAt).toBeNull()
    expect(copy.attachmentIds).toEqual([9])
    expect(copy.title).toBe('通知')
  })
  it('变量识别去重且不将表达式当作可执行变量', () => {
    // eslint-disable-next-line no-template-curly-in-string
    expect(templateVariables('${name} ${count} ${name} ${user.name} ${7name}')).toEqual(['name', 'count'])
  })
  it('撤回和真实过期内容不可用，未来有效期不误判', () => {
    expect(messageUnavailable({ taskStatus: 'WITHDRAWN', expiresAt: null })).toBe(true)
    expect(messageUnavailable({ taskStatus: 'SENT', expiresAt: '2000-01-01 12:00:00' })).toBe(true)
    expect(messageUnavailable({ taskStatus: 'SENT', expiresAt: '2099-01-01 12:00:00' })).toBe(false)
  })
})
