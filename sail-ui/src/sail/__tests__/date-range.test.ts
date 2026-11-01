import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'

import { normalizeDateRange } from '@/sail/date-range'

describe('服务端查询时间边界', () => {
  it('清除选择时移除两侧条件', () => {
    expect(normalizeDateRange(null, 'date')).toEqual({ from: '', to: '' })
  })
  it.each([
    ['date', '2024-02-29', '2024-02-29', '2024-02-29T00:00:00', '2024-02-29T23:59:59.999999'],
    ['week', '2024-12-31', '2024-12-31', '2024-12-30T00:00:00', '2025-01-05T23:59:59.999999'],
    ['month', '2024-02-15', '2024-03-02', '2024-02-01T00:00:00', '2024-03-31T23:59:59.999999'],
    ['quarter', '2024-02-15', '2024-04-02', '2024-01-01T00:00:00', '2024-06-30T23:59:59.999999'],
    ['year', '2024-02-15', '2025-04-02', '2024-01-01T00:00:00', '2025-12-31T23:59:59.999999'],
    ['datetime', '2024-02-29T11:22:33', '2024-02-29T12:34:56', '2024-02-29T11:22:33', '2024-02-29T12:34:56'],
  ] as const)('正确计算 %s 的完整区间且不发生时区偏移', (mode, start, end, from, to) => {
    expect(normalizeDateRange([dayjs(start), dayjs(end)], mode)).toEqual({ from, to })
  })
})
