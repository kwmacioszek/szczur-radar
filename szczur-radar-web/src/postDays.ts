// The list of posts in the frames of their days (ui-design.md, "Wiersz posta"; "Daty, godziny i liczby": the day's
// heading in words, with "Dziś" and "Wczoraj"). Days are Warsaw calendar days, as every date of the application.
import type { PostItem } from './api/posts'

/** One day of the list: its posts in the list's order under its heading. */
export interface PostDay {
  /** The Warsaw calendar day, `YYYY-MM-DD`: unique within one list. */
  key: string
  /** The day's heading, e.g. `Dziś, 20 października` or `Sobota, 17 października`. */
  title: string
  items: PostItem[]
}

const TIME_ZONE = 'Europe/Warsaw'

/** `en-CA` writes a day as `YYYY-MM-DD`. */
const dayKey = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: TIME_ZONE })
const dayMonth = new Intl.DateTimeFormat('pl-PL', { day: 'numeric', month: 'long', timeZone: TIME_ZONE })
const dayMonthYear = new Intl.DateTimeFormat('pl-PL', { day: 'numeric', month: 'long', year: 'numeric', timeZone: TIME_ZONE })
const weekday = new Intl.DateTimeFormat('pl-PL', { weekday: 'long', timeZone: TIME_ZONE })

const DAY_MS = 24 * 60 * 60 * 1000

/** The Warsaw day before `key`: counted on the calendar, so a 25-hour day of a clock change shifts nothing. */
function previousDay(key: string): string {
  return new Date(Date.parse(`${key}T00:00:00Z`) - DAY_MS).toISOString().slice(0, 10)
}

function capitalized(text: string): string {
  return text.charAt(0).toLocaleUpperCase('pl-PL') + text.slice(1)
}

/** The heading of the day of `instant` at `now`: today and yesterday by name, another year with its year. */
function dayTitle(instant: Date, key: string, now: Date): string {
  const today = dayKey.format(now)
  if (key === today) return `Dziś, ${dayMonth.format(instant)}`
  if (key === previousDay(today)) return `Wczoraj, ${dayMonth.format(instant)}`
  const sameYear = key.slice(0, 4) === today.slice(0, 4)
  return `${capitalized(weekday.format(instant))}, ${(sameYear ? dayMonth : dayMonthYear).format(instant)}`
}

/**
 * `items` in the order they come, cut into their Warsaw days at `now` (ms since the epoch). A day split between two
 * pages of the list is a day on each.
 */
export function groupByDay(items: readonly PostItem[], now: number): PostDay[] {
  const at = new Date(now)
  const days: PostDay[] = []
  for (const item of items) {
    const instant = new Date(item.publishedAt)
    const key = dayKey.format(instant)
    const last = days.at(-1)
    if (last?.key === key) last.items.push(item)
    else days.push({ key, title: dayTitle(instant, key, at), items: [item] })
  }
  return days
}
