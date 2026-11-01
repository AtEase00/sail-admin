import { useDark } from '@vueuse/core'
import { storeToRefs } from 'pinia'
import { watch } from 'vue'

import { THEMES } from '@/constants/themes'
import { createThemePalette } from '@/sail/theme-palette'
import { useThemeStore } from '@/stores/theme'

/** 将当前浏览器保存的外观设置应用到全局语义颜色。 */
export function useSystemTheme() {
  const themeStore = useThemeStore()
  const { setTheme, setRadius } = themeStore
  const { theme, radius, brandColor } = storeToRefs(themeStore)
  const dark = useDark()

  if (typeof document !== 'undefined') {
    watch(theme, (theme) => {
      document.documentElement.classList.remove(...THEMES.map(t => `theme-${t}`))
      document.documentElement.classList.add(`theme-${theme}`)
    }, { immediate: true })

    watch(radius, (radius) => {
      document.documentElement.style.setProperty('--radius', `${radius}rem`)
    }, { immediate: true })

    // 主色与模式联动更新语义变量，避免第三方控件与系统按钮出现两套配色。
    watch([brandColor, dark], ([color, isDark]) => {
      const palette = createThemePalette(color, isDark)
      Object.entries(palette).forEach(([name, value]) => document.documentElement.style.setProperty(name, value))
    }, { immediate: true })
  }

  return {
    theme,
    radius,
    setTheme,
    setRadius,
    brandColor,
  }
}
