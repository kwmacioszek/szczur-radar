// For the tests of the admin panel's parts only: their records and the mount of one part on its address, with a
// panel whose calls the test sees.
import { flushPromises, mount } from '@vue/test-utils'
import { vi } from 'vitest'
import type { Component } from 'vue'
import { createMemoryHistory } from 'vue-router'
import type { Run } from '../../api/pipeline'
import type { FailedPost } from '../../api/posts'
import { createAppRouter } from '../../router'
import { ADMIN_PANEL_KEY, type AdminPanel } from './adminPanel'

export function run(overrides: Partial<Run> = {}): Run {
  return {
    id: 'r1',
    kind: 'range',
    status: 'succeeded',
    interruptionCause: null,
    needsAttention: false,
    attentionReasons: [],
    scheduled: false,
    rangeFrom: '2026-09-30T22:00:00Z',
    rangeTo: '2026-10-01T21:59:59Z',
    sources: ['wykop'],
    requestedPostIds: null,
    createdAt: '2026-10-02T06:00:00Z',
    startedAt: '2026-10-02T06:00:01Z',
    finishedAt: '2026-10-02T06:20:00Z',
    lastActivityAt: '2026-10-02T06:20:00Z',
    fetched: 270,
    newPosts: 268,
    assessedOk: 260,
    assessedFailed: 8,
    processingErrors: 0,
    dead: 0,
    skipped: 0,
    sourcesFailed: 0,
    sourcesEmpty: 0,
    llmRetries: 0,
    flexFallbacks: 0,
    inFlight: 0,
    error: null,
    steps: [
      { step: 'fetch', status: 'succeeded', startedAt: '2026-10-02T06:00:01Z', finishedAt: null, itemsIn: null, itemsOut: 270, error: null },
    ],
    sourceResults: [],
    ...overrides,
  }
}

export function failed(id: string, stage: FailedPost['stage'], overrides: Partial<FailedPost> = {}): FailedPost {
  return {
    id,
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-01T07:00:00Z',
    title: null,
    body: `Wpis ${id}`,
    webUrl: null,
    stage,
    assessmentAttempts: stage === 'dead' ? 3 : 1,
    lastAttempt: { failureClass: 'timeout', violations: [], assessedAt: '2026-10-02T06:10:00Z', provider: 'gemini', model: 'm' },
    technicalFailures: 0,
    lastTechnicalError: null,
    onHold: false,
    ...overrides,
  }
}

/** A stored post on hold: no stored attempt, three technical failures in a row. */
export function onHold(id: string, lastTechnicalError = 'IllegalStateException: a bug in the mapper'): FailedPost {
  return failed(id, 'stored', { assessmentAttempts: 0, lastAttempt: null, technicalFailures: 3, lastTechnicalError, onHold: true })
}

export function never<T>(): Promise<T> {
  return new Promise(() => {})
}

/** `count` succeeded runs `r<from>`, `r<from+1>`, …, created a minute apart, newest first. */
export function runsFrom(from: number, count: number): Run[] {
  return Array.from({ length: count }, (_, at) =>
    run({ id: `r${from + at}`, createdAt: new Date(Date.UTC(2026, 9, 2, 6) - (from + at) * 60_000).toISOString() }),
  )
}

export function page(items: Run[], total: number, size = 20) {
  return { items, page: 0, size, total }
}

/**
 * The part `component` alone on `path` in the application's router, inside a panel the test sees (`panel`), not the
 * layout. `attachTo`: in the document, for focus.
 */
export async function mountPart(component: Component, path: string, { attachTo }: { attachTo?: HTMLElement } = {}) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const panel = { refreshFailedCount: vi.fn<AdminPanel['refreshFailedCount']>(), focusTitle: vi.fn<AdminPanel['focusTitle']>() }
  const wrapper = mount(component, { global: { plugins: [router], provide: { [ADMIN_PANEL_KEY]: panel } }, attachTo })
  await flushPromises()
  return { wrapper, panel, router }
}
