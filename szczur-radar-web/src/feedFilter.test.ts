import { describe, expect, it } from 'vitest'
import { filterFromQuery, filterToQuery, isFilterActive, normalizeQuery, sameFilter } from './feedFilter'

describe('feed filter in the address', () => {
  it('reads the known values', () => {
    expect(filterFromQuery({ q: 'cdr', kind: 'macro_topic', direction: 'bullish' })).toEqual({
      q: 'cdr',
      kind: 'macro_topic',
      direction: 'bullish',
    })
  })

  it('drops unknown values instead of sending them', () => {
    expect(filterFromQuery({ kind: 'xyz', direction: 'BULLISH', page: '2' })).toEqual({})
    expect(filterFromQuery({ kind: 'spolka', direction: 'bearish' })).toEqual({ direction: 'bearish' })
  })

  it('trims the text and drops a blank one', () => {
    expect(filterFromQuery({ q: '  żabka  ' })).toEqual({ q: 'żabka' })
    expect(filterFromQuery({ q: '   ' })).toEqual({})
    expect(filterFromQuery({ q: '' })).toEqual({})
    expect(filterFromQuery({ q: null })).toEqual({})
  })

  it('cuts the text to 100 characters', () => {
    expect(filterFromQuery({ q: 'a'.repeat(101) }).q).toBe('a'.repeat(100))
    expect(normalizeQuery(`${'a'.repeat(99)} b`)).toBe('a'.repeat(99))
  })

  it('takes the first value of a repeated key', () => {
    expect(filterFromQuery({ q: ['a', 'b'], kind: ['asset', 'sector'] })).toEqual({ q: 'a', kind: 'asset' })
    expect(filterFromQuery({ q: [null, 'b'] })).toEqual({})
  })

  it('writes only the keys with a value', () => {
    expect(filterToQuery({ q: 'cdr', direction: 'bullish' })).toEqual({ q: 'cdr', direction: 'bullish' })
    expect(filterToQuery({ q: '', kind: undefined })).toEqual({})
  })

  it('reads back what it writes', () => {
    const filter = { q: 'orlen', kind: 'company', direction: 'mixed' } as const
    expect(filterFromQuery(filterToQuery(filter))).toEqual(filter)
  })

  it('is active with any condition', () => {
    expect(isFilterActive({})).toBe(false)
    expect(isFilterActive({ q: '' })).toBe(false)
    expect(isFilterActive({ q: 'cdr' })).toBe(true)
    expect(isFilterActive({ kind: 'asset' })).toBe(true)
    expect(isFilterActive({ direction: 'unclear' })).toBe(true)
  })

  it('compares filters by their conditions', () => {
    expect(sameFilter({}, { q: '' })).toBe(true)
    expect(sameFilter({ q: 'cdr', kind: 'company' }, { kind: 'company', q: 'cdr' })).toBe(true)
    expect(sameFilter({ q: 'cdr' }, { q: 'xtb' })).toBe(false)
    expect(sameFilter({ direction: 'bullish' }, {})).toBe(false)
  })
})
