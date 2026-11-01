/** 系统预设主题色保持明确色值，所有配套颜色由同一主色生成。 */
export const BRAND_COLORS = [
  { name: '青绿', value: '#00b96b' },
  { name: '蓝色', value: '#1677ff' },
  { name: '紫色', value: '#722ed1' },
  { name: '青色', value: '#08979c' },
  { name: '橙色', value: '#d46b08' },
  { name: '红色', value: '#cf1322' },
  { name: '石墨', value: '#434343' },
] as const
export const DEFAULT_BRAND_COLOR = '#00b96b'

/** 在 RGB 空间混色生成同色系底色、悬停与按下状态，不引入额外调色库。 */
function mix(color: string, target: number, amount: number) {
  const channels = [1, 3, 5].map(start => Number.parseInt(color.slice(start, start + 2), 16))
  return `#${channels.map(channel => Math.round(channel + (target - channel) * amount).toString(16).padStart(2, '0')).join('')}`
}

/** 同步普通控件、Ant 控件、导航、图表与工作区背景的语义色。 */
export function createThemePalette(primary: string, dark: boolean): Record<string, string> {
  const text = dark ? mix(primary, 255, 0.55) : mix(primary, 0, 0.4)
  const accent = dark ? mix(primary, 0, 0.78) : mix(primary, 255, 0.92)
  return {
    '--primary': primary,
    '--primary-hover': mix(primary, dark ? 255 : 0, 0.12),
    '--primary-active': mix(primary, 0, 0.22),
    '--primary-text': text,
    '--primary-foreground': '#ffffff',
    '--accent': accent,
    '--accent-foreground': text,
    '--ring': primary,
    '--sidebar-primary': primary,
    '--sidebar-primary-foreground': '#ffffff',
    '--sidebar-accent': accent,
    '--sidebar-accent-foreground': text,
    '--sidebar-ring': primary,
    '--chart-1': primary,
    '--chart-2': mix(primary, dark ? 255 : 0, 0.3),
    '--workspace-background': dark ? '#141414' : mix(primary, 255, 0.975),
  }
}
