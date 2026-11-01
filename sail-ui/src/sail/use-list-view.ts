import { useLocalStorage } from '@vueuse/core'
import { computed, ref } from 'vue'

import type { ListColumn, TableDensity } from './list-models'

/** 持久化表格显示偏好，不持久化业务数据或授权结果。 */
export function useListView(key: string, columns: ListColumn[]) {
  const showQuery = ref(true)
  const hiddenColumns = useLocalStorage<string[]>(`sail:list:${key}:hidden`, [])
  const columnOrder = useLocalStorage<string[]>(`sail:list:${key}:order`, [])
  const leftPins = useLocalStorage<string[]>(`sail:list:${key}:left`, [])
  const rightPins = useLocalStorage<string[]>(`sail:list:${key}:right`, [])
  const density = useLocalStorage<TableDensity>(`sail:list:${key}:density`, 'compact')
  const orderedColumns = computed(() => {
    // 移除失效字段并追加新增字段，首列默认保留以便识别记录。
    const saved = Array.isArray(columnOrder.value) ? columnOrder.value : []
    const keys = [...new Set([...saved, ...columns.map(column => column.key)])]
    const ordered = keys.map(key => columns.find(column => column.key === key)).filter((column): column is ListColumn => Boolean(column))
    const left = Array.isArray(leftPins.value) ? leftPins.value : []
    const right = Array.isArray(rightPins.value) ? rightPins.value : []
    return [...ordered.filter(column => left.includes(column.key)), ...ordered.filter(column => !left.includes(column.key) && !right.includes(column.key)), ...ordered.filter(column => !left.includes(column.key) && right.includes(column.key))]
  })
  const visibleColumns = computed(() => orderedColumns.value.filter(column => column.key === columns[0]?.key || !Array.isArray(hiddenColumns.value) || !hiddenColumns.value.includes(column.key)))
  return { showQuery, hiddenColumns, density, visibleColumns, orderedColumns, columnOrder, leftPins, rightPins }
}
