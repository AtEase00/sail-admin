import { defineStore } from 'pinia'
import { ref } from 'vue'

import { request } from '@/lib/api-client'

/** 字典仅负责展示元数据，真实业务状态仍由后端接口校验。 */
export interface DictionaryOption {
  id: number
  label: string
  value: string
  color: 'default' | 'success' | 'warning' | 'error' | 'processing'
  isDefault: boolean
}
/** 从后台加载启用的类型和数据，不使用写死的展示标签冒充配置。 */
export const useDictionaryStore = defineStore('sail-dictionaries', () => {
  const entries = ref<Record<string, DictionaryOption[]>>({})
  const error = ref('')
  let pending: Promise<void> | null = null
  /** 合并同一时间的加载请求，读取失败时清除旧选项并保存错误。 */
  function load() {
    if (pending)
      return pending
    pending = request<Record<string, DictionaryOption[]>>('/system/dictionaries/options').then((result) => {
      entries.value = result
      error.value = ''
    }).catch((failure: unknown) => {
      entries.value = {}
      error.value = failure instanceof Error ? failure.message : '字典加载失败'
    }).finally(() => {
      pending = null
    })
    return pending
  }
  /** 返回指定类型的启用选项，不推测缺失类型。 */
  function options(code: string) {
    return entries.value[code] ?? []
  }
  /** 按业务原值匹配，数字与布尔字段以同一字符串规则查找。 */
  function item(code: string, value: unknown) {
    return options(code).find(option => option.value === String(value))
  }
  /** 未配置的业务值保留原文，避免显示错误的其他状态。 */
  function label(code: string, value: unknown) {
    return item(code, value)?.label ?? (value === null || value === undefined || value === '' ? '—' : String(value))
  }
  return { entries, error, load, options, item, label }
})
