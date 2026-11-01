/** 工作台真实统计与会话摘要，字段与后端只读视图保持一致。 */
export interface AuditDay {
  date: string
  success: number
  failed: number
  loginFailures: number
}
export interface AuditOverview {
  from: string
  to: string
  days: AuditDay[]
  actions: { action: string, count: number }[]
  recent: { id: number, userId: number | null, username: string | null, nickname: string | null, action: string, success: boolean, createdAt: string }[]
}
export interface OnlineOverview {
  total: number
  sessionTotal: number
  records: { id: number, username: string, nickname: string, sessions: number, latestLoginAt: string }[]
}
