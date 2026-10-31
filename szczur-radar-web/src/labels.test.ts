import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime, formatDuration, formatRelative, label, plural, postOrigin } from './labels'

const NOW = Date.parse('2026-10-15T08:00:00Z')
const SECOND = 1000
const MINUTE = 60 * SECOND
const HOUR = 60 * MINUTE

function ago(ms: number): string {
  return new Date(NOW - ms).toISOString()
}

describe('labels', () => {
  it('names the causes of an interruption and an interrupted step', () => {
    expect(label('interruptionCause', 'deploy')).toBe('nowe wdrożenie')
    expect(label('interruptionCause', 'restart')).toBe('restart aplikacji')
    expect(label('interruptionCause', 'shutdown')).toBe('zatrzymanie aplikacji')
    expect(label('interruptionCause', 'lost')).toBe('utracona instancja')
    expect(label('stepStatus', 'interrupted')).toBe('przerwany')
    expect(label('stepStatus', 'failed')).toBe('błąd')
  })

  it('shows an unknown value as it is', () => {
    expect(label('interruptionCause', 'meteor')).toBe('meteor')
  })
})

describe('postOrigin', () => {
  it.each([
    ['wykop', 'gielda', 'Wykop · #gielda'],
    ['reddit', 'ValueInvesting', 'Reddit · r/ValueInvesting'],
    ['x', 'y', 'x · y'],
  ])('shows %s and %s as "%s"', (source, channel, text) => {
    expect(postOrigin(source, channel)).toBe(text)
  })
})

describe('formatRelative', () => {
  it.each([
    [0, 'przed chwilą'],
    [59 * SECOND, 'przed chwilą'],
    [MINUTE, '1 min temu'],
    [7 * MINUTE + 30 * SECOND, '7 min temu'],
    [59 * MINUTE + 59 * SECOND, '59 min temu'],
    [HOUR, '1 h temu'],
    [23 * HOUR + 59 * MINUTE, '23 h temu'],
  ])('says %i ms ago is "%s"', (ms, text) => {
    expect(formatRelative(ago(ms), NOW)).toBe(text)
  })

  it('gives the Warsaw date and time from a day back', () => {
    expect(formatRelative(ago(24 * HOUR), NOW)).toBe('14.10.2026, 10:00')
    expect(formatRelative(ago(24 * HOUR), NOW)).toBe(formatDateTime(ago(24 * HOUR)))
  })

  it('treats a time slightly ahead of the clock as just now', () => {
    expect(formatRelative(ago(-5 * SECOND), NOW)).toBe('przed chwilą')
  })
})

describe('formatDuration', () => {
  it.each([
    [0, '0 s'],
    [-1000, '0 s'],
    [999, '0 s'],
    [45 * SECOND, '45 s'],
    [59 * SECOND + 999, '59 s'],
    [MINUTE, '1 min'],
    [3 * MINUTE + 20 * SECOND, '3 min 20 s'],
    [59 * MINUTE + 59 * SECOND, '59 min 59 s'],
    [HOUR, '1 h'],
    [HOUR + 5 * MINUTE + 59 * SECOND, '1 h 5 min'],
    [26 * HOUR, '26 h'],
  ])('shows %i ms as "%s"', (ms, text) => {
    expect(formatDuration(ms)).toBe(text)
  })
})

describe('formatDate', () => {
  it('gives the Warsaw day of an instant', () => {
    expect(formatDate('2026-10-10T22:00:00Z')).toBe('11.10.2026')
    expect(formatDate('2026-10-11T21:59:59Z')).toBe('11.10.2026')
  })
})

describe('plural', () => {
  it.each([
    [1, 'post'],
    [2, 'posty'],
    [4, 'posty'],
    [5, 'postów'],
    [12, 'postów'],
    [22, 'posty'],
    [0, 'postów'],
  ])('takes the form of %i', (count, form) => {
    expect(plural(count, 'post', 'posty', 'postów')).toBe(form)
  })
})
