<script setup lang="ts">
import type { Dayjs } from 'dayjs'

import { useDark } from '@vueuse/core'
import { ConfigProvider, DatePicker, theme } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import advancedFormat from 'dayjs/plugin/advancedFormat'
import weekOfYear from 'dayjs/plugin/weekOfYear'
import { computed } from 'vue'

import type { DateRangeMode } from '@/sail/date-range'

import { normalizeDateRange } from '@/sail/date-range'
import { useThemeStore } from '@/stores/theme'

const props = defineProps<{ from?: string, to?: string, mode: DateRangeMode }>()
const emit = defineEmits<{ 'update:from': [value: string], 'update:to': [value: string] }>()
dayjs.extend(advancedFormat)
dayjs.extend(weekOfYear)
const dark = useDark()
const settings = useThemeStore()
// 页面明确选择粒度，年月面板跳转仍用于定位当前类型的日期。
const format = computed(() => ({ date: 'YYYY-MM-DD', datetime: 'YYYY-MM-DD HH:mm:ss', week: 'YYYY-wo', month: 'YYYY-MM', quarter: 'YYYY-[Q]Q', year: 'YYYY' })[props.mode])
const placeholder = computed<[string, string]>(() => {
  const label = { date: '日期', datetime: '时间', week: '周', month: '月份', quarter: '季度', year: '年份' }[props.mode]
  return [`开始${label}`, `结束${label}`]
})
const value = computed<[Dayjs, Dayjs] | undefined>(() => props.from && props.to ? [dayjs(props.from).locale('zh-cn'), dayjs(props.to).locale('zh-cn')] : undefined)
// 日级快捷区间仅提供给日期和日期时间字段，避免其他粒度提交不匹配区间。
const presets = computed(() => ['date', 'datetime'].includes(props.mode)
  ? [
      { label: '今天', value: [dayjs().startOf('day'), dayjs().endOf('day')] },
      { label: '近 7 天', value: [dayjs().subtract(6, 'day').startOf('day'), dayjs().endOf('day')] },
      { label: '本月', value: [dayjs().startOf('month'), dayjs().endOf('month')] },
      { label: '本年', value: [dayjs().startOf('year'), dayjs().endOf('year')] },
    ]
  : [])
/** 统一向服务端提交当前粒度的真实边界，时间模式保留用户选择的秒。 */
function update(dates: [Dayjs, Dayjs] | [string, string] | null) {
  const range = normalizeDateRange(dates ? [dayjs(dates[0]), dayjs(dates[1])] : null, props.mode)
  emit('update:from', range.from)
  emit('update:to', range.to)
}
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="{ algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm, token: { colorPrimary: settings.brandColor, borderRadius: 4, controlHeight: 32, fontSize: 13 }, components: { DatePicker: { zIndexPopup: 60 } } }">
    <DatePicker.RangePicker
      class="sail-date-range min-w-0 flex-1"
      :value="value"
      :picker="mode === 'datetime' ? 'date' : mode"
      :show-time="mode === 'datetime' ? { format: 'HH:mm:ss' } : false"
      :format="format"
      :presets="presets"
      :placeholder="placeholder"
      :allow-clear="true"
      :input-read-only="false"
      popup-class-name="sail-date-popup"
      @change="update"
    />
  </ConfigProvider>
</template>
