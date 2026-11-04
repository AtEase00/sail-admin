// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'

import { request } from '@/lib/api-client'
import { useMessageFeed } from '@/sail/use-message-feed'

vi.mock('@/lib/api-client', () => ({ request: vi.fn() }))
vi.mock('@/lib/session-token', () => ({ readToken: () => 'test-session' }))
vi.mock('@/utils/env', () => ({ env: { VITE_SERVER_API_URL: '/api' } }))

class FakeSocket {
  static OPEN = 1
  static instances: FakeSocket[] = []
  readyState = 0
  onopen: (() => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  send = vi.fn()
  close = vi.fn(() => { this.readyState = 3 })
  constructor(public url: URL) { FakeSocket.instances.push(this) }
  open() {
    this.readyState = 1
    this.onopen?.()
  }

  message(type: string) { this.onmessage?.({ data: JSON.stringify({ type }) }) }
}
const shell = defineComponent({ setup: useMessageFeed, template: '<div>{{ connection }} {{ unread }}</div>' })
const mounted: ReturnType<typeof mount>[] = []
function render() {
  const wrapper = mount(shell)
  mounted.push(wrapper)
  return wrapper
}

describe('消息实时连接与补读', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('WebSocket', FakeSocket)
    FakeSocket.instances = []
    vi.mocked(request).mockImplementation(async path => path.endsWith('socket-ticket') ? { ticket: 'once', path: '/ws/messages' } : 3)
  })
  afterEach(() => {
    mounted.splice(0).forEach(w => w.unmount())
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('支持相对 API 地址，补读失败后成功恢复不会遗留故障提示', async () => {
    const wrapper = render()
    await flushPromises()
    const socket = FakeSocket.instances[0]!
    expect(socket.url.pathname).toBe('/ws/messages')
    expect(socket.url.protocol).toBe('ws:')
    socket.open()
    await flushPromises()
    expect(wrapper.text()).toContain('消息已连接 3')
    vi.mocked(request).mockRejectedValueOnce(new Error('temporary'))
    window.dispatchEvent(new Event('sail:messages-read'))
    await flushPromises()
    expect(wrapper.text()).toContain('消息补读暂不可用')
    window.dispatchEvent(new Event('sail:messages-read'))
    await flushPromises()
    expect(wrapper.text()).toContain('消息已连接 3')
  })

  it('发送心跳并处理 PONG；无响应时断开并使用新票据重连', async () => {
    render()
    await flushPromises()
    const socket = FakeSocket.instances[0]!
    socket.open()
    await vi.advanceTimersByTimeAsync(25000)
    expect(socket.send).toHaveBeenCalledWith('{"type":"PING"}')
    socket.message('PONG')
    await vi.advanceTimersByTimeAsync(10000)
    expect(socket.close).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(25000)
    expect(socket.close).toHaveBeenCalledOnce()
    await vi.advanceTimersByTimeAsync(2000)
    expect(FakeSocket.instances).toHaveLength(2)
    expect(vi.mocked(request).mock.calls.filter(([path]) => path.endsWith('socket-ticket'))).toHaveLength(2)
  })

  it('握手超时可重试，停止后不再申请票据或补读', async () => {
    const wrapper = render()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(10000)
    expect(wrapper.text()).toContain('消息握手超时')
    window.dispatchEvent(new Event('sail:session-expired'))
    const requests = vi.mocked(request).mock.calls.length
    await vi.advanceTimersByTimeAsync(120000)
    window.dispatchEvent(new Event('online'))
    await flushPromises()
    expect(request).toHaveBeenCalledTimes(requests)
    expect(FakeSocket.instances).toHaveLength(1)
  })

  it('卸载时尚未完成的票据请求不能创建连接', async () => {
    let resolve!: (value: unknown) => void
    vi.mocked(request).mockImplementation((path) => {
      if (path.endsWith('socket-ticket')) {
        return new Promise((r) => {
          resolve = r
        })
      }
      return Promise.resolve(0)
    })
    const wrapper = render()
    wrapper.unmount()
    mounted.pop()
    resolve({ ticket: 'late', path: '/ws/messages' })
    await flushPromises()
    expect(FakeSocket.instances).toHaveLength(0)
    expect(vi.getTimerCount()).toBe(0)
  })
})
