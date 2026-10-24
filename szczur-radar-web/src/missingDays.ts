// The days without data (GET /api/admin/pipeline-runs/missing-days) as ranges the "Nowy przebieg" form can start.
import type { MissingDay } from './api/pipeline'

/** The most days a range run takes, both ends included: szczur-radar-api PipelineRunController.MAX_RANGE_DAYS. */
export const MAX_RANGE_DAYS = 7

/** Days `from`–`to` (`YYYY-MM-DD`, both included) missing the same `sources`. */
export interface MissingRange {
  from: string
  to: string
  sources: string[]
}

const DAY_MS = 24 * 60 * 60 * 1000

/**
 * A `YYYY-MM-DD` day as a day number: its UTC midnight, so the browser's time zone (and its 25-hour day of a clock
 * change) never shifts the calendar.
 */
function dayNumber(day: string): number {
  return Date.parse(`${day}T00:00:00Z`) / DAY_MS
}

/** `day` is the next one after `range`, misses the same sources and keeps it within MAX_RANGE_DAYS days. */
function continues(range: MissingRange, { day, sources }: MissingDay): boolean {
  return (
    dayNumber(day) === dayNumber(range.to) + 1 &&
    dayNumber(day) - dayNumber(range.from) < MAX_RANGE_DAYS &&
    range.sources.join(',') === sources.join(',')
  )
}

/**
 * `days` (ascending) as ranges, ascending: consecutive days missing the same sources make one range, cut into pieces
 * of at most MAX_RANGE_DAYS days, as many as one start takes. A gap of a day or another set of sources starts a new
 * range.
 */
export function missingRanges(days: MissingDay[]): MissingRange[] {
  const ranges: MissingRange[] = []
  let current: MissingRange | null = null
  for (const missing of days) {
    const { day, sources } = missing
    if (current !== null && continues(current, missing)) {
      current.to = day
    } else {
      current = { from: day, to: day, sources: [...sources] }
      ranges.push(current)
    }
  }
  return ranges
}
