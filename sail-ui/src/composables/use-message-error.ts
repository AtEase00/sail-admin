import { customRef } from 'vue'

import { message } from '@/lib/message'

/** 保留页面的失败/重试状态，反馈统一交给全局 Message，连续同错不重复弹出。 */
export function useMessageError() {
  let value = ''
  return customRef<string>((track, trigger) => ({
    get() {
      track()
      return value
    },
    set(next) {
      if (next === value)
        return
      value = next
      if (next)
        message.error(next)
      trigger()
    },
  }))
}
