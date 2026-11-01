// @vitest-environment jsdom
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'

import { useThemeStore } from '@/stores/theme'

import { createThemePalette } from '../theme-palette'

describe('系统主题联动', () => {
  it('主色同步导航和图表，浅深模式生成不同配套底色且始终使用白色主按钮文字', () => {
    const light = createThemePalette('#1677ff', false)
    const dark = createThemePalette('#1677ff', true)
    expect(light['--primary']).toBe('#1677ff')
    expect(light['--sidebar-primary']).toBe(light['--primary'])
    expect(light['--chart-1']).toBe(light['--primary'])
    expect(light['--primary-foreground']).toBe('#ffffff')
    expect(dark['--primary-foreground']).toBe('#ffffff')
    expect(light['--accent']).not.toBe(dark['--accent'])
    expect(light['--primary-text']).not.toBe(dark['--primary-text'])
  })

  it('无效手工色值不能覆盖现有主题', () => {
    setActivePinia(createPinia())
    const store = useThemeStore()
    expect(store.setBrandColor('#722ED1')).toBe(true)
    expect(store.brandColor).toBe('#722ed1')
    expect(store.setBrandColor('purple')).toBe(false)
    expect(store.brandColor).toBe('#722ed1')
  })
})
