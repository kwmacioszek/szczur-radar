import { describe, expect, it } from 'vitest'
import type { AttentionReason, Run, RunStep } from './api/pipeline'
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
    attentionReasons: [],
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

  it.each(['deploy', 'restart', 'shutdown', 'lost'] as const)(
    'warns about an interruption whatever its cause, %s included',
    (interruptionCause) => {
      expect(runTone({ status: 'interrupted', interruptionCause, skipped: 0 })).toBe('warning')
    },
  )

  it('warns about a succeeded run with skipped elements; a partial run stays a warning', () => {
    expect(runTone({ status: 'succeeded', interruptionCause: null, skipped: 3 })).toBe('warning')
    expect(runTone({ status: 'partial', interruptionCause: null, skipped: 3 })).toBe('warning')
    expect(runTone({ status: 'failed', interruptionCause: null, skipped: 3 })).toBe('destructive')
  })
})

describe('stepTone', () => {
  it('gives each step status its tone; an interrupted step is a warning', () => {
    expect(stepTone('running')).toBe('info')
    expect(stepTone('succeeded')).toBe('success')
    expect(stepTone('failed')).toBe('destructive')
    expect(stepTone('interrupted')).toBe('warning')
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
  /** A run that needs attention for the API's `codes`. */
  function attention(codes: AttentionReason[], overrides: Partial<Run> = {}): Run {
    return run({ needsAttention: true, attentionReasons: codes, ...overrides })
  }

  it("gives a failed run's error as it is, the watchdog's included", () => {
    expect(
      attentionReasons(
        attention(['failed'], { status: 'failed', error: 'no progress for 39m 00s: the run was stopped', dead: 2 }),
      ),
    ).toEqual(['no progress for 39m 00s: the run was stopped'])
    const wykop = attention(['failed'], { status: 'failed', error: 'Wykop GET search answered HTTP 503' })
    expect(attentionReasons(wykop)).toEqual(['Wykop GET search answered HTTP 503'])
  })

  it('says a failed run without an error failed', () => {
    const failed = attention(['failed'], { status: 'failed' })
    expect(attentionReasons(failed)).toEqual(['przebieg zakończył się błędem'])
  })

  it('names the cause of an interruption', () => {
    const interrupted = (cause: Run['interruptionCause']) =>
      attention(['interrupted'], { status: 'interrupted', interruptionCause: cause })
    expect(attentionReasons(interrupted('deploy'))).toEqual(['przerwany: nowe wdrożenie'])
    expect(attentionReasons(interrupted('restart'))).toEqual(['przerwany: restart aplikacji'])
    expect(attentionReasons(interrupted(null))).toEqual(['przerwany'])
  })

  it.each([
    [1, '1 post martwy'],
    [3, '3 posty martwe'],
    [5, '5 postów martwych'],
  ])('counts %i dead posts', (dead, reason) => {
    const partial = attention(['dead_posts'], { status: 'partial', dead, assessedFailed: 9 })
    expect(attentionReasons(partial)).toEqual([reason])
  })

  it('counts the technical errors and the skipped elements', () => {
    expect(attentionReasons(attention(['processing_errors'], { status: 'partial', processingErrors: 2 }))).toEqual([
      'błędy techniczne: 2',
    ])
    expect(attentionReasons(attention(['skipped'], { skipped: 3 }))).toEqual(['pominięte elementy: 3'])
  })

  it("keeps the API's order", () => {
    const counters = { status: 'partial', dead: 1, processingErrors: 2, skipped: 3 } as const
    expect(attentionReasons(attention(['dead_posts', 'processing_errors', 'skipped'], counters))).toEqual([
      '1 post martwy',
      'błędy techniczne: 2',
      'pominięte elementy: 3',
    ])
    expect(attentionReasons(attention(['skipped', 'dead_posts'], { status: 'partial', dead: 1, skipped: 3 }))).toEqual([
      'pominięte elementy: 3',
      '1 post martwy',
    ])
  })

  it('shows a code it has no text for as it came', () => {
    expect(attentionReasons(attention(['stale_source' as AttentionReason, 'skipped'], { skipped: 1 }))).toEqual([
      'stale_source',
      'pominięte elementy: 1',
    ])
  })

  it('gives a run that needs attention without any reason a stand-in text', () => {
    // An API from before S-22 sends the flag without the field.
    const legacy = run({ needsAttention: true, status: 'failed', error: 'boom' })
    delete legacy.attentionReasons
    expect(attentionReasons(legacy)).toEqual(['sprawdź kroki i błąd przebiegu'])
    const empty = attention([], { status: 'partial', dead: 1 })
    expect(attentionReasons(empty)).toEqual(['sprawdź kroki i błąd przebiegu'])
  })

  it('counts nothing itself: a run the API does not flag has no reason, whatever its counters', () => {
    expect(attentionReasons(run({ status: 'partial', dead: 1, processingErrors: 1, skipped: 1 }))).toEqual([])
    expect(attentionReasons(run({ status: 'failed', error: 'boom' }))).toEqual([])
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
