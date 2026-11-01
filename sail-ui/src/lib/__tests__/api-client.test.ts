// @vitest-environment happy-dom
import { createServer } from 'node:http'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'

import { apiFetch } from '@/lib/api-client'
import { readToken, writeToken } from '@/lib/session-token'

let origin = ''
let attempts = 0
let notifyOld: (() => void) | undefined
let releaseOld: (() => void) | undefined
// 实际监听随机本地端口验证请求行为，不替换生产 fetch 或会话实现。
const server = createServer(async (request, response) => {
  response.setHeader('Access-Control-Allow-Origin', '*')
  response.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type')
  response.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS')
  response.setHeader('Content-Type', 'application/json')
  // 测试页与随机端口不同源，预检必须成功才会发出真正的认证请求。
  if (request.method === 'OPTIONS') {
    response.writeHead(204).end()
    return
  }
  attempts++
  if (request.url === '/old') {
    notifyOld?.()
    await new Promise<void>((resolve) => {
      releaseOld = resolve
    })
  }
  response.statusCode = request.url === '/forbidden' ? 403 : request.url === '/expired' || request.url === '/old' ? 401 : 200
  response.end(JSON.stringify({ authorization: request.headers.authorization, message: 'HTTP transport test' }))
})

beforeAll(async () => {
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  const address = server.address()
  if (!address || typeof address === 'string')
    throw new Error('本地 HTTP 验收端口未建立')
  origin = `http://127.0.0.1:${address.port}`
})
afterEach(() => {
  writeToken('')
  attempts = 0
})
afterAll(async () => {
  releaseOld?.()
  await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
})

describe('真实 HTTP 请求与会话隔离', () => {
  it('携带当前 Bearer 令牌', async () => {
    writeToken('transport-session')
    expect(await apiFetch(`${origin}/ok`)).toMatchObject({ authorization: 'Bearer transport-session' })
  })
  it('403 保留当前会话且不重复请求', async () => {
    writeToken('transport-session')
    await expect(apiFetch(`${origin}/forbidden`)).rejects.toMatchObject({ status: 403 })
    expect(readToken()).toBe('transport-session')
    expect(attempts).toBe(1)
  })
  it('当前会话 401 只撤销一次并发出失效事件', async () => {
    writeToken('transport-session')
    let events = 0
    const listener = () => events++
    window.addEventListener('sail:session-expired', listener)
    try {
      await expect(apiFetch(`${origin}/expired`)).rejects.toMatchObject({ status: 401 })
      await expect(apiFetch(`${origin}/expired`)).rejects.toMatchObject({ status: 401 })
      expect(readToken()).toBe('')
      expect(events).toBe(1)
    }
    finally {
      window.removeEventListener('sail:session-expired', listener)
    }
  })
  it('旧请求的 401 不能撤销后来建立的新会话', async () => {
    writeToken('old-session')
    const arrived = new Promise<void>((resolve) => {
      notifyOld = resolve
    })
    const oldRequest = apiFetch(`${origin}/old`).catch(error => error)
    await arrived
    writeToken('new-session')
    releaseOld?.()
    expect(await oldRequest).toMatchObject({ status: 401 })
    expect(readToken()).toBe('new-session')
  })
})
