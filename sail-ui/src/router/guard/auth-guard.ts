import type { Router } from 'vue-router'

import pinia from '@/plugins/pinia/setup'
import { useAuthStore } from '@/stores/auth'

/** 登录和页面权限双重检查；未声明业务能力的页面不进入管理工作流。 */
export function setupAuthGuard(router: Router) {
  router.beforeEach(async (to) => {
    if (to.path === '/auth/sign-in')
      return true
    const auth = useAuthStore(pinia)
    if (!auth.token)
      return { path: '/auth/sign-in', query: { redirect: to.fullPath } }
    try {
      await auth.hydrate()
    }
    catch {
      return { path: '/auth/sign-in' }
    }
    if (!auth.token || !auth.identity)
      return { path: '/auth/sign-in' }
    if (!to.meta.sail)
      return { path: '/dashboard' }
    if ((to.meta.admin && !auth.isAdmin) || !auth.can(to.meta.permission))
      return { path: '/dashboard', query: { denied: '1' } }
    return true
  })
  window.addEventListener('sail:session-expired', () => {
    useAuthStore(pinia).reset()
    void router.replace({ path: '/auth/sign-in', query: { expired: '1' } })
  })
}
