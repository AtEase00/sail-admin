import type { Row } from '@/services/types/sail'

export interface JobInput {
  name: string
  groupName: string
  handler: string
  parameters: string
  scheduleType: 'CRON' | 'INTERVAL'
  cron: string
  intervalSeconds: number
  zoneId: string
  misfirePolicy: 'SKIP' | 'FIRE_ONCE'
  timeoutSeconds: number
  maxRetries: number
  retryDelaySeconds: number
  retentionDays: number
  version: number
}
export interface JobDefinition extends Row, JobInput {
  status: 'ENABLED' | 'PAUSED' | 'ARCHIVED'
  syncStatus: 'PENDING' | 'SYNCED' | 'ERROR'
  syncError: string | null
  activeRunId: number | null
  nextFireAt: number | null
  lastStartedAt: number | null
  lastStatus: string | null
}
export interface JobHandler {
  code: string
  name: string
  description: string
  retryable: boolean
}
export interface JobExecution extends Row {
  jobId: number
  jobName: string
  jobVersion: number
  handler: string
  parameters: string
  configSnapshot: string
  source: string
  actorName: string | null
  actorId: number | null
  status: string
  attempt: number
  parentRunId: number | null
  scheduledAt: number
  startedAt: number | null
  finishedAt: number | null
  heartbeatAt: number | null
  durationMs: number | null
  instanceId: string | null
  traceId: string
  succeeded: number
  failed: number
  summary: string | null
  resolution: string | null
}
export const jobStates: Record<string, string> = { ENABLED: '已启用', PAUSED: '已暂停', ARCHIVED: '已归档', PENDING: '待同步', SYNCED: '已同步', ERROR: '同步失败', QUEUED: '排队中', RUNNING: '运行中', SUCCESS: '成功', PARTIAL: '部分失败', FAILED: '失败', TIMED_OUT: '已超时', UNKNOWN: '结果未知', SKIPPED: '已跳过' }
export const jobSources: Record<string, string> = { SCHEDULED: '定时触发', MANUAL: '手动执行', RETRY: '重试' }
export function jobTime(value: number | null | undefined, zone = 'Asia/Shanghai') {
  if (value == null)
    return '—'
  return new Intl.DateTimeFormat('zh-CN', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(value))
}
export function jobDefaults(): JobInput {
  return { name: '', groupName: '系统任务', handler: '', parameters: '{}', scheduleType: 'CRON', cron: '0 0/5 * * * ?', intervalSeconds: 300, zoneId: 'Asia/Shanghai', misfirePolicy: 'SKIP', timeoutSeconds: 60, maxRetries: 0, retryDelaySeconds: 30, retentionDays: 30, version: 0 }
}
/** 只提交编辑白名单，不回传运行状态和内部同步字段。 */
export function jobPayload(form: JobInput): JobInput {
  return Object.fromEntries(Object.keys(jobDefaults()).map(key => [key, form[key as keyof JobInput]])) as unknown as JobInput
}
export function canRunJob(job: JobDefinition) {
  return job.status === 'ENABLED' && job.syncStatus === 'SYNCED' && job.activeRunId === null
}
