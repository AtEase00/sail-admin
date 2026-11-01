import { onBeforeUnmount, onMounted, ref } from 'vue'

import { request } from '@/lib/api-client'
import { readToken } from '@/lib/session-token'
import { env } from '@/utils/env'

/** 实时通知只作变化信号，断线和回到页面都从数据库补读未读数量。 */
export function useMessageFeed() {
  const unread = ref(0)
  const connection = ref('正在连接消息服务')
  let socket: WebSocket | null = null
  let stopped = false
  let reconnect: number | undefined
  let polling: number | undefined
  /** 从持久化消息记录补读未读数。 */
  async function refresh() {
    if (stopped)
      return
    try {
      const count = await request<number>('/messages/unread-count')
      if (!stopped)
        unread.value = count
      if (!stopped)
        window.dispatchEvent(new Event('sail:message-feed-check'))
    }
    catch {
      connection.value = '消息服务暂不可用'
    }
  }
  /** 取得一次性票据后建立消息连接，失败时安排重连。 */
  async function connect() {
    if (stopped || !readToken())
      return
    const token = readToken()
    try {
      const ticket = await request<{ ticket: string, path: string }>(
        '/messages/socket-ticket',
        'POST',
      )
      if (stopped || token !== readToken())
        return
      const url = new URL(ticket.path, env.VITE_SERVER_API_URL || window.location.origin)
      url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
      url.searchParams.set('ticket', ticket.ticket)
      socket = new WebSocket(url)
      socket.onopen = () => {
        connection.value = '消息已连接'
        void refresh()
      }
      socket.onmessage = (event) => {
        // 连接数据属于外部输入；非 JSON 帧只记录状态，不中断收件箱补读。
        let message: { type?: string } | null
        try {
          message = JSON.parse(String(event.data))
        }
        catch {
          connection.value = '消息格式异常，定时补读中'
          return
        }
        if (!stopped && message?.type === 'MESSAGE_CHANGED') {
          void refresh()
          window.dispatchEvent(new Event('sail:messages-changed'))
        }
      }
      socket.onclose = () => {
        connection.value = '消息连接已断开，定时补读中'
        if (!stopped)
          reconnect = window.setTimeout(connect, 10000)
      }
      socket.onerror = () => {
        connection.value = '消息连接异常，定时补读中'
      }
    }
    catch {
      connection.value = '消息服务暂不可用，定时补读中'
      if (!stopped)
        reconnect = window.setTimeout(connect, 15000)
    }
  }
  /** 会话失效和页面卸载时停止重连，防止旧会话继续申请票据。 */
  function stop() {
    stopped = true
    unread.value = 0
    window.clearTimeout(reconnect)
    window.clearInterval(polling)
    socket?.close()
  }
  onMounted(() => {
    void refresh()
    void connect()
    polling = window.setInterval(refresh, 60000)
    window.addEventListener('sail:messages-read', refresh)
    window.addEventListener('sail:session-expired', stop)
  })
  onBeforeUnmount(() => {
    stop()
    window.removeEventListener('sail:messages-read', refresh)
    window.removeEventListener('sail:session-expired', stop)
  })
  return { unread, connection }
}
