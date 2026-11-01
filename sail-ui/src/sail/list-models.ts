/** 列表展示配置，不包含业务数据或授权信息。 */
export interface ListColumn {
  key: string
  label: string
  dictionary?: string
  kind?: 'state' | 'money' | 'boolean' | 'date'
}
/** 完整数据集上的附加查询字段。 */
export interface QueryField {
  key: string
  label: string
  placeholder?: string
  options?: { value: string, label: string }[]
  exact?: boolean
  numeric?: boolean
}
export type TableDensity = 'compact' | 'normal' | 'loose'
