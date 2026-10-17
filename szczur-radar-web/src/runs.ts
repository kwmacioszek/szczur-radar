// How a pipeline run is shown: its tone, status text, "wymaga uwagi" reasons, range, duration and progress —
// one place for the run list (/admin), the run's page (/admin/runs/:id) and the notification bell.
import type { InterruptionCause, Run, RunStatus, StepStatus } from './api/pipeline'
import type { BadgeTone } from './components/StatusBadge.vue'
import { formatDate, label, plural } from './labels'

/** What the tone and status text need; a run of the list and the run of a notification both have it. */
type RunState = { status: RunStatus; interruptionCause: InterruptionCause | null }

/** What the "wymaga uwagi" reasons are built from. */
type AttentionFields = RunState & Pick<Run, 'dead' | 'processingErrors' | 'error'>

const statusTone: Record<RunStatus, BadgeTone> = {
  queued: 'neutral',
  running: 'info',
  succeeded: 'success',
  partial: 'warning',
  interrupted: 'warning',
  failed: 'destructive',
}

/**
 * The badge tone of a run. A run interrupted by a new deployment is expected (the operator merged), so it is
 * `neutral`; any other interruption is a `warning`.
 */
export function runTone(run: RunState): BadgeTone {
  if (run.status === 'interrupted' && run.interruptionCause === 'deploy') return 'neutral'
  return statusTone[run.status]
}

const stepStatusTone: Record<StepStatus, BadgeTone> = {
  running: 'info',
  succeeded: 'success',
  failed: 'destructive',
  interrupted: 'warning',
}

/** The badge tone of a step; an interrupted step takes the tone of its run's interruption. */
export function stepTone(status: StepStatus, cause: InterruptionCause | null): BadgeTone {
  if (status === 'interrupted' && cause === 'deploy') return 'neutral'
  return stepStatusTone[status]
}

/** The status in words with the interruption's cause, e.g. `przerwany — nowe wdrożenie`. */
export function runStatusLabel(run: RunState): string {
  const status = label('runStatus', run.status)
  return run.status === 'interrupted' && run.interruptionCause
    ? `${status} — ${label('interruptionCause', run.interruptionCause)}`
    : status
}

/**
 * Why the run needs attention, in words, along the API's rule (pipeline/run/PipelineRun.needsAttention): a failed
 * run's own error text, as it is (the watchdog's "no progress for …" too); an interrupted run's cause; a partial
 * run's dead posts and technical errors. Empty for a run that does not need attention.
 */
export function attentionReasons(run: AttentionFields): string[] {
  switch (run.status) {
    case 'failed':
      return [run.error ?? 'przebieg zakończył się błędem']
    case 'interrupted':
      return [run.interruptionCause ? `przerwany: ${label('interruptionCause', run.interruptionCause)}` : 'przerwany']
    case 'partial': {
      const reasons: string[] = []
      if (run.dead > 0) reasons.push(`${run.dead} ${plural(run.dead, 'post martwy', 'posty martwe', 'postów martwych')}`)
      if (run.processingErrors > 0) reasons.push(`błędy techniczne: ${run.processingErrors}`)
      return reasons
    }
    default:
      return []
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
