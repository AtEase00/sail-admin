/** Sail 后端真实响应与跨页面共享资料。 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  traceId: string
}
export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}
export interface UserInfo {
  id: number
  username: string
  nickname: string
  deptId: number | null
  status: number
}
export interface MenuInfo {
  id: number
  parentId: number | null
  name: string
  path: string | null
  permission: string | null
  type: string
  icon: string | null
  status: number
}
export interface Identity {
  user: UserInfo
  roles: string[]
  permissions: string[]
}
export interface LoginRequest {
  username: string
  password: string
}
export interface LoginResult {
  tokenName: string
  tokenValue: string
  timeout: number
}
export type Row = Record<string, unknown> & { id: number }
export interface MessageRecord extends Row {
  title: string
  content: string
  readFlag: boolean
  createdAt: string
  senderId: number | null
}
export interface PayOrder extends Row {
  businessNo: string
  orderNo: string
  subject: string
  amount: number
  status: 'PENDING' | 'PAID' | 'CLOSED' | 'REFUNDED'
  qrCode: string | null
  expiresAt: string
  channelId: number
}
export interface StorageInfo extends Row {
  name: string
  type: string
  endpoint: string | null
  bucket: string | null
  region: string | null
  enabled: boolean
  selected: boolean
  credentialsConfigured: boolean
}
