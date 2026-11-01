import { defineStore } from 'pinia'

import type { ContentLayout, Radius, Theme } from '@/constants/themes'

import { DEFAULT_BRAND_COLOR } from '@/sail/theme-palette'

export const useThemeStore = defineStore('system-config', () => {
  const brandColor = shallowRef(DEFAULT_BRAND_COLOR)
  /** 只接受完整十六进制色值，输入错误时保留当前已选主题。 */
  function setBrandColor(color: string) {
    if (!/^#[a-f\d]{6}$/i.test(color))
      return false
    brandColor.value = color.toLowerCase()
    return true
  }
  const radius = shallowRef(0.5)
  function setRadius(newRadius: Radius) {
    radius.value = newRadius
  }
  const theme = shallowRef<Theme>('zinc')
  function setTheme(newTheme: Theme) {
    theme.value = newTheme
  }

  const contentLayout = shallowRef<ContentLayout>('centered')
  function setContentLayout(newContentLayout: ContentLayout) {
    contentLayout.value = newContentLayout
  }
  return {
    brandColor,
    setBrandColor,
    radius,
    setRadius,

    theme,
    setTheme,

    contentLayout,
    setContentLayout,
  }
}, {
  persist: {
    storage: typeof localStorage !== 'undefined' ? localStorage : undefined,
  },
})
