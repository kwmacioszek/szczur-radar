import { describe, expect, it } from 'vitest'
import type { Run, RunStatus, RunStep } from './api/pipeline'
import {
  assessProgress,
  attentionReasons,
  currentStep,
  isActive,
  requester,
  runDuration,
  runRange,
  runStatusLabel,
  runTone,
  stepDuration,
  stepTone,
} from './runs'

const NOW = Date.parse('2026-10-13T00:40:00Z')

function step(name: RunStep['step'], status: RunStep['status'], itemsIn: number | null = null): RunStep {
  return {
    step: name,
    status,
    startedAt: '2026-10-13T00:30:05Z',
    finishedAt: status === 'running' ? null : '2026-10-13T00:31:05Z',
    itemsIn,
    itemsOut: null,
    error: null,
  }
}

function run(overrides: Partial<Run> = {}): Run {
  return {
    id: 'r1',
    kind: 'range',
    status: 'succeeded',
    interruptionCause: null,
    needsAttention: false,
    scheduled: false,
    rangeFrom: '2026-10-10T22:00:00Z',
    rangeTo: '2026-10-12T21:59:59Z',
    sources: ['wykop'],
    requestedPostIds: null,
    createdAt: '2026-10-13T00:30:00Z',
    startedAt: '2026-10-13T00:30:01Z',
    finishedAt: '2026-10-13T00:43:51Z',
    lastActivityAt: null,
    fetched: 0,
    newPosts: 0,
    assessedOk: 0,
    assessedFailed: 0,
    processingErrors: 0,
    dead: 0,
    skipped: 0,
    llmRetries: 0,
    flexFallbacks: 0,
    inFlight: 0,
    error: null,
    steps: [],
    ...overrides,
  }
}

describe('runTone', () => {
  it.each([
    ['queued', 'neutral'],
    ['running', 'info'],
    ['succeeded', 'success'],
    ['partial', 'warning'],
    ['failed', 'destructive'],
    ['interrupted', 'warning'],
  ] as const)('gives a %s run the %s tone', (status, tone) => {
    expect(runTone({ status, interruptionCause: null, skipped: 0 })).toBe(tone)
  })

  it('treats an interruption by a new deployment as expected and any other as a warning', () => {
    expect(runTone({ status: 'interrupted', interruptionCause: 'deploy', skipped: 0 })).toBe('neutral')
    expect(runTone({ status: 'interrupted', interruptionCause: 'restart', skipped: 0 })).toBe('warning')
    expect(runTone({ status: 'interrupted', interruptionCause: 'shutdown', skipped: 0 })).toBe('warning')
    expect(runTone({ status: 'interrupted', interruptionCause: 'lost', skipped: 0 })).toBe('warning')
  })

  it('warns about a succeeded run with skipped elements; a partial run stays a warning', () => {
    expect(runTone({ status: 'succeeded', interruptionCause: null, skipped: 3 })).toBe('warning')
    expect(runTone({ status: 'partial', interruptionCause: null, skipped: 3 })).toBe('warning')
    expect(runTone({ status: 'failed', interruptionCause: null, skipped: 3 })).toBe('destructive')
  })
})

describe('stepTone', () => {
  it('gives each step status its tone; an interrupted step follows its run', () => {
    expect(stepTone('running', null)).toBe('info')
    expect(stepTone('succeeded', null)).toBe('success')
    expect(stepTone('failed', null)).toBe('destructive')
    expect(stepTone('interrupted', null)).toBe('warning')
    expect(stepTone('interrupted', 'restart')).toBe('warning')
    expect(stepTone('interrupted', 'lost')).toBe('warning')
    expect(stepTone('interrupted', 'deploy')).toBe('neutral')
  })
})

describe('runStatusLabel', () => {
  it('adds the cause to an interrupted run', () => {
    expect(runStatusLabel({ status: 'interrupted', interruptionCause: 'deploy', skipped: 0 })).toBe(
      'przerwany — nowe wdrożenie',
    )
    expect(runStatusLabel({ status: 'interrupted', interruptionCause: 'shutdown', skipped: 0 })).toBe(
      'przerwany — zatrzymanie aplikacji',
    )
    expect(runStatusLabel({ status: 'interrupted', interruptionCause: 'lost', skipped: 0 })).toBe(
      'przerwany — utracona instancja',
    )
  })

  it('adds the skipped elements to a succeeded or partial run', () => {
    expect(runStatusLabel({ status: 'succeeded', interruptionCause: null, skipped: 3 })).toBe('zakończony — pominięte: 3')
    expect(runStatusLabel({ status: 'partial', interruptionCause: null, skipped: 1 })).toBe('częściowo — pominięte: 1')
  })

  it('says only the status otherwise, also for an interruption recorded before its cause', () => {
    expect(runStatusLabel({ status: 'interrupted', interruptionCause: null, skipped: 0 })).toBe('przerwany')
    expect(runStatusLabel({ status: 'partial', interruptionCause: null, skipped: 0 })).toBe('częściowo')
    expect(runStatusLabel({ status: 'succeeded', interruptionCause: null, skipped: 0 })).toBe('zakończony')
    expect(runStatusLabel({ status: 'failed', interruptionCause: null, skipped: 4 })).toBe('błąd')
  })
})

describe('attentionReasons', () => {
  it("gives a failed run's error as it is, the watchdog's included", () => {
    expect(
      attentionReasons(run({ status: 'failed', error: 'no progress for 39m 00s: the run was stopped', dead: 2 })),
    ).toEqual(['no progress for 39m 00s: the run was stopped'])
    expect(attentionReasons(run({ status: 'failed', error: 'Wykop GET search answered HTTP 503' }))).toEqual([
      'Wykop GET search answered HTTP 503',
    ])
  })

  it('says a failed run without an error failed', () => {
    expect(attentionReasons(run({ status: 'failed' }))).toEqual(['przebieg zakończył się błędem'])
  })

  it('names the cause of an interruption', () => {
    expect(attentionReasons(run({ status: 'interrupted', interruptionCause: 'deploy' }))).toEqual([
      'przerwany: nowe wdrożenie',
    ])
    expect(attentionReasons(run({ status: 'interrupted', interruptionCause: 'restart' }))).toEqual([
      'przerwany: restart aplikacji',
    ])
    expect(attentionReasons(run({ status: 'interrupted' }))).toEqual(['przerwany'])
  })

  it.each([
    [1, 0, ['1 post martwy']],
    [3, 0, ['3 posty martwe']],
    [5, 2, ['5 postów martwych', 'błędy techniczne: 2']],
    [0, 1, ['błędy techniczne: 1']],
    [0, 0, []],
  ])('counts %i dead posts and %i technical errors of a partial run', (dead, processingErrors, reasons) => {
    expect(attentionReasons(run({ status: 'partial', dead, processingErrors, assessedFailed: 9 }))).toEqual(reasons)
  })

  it.each(['queued', 'running', 'succeeded'] as RunStatus[])('has no reason for a %s run', (status) => {
    expect(attentionReasons(run({ status, dead: 1, processingErrors: 1 }))).toEqual([])
  })

  it('names the skipped elements of a succeeded run', () => {
    expect(attentionReasons(run({ status: 'succeeded', skipped: 3 }))).toEqual(['pominięte elementy: 3'])
  })

  it('names the skipped elements of a partial run after its dead posts and technical errors', () => {
    expect(attentionReasons(run({ status: 'partial', dead: 1, processingErrors: 2, skipped: 3 }))).toEqual([
      '1 post martwy',
      'błędy techniczne: 2',
      'pominięte elementy: 3',
    ])
    expect(attentionReasons(run({ status: 'partial', skipped: 1 }))).toEqual(['pominięte elementy: 1'])
  })

  it('gives a failed run its error only, whatever it skipped', () => {
    expect(
      attentionReasons(
        run({
          status: 'failed',
          fetched: 4,
          skipped: 4,
          error: 'NoPostsFetchedException: wykop gave no post to store (fetched 4, skipped 4)',
        }),
      ),
    ).toEqual(['NoPostsFetchedException: wykop gave no post to store (fetched 4, skipped 4)'])
  })
})

describe('the rest of a run', () => {
  it('is active while queued or running', () => {
    expect(isActive(run({ status: 'queued' }))).toBe(true)
    expect(isActive(run({ status: 'running' }))).toBe(true)
    expect(isActive(run({ status: 'partial' }))).toBe(false)
  })

  it('says who started it', () => {
    expect(requester(run({ scheduled: true }))).toBe('cron')
    expect(requester(run({ scheduled: false }))).toBe('admin')
  })

  it('gives the Warsaw days of its range, one day once', () => {
    expect(runRange(run())).toBe('11.10.2026 – 12.10.2026')
    expect(runRange(run({ rangeFrom: '2026-10-11T22:00:00Z' }))).toBe('12.10.2026')
    expect(runRange(run({ kind: 'retry', rangeFrom: null, rangeTo: null }))).toBeNull()
  })

  it('lasts from its start to its end, or to now while it runs', () => {
    expect(runDuration(run(), NOW)).toBe(13 * 60_000 + 50_000)
    expect(runDuration(run({ status: 'running', finishedAt: null }), NOW)).toBe(9 * 60_000 + 59_000)
    expect(runDuration(run({ status: 'queued', startedAt: null, finishedAt: null }), NOW)).toBeNull()
    expect(runDuration(run({ status: 'interrupted', finishedAt: null }), NOW)).toBeNull()
  })

  it('times a step the same way', () => {
    expect(stepDuration(step('fetch', 'succeeded'), NOW)).toBe(60_000)
    expect(stepDuration(step('assess', 'running'), NOW)).toBe(9 * 60_000 + 55_000)
    expect(stepDuration({ ...step('assess', 'interrupted'), finishedAt: null }, NOW)).toBeNull()
  })

  it('finds the step running now', () => {
    expect(currentStep(run({ steps: [step('fetch', 'succeeded'), step('assess', 'running', 41)] }))?.step).toBe('assess')
    expect(currentStep(run({ steps: [step('fetch', 'succeeded')] }))).toBeNull()
  })

  it('counts the assessed posts against what the assess step took in', () => {
    const assessing = run({ assessedOk: 17, assessedFailed: 2, steps: [step('fetch', 'succeeded'), step('assess', 'running', 41)] })
    expect(assessProgress(assessing)).toEqual({ done: 19, total: 41 })
    expect(assessProgress(run({ steps: [step('fetch', 'running')] }))).toEqual({ done: 0, total: null })
  })
})
