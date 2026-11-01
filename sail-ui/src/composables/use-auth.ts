import { ref } from 'vue'
import { useRouter } from 'vue-router'

import type { LoginRequest } from '@/services/types/sail'

import { useAuthStore } from '@/stores/auth'

/** 认证页面的导航与提交状态，保留上游 composable 接口名称。 */
export function useAuth() {
  const router = useRouter()
  const auth = useAuthStore()
  const loading = ref(false)
  /** 登录成功后只回到站内允许访问的路径。 */
  async function login(credentials?: LoginRequest) {
    if (!credentials)
      throw new Error('必须填写真实账号和密码')
    if (loading.value)
      return
    loading.value = true
    try {
      await auth.signIn(credentials)
      const target = router.currentRoute.value.query.redirect
      await router.replace(
        typeof target === 'string'
        && target.startsWith('/')
        && !target.startsWith('//')
        && !target.startsWith('/auth')
          ? target
          : '/dashboard',
      )
    }
    finally {
      loading.value = false
    }
  }
  /** 退出登录并返回登录页。 */
  async function logout() {
    await auth.signOut()
    await router.replace('/auth/sign-in')
  }
  /** 进入当前账号的工作台。 */
  function toHome() {
    return router.push('/dashboard')
  }
  return { login, logout, toHome, loading }
}
