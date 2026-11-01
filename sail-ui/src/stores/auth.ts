import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import type { Identity, LoginRequest, LoginResult, MenuInfo } from '@/services/types/sail'

import { request } from '@/lib/api-client'
import { readToken, writeToken } from '@/lib/session-token'

/** 登录、身份和实时权限来源于后端，不使用上游演示登录标志。 */
export const useAuthStore = defineStore('user', () => {
  const token = ref(readToken())
  const identity = ref<Identity | null>(null)
  const menus = ref<MenuInfo[]>([])
  const isLogin = computed(() => Boolean(token.value))
  const isAdmin = computed(() => identity.value?.roles.includes('SUPER_ADMIN') ?? false)
  let pending: { token: string, promise: Promise<void> } | null = null
  /** 根据后端返回的权限集合判断操作是否可用。 */
  function can(permission?: string) {
    return (
      !permission || Boolean(identity.value?.permissions.some(p => p === '*' || p === permission))
    )
  }
  /** 清除当前会话及旧用户的菜单权限资料。 */
  function reset() {
    // 会话失效时同步清除旧用户、权限与菜单，避免账号切换串数据。
    writeToken('')
    token.value = ''
    identity.value = null
    menus.value = []
    pending = null
  }
  /** 合并同一会话的身份读取，避免旧会话覆盖新会话。 */
  async function hydrate(force = false) {
    if (identity.value && !force)
      return
    if (pending?.token === token.value)
      return pending.promise
    const currentToken = token.value
    const promise: Promise<void> = Promise.all([
      request<Identity>('/system/auth/me'),
      request<MenuInfo[]>('/system/auth/menus'),
    ])
      .then(([me, navigation]) => {
        // 旧请求不能在退出登录或账号切换后重新写入身份。
        if (token.value === currentToken) {
          identity.value = me
          menus.value = navigation
        }
      })
      .finally(() => {
        if (pending?.promise === promise)
          pending = null
      })
    pending = { token: currentToken, promise }
    return promise
  }
  /** 提交真实凭据并加载新会话的身份与权限。 */
  async function signIn(credentials: LoginRequest) {
    const result = await request<LoginResult>('/system/auth/login', 'POST', { ...credentials })
    reset()
    writeToken(result.tokenValue)
    token.value = result.tokenValue
    try {
      await hydrate(true)
    }
    catch (failure) {
      reset()
      throw failure
    }
  }
  /** 请求后端撤销会话后再清除浏览器令牌。 */
  async function signOut() {
    // 后端撤销成功后再退出；网络失败明确报错，避免误报会话已被撤销。
    await request('/system/auth/logout', 'POST')
    reset()
  }
  return { token, identity, menus, isLogin, isAdmin, can, reset, hydrate, signIn, signOut }
})
