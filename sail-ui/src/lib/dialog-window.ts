import { Maximize2, Minimize2 } from '@lucide/vue'
import { h, render } from 'vue'

/** 统一适配 Reka、Ant 弹窗及抽屉，保留各组件原有的关闭、焦点和遮罩管理。 */
const surfaceSelector = '[data-slot="dialog-content"], [data-slot="alert-dialog-content"], [data-slot="sheet-content"], [data-slot="drawer-content"], .ant-modal-content, .ant-drawer-content'
const headerSelector = '[data-slot="dialog-header"], [data-slot="alert-dialog-header"], [data-slot="sheet-header"], [data-slot="drawer-header"], .ant-modal-header, .ant-modal-confirm-title, .ant-drawer-header'
const interactiveSelector = 'button, a, input, textarea, select, [role="button"], [role="combobox"], [contenteditable="true"]'
const positionProperties = ['position', 'inset', 'left', 'top', 'right', 'bottom', 'width', 'height', 'max-width', 'max-height', 'margin', 'margin-top', 'margin-right', 'margin-bottom', 'margin-left', 'transform', 'translate'] as const

/** 记录被窗口交互接管的样式，恢复时保留其他业务样式。 */
function savePosition(element: HTMLElement) {
  const values = positionProperties.map(key => [key, element.style.getPropertyValue(key), element.style.getPropertyPriority(key)] as const)
  return () => {
    for (const [key, value, priority] of values) {
      if (value)
        element.style.setProperty(key, value, priority)
      else
        element.style.removeProperty(key)
    }
  }
}

/** 将窗口完整限制在视口内，小屏下仍能操作标题和关闭按钮。 */
export function boundDialogPosition(rect: Pick<DOMRect, 'width' | 'height'>, point: { x: number, y: number }, viewport: { width: number, height: number }) {
  return {
    x: Math.max(0, Math.min(point.x, Math.max(0, viewport.width - rect.width))),
    y: Math.max(0, Math.min(point.y, Math.max(0, viewport.height - rect.height))),
  }
}

/** 为单个弹层安装拖动和全屏操作，返回关闭复位与完整卸载接口。 */
export function attachDialogWindow(surface: HTMLElement) {
  const header = surface.querySelector<HTMLElement>(headerSelector)
  if (!header)
    return
  // Ant 的尺寸与位移由外层容器管理，不能只移动内容造成遮罩点击区域错位。
  const frame = surface.closest<HTMLElement>('.ant-modal, .ant-drawer-content-wrapper') ?? surface
  const restoreInitial = savePosition(frame)
  let restoreWindow: (() => void) | undefined
  let fullscreen = false
  let positioned = false
  let drag: { id: number, x: number, y: number, left: number, top: number } | undefined
  const control = document.createElement('button')
  control.type = 'button'
  control.className = 'sail-window-toggle'
  surface.appendChild(control)
  surface.classList.add('sail-window')
  header.classList.add('sail-window-header')
  const hadVaulDragLock = header.hasAttribute('data-vaul-no-drag')
  // 移动端抽屉标题使用统一拖动，不与 Vaul 的下拉关闭手势争抢指针。
  header.setAttribute('data-vaul-no-drag', '')

  /** 同步图标、提示和辅助技术状态，不参与业务表单提交。 */
  function updateControl() {
    const label = fullscreen ? '还原窗口' : '系统内全屏'
    control.title = label
    control.setAttribute('aria-label', label)
    control.setAttribute('aria-pressed', String(fullscreen))
    render(h(fullscreen ? Minimize2 : Maximize2, { 'size': 16, 'aria-hidden': true }), control)
  }

  /** 固定窗口坐标，清除居中变换，避免拖动时跳位。 */
  function place(x: number, y: number) {
    const rect = frame.getBoundingClientRect()
    const point = boundDialogPosition(rect, { x, y }, { width: window.innerWidth, height: window.innerHeight })
    Object.assign(frame.style, {
      position: 'fixed',
      left: `${point.x}px`,
      top: `${point.y}px`,
      right: 'auto',
      bottom: 'auto',
      width: `${Math.min(rect.width, window.innerWidth)}px`,
      maxWidth: '100dvw',
      maxHeight: `${Math.min(rect.height, window.innerHeight)}px`,
      margin: '0',
      transform: 'none',
      translate: 'none',
    })
    frame.classList.add('sail-window-positioned')
    positioned = true
  }

  /** 拖动结束释放捕获；不拦截冒泡，保留 Reka 对内部指针的判断。 */
  function endDrag() {
    if (!drag)
      return
    if (header!.hasPointerCapture?.(drag.id))
      header!.releasePointerCapture(drag.id)
    drag = undefined
    header!.classList.remove('sail-window-dragging')
  }

  /** 在系统视口内全屏，退出后还原拖动前的尺寸和位置。 */
  function toggleFullscreen() {
    endDrag()
    if (fullscreen) {
      restoreWindow?.()
      frame.classList.remove('sail-window-fullscreen')
      if (!positioned)
        frame.classList.remove('sail-window-positioned')
    }
    else {
      restoreWindow = savePosition(frame)
      Object.assign(frame.style, {
        position: 'fixed',
        inset: '0',
        width: '100dvw',
        height: '100dvh',
        maxWidth: '100dvw',
        maxHeight: '100dvh',
        margin: '0',
        transform: 'none',
        translate: 'none',
      })
      frame.classList.add('sail-window-fullscreen', 'sail-window-positioned')
    }
    fullscreen = !fullscreen
    // 全屏期间可能改变屏幕尺寸，还原时重新限制已移动窗口的位置。
    if (!fullscreen)
      resize()
    updateControl()
  }

  /** 只有标题空白区域响应主指针，按钮、输入框和链接继续执行原有行为。 */
  function startDrag(event: PointerEvent) {
    if (fullscreen || event.button !== 0 || (event.target as Element).closest(interactiveSelector))
      return
    const rect = frame.getBoundingClientRect()
    place(rect.left, rect.top)
    drag = { id: event.pointerId, x: event.clientX, y: event.clientY, left: rect.left, top: rect.top }
    header!.setPointerCapture?.(event.pointerId)
    header!.classList.add('sail-window-dragging')
    event.preventDefault()
  }

  /** 捕获指针后持续计算视口坐标，鼠标移出标题仍能顺畅拖动。 */
  function moveDrag(event: PointerEvent) {
    if (!drag || drag.id !== event.pointerId)
      return
    place(drag.left + event.clientX - drag.x, drag.top + event.clientY - drag.y)
  }

  /** 双击标题切换窗口状态，避免影响标题中业务控件。 */
  function doubleClick(event: MouseEvent) {
    if (!(event.target as Element).closest(interactiveSelector))
      toggleFullscreen()
  }

  /** 视口缩小时收回越界窗口，全屏尺寸由动态视口单位自动跟随。 */
  function resize() {
    if (!positioned || fullscreen)
      return
    const rect = frame.getBoundingClientRect()
    place(rect.left, rect.top)
  }

  /** 关闭后复位，复用同一弹窗节点时不沿用上次全屏和位移。 */
  function reset() {
    endDrag()
    if (!fullscreen && !positioned)
      return
    restoreInitial()
    frame.classList.remove('sail-window-fullscreen', 'sail-window-positioned')
    fullscreen = false
    positioned = false
    restoreWindow = undefined
    updateControl()
  }

  control.addEventListener('click', toggleFullscreen)
  header.addEventListener('pointerdown', startDrag)
  header.addEventListener('pointermove', moveDrag)
  header.addEventListener('pointerup', endDrag)
  header.addEventListener('pointercancel', endDrag)
  header.addEventListener('lostpointercapture', endDrag)
  header.addEventListener('dblclick', doubleClick)
  window.addEventListener('resize', resize)
  updateControl()

  return {
    reset,
    /** 清除监听器、渲染实例与添加的样式，不遗留页面级事件。 */
    dispose() {
      reset()
      control.removeEventListener('click', toggleFullscreen)
      header.removeEventListener('pointerdown', startDrag)
      header.removeEventListener('pointermove', moveDrag)
      header.removeEventListener('pointerup', endDrag)
      header.removeEventListener('pointercancel', endDrag)
      header.removeEventListener('lostpointercapture', endDrag)
      header.removeEventListener('dblclick', doubleClick)
      window.removeEventListener('resize', resize)
      render(null, control)
      control.remove()
      surface.classList.remove('sail-window')
      header.classList.remove('sail-window-header')
      if (!hadVaulDragLock)
        header.removeAttribute('data-vaul-no-drag')
    },
  }
}

/** 全局登记 Teleport 和命令式确认框；只监听弹层结构及开关属性，不轮询页面。 */
export function installDialogWindows(root: HTMLElement = document.body) {
  const windows = new Map<HTMLElement, NonNullable<ReturnType<typeof attachDialogWindow>>>()
  const sync = () => {
    for (const [surface, controller] of windows) {
      if (!root.contains(surface)) {
        controller.dispose()
        windows.delete(surface)
      }
      else if (surface.dataset.state === 'closed' || surface.closest('[style*="display: none"], .ant-drawer:not(.ant-drawer-open)')) {
        controller.reset()
      }
    }
    for (const surface of root.querySelectorAll<HTMLElement>(surfaceSelector)) {
      if (windows.has(surface))
        continue
      const controller = attachDialogWindow(surface)
      if (controller)
        windows.set(surface, controller)
    }
  }
  const observer = new MutationObserver(sync)
  observer.observe(root, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-state', 'class', 'style'] })
  sync()
  return () => {
    observer.disconnect()
    for (const controller of windows.values())
      controller.dispose()
    windows.clear()
  }
}
