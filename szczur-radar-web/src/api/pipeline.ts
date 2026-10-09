// /api/admin/pipeline-runs — the contract of szczur-radar-api pipeline/api/PipelineRunController.
import { ApiError, apiFetch } from './client'
import type { Page } from './posts'

export type RunKind = 'range' | 'retry' | 'posts'
export type RunStatus = 'queued' | 'running' | 'succeeded' | 'partial' | 'failed' | 'interrupted'

export interface RunStep {
  step: 'fetch' | 'store' | 'select' | 'assess' | 'summarize'
  status: 'running' | 'succeeded' | 'failed'
  startedAt: string
  finishedAt: string | null
  itemsIn: number | null
  itemsOut: number | null
  error: string | null
}

export interface Run {
  id: string
  kind: RunKind
  status: RunStatus
  rangeFrom: string | null
  rangeTo: string | null
  sources: string[]
  requestedPostIds: string[] | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  fetched: number
  newPosts: number
  assessedOk: number
  assessedFailed: number
  /** Posts with a technical error and no stored assessment (not failed LLM attempts). */
  processingErrors: number
  dead: number
  skipped: number
  error: string | null
  steps: RunStep[]
}

/** A source a range run may fetch, and whether it is configured on the server. */
export interface SourceState {
  source: string
  configured: boolean
}

/** Queue a range run (plus a retry run when there are leftovers). Days as `YYYY-MM-DD`, Warsaw time. */
export function startRange(from: string, to: string, sources: string[]): Promise<Run[]> {
  return apiFetch<Run[]>('/api/admin/pipeline-runs', {
    method: 'POST',
    body: JSON.stringify({ from, to, sources }),
  })
}

/** Queue a run over the given failed posts. */
export function retryPosts(postIds: string[]): Promise<Run[]> {
  return apiFetch<Run[]>('/api/admin/pipeline-runs', {
    method: 'POST',
    body: JSON.stringify({ postIds }),
  })
}

export function fetchRuns(page = 0, size = 20): Promise<Page<Run>> {
  return apiFetch<Page<Run>>(`/api/admin/pipeline-runs?page=${page}&size=${size}`)
}

export function fetchSources(): Promise<SourceState[]> {
  return apiFetch<SourceState[]>('/api/admin/pipeline-runs/sources')
}

/** A Polish message for a failed start or retry. */
export function startErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.detail) {
      case 'pipeline_busy':
        return 'Przebieg już trwa. Poczekaj, aż się zakończy, i spróbuj ponownie.'
      case 'post_dead':
        return 'Wśród zaznaczonych jest post, którego nie można już ponowić (3 nieudane próby).'
      case 'source_not_configured':
        return 'Wybrane źródło nie jest skonfigurowane na serwerze (brak kluczy).'
      case 'invalid_request':
        return 'Nieprawidłowy zakres: maks. 7 dni, „od” nie później niż „do”, „do” najpóźniej dziś.'
    }
  }
  return 'Nie udało się uruchomić przebiegu. Spróbuj ponownie.'
}
