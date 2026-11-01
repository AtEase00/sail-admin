import { defineStore } from 'pinia'
import { ref } from 'vue'

import { request } from '@/lib/api-client'

/** 站点展示资料从后台受控配置获取，配置缺失时保留明确提示。 */
export const useWebsiteStore = defineStore('sail-website', () => {
  const title = ref('')
  const logo = ref('')
  const error = ref('')
  /** 浏览器标签图标与侧栏共用后台标志，清除旧媒体条件避免覆盖。 */
  function updateFavicon() {
    document.querySelectorAll('link[rel="icon"]').forEach(link => link.remove())
    const link = document.createElement('link')
    link.rel = 'icon'
    link.href = logo.value || '/logo-dark.svg'
    document.head.appendChild(link)
  }
  /** 加载受控的站点展示字段，服务失败时保留明确的错误状态。 */
  async function load() {
    try {
      const values = await request<Record<string, string>>('/system/configs/website')
      title.value = values['website.title'] ?? ''
      logo.value = values['website.logo'] ?? ''
      updateFavicon()
      error.value = ''
    }
    catch (failure) {
      error.value = failure instanceof Error ? failure.message : '站点信息读取失败'
    }
  }
  return { title, logo, error, load }
})
