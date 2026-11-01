import { nextTick } from 'vue'

/** 对话框打开后优先聚焦业务输入；没有表单时聚焦容器，避免关闭按钮误显选中。 */
export function focusDialogContent(event: Event) {
  if (event.defaultPrevented)
    return
  const container = event.target as HTMLElement
  event.preventDefault()
  // 等表单子组件挂载后聚焦，跳过上传控件的隐藏 file input。
  void nextTick(() => {
    if (!container.isConnected)
      return
    const input = container.querySelector<HTMLElement>('[autofocus], input:not([type="hidden"]):not([type="file"]):not(:disabled), textarea:not(:disabled), [role="combobox"]:not([aria-disabled="true"])')
    ;(input ?? container).focus({ preventScroll: true })
  })
}
