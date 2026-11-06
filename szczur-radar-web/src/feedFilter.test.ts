import { describe, expect, it } from 'vitest'
import { filterFromQuery, filterToQuery, isFilterActive, normalizeQuery, pageFromQuery, sameFilter } from './feedFilter'
import { MAX_PAGE } from './api/posts'

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

  it('trims the white space the API trims (contract §10), not what String.trim() does', () => {
    expect(normalizeQuery('\u0085\u2007orlen\u001F\u3000')).toBe('orlen')
    expect(normalizeQuery('\u0085')).toBeUndefined()
    // The API keeps U+FEFF, so it is part of the text.
    expect(normalizeQuery('\uFEFForlen')).toBe('\uFEFForlen')
    expect(normalizeQuery('or len')).toBe('or len')
  })

  it('cuts the text to 100 characters', () => {
    expect(filterFromQuery({ q: 'a'.repeat(101) }).q).toBe('a'.repeat(100))
    expect(normalizeQuery(`${'a'.repeat(99)} b`)).toBe('a'.repeat(99))
  })

  it('drops U+0000, which the API refuses with a 400 (a link such as /?q=a%00b)', () => {
    expect(filterFromQuery({ q: 'a\u0000b' })).toEqual({ q: 'ab' })
    expect(normalizeQuery('\u0000orlen\u0000')).toBe('orlen')
    expect(normalizeQuery('\u0000')).toBeUndefined()
    expect(normalizeQuery(' \u0000 ')).toBeUndefined()
    // The 100 characters count what is left, and what is left has no U+0000 and no edge white space.
    expect(normalizeQuery(`${'a'.repeat(99)}\u0000bc`)).toBe(`${'a'.repeat(99)}b`)
    expect(normalizeQuery(`a\u0000 ${'b'.repeat(98)}\u0000 c`)).toBe(`a ${'b'.repeat(98)}`)
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

describe('the page in the address', () => {
  it('reads a page from 0 up to the API\'s largest', () => {
    expect(pageFromQuery({})).toBe(0)
    expect(pageFromQuery({ page: '0' })).toBe(0)
    expect(pageFromQuery({ page: '2' })).toBe(2)
    expect(pageFromQuery({ page: String(MAX_PAGE) })).toBe(MAX_PAGE)
  })

  it('takes a page above the API\'s largest as no page, so the feed opens instead of failing with a 400', () => {
    expect(pageFromQuery({ page: String(MAX_PAGE + 1) })).toBe(0)
    expect(pageFromQuery({ page: '99999999999' })).toBe(0)
    // parseInt reads the digits up to the first other character: '1e9' is page 1, as before.
    expect(pageFromQuery({ page: '1e9' })).toBe(1)
  })

  it('takes what is not a page as the first one', () => {
    expect(pageFromQuery({ page: '-1' })).toBe(0)
    expect(pageFromQuery({ page: 'x' })).toBe(0)
    expect(pageFromQuery({ page: '' })).toBe(0)
    expect(pageFromQuery({ page: null })).toBe(0)
  })

  it('takes the first value of a repeated key', () => {
    expect(pageFromQuery({ page: ['3', '4'] })).toBe(3)
  })
})
