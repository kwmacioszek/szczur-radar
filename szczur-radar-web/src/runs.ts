// How a pipeline run is shown: its tone, status text, the text of its "wymaga uwagi" reasons, range, duration and
// progress — one place for the run list (/admin), the run's page (/admin/runs/:id) and the notification bell. The
// reasons themselves come from the API (pipeline/run/PipelineRun.attentionReasons); here they only get their text.
import type { AttentionReason, InterruptionCause, Run, RunStatus, StepStatus } from './api/pipeline'
import type { BadgeTone } from './components/StatusBadge.vue'
import { formatDate, label, plural } from './labels'

/** What the tone and status text need; a run of the list and the run of a notification both have it. */
type RunState = { status: RunStatus; interruptionCause: InterruptionCause | null; skipped: number }

/** The API's reason codes and what their text is built from. */
type AttentionFields = Pick<
  Run,
  'needsAttention' | 'attentionReasons' | 'interruptionCause' | 'dead' | 'processingErrors' | 'skipped' | 'error'
>

const statusTone: Record<RunStatus, BadgeTone> = {
  queued: 'neutral',
  running: 'info',
  succeeded: 'success',
  partial: 'warning',
  interrupted: 'warning',
  failed: 'destructive',
}

/**
 * The badge tone of a run. Every interruption is a `warning`, whatever its cause: the API's rule
 * (pipeline/run/PipelineRun.attentionReasons) says an interrupted run needs attention, a new deployment's included.
 * A succeeded run whose source skipped elements is a `warning` too.
 */
export function runTone(run: RunState): BadgeTone {
  if (run.status === 'succeeded' && run.skipped > 0) return 'warning'
  return statusTone[run.status]
}

const stepStatusTone: Record<StepStatus, BadgeTone> = {
  running: 'info',
  succeeded: 'success',
  failed: 'destructive',
  interrupted: 'warning',
}

/** The badge tone of a step; an interrupted step is a `warning` like its run, whatever the cause. */
export function stepTone(status: StepStatus): BadgeTone {
  return stepStatusTone[status]
}

/**
 * The status in words with the interruption's cause, e.g. `przerwany — nowe wdrożenie`, or with the skipped elements
 * of a succeeded or partial run, e.g. `zakończony — pominięte: 3`.
 */
export function runStatusLabel(run: RunState): string {
  const status = label('runStatus', run.status)
  if (run.status === 'interrupted' && run.interruptionCause) {
    return `${status} — ${label('interruptionCause', run.interruptionCause)}`
  }
  if (hasSkipped(run)) return `${status} — pominięte: ${run.skipped}`
  return status
}

/** A succeeded or partial run whose source skipped elements (unexpected shape or not mappable). */
function hasSkipped(run: RunState): boolean {
  return (run.status === 'succeeded' || run.status === 'partial') && run.skipped > 0
}

/**
 * Why the run needs attention, in words: the text of each reason code the API sends
 * (pipeline/run/PipelineRun.attentionReasons), in its order — nothing is counted here. `failed`: the run's own error
 * text, as it is (the watchdog's "no progress for …" and an empty source's "NoPostsFetchedException: …" too);
 * `interrupted`: its cause; `dead_posts`, `processing_errors`, `skipped`: the run's counter. A code without a text
 * here shows as it came. A run that needs attention without any reason (an API from before S-22 sends none) gets one
 * stand-in text, so "Wymaga uwagi:" is never empty; a run that does not need attention has none.
 */
export function attentionReasons(run: AttentionFields): string[] {
  if (!run.needsAttention) return []
  const reasons = (run.attentionReasons ?? []).map((code) => attentionReasonText(code, run))
  return reasons.length > 0 ? reasons : ['sprawdź kroki i błąd przebiegu']
}

function attentionReasonText(code: AttentionReason, run: AttentionFields): string {
  switch (code) {
    case 'failed':
      return run.error ?? 'przebieg zakończył się błędem'
    case 'interrupted':
      return run.interruptionCause ? `przerwany: ${label('interruptionCause', run.interruptionCause)}` : 'przerwany'
    case 'dead_posts':
      return `${run.dead} ${plural(run.dead, 'post martwy', 'posty martwe', 'postów martwych')}`
    case 'processing_errors':
      return `błędy techniczne: ${run.processingErrors}`
    case 'skipped':
      return `pominięte elementy: ${run.skipped}`
    default: {
      // A code the API added after this SPA was built; a code of the union without a text here fails the build.
      const unknown: never = code
      return String(unknown)
    }
  }
}

/** Queued or running: the run may still change, so its views refresh. */
export function isActive(run: Pick<Run, 'status'>): boolean {
  return run.status === 'queued' || run.status === 'running'
}

/** Who requested it: the daily cron or an admin. */
export function requester(run: Pick<Run, 'scheduled'>): string {
  return run.scheduled ? 'cron' : 'admin'
}

/** The days of a range run in Warsaw, e.g. `11.10.2026 – 12.10.2026`, or one day; null without a range. */
export function runRange(run: Pick<Run, 'rangeFrom' | 'rangeTo'>): string | null {
  if (!run.rangeFrom || !run.rangeTo) return null
  const [from, to] = [formatDate(run.rangeFrom), formatDate(run.rangeTo)]
  return from === to ? from : `${from} – ${to}`
}

/** From start to end in ms; to `now` while it runs; null before its start or without an end. */
export function runDuration(run: Pick<Run, 'status' | 'startedAt' | 'finishedAt'>, now: number): number | null {
  if (!run.startedAt) return null
  const end = run.finishedAt ? new Date(run.finishedAt).getTime() : isActive(run) ? now : null
  return end === null ? null : end - new Date(run.startedAt).getTime()
}

/** A step's time in ms; to `now` while it runs; null when it ended without an end time. */
export function stepDuration(step: Run['steps'][number], now: number): number | null {
  const end = step.finishedAt ? new Date(step.finishedAt).getTime() : step.status === 'running' ? now : null
  return end === null ? null : end - new Date(step.startedAt).getTime()
}

/** The step running now, if any. */
export function currentStep(run: Pick<Run, 'steps'>): Run['steps'][number] | null {
  return run.steps.filter((step) => step.status === 'running').at(-1) ?? null
}

/**
 * The assessment's progress: posts assessed so far (ok + failed; a dead post is a failed one) out of the posts the
 * `assess` step took in. `total` is null until that step has started.
 */
export function assessProgress(run: Pick<Run, 'assessedOk' | 'assessedFailed' | 'steps'>): { done: number; total: number | null } {
  const assess = run.steps.filter((step) => step.step === 'assess').at(-1)
  return { done: run.assessedOk + run.assessedFailed, total: assess?.itemsIn ?? null }
}
