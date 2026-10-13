// The feed filter in the address (`/?q=cdr&kind=company&direction=bullish`), so a refresh, a link and
// back/forward keep it. Read leniently: a hand-made link drops what the API would refuse with a 400.
import type { LocationQuery, LocationQueryValue } from 'vue-router'
import { DIRECTIONS, type PostFilter, TARGET_KINDS } from './api/posts'

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
 * The text as the API matches it: white space at both ends dropped, at most 100 characters, and
 * undefined when nothing is left (blank is no condition).
 */
export function normalizeQuery(text: string | undefined): string | undefined {
  const trimmed = text?.trim().slice(0, MAX_QUERY_LENGTH).trim()
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
