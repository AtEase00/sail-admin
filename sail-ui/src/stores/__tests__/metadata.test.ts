// @vitest-environment happy-dom
import { createServer } from 'node:http'
import { createPinia, setActivePinia } from 'pinia'
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'

let dictionaryRequests = 0
let unavailable = false
let label = '创建记录'
// 测试服务器只提供展示元数据，用真实 HTTP 验证加载去重、更新和失败清理。
const server = createServer((request, response) => {
  response.setHeader('Access-Control-Allow-Origin', '*')
  response.setHeader('Content-Type', 'application/json')
  response.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type')
  response.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS')
  // 预检不计入业务请求，服务故障只影响真实 GET 响应。
  if (request.method === 'OPTIONS') {
    response.writeHead(204).end()
    return
  }
  if (request.url === '/api/system/dictionaries/options')
    dictionaryRequests++
  response.statusCode = unavailable ? 503 : 200
  response.end(JSON.stringify({ code: unavailable ? 503 : 0, message: unavailable ? '字典服务暂不可用' : '成功', traceId: 'metadata-http-test', data: request.url?.includes('dictionaries') ? { sys_operation_type: [{ id: 1, value: 'INSERT', label, color: 'success', isDefault: true }], sys_status: [{ id: 2, value: '1', label: '有效', color: 'success' }], sys_result: [{ id: 3, value: 'false', label: '失败', color: 'error' }] } : { 'website.title': '展示配置', 'website.logo': '/logo.svg' } }))
})
let dictionaries: typeof import('../dictionaries')
let website: typeof import('../website')
beforeAll(async () => {
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  const address = server.address()
  if (!address || typeof address === 'string')
    throw new Error('元数据测试端口未建立')
  vi.stubEnv('VITE_SERVER_API_URL', `http://127.0.0.1:${address.port}`)
  vi.stubEnv('VITE_SERVER_API_PREFIX', '/api')
  dictionaries = await import('../dictionaries')
  website = await import('../website')
})
beforeEach(() => {
  setActivePinia(createPinia())
  sessionStorage.clear()
  dictionaryRequests = 0
  unavailable = false
  label = '创建记录'
})
afterAll(async () => {
  vi.unstubAllEnvs()
  await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
})

describe('后台展示元数据', () => {
  it('合并并发加载，展示修改的标签并保留未知业务值', async () => {
    const store = dictionaries.useDictionaryStore()
    await Promise.all([store.load(), store.load(), store.load()])
    expect(dictionaryRequests).toBe(1)
    expect(store.label('sys_operation_type', 'INSERT')).toBe('创建记录')
    expect(store.label('sys_status', 1)).toBe('有效')
    expect(store.label('sys_result', false)).toBe('失败')
    expect(store.label('sys_operation_type', 'UNKNOWN_TYPE')).toBe('UNKNOWN_TYPE')
    expect(store.label('sys_operation_type', null)).toBe('—')
    label = '新增'
    await store.load()
    expect(store.label('sys_operation_type', 'INSERT')).toBe('新增')
    expect(store.item('sys_operation_type', 'INSERT')?.color).toBe('success')
  })
  it('加载失败清除过期字典，用户错误不拼接追踪号', async () => {
    const store = dictionaries.useDictionaryStore()
    await store.load()
    unavailable = true
    await store.load()
    expect(store.options('sys_operation_type')).toEqual([])
    expect(store.error).toContain('字典服务暂不可用')
    expect(store.error).not.toContain('metadata-http-test')
  })
  it('站点资料读取实际配置，失败保留已加载内容并明确报错', async () => {
    const store = website.useWebsiteStore()
    await store.load()
    expect(store.title).toBe('展示配置')
    expect(store.logo).toBe('/logo.svg')
    unavailable = true
    await store.load()
    expect(store.title).toBe('展示配置')
    expect(store.error).toContain('字典服务暂不可用')
  })
})
