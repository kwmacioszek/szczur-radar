// /api/admin/pipeline-runs — the contract of szczur-radar-api pipeline/api/PipelineRunController.
import { ApiError, apiFetch } from './client'
import type { Page } from './posts'

export type RunKind = 'range' | 'retry' | 'posts'
export type RunStatus = 'queued' | 'running' | 'succeeded' | 'partial' | 'failed' | 'interrupted'
export type RunStepName = 'fetch' | 'store' | 'select' | 'assess' | 'summarize'
/** `interrupted`: its run was interrupted (or stopped) while the step was running. */
export type StepStatus = 'running' | 'succeeded' | 'failed' | 'interrupted'
/**
 * Why an interrupted run stopped — pipeline/run/InterruptionCause: another deployment took over (`deploy`), the
 * application restarted (`restart`), the instance was stopping (`shutdown`) or the instance running it was lost
 * without closing it, so another instance's start request cleaned it up after `PIPELINE_STALL_TIMEOUT` without
 * activity (`lost`).
 */
export type InterruptionCause = 'deploy' | 'restart' | 'shutdown' | 'lost'

export interface RunStep {
  step: RunStepName
  status: StepStatus
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
  /** Why an interrupted run stopped; null for any other run and for one interrupted before causes were recorded. */
  interruptionCause: InterruptionCause | null
  /**
   * Failed, interrupted, partial with a dead post or a technical error, or succeeded or partial with skipped elements
   * (the API's rule).
   */
  needsAttention: boolean
  /** Requested by the daily cron, not by an admin. */
  scheduled: boolean
  rangeFrom: string | null
  rangeTo: string | null
  sources: string[]
  requestedPostIds: string[] | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  /** The last change while it was running. */
  lastActivityAt: string | null
  fetched: number
  newPosts: number
  assessedOk: number
  assessedFailed: number
  /** Posts with a technical error and no stored assessment (not failed LLM attempts). */
  processingErrors: number
  dead: number
  skipped: number
  /** HTTP attempts beyond the first of the assessments' LLM calls. */
  llmRetries: number
  /** Assessments whose call fell back from the flex tier to the default one. */
  flexFallbacks: number
  /** The posts it is assessing now; 0 unless it is running. */
  inFlight: number
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

/** The runs, newest first; `page` from 0, `size` at most 100. */
export function fetchRuns(page = 0, size = 20): Promise<Page<Run>> {
  return apiFetch<Page<Run>>(`/api/admin/pipeline-runs?page=${page}&size=${size}`)
}

/** One run with its steps; 404 `not_found` for an unknown id, 400 `invalid_request` for a malformed one. */
export function fetchRun(id: string): Promise<Run> {
  return apiFetch<Run>(`/api/admin/pipeline-runs/${encodeURIComponent(id)}`)
}

export function fetchSources(): Promise<SourceState[]> {
  return apiFetch<SourceState[]>('/api/admin/pipeline-runs/sources')
}

/** A Warsaw day (`YYYY-MM-DD`) and the configured sources no succeeded or partial range run covers on it. */
export interface MissingDay {
  day: string
  /** In the API's order of sources. */
  sources: string[]
}

/** The checked window `from`–`to` (both included, `YYYY-MM-DD`: D-15…D-2 in Warsaw) and its days without data. */
export interface MissingDays {
  from: string
  to: string
  /** Ascending. */
  days: MissingDay[]
}

/** The days without data of the last 14 days up to the day before yesterday, per configured source. */
export function fetchMissingDays(): Promise<MissingDays> {
  return apiFetch<MissingDays>('/api/admin/pipeline-runs/missing-days')
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
