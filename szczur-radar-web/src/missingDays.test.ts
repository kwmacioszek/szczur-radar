import { describe, expect, it } from 'vitest'
import { MAX_RANGE_DAYS, missingRanges } from './missingDays'

const wykop = (day: string) => ({ day, sources: ['wykop'] })

describe('missingRanges', () => {
  it('makes one range of consecutive days missing the same source', () => {
    // The plan's example: today 2026-10-24, Wykop has no data on 19 and 20 October.
    expect(missingRanges([wykop('2026-10-19'), wykop('2026-10-20')])).toEqual([
      { from: '2026-10-19', to: '2026-10-20', sources: ['wykop'] },
    ])
  })

  it('starts a new range after a gap of a day', () => {
    expect(missingRanges([wykop('2026-10-12'), wykop('2026-10-13'), wykop('2026-10-15')])).toEqual([
      { from: '2026-10-12', to: '2026-10-13', sources: ['wykop'] },
      { from: '2026-10-15', to: '2026-10-15', sources: ['wykop'] },
    ])
  })

  it('starts a new range when the missing sources change', () => {
    expect(
      missingRanges([
        wykop('2026-10-12'),
        { day: '2026-10-13', sources: ['wykop', 'reddit'] },
        { day: '2026-10-14', sources: ['wykop', 'reddit'] },
        { day: '2026-10-15', sources: ['reddit'] },
      ]),
    ).toEqual([
      { from: '2026-10-12', to: '2026-10-12', sources: ['wykop'] },
      { from: '2026-10-13', to: '2026-10-14', sources: ['wykop', 'reddit'] },
      { from: '2026-10-15', to: '2026-10-15', sources: ['reddit'] },
    ])
  })

  it('cuts 9 consecutive days into ranges of 7 and 2, as many as one start takes', () => {
    const days = Array.from({ length: 9 }, (_, at) => wykop(`2026-10-0${at + 1}`))
    expect(MAX_RANGE_DAYS).toBe(7)
    expect(missingRanges(days)).toEqual([
      { from: '2026-10-01', to: '2026-10-07', sources: ['wykop'] },
      { from: '2026-10-08', to: '2026-10-09', sources: ['wykop'] },
    ])
  })

  it('has no range without days', () => {
    expect(missingRanges([])).toEqual([])
  })

  it('counts consecutive days across the clock change of 2026-10-25 and across a month end', () => {
    const days = ['2026-10-24', '2026-10-25', '2026-10-26', '2026-10-31', '2026-11-01'].map(wykop)
    expect(missingRanges(days)).toEqual([
      { from: '2026-10-24', to: '2026-10-26', sources: ['wykop'] },
      { from: '2026-10-31', to: '2026-11-01', sources: ['wykop'] },
    ])
  })
})
