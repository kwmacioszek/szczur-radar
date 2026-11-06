// The feed filter in the address (`/?q=cdr&kind=company&direction=bullish`), so a refresh, a link and
// back/forward keep it. Read leniently: a hand-made link drops what the API would refuse with a 400 (an
// unknown value, a U+0000 in the text, a page above the API's largest).
import type { LocationQuery, LocationQueryValue } from 'vue-router'
import { DIRECTIONS, MAX_PAGE, type PostFilter, TARGET_KINDS } from './api/posts'

/** The API's limit for `q` (PostController, `@Size(max = 100)`). */
export const MAX_QUERY_LENGTH = 100

/** The first value of a query key: `?q=a&q=b` gives `a`. */
function first(value: LocationQueryValue | LocationQueryValue[] | undefined): string | undefined {
  const found = Array.isArray(value) ? value[0] : value
  return found ?? undefined
}

function oneOf<T extends string>(values: readonly T[], value: string | undefined): T | undefined {
  return values.find((known) => known === value)
}

/**
 * White space at either end, from the set of contract-v2.md §10 that the API strips
 * (`text/Whitespace.java`). `String.trim()` is a different set: it keeps U+0085 and U+001C–U+001F and
 * drops U+FEFF, so a link with one of them would be a filter here and none in the API.
 */
export const WHITE_SPACE = '\\t-\\r\\x1C-\\x20\\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000'
const EDGE_WHITE_SPACE = new RegExp(`^[${WHITE_SPACE}]+|[${WHITE_SPACE}]+$`, 'g')

function strip(text: string): string {
  return text.replace(EDGE_WHITE_SPACE, '')
}

/**
 * The text as the API matches it: U+0000 dropped (Postgres text cannot hold it, the API answers a 400:
 * `PostController`, `@Pattern("[^\\x00]*")`), white space at both ends dropped, at most 100 characters, and
 * undefined when nothing is left (blank is no condition).
 */
export function normalizeQuery(text: string | undefined): string | undefined {
  const trimmed = text === undefined ? undefined : strip(strip(text.replaceAll('\u0000', '')).slice(0, MAX_QUERY_LENGTH))
  return trimmed ? trimmed : undefined
}

/** A filter without the absent keys, so two equal filters have the same keys. */
function compact(filter: PostFilter): PostFilter {
  const result: PostFilter = {}
  if (filter.q) result.q = filter.q
  if (filter.kind) result.kind = filter.kind
  if (filter.direction) result.direction = filter.direction
  return result
}

/** The filter in the address; unknown values are dropped, never sent. */
export function filterFromQuery(query: LocationQuery): PostFilter {
  return compact({
    q: normalizeQuery(first(query.q)),
    kind: oneOf(TARGET_KINDS, first(query.kind)),
    direction: oneOf(DIRECTIONS, first(query.direction)),
  })
}

/**
 * The page in the address (`?page=`, from 0), so a refresh keeps it. What is not a page, and a page above the
 * API's largest (`MAX_PAGE`, which answers a 400), is the first page.
 */
export function pageFromQuery(query: LocationQuery): number {
  const value = Number.parseInt(first(query.page) ?? '0', 10)
  return Number.isFinite(value) && value > 0 && value <= MAX_PAGE ? value : 0
}

/** The address keys of the filter, only those with a value. */
export function filterToQuery(filter: PostFilter): Record<string, string> {
  const query: Record<string, string> = {}
  for (const [key, value] of Object.entries(compact(filter))) query[key] = value
  return query
}

export function isFilterActive(filter: PostFilter): boolean {
  return Boolean(filter.q || filter.kind || filter.direction)
}

export function sameFilter(a: PostFilter, b: PostFilter): boolean {
  return (a.q ?? '') === (b.q ?? '') && a.kind === b.kind && a.direction === b.direction
}
