import { describe, expect, it } from 'vitest'
import type { PostItem } from './api/posts'
import { FIXED_NOW } from './contract/mockModes'
import { groupByDay } from './postDays'

/** A post published at `publishedAt`; the rest does not matter to the grouping. */
function post(id: string, publishedAt: string): PostItem {
  return {
    id,
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt,
    title: null,
    body: `Wpis ${id}`,
    author: 'uzytkownik1',
    webUrl: null,
    assessment: null,
  }
}

/** 2026-10-20, 12:00 in Warsaw: the mock's and the E2E tests' "now". */
const NOW = Date.parse(FIXED_NOW)

function titles(items: PostItem[], now = NOW): string[] {
  return groupByDay(items, now).map((day) => day.title)
}

describe('groupByDay', () => {
  it('names today and yesterday by name, with the day of the month', () => {
    expect(titles([post('1', '2026-10-20T07:41:00Z'), post('2', '2026-10-19T20:47:00Z')])).toEqual([
      'Dziś, 20 października',
      'Wczoraj, 19 października',
    ])
  })

  it('names an older day of this year by its weekday', () => {
    expect(titles([post('1', '2026-10-17T10:00:00Z'), post('2', '2026-01-02T10:00:00Z')])).toEqual([
      'Sobota, 17 października',
      'Piątek, 2 stycznia',
    ])
  })

  it('adds the year to a day of another year', () => {
    expect(titles([post('1', '2025-12-31T12:00:00Z')])).toEqual(['Środa, 31 grudnia 2025'])
  })

  it('cuts the days at midnight in Warsaw, not in UTC', () => {
    // 2026-10-19, 23:59:59 and 2026-10-20, 00:00 in Warsaw (summer time, UTC+2).
    const days = groupByDay([post('today', '2026-10-19T22:00:00Z'), post('yesterday', '2026-10-19T21:59:59Z')], NOW)
    expect(days.map((day) => day.title)).toEqual(['Dziś, 20 października', 'Wczoraj, 19 października'])
    expect(days.map((day) => day.items.map((item) => item.id))).toEqual([['today'], ['yesterday']])
  })

  it('counts yesterday on the calendar across the change to winter time', () => {
    // Warsaw goes back to UTC+1 on 2026-10-25: the 26th at 00:30 is 23:30 UTC on the 25th.
    const now = Date.parse('2026-10-26T08:00:00Z')
    expect(titles([post('1', '2026-10-25T23:30:00Z'), post('2', '2026-10-24T22:30:00Z')], now)).toEqual([
      'Dziś, 26 października',
      'Wczoraj, 25 października',
    ])
  })

  it('keeps the posts of a day together in the order of the list, a day by its Warsaw date', () => {
    const items = [
      post('a', '2026-10-12T20:00:00Z'),
      post('b', '2026-10-12T06:00:00Z'),
      post('c', '2026-10-11T21:59:00Z'),
      post('d', '2026-10-11T08:00:00Z'),
    ]
    const days = groupByDay(items, NOW)
    expect(days.map((day) => [day.key, day.title, day.items.map((item) => item.id)])).toEqual([
      ['2026-10-12', 'Poniedziałek, 12 października', ['a', 'b']],
      ['2026-10-11', 'Niedziela, 11 października', ['c', 'd']],
    ])
  })

  it('gives no day for no posts', () => {
    expect(groupByDay([], NOW)).toEqual([])
  })
})
