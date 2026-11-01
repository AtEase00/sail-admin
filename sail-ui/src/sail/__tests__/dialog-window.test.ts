import { mount } from '@vue/test-utils'
// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { attachDialogWindow, boundDialogPosition, installDialogWindows } from '@/lib/dialog-window'

const disposers: (() => void)[] = []
afterEach(() => {
  disposers.splice(0).forEach(dispose => dispose())
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

/** 创建接近真实层级的弹层，模拟浏览器几何尺寸供指针坐标回归使用。 */
function createWindow(ant = false) {
  const frame = document.createElement('div')
  frame.className = ant ? 'ant-modal' : ''
  frame.style.cssText = 'width: 520px; max-height: 85vh; transform: translate(-50%, -50%);'
  frame.innerHTML = ant
    ? '<div class="ant-modal-content"><div class="ant-modal-header">公告<button>业务操作</button></div><div class="ant-modal-body"><input></div></div>'
    : '<div data-slot="dialog-header">标题<button>业务操作</button></div><input>'
  const surface = ant ? frame.firstElementChild as HTMLElement : frame
  if (!ant)
    surface.dataset.slot = 'dialog-content'
  document.body.appendChild(frame)
  vi.spyOn(frame, 'getBoundingClientRect').mockImplementation(() => ({
    x: 200,
    y: 100,
    left: Number.parseFloat(frame.style.left) || 200,
    top: Number.parseFloat(frame.style.top) || 100,
    width: 520,
    height: 300,
    right: 720,
    bottom: 400,
    toJSON: () => ({}),
  }))
  return { frame, surface, header: surface.firstElementChild as HTMLElement }
}

/** jsdom 的指针事件补齐标识，事件继续冒泡以检查弹窗内外判断。 */
function pointer(target: Element, type: string, x: number, y: number) {
  const event = new MouseEvent(type, { bubbles: true, cancelable: true, clientX: x, clientY: y, button: 0 })
  Object.defineProperty(event, 'pointerId', { value: 1 })
  target.dispatchEvent(event)
}

describe('弹窗统一窗口交互', () => {
  it('边界限制包括超大窗口，标题不会被拖到屏幕外', () => {
    expect(boundDialogPosition({ width: 520, height: 300 }, { x: 9999, y: -50 }, { width: 1024, height: 768 })).toEqual({ x: 504, y: 0 })
    expect(boundDialogPosition({ width: 1200, height: 900 }, { x: 60, y: 50 }, { width: 800, height: 600 })).toEqual({ x: 0, y: 0 })
  })

  it.each([false, true])('拖动、全屏和还原保留内容与原始尺寸（Ant=%s）', (ant) => {
    const { frame, surface, header } = createWindow(ant)
    const initial = frame.style.cssText
    const controller = attachDialogWindow(surface)!
    disposers.push(() => controller.dispose())
    const input = surface.querySelector('input')!
    input.value = '未提交内容'
    pointer(header, 'pointerdown', 220, 120)
    pointer(header, 'pointermove', 320, 170)
    pointer(header, 'pointerup', 320, 170)
    expect(frame.style.left).toBe('300px')
    expect(frame.style.top).toBe('150px')
    const moved = { left: frame.style.left, top: frame.style.top, width: frame.style.width, transform: frame.style.transform }
    const control = surface.querySelector<HTMLButtonElement>('.sail-window-toggle')!
    control.click()
    expect(frame.classList.contains('sail-window-fullscreen')).toBe(true)
    expect(control.getAttribute('aria-label')).toBe('还原窗口')
    expect(input.value).toBe('未提交内容')
    control.click()
    expect({ left: frame.style.left, top: frame.style.top, width: frame.style.width, transform: frame.style.transform }).toEqual(moved)
    controller.reset()
    expect(frame.style.cssText).toBe(initial)
  })

  it('标题业务按钮不触发拖动，双击空白可全屏，卸载后监听器失效', () => {
    const { frame, surface, header } = createWindow()
    const initial = frame.style.cssText
    const controller = attachDialogWindow(surface)!
    pointer(header.querySelector('button')!, 'pointerdown', 220, 120)
    pointer(header, 'pointermove', 400, 400)
    expect(frame.style.cssText).toBe(initial)
    header.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }))
    expect(frame.classList.contains('sail-window-fullscreen')).toBe(true)
    controller.dispose()
    header.dispatchEvent(new MouseEvent('dblclick', { bubbles: true }))
    expect(frame.classList.contains('sail-window-fullscreen')).toBe(false)
    expect(surface.querySelector('.sail-window-toggle')).toBeNull()
  })

  it('命令式弹层自动登记且不重复按钮，关闭复位，移除后释放控制', async () => {
    const dispose = installDialogWindows()
    disposers.push(dispose)
    const { frame, surface } = createWindow(true)
    await new Promise(resolve => setTimeout(resolve, 0))
    const control = surface.querySelector<HTMLButtonElement>('.sail-window-toggle')!
    control.click()
    expect(frame.classList.contains('sail-window-fullscreen')).toBe(true)
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(surface.querySelectorAll('.sail-window-toggle')).toHaveLength(1)
    frame.style.display = 'none'
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(frame.classList.contains('sail-window-fullscreen')).toBe(false)
    frame.remove()
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(surface.querySelector('.sail-window-toggle')).toBeNull()
  })

  it('真实 Reka 弹窗点击、输入和全屏不会误关闭，关闭按钮正常生效', async () => {
    const Harness = defineComponent({
      setup() {
        const open = ref(true)
        return () => h(Dialog, { 'open': open.value, 'onUpdate:open': value => open.value = value }, () => h(DialogContent, {}, () => [
          h(DialogHeader, {}, () => h(DialogTitle, {}, () => '测试窗口')),
          h(DialogDescription, {}, () => '回归检查'),
          h('input', { 'aria-label': '名称' }),
        ]))
      },
    })
    const wrapper = mount(Harness, { attachTo: document.body })
    disposers.push(() => wrapper.unmount())
    disposers.push(installDialogWindows())
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 0))
    const dialog = document.querySelector<HTMLElement>('[data-slot="dialog-content"]')!
    const input = dialog.querySelector('input')!
    pointer(input, 'pointerdown', 100, 100)
    input.click()
    input.value = '保持编辑'
    dialog.querySelector<HTMLButtonElement>('.sail-window-toggle')!.click()
    await nextTick()
    expect(document.querySelector('[data-slot="dialog-content"]')).toBe(dialog)
    expect(input.value).toBe('保持编辑')
    pointer(document.body, 'pointerdown', 0, 0)
    await nextTick()
    expect(dialog.dataset.state).toBe('open')
    dialog.querySelector<HTMLButtonElement>('[data-slot="dialog-close"]')!.click()
    await nextTick()
    expect(dialog.dataset.state).toBe('closed')
  })

  it('抽屉移动实际尺寸容器，全屏关闭后恢复靠边布局', () => {
    const root = document.createElement('div')
    root.className = 'ant-drawer ant-drawer-open'
    root.innerHTML = '<div class="ant-drawer-content-wrapper" style="width:800px"><div class="ant-drawer-content"><div class="ant-drawer-header">消息详情</div><div class="ant-drawer-body">内容</div></div></div>'
    document.body.appendChild(root)
    const surface = root.querySelector<HTMLElement>('.ant-drawer-content')!
    const frame = root.querySelector<HTMLElement>('.ant-drawer-content-wrapper')!
    const controller = attachDialogWindow(surface)!
    disposers.push(() => controller.dispose())
    surface.querySelector<HTMLButtonElement>('.sail-window-toggle')!.click()
    expect(frame.style.width).toBe('100dvw')
    controller.reset()
    expect(frame.style.width).toBe('800px')
    expect(frame.style.position).toBe('')
  })
})
