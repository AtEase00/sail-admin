import { ofetch } from 'ofetch'

import type { ApiResult } from '@/services/types/sail'

import { API_BASE_URL, API_TIMEOUT } from '@/constants/app-config'
import { readToken, writeToken } from '@/lib/session-token'

/** 服务端错误保留追踪号，界面展示明确业务原因。 */
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public traceId = '',
  ) {
    super(traceId ? `${message}（追踪号 ${traceId}）` : message)
  }
}
/** 保留上游 ofetch 入口，实际请求携带 Sail Bearer 令牌。 */
export const apiFetch = ofetch.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
  retry: 0,
  onRequest({ options }) {
    const token = readToken()
    if (token)
      options.headers.set('Authorization', `Bearer ${token}`)
  },
  onResponseError({ response, options }) {
    // 只对真实认证失效撤销会话，403 不冒充未登录。
    if (
      response.status === 401
      && readToken()
      && options.headers.get('Authorization') === `Bearer ${readToken()}`
    ) {
      writeToken('')
      window.dispatchEvent(new Event('sail:session-expired'))
    }
  },
})
/** JSON 与 multipart 共用请求入口，提交类请求不自动重试。 */
export async function request<T>(
  path: string,
  method = 'GET',
  body?: Record<string, unknown> | unknown[] | FormData,
  query?: Record<string, string | number>,
): Promise<T> {
  try {
    const result = await apiFetch<ApiResult<T>>(path, {
      method: method as 'GET' | 'POST' | 'PUT' | 'DELETE',
      body,
      query,
    })
    if (result.code !== 0)
      throw new ApiError(result.message, result.code, result.traceId)
    return result.data
  }
  catch (error) {
    if (error instanceof ApiError)
      throw error
    const failure = error as { data?: ApiResult<never>, status?: number }
    if (failure.data) {
      throw new ApiError(
        failure.data.message,
        failure.status ?? failure.data.code,
        failure.data.traceId,
      )
    }
    throw new ApiError('无法连接服务，请检查网络或服务地址后重试', failure.status ?? 0)
  }
}
/** 下载先鉴权取得 Blob，不将令牌放入下载 URL。 */
export async function downloadFile(id: number, name: string) {
  const blob = await apiFetch<Blob, 'blob'>(`/file/objects/${id}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
