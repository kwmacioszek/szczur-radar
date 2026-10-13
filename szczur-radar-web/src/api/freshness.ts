// /api/freshness — the contract of szczur-radar-api pipeline/api/FreshnessController.
import { apiFetch } from './client'

export interface Freshness {
  /** The end of the last successful update (ISO-8601 instant); null before the first one. */
  lastUpdatedAt: string | null
}

/** When the data was last updated, for the label above the feed (any logged-in user). */
export function fetchFreshness(): Promise<Freshness> {
  return apiFetch<Freshness>('/api/freshness')
}
