// /api/freshness — the contract of szczur-radar-api pipeline/api/FreshnessController.
import { apiFetch } from './client'

export interface SourceFreshness {
  /** The source's value (`wykop`, `reddit`). */
  source: string
  /** The end of the source's last successful update (ISO-8601 instant). */
  lastUpdatedAt: string
}

export interface Freshness {
  /** The sources with an update, in the API's order; empty before the first one. */
  sources: SourceFreshness[]
}

/** When each source's data was last updated, for the label above the feed (any logged-in user). */
export function fetchFreshness(): Promise<Freshness> {
  return apiFetch<Freshness>('/api/freshness')
}
