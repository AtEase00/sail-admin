import type { Dayjs } from 'dayjs'

import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import quarterOfYear from 'dayjs/plugin/quarterOfYear'

dayjs.extend(quarterOfYear)

/** 选择粒度由业务字段指定，不向用户开放跨类型切换。 */
export type DateRangeMode = 'date' | 'datetime' | 'week' | 'month' | 'quarter' | 'year'

/** 查询时间以本地时间序列传输，避免 ISO UTC 转换造成日期偏移。 */
export function normalizeDateRange(dates: [Dayjs, Dayjs] | null, mode: DateRangeMode) {
  if (!dates)
    return { from: '', to: '' }
  // MySQL DATETIME(6) 使用微秒上界，完整覆盖当前粒度最后一秒。
  const unit = mode === 'date' ? 'day' : mode === 'datetime' ? undefined : mode
  const from = unit ? dates[0].locale('zh-cn').startOf(unit) : dates[0]
  const to = unit ? dates[1].locale('zh-cn').endOf(unit) : dates[1]
  return { from: from.format('YYYY-MM-DDTHH:mm:ss'), to: `${to.format('YYYY-MM-DDTHH:mm:ss')}${unit ? '.999999' : ''}` }
}
