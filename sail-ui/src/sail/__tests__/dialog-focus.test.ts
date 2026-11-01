// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

import { focusDialogContent } from '@/lib/dialog-focus'

describe('弹窗首次焦点', () => {
  it('优先聚焦业务输入，关闭按钮不作为默认选中', async () => {
    const dialog = document.createElement('div')
    dialog.tabIndex = -1
    dialog.innerHTML = '<button>关闭</button><input type="file" style="display:none"><input aria-label="名称">'
    document.body.appendChild(dialog)
    dialog.addEventListener('open', focusDialogContent)
    dialog.dispatchEvent(new Event('open', { cancelable: true }))
    await nextTick()
    expect(document.activeElement).toBe(dialog.querySelector('input[aria-label="名称"]'))
    dialog.remove()
  })
  it('无表单的详情聚焦对话框容器，保留键盘焦点与关闭能力', async () => {
    const dialog = document.createElement('div')
    dialog.tabIndex = -1
    dialog.innerHTML = '<button>关闭</button>'
    document.body.appendChild(dialog)
    dialog.addEventListener('open', focusDialogContent)
    dialog.dispatchEvent(new Event('open', { cancelable: true }))
    await nextTick()
    expect(document.activeElement).toBe(dialog)
    dialog.remove()
  })
})
