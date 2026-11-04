import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { request } from '@/lib/api-client'
import { readToken } from '@/lib/session-token'
import { env } from '@/utils/env'

/** 实时信号与 HTTP 补读分别记录健康状态，恢复后不遗留错误提示。 */
export function useMessageFeed() {
  const unread = ref(0)
  const realtime = ref('正在连接消息服务')
  const readFailed = ref(false)
  const connection = computed(() => readFailed.value ? '消息补读暂不可用，稍后重试' : realtime.value)
  let socket: WebSocket | null = null
  let stopped = false
  let connecting = false
  let refreshing = false
  let reconnect: number | undefined
  let polling: number | undefined
  let heartbeat: number | undefined
  let deadline: number | undefined
  let attempt = 0

  async function refresh() {
    if (stopped || refreshing || !readToken())
      return
    const token = readToken()
    refreshing = true
    try {
      const count = await request<number>('/messages/unread-count')
      if (!stopped && token === readToken()) {
        unread.value = count
        readFailed.value = false
        window.dispatchEvent(new Event('sail:message-feed-check'))
      }
    }
    catch {
      if (!stopped && token === readToken())
        readFailed.value = true
    }
    finally {
      refreshing = false
    }
  }

  function releaseSocket() {
    window.clearInterval(heartbeat)
    window.clearTimeout(deadline)
    const current = socket
    socket = null
    if (current) {
      current.onopen = current.onmessage = current.onclose = current.onerror = null
      current.close()
    }
  }

  function retry(reason: string) {
    releaseSocket()
    if (stopped)
      return
    realtime.value = reason
    window.clearTimeout(reconnect)
    // 指数退避加抖动，避免服务恢复时所有浏览器同时抢票据。
    const delay = Math.min(30000, 1000 * 2 ** Math.min(attempt++, 5)) + Math.random() * 1000
    reconnect = window.setTimeout(connect, delay)
  }

  function ping() {
    if (socket?.readyState !== WebSocket.OPEN)
      return
    try {
      socket.send('{"type":"PING"}')
      window.clearTimeout(deadline)
      deadline = window.setTimeout(retry, 10000, '消息连接超时，定时补读中')
    }
    catch {
      retry('消息连接异常，定时补读中')
    }
  }

  async function connect() {
    if (stopped || connecting || socket || !readToken())
      return
    window.clearTimeout(reconnect)
    connecting = true
    const token = readToken()
    try {
      const ticket = await request<{ ticket: string, path: string }>('/messages/socket-ticket', 'POST')
      if (stopped || token !== readToken())
        return
      const base = new URL(env.VITE_SERVER_API_URL || '/', window.location.origin)
      const url = new URL(ticket.path, base)
      url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
      url.searchParams.set('ticket', ticket.ticket)
      const current = new WebSocket(url)
      socket = current
      deadline = window.setTimeout(retry, 10000, '消息握手超时，定时补读中')
      current.onopen = () => {
        if (stopped || socket !== current)
          return
        window.clearTimeout(deadline)
        attempt = 0
        realtime.value = '消息已连接'
        heartbeat = window.setInterval(ping, 25000)
        void refresh()
      }
      current.onmessage = (event) => {
        if (stopped || socket !== current)
          return
        let message: { type?: string } | null
        try {
          message = JSON.parse(String(event.data))
        }
        catch {
          retry('消息格式异常，定时补读中')
          return
        }
        if (message?.type === 'PONG') {
          window.clearTimeout(deadline)
        }
        else if (message?.type === 'MESSAGE_CHANGED') {
          void refresh()
          window.dispatchEvent(new Event('sail:messages-changed'))
        }
      }
      current.onclose = () => retry('消息连接已断开，定时补读中')
      current.onerror = () => retry('消息连接异常，定时补读中')
    }
    catch {
      if (!stopped && token === readToken())
        retry('实时消息暂不可用，定时补读中')
    }
    finally {
      connecting = false
    }
  }

  function resume() {
    if (stopped || document.visibilityState === 'hidden')
      return
    void refresh()
    void connect()
    ping()
  }

  function stop() {
    stopped = true
    unread.value = 0
    window.clearTimeout(reconnect)
    window.clearInterval(polling)
    releaseSocket()
  }
  onMounted(() => {
    void refresh()
    void connect()
    polling = window.setInterval(refresh, 60000)
    window.addEventListener('sail:messages-read', refresh)
    window.addEventListener('sail:session-expired', stop)
    window.addEventListener('online', resume)
    document.addEventListener('visibilitychange', resume)
  })
  onBeforeUnmount(() => {
    stop()
    window.removeEventListener('sail:messages-read', refresh)
    window.removeEventListener('sail:session-expired', stop)
    window.removeEventListener('online', resume)
    document.removeEventListener('visibilitychange', resume)
  })
  return { unread, connection }
}
