// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'

import { cleanDocumentHtml, previewKind } from '../file-preview'

describe('文件预览类型与文档净化', () => {
  it('各格式使用对应阅读器，未知格式不冒充图片或文档', () => {
    expect(previewKind('合同.DOCX')).toBe('word')
    expect(previewKind('旧合同.doc')).toBe('office-text')
    expect(previewKind('报表.xlsx')).toBe('sheet')
    expect(previewKind('公告.md')).toBe('markdown')
    expect(previewKind('资料.PDF')).toBe('pdf')
    expect(previewKind('图片.gif')).toBe('image')
    expect(previewKind('记录.zip')).toBe('unsupported')
  })
  it('word 和 Markdown 不执行脚本、不加载外部图片、不保留危险协议', () => {
    const html = cleanDocumentHtml('<h1>公告</h1><script>alert(1)</script><img src="https://example.com/private"><img src="data:image/png;base64,YQ==" onerror="alert(1)"><a href="javascript:alert(1)">危险链接</a><a href="https://example.com">正常链接</a><table><tr><td>内容</td></tr></table>')
    expect(html).toContain('<h1>公告</h1>')
    expect(html).toContain('<td>内容</td>')
    expect(html).toContain('data:image/png;base64,YQ==')
    expect(html).toContain('noopener noreferrer')
    expect(html).not.toMatch(/<script|onerror|javascript:|src="https:/)
  })
})
