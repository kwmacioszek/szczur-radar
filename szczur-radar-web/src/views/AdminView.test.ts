import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { ApiError } from '../api/client'
import { type MissingDays, RUNS_PAGE_SIZE, type Run, fetchMissingDays, fetchRuns, fetchSources, retryPosts, startRange } from '../api/pipeline'
import { type FailedPost, fetchFailedPosts, restorePosts } from '../api/posts'
import { fetchAssessmentQuality } from '../api/quality'
import { resetSession } from '../auth/session'
import {
  sampleAssessmentQuality,
  sampleAssessmentQualityFewAttempts,
  sampleAssessmentQualityLanguageUnavailable,
} from '../contract/samples'
import { createAppRouter } from '../router'
import AdminView from './AdminView.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/posts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/posts')>()),
  fetchPosts: vi.fn(),
  fetchFailedPosts: vi.fn(),
  restorePosts: vi.fn(),
}))
vi.mock('../api/quality', () => ({ fetchAssessmentQuality: vi.fn() }))
vi.mock('../api/pipeline', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/pipeline')>()),
  startRange: vi.fn(),
  retryPosts: vi.fn(),
  fetchRuns: vi.fn(),
  fetchSources: vi.fn(),
  fetchMissingDays: vi.fn(),
}))

function run(overrides: Partial<Run> = {}): Run {
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

function failed(id: string, stage: FailedPost['stage'], overrides: Partial<FailedPost> = {}): FailedPost {
  return {
    id,
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
function onHold(id: string, lastTechnicalError = 'IllegalStateException: a bug in the mapper'): FailedPost {
  return failed(id, 'stored', { assessmentAttempts: 0, lastAttempt: null, technicalFailures: 3, lastTechnicalError, onHold: true })
}

/** `attachTo`: in the document, for focus. */
async function mountAdmin({ attachTo }: { attachTo?: HTMLElement } = {}) {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/admin')
  const wrapper = mount(AdminView, { global: { plugins: [router] }, attachTo })
  await flushPromises()
  return wrapper
}

function never<T>(): Promise<T> {
  return new Promise(() => {})
}

function refreshButton(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
  return wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!
}

/** `count` succeeded runs `r<from>`, `r<from+1>`, …, created a minute apart, newest first. */
function runsFrom(from: number, count: number): Run[] {
  return Array.from({ length: count }, (_, at) =>
    run({ id: `r${from + at}`, createdAt: new Date(Date.UTC(2026, 9, 2, 6) - (from + at) * 60_000).toISOString() }),
  )
}

function page(items: Run[], total: number, size = 20) {
  return { items, page: 0, size, total }
}

enableAutoUnmount(afterEach)

describe('AdminView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    vi.mocked(fetchSources).mockReset().mockResolvedValue([{ source: 'wykop', configured: true }])
    vi.mocked(fetchRuns).mockReset().mockResolvedValue({ items: [], page: 0, size: 20, total: 0 })
    vi.mocked(fetchFailedPosts).mockReset().mockResolvedValue({ items: [], page: 0, size: 50, total: 0 })
    vi.mocked(fetchMissingDays).mockReset().mockResolvedValue({ from: '2026-10-09', to: '2026-10-22', days: [] })
    vi.mocked(fetchAssessmentQuality)
      .mockReset()
      .mockResolvedValue({ from: '2026-10-16', to: '2026-10-22', windowDays: 7, minAttempts: 100, minTheses: 500, sources: [] })
    vi.mocked(startRange).mockReset()
    vi.mocked(retryPosts).mockReset()
    vi.mocked(restorePosts).mockReset()
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('starts a range with the configured sources checked by default', async () => {
    vi.mocked(startRange).mockResolvedValue([run({ kind: 'retry' }), run()])
    const wrapper = await mountAdmin()
    await wrapper.findAll('input[type="date"]')[0]!.setValue('2026-10-01')
    await wrapper.findAll('input[type="date"]')[1]!.setValue('2026-10-02')
    expect((wrapper.find('input[type="checkbox"][value="wykop"]').element as HTMLInputElement).checked).toBe(true)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(startRange).toHaveBeenCalledWith('2026-10-01', '2026-10-02', ['wykop'])
    expect(wrapper.find('[role="status"]').text()).toBe('Uruchomiono: ponowienie resztek, zakres dat.')
    expect(fetchRuns).toHaveBeenCalledTimes(2)
  })

  it('blocks the button until the server answers', async () => {
    let answer: (runs: Run[]) => void = () => {}
    vi.mocked(startRange).mockReturnValue(new Promise((resolve) => (answer = resolve)))
    const wrapper = await mountAdmin()
    await wrapper.find('form').trigger('submit')
    expect(wrapper.find('form button').attributes('disabled')).toBeDefined()
    await wrapper.find('form').trigger('submit')
    expect(startRange).toHaveBeenCalledTimes(1)
    answer([run()])
    await flushPromises()
    expect(wrapper.find('form button').attributes('disabled')).toBeUndefined()
  })

  it('says the run is starting while the server answers', async () => {
    vi.mocked(startRange).mockReturnValue(never())
    const wrapper = await mountAdmin()
    await wrapper.find('form').trigger('submit')
    const button = wrapper.find('form button')
    expect(button.text()).toBe('Uruchamianie…')
    expect(button.attributes('aria-busy')).toBe('true')
  })

  it('says a run is already going on', async () => {
    vi.mocked(startRange).mockRejectedValue(new ApiError(409, 'pipeline_busy'))
    const wrapper = await mountAdmin()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('Przebieg już trwa')
  })

  it('offers an unconfigured source unchecked and disabled', async () => {
    vi.mocked(fetchSources).mockResolvedValue([{ source: 'wykop', configured: false }])
    const wrapper = await mountAdmin()
    const box = wrapper.find('input[type="checkbox"][value="wykop"]')
    expect((box.element as HTMLInputElement).checked).toBe(false)
    expect(box.attributes('disabled')).toBeDefined()
    expect(wrapper.find('form button').attributes('disabled')).toBeDefined()
    expect(wrapper.find('#start-hint').text()).toBe('Żadne źródło nie jest skonfigurowane na serwerze.')
  })

  it('says why Uruchom is disabled when no source is checked', async () => {
    const wrapper = await mountAdmin()
    const button = wrapper.find('form button')
    expect(wrapper.find('#start-hint').exists()).toBe(false)
    expect(button.attributes('aria-describedby')).toBeUndefined()

    await wrapper.find('input[type="checkbox"][value="wykop"]').setValue(false)

    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.find('#start-hint').text()).toBe('Wybierz co najmniej jedno źródło.')
    expect(button.attributes('aria-describedby')).toBe('start-hint')
  })

  it('says it is loading, not that there is nothing, until the API answers', async () => {
    vi.mocked(fetchSources).mockReturnValue(never())
    vi.mocked(fetchRuns).mockReturnValue(never())
    vi.mocked(fetchFailedPosts).mockReturnValue(never())
    const wrapper = await mountAdmin()
    expect(wrapper.text()).not.toContain('Jeszcze nie było przebiegu.')
    expect(wrapper.text()).not.toContain('Brak nieudanych postów.')
    const skeletons = wrapper.findAll('.loading-state')
    expect(skeletons.map((shown) => shown.classes()[1])).toEqual(['form', 'rows', 'rows'])
    expect(skeletons.map((shown) => shown.text())).toEqual(['Wczytywanie…', 'Wczytywanie…', 'Wczytywanie…'])
    expect(wrapper.findAll('section[aria-busy="true"]')).toHaveLength(3)
    expect(wrapper.find('#start-hint').exists()).toBe(false)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(refreshButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('has every section loading at once, each marked busy, and Odśwież disabled', async () => {
    vi.mocked(fetchSources).mockReturnValue(never())
    vi.mocked(fetchMissingDays).mockReturnValue(never())
    vi.mocked(fetchAssessmentQuality).mockReturnValue(never())
    vi.mocked(fetchRuns).mockReturnValue(never())
    vi.mocked(fetchFailedPosts).mockReturnValue(never())
    const wrapper = await mountAdmin()
    const sections = wrapper.findAll('section')
    expect(sections).toHaveLength(5)
    expect(sections.map((shown) => shown.attributes('aria-busy'))).toEqual(['true', 'true', 'true', 'true', 'true'])
    expect(sections.map((shown) => shown.find('.loading-state').classes()[1])).toEqual([
      'form',
      'lines',
      'lines',
      'rows',
      'rows',
    ])
    expect(refreshButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('enables Odśwież once the runs have loaded, and keeps it enabled while refreshing an empty list', async () => {
    const wrapper = await mountAdmin()
    expect(refreshButton(wrapper).attributes('disabled')).toBeUndefined()

    vi.mocked(fetchRuns).mockReturnValue(never())
    await refreshButton(wrapper).trigger('click')

    expect(refreshButton(wrapper).attributes('disabled')).toBeUndefined()
    expect(wrapper.find('section[aria-labelledby="runs-title"] .state-title').text()).toBe('Jeszcze nie było przebiegu')
  })

  it.each(['empty', 'data'] as const)('keeps the errors with a busy button while recovering, then the %s response', async (response) => {
    let answerRuns: (value: Awaited<ReturnType<typeof fetchRuns>>) => void = () => {}
    let answerFailed: (value: Awaited<ReturnType<typeof fetchFailedPosts>>) => void = () => {}
    vi.mocked(fetchRuns)
      .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      .mockReturnValueOnce(new Promise((resolve) => (answerRuns = resolve)))
    vi.mocked(fetchFailedPosts)
      .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      .mockReturnValueOnce(new Promise((resolve) => (answerFailed = resolve)))
    const wrapper = await mountAdmin()
    const runsSection = wrapper.find('section[aria-labelledby="runs-title"]')
    const failedSection = wrapper.find('section[aria-labelledby="failed-title"]')
    expect(runsSection.find('[role="alert"] .state-title').text()).toBe('Nie udało się wczytać przebiegów')
    expect(runsSection.find('[role="alert"] .state-text').text()).toBe('Pozostałe sekcje działają osobno.')
    expect(failedSection.find('[role="alert"] .state-title').text()).toBe('Nie udało się wczytać nieudanych postów')
    expect(refreshButton(wrapper).attributes('disabled')).toBeUndefined()

    await refreshButton(wrapper).trigger('click')

    expect(fetchRuns).toHaveBeenCalledTimes(2)
    expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
    for (const section of [runsSection, failedSection]) {
      const error = section.find('.state-error')
      expect(error.exists()).toBe(true)
      expect(error.attributes('role')).toBeUndefined()
      expect(error.find('button').attributes('aria-busy')).toBe('true')
      expect(error.find('button').text()).toBe('Wczytywanie…')
      expect(section.find('.loading-state').exists()).toBe(false)
      expect(section.attributes('aria-busy')).toBe('true')
    }

    const hasItems = response === 'data'
    answerRuns({ items: hasItems ? [run()] : [], page: 0, size: 20, total: hasItems ? 1 : 0 })
    await flushPromises()

    expect(runsSection.find('.state-error').exists()).toBe(false)
    expect(runsSection.attributes('aria-busy')).toBeUndefined()
    expect(runsSection.findAll('.run')).toHaveLength(hasItems ? 1 : 0)
    expect(runsSection.find('.state-title').exists()).toBe(!hasItems)
    expect(failedSection.find('.state-error button').attributes('aria-busy')).toBe('true')

    answerFailed({ items: hasItems ? [failed('a', 'failed')] : [], page: 0, size: 50, total: hasItems ? 1 : 0 })
    await flushPromises()

    expect(failedSection.find('.state-error').exists()).toBe(false)
    expect(failedSection.findAll('.failed-posts li')).toHaveLength(hasItems ? 1 : 0)
    expect(failedSection.find('.state-title').exists()).toBe(!hasItems)
    expect(failedSection.attributes('aria-busy')).toBeUndefined()
  })

  it('shows an empty list of runs and of failed posts as the shared empty state', async () => {
    const wrapper = await mountAdmin()
    const runsEmpty = wrapper.find('section[aria-labelledby="runs-title"] .state')
    expect(runsEmpty.find('.state-title').text()).toBe('Jeszcze nie było przebiegu')
    expect(runsEmpty.find('.state-title').element.tagName).toBe('P')
    expect(runsEmpty.find('.state-text').text()).toBe('Pierwszy uruchomisz w sekcji „Nowy przebieg”.')
    expect(runsEmpty.find('use').attributes('href')).toBe('#i-inbox')
    const failedEmpty = wrapper.find('section[aria-labelledby="failed-title"] .state')
    expect(failedEmpty.find('.state-title').text()).toBe('Brak nieudanych postów')
    expect(failedEmpty.find('.state-text').text()).toBe('Żaden post nie czeka na ponowienie ani nie jest martwy.')
    expect(failedEmpty.find('use').attributes('href')).toBe('#i-circle-check')
    expect(wrapper.find('section[aria-busy="true"]').exists()).toBe(false)
  })

  it('keeps the runs and failed posts on screen while refreshing', async () => {
    vi.mocked(fetchRuns).mockResolvedValueOnce({ items: [run()], page: 0, size: 20, total: 1 }).mockReturnValue(never())
    vi.mocked(fetchFailedPosts).mockResolvedValueOnce({ items: [failed('a', 'failed')], page: 0, size: 50, total: 1 }).mockReturnValue(never())
    const wrapper = await mountAdmin()
    await wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!.trigger('click')
    expect(fetchRuns).toHaveBeenCalledTimes(2)
    expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('.run')).toHaveLength(1)
    expect(wrapper.findAll('.failed-posts li')).toHaveLength(1)
    expect(wrapper.find('.loading').exists()).toBe(false)
  })

  it.each(['Spróbuj ponownie', 'Odśwież'])('loads the sources again after an error with %s', async (name) => {
    vi.mocked(fetchSources).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
    const wrapper = await mountAdmin()
    const start = wrapper.find('section[aria-labelledby="start-title"]')
    expect(start.find('[role="alert"] .state-title').text()).toBe('Nie udało się wczytać listy źródeł')
    expect(start.find('[role="alert"] .state-text').text()).toBe('Bez listy źródeł nie da się uruchomić przebiegu.')
    expect(start.find('form').exists()).toBe(false)
    expect(start.attributes('aria-busy')).toBeUndefined()

    await wrapper.findAll('button').find((button) => button.text() === name)!.trigger('click')
    await flushPromises()

    expect(fetchSources).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect((wrapper.find('input[type="checkbox"][value="wykop"]').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.find('form button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('replaces the whole form with a skeleton until the sources answer, and keeps the error busy while retrying', async () => {
    let answer: (sources: Awaited<ReturnType<typeof fetchSources>>) => void = () => {}
    vi.mocked(fetchSources)
      .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      .mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
    const wrapper = await mountAdmin()
    const start = wrapper.find('section[aria-labelledby="start-title"]')

    await start.find('.state-error button').trigger('click')

    expect(start.find('.state-error button').attributes('aria-busy')).toBe('true')
    expect(start.find('.loading-state').exists()).toBe(false)
    expect(start.find('form').exists()).toBe(false)

    answer([{ source: 'wykop', configured: true }])
    await flushPromises()

    expect(start.find('.state-error').exists()).toBe(false)
    expect(start.find('form').exists()).toBe(true)
  })

  describe('a section retries on its own', () => {
    const SECTIONS = [
      ['start-title', 'Nowy przebieg', 'sources'],
      ['missing-title', 'Dni bez danych', 'missing'],
      ['quality-title', 'Jakość ocen (7 dni)', 'quality'],
      ['runs-title', 'Ostatnie przebiegi', 'runs'],
      ['failed-title', 'Nieudane posty', 'failed'],
    ] as const

    function reads() {
      return { sources: fetchSources, missing: fetchMissingDays, quality: fetchAssessmentQuality, runs: fetchRuns, failed: fetchFailedPosts }
    }

    it.each(SECTIONS)('%s: its "Spróbuj ponownie" reads only %s', async (id, _name, key) => {
      vi.mocked(reads()[key]).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin()
      const alerts = wrapper.findAll('[role="alert"]')
      expect(alerts).toHaveLength(1)
      expect(wrapper.find(`section[aria-labelledby="${id}"] [role="alert"]`).exists()).toBe(true)
      const before = Object.fromEntries(Object.entries(reads()).map(([name, read]) => [name, vi.mocked(read).mock.calls.length]))

      await alerts[0]!.find('button').trigger('click')
      await flushPromises()

      for (const [name, read] of Object.entries(reads())) {
        expect(vi.mocked(read).mock.calls.length - before[name]!, name).toBe(name === key ? 1 : 0)
      }
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    })

    it.each(SECTIONS)('%s: the focus goes to its heading after a successful retry', async (id, _name, key) => {
      vi.mocked(reads()[key]).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin({ attachTo: document.body })

      await wrapper.find(`section[aria-labelledby="${id}"] .state-error button`).trigger('click')
      await flushPromises()

      const heading = wrapper.find(`#${id}`)
      expect(heading.attributes('tabindex')).toBe('-1')
      expect(document.activeElement).toBe(heading.element)
    })

    it.each(SECTIONS)('%s: another failure is announced again and the focus returns to "Spróbuj ponownie"', async (id, _name, key) => {
      vi.mocked(reads()[key]).mockRejectedValue(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin({ attachTo: document.body })
      const section = wrapper.find(`section[aria-labelledby="${id}"]`)
      const first = section.find('[role="alert"]').element

      await section.find('.state-error button').trigger('click')
      await flushPromises()

      const alert = section.find('[role="alert"]')
      expect(alert.element).not.toBe(first)
      expect(document.activeElement).toBe(alert.find('button').element)
    })

    it('moves the focus after a retry also when a read nobody pressed for superseded it', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
      vi.mocked(fetchRuns)
        .mockResolvedValueOnce(page([run({ status: 'running', finishedAt: null })], 1))
        .mockResolvedValue(page([run({ status: 'succeeded' })], 1))
      vi.mocked(fetchMissingDays)
        .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
        // The retry's answer never comes; the read after the run ended answers first.
        .mockReturnValueOnce(new Promise(() => {}))
      const wrapper = await mountAdmin({ attachTo: document.body })

      await wrapper.find('section[aria-labelledby="missing-title"] .state-error button').trigger('click')
      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()

      expect(fetchMissingDays).toHaveBeenCalledTimes(3)
      expect(wrapper.find('section[aria-labelledby="missing-title"] .state-error').exists()).toBe(false)
      expect(document.activeElement).toBe(wrapper.find('#missing-title').element)
    })

    it('leaves the focus on Odśwież when it supersedes a retry', async () => {
      let answer: (value: Awaited<ReturnType<typeof fetchAssessmentQuality>>) => void = () => {}
      vi.mocked(fetchAssessmentQuality)
        .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
        .mockReturnValueOnce(new Promise(() => {}))
        .mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
      const wrapper = await mountAdmin({ attachTo: document.body })
      await wrapper.find('section[aria-labelledby="quality-title"] .state-error button').trigger('click')
      const refresh = refreshButton(wrapper)
      ;(refresh.element as HTMLElement).focus()

      await refresh.trigger('click')
      answer({ from: '2026-10-16', to: '2026-10-22', windowDays: 7, minAttempts: 100, minTheses: 500, sources: [] })
      await flushPromises()

      expect(wrapper.find('section[aria-labelledby="quality-title"] .state-error').exists()).toBe(false)
      expect(document.activeElement).toBe(refresh.element)
    })

    it('leaves the focus on Odśwież when it retries a section in error', async () => {
      vi.mocked(fetchAssessmentQuality).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin({ attachTo: document.body })
      const refresh = refreshButton(wrapper)
      ;(refresh.element as HTMLElement).focus()

      await refresh.trigger('click')
      await flushPromises()

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(document.activeElement).toBe(refresh.element)
    })

    it('keeps the error busy until the answer when the retry comes from Odśwież, and leaves the focus', async () => {
      let answer: (value: Awaited<ReturnType<typeof fetchAssessmentQuality>>) => void = () => {}
      vi.mocked(fetchAssessmentQuality)
        .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
        .mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
      const wrapper = await mountAdmin({ attachTo: document.body })
      const refresh = refreshButton(wrapper)
      ;(refresh.element as HTMLElement).focus()

      await refresh.trigger('click')

      const error = wrapper.find('section[aria-labelledby="quality-title"] .state-error')
      expect(error.attributes('role')).toBeUndefined()
      expect(error.find('button').attributes('aria-busy')).toBe('true')

      answer({ from: '2026-10-16', to: '2026-10-22', windowDays: 7, minAttempts: 100, minTheses: 500, sources: [] })
      await flushPromises()

      expect(wrapper.find('section[aria-labelledby="quality-title"] .state-error').exists()).toBe(false)
      expect(document.activeElement).toBe(refresh.element)
    })

    it('leaves the focus when another failure follows a retry from Odśwież', async () => {
      vi.mocked(fetchAssessmentQuality).mockRejectedValue(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin({ attachTo: document.body })
      const refresh = refreshButton(wrapper)
      ;(refresh.element as HTMLElement).focus()

      await refresh.trigger('click')
      await flushPromises()

      expect(wrapper.find('section[aria-labelledby="quality-title"] [role="alert"]').exists()).toBe(true)
      expect(document.activeElement).toBe(refresh.element)
    })
  })

  it('shows a partial run with its technical errors and error text', async () => {
    vi.mocked(fetchRuns).mockResolvedValue({
      items: [run({ status: 'partial', processingErrors: 1, error: '1 post(s) with a technical error: x (IllegalStateException)' })],
      page: 0,
      size: 20,
      total: 1,
    })
    const wrapper = await mountAdmin()
    const shown = wrapper.find('.run')
    expect(shown.find('.status').text()).toBe('częściowo')
    expect(shown.find('.alarm').text()).toBe('błędy techniczne 1')
    expect(shown.find('.run-error').text()).toContain('IllegalStateException')
    expect(shown.text()).toContain('pobranie (270)')
  })

  it.each([
    ['succeeded', 'zakończony', 'success'],
    ['partial', 'częściowo', 'warning'],
    ['interrupted', 'przerwany', 'warning'],
    ['failed', 'błąd', 'destructive'],
    ['running', 'trwa', 'info'],
    ['queued', 'w kolejce', 'neutral'],
  ] as const)('marks a %s run with the %s badge in the %s tone', async (status, text, tone) => {
    vi.mocked(fetchRuns).mockResolvedValue({ items: [run({ status })], page: 0, size: 20, total: 1 })
    const wrapper = await mountAdmin()
    const badge = wrapper.find('.run .status')
    expect(badge.text()).toBe(text)
    expect(badge.attributes('data-tone')).toBe(tone)
  })

  it('says in text which step failed and which is running', async () => {
    const step = (name: 'fetch' | 'assess', status: 'succeeded' | 'failed' | 'running') => ({
      step: name, status, startedAt: '2026-10-02T06:00:01Z', finishedAt: null, itemsIn: null, itemsOut: null, error: null,
    })
    vi.mocked(fetchRuns).mockResolvedValue({
      items: [run({ id: 'r1', status: 'failed', steps: [step('fetch', 'failed')] }), run({ id: 'r2', status: 'running', steps: [step('assess', 'running')] })],
      page: 0,
      size: 20,
      total: 2,
    })
    const wrapper = await mountAdmin()
    const [failedRun, runningRun] = wrapper.findAll('.run')
    expect(failedRun!.find('.steps').text()).toBe('pobranie (błąd)')
    expect(runningRun!.find('.steps').text()).toBe('ocena (trwa)')
  })

  it('names the failure class of the last attempt in Polish', async () => {
    vi.mocked(fetchFailedPosts).mockResolvedValue({ items: [failed('a', 'failed')], page: 0, size: 50, total: 1 })
    const wrapper = await mountAdmin()
    const attempt = wrapper.find('.failed-posts .attempt').text()
    expect(attempt).toContain('przekroczony czas')
    expect(attempt).not.toContain('timeout')
  })

  describe('failed posts: retry and restore', () => {
    /** a and c failed, b and d dead. */
    const mixed = { items: [failed('a', 'failed'), failed('b', 'dead'), failed('c', 'failed'), failed('d', 'dead')], page: 0, size: 50, total: 4 }

    function section(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
      return wrapper.find('section[aria-labelledby="failed-title"]')
    }

    function boxes(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
      return section(wrapper).findAll('.failed-posts input[type="checkbox"]')
    }

    function retryButton(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
      return section(wrapper).findAll('button').find((button) => button.text().startsWith('Ponów zaznaczone'))!
    }

    function restoreButton(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
      return section(wrapper).findAll('button').find((button) => button.text().startsWith('Przywróć martwe'))!
    }

    it('lets a dead post be checked and counts each kind of the selection in its button', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      const wrapper = await mountAdmin()
      expect(section(wrapper).find('.failed-posts').text()).toContain('martwy')
      expect(boxes(wrapper).every((box) => box.attributes('disabled') === undefined)).toBe(true)
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (0)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (0)')
      expect(retryButton(wrapper).attributes('disabled')).toBeDefined()
      expect(restoreButton(wrapper).attributes('disabled')).toBeDefined()

      await boxes(wrapper)[1]!.setValue(true)

      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (0)')
      expect(retryButton(wrapper).attributes('disabled')).toBeDefined()
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (1)')
      expect(restoreButton(wrapper).attributes('disabled')).toBeUndefined()

      await boxes(wrapper)[1]!.setValue(false)
      await boxes(wrapper)[0]!.setValue(true)
      await boxes(wrapper)[2]!.setValue(true)

      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (2)')
      expect(retryButton(wrapper).attributes('disabled')).toBeUndefined()
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (0)')
      expect(restoreButton(wrapper).attributes('disabled')).toBeDefined()
    })

    it('drops from the selection on a reload only the posts gone from the list, a dead one stays checked', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      const wrapper = await mountAdmin()
      await boxes(wrapper)[0]!.setValue(true)
      await boxes(wrapper)[1]!.setValue(true)
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (1)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (1)')

      // a left the list (assessed meanwhile); b is still dead.
      vi.mocked(fetchFailedPosts).mockResolvedValue({ ...mixed, items: mixed.items.slice(1), total: 3 })
      await wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!.trigger('click')
      await flushPromises()

      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (0)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (1)')
      expect((boxes(wrapper)[0]!.element as HTMLInputElement).checked).toBe(true)
    })

    it('retries only the checked posts that are not dead and keeps the dead ones checked', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(retryPosts).mockResolvedValue([run({ kind: 'posts' })])
      const wrapper = await mountAdmin()
      for (const box of boxes(wrapper)) await box.setValue(true)
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (2)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (2)')

      await retryButton(wrapper).trigger('click')
      await flushPromises()

      expect(retryPosts).toHaveBeenCalledWith(['a', 'c'])
      expect(restorePosts).not.toHaveBeenCalled()
      expect(section(wrapper).find('[role="status"]').text()).toContain('Ponowienie 2')
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (0)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (2)')
    })

    it('restores only the checked dead posts, says so and reads the list again without them', async () => {
      vi.mocked(fetchFailedPosts)
        .mockResolvedValueOnce(mixed)
        .mockResolvedValue({ items: [failed('a', 'failed'), failed('c', 'failed')], page: 0, size: 50, total: 2 })
      vi.mocked(restorePosts).mockResolvedValue({ restored: 2 })
      const wrapper = await mountAdmin()
      for (const box of boxes(wrapper)) await box.setValue(true)

      await restoreButton(wrapper).trigger('click')
      await flushPromises()

      expect(restorePosts).toHaveBeenCalledWith(['b', 'd'])
      expect(retryPosts).not.toHaveBeenCalled()
      expect(section(wrapper).find('[role="status"]').text()).toBe(
        'Przywrócono 2 post(ów) do oceny. Oceni je kolejny przebieg.',
      )
      expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
      expect(section(wrapper).findAll('.failed-posts li')).toHaveLength(2)
      // The posts to retry stay checked; the restored ones left the selection.
      expect(boxes(wrapper).map((box) => (box.element as HTMLInputElement).checked)).toEqual([true, true])
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (2)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (0)')
      expect(restoreButton(wrapper).attributes('disabled')).toBeDefined()
    })

    it('says none of the checked posts was dead any more', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(restorePosts).mockResolvedValue({ restored: 0 })
      const wrapper = await mountAdmin()
      await boxes(wrapper)[1]!.setValue(true)

      await restoreButton(wrapper).trigger('click')
      await flushPromises()

      expect(section(wrapper).find('[role="status"]').text()).toBe('Żaden z zaznaczonych postów nie był już martwy.')
    })

    it('says the restore failed and keeps the selection', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(restorePosts).mockRejectedValue(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin()
      await boxes(wrapper)[1]!.setValue(true)

      await restoreButton(wrapper).trigger('click')
      await flushPromises()

      expect(section(wrapper).find('[role="alert"]').text()).toBe('Nie udało się przywrócić postów.')
      expect(section(wrapper).find('[role="status"]').exists()).toBe(false)
      expect(fetchFailedPosts).toHaveBeenCalledTimes(1)
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (1)')
    })

    it('says the restore is under way and blocks the retry while the server answers', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(restorePosts).mockReturnValue(never())
      const wrapper = await mountAdmin()
      await boxes(wrapper)[0]!.setValue(true)
      await boxes(wrapper)[1]!.setValue(true)
      const restore = restoreButton(wrapper)
      const retry = retryButton(wrapper)

      await restore.trigger('click')

      expect(restore.text()).toBe('Przywracanie…')
      expect(restore.attributes('aria-busy')).toBe('true')
      expect(retry.attributes('disabled')).toBeDefined()
      await retry.trigger('click')
      expect(retryPosts).not.toHaveBeenCalled()
    })

    it('blocks the restore while a retry is under way', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(retryPosts).mockReturnValue(never())
      const wrapper = await mountAdmin()
      await boxes(wrapper)[0]!.setValue(true)
      await boxes(wrapper)[1]!.setValue(true)

      await retryButton(wrapper).trigger('click')

      expect(restoreButton(wrapper).attributes('disabled')).toBeDefined()
      await restoreButton(wrapper).trigger('click')
      expect(restorePosts).not.toHaveBeenCalled()
    })
  })

  it('shows a post on hold with its technical errors and retries it', async () => {
    vi.mocked(fetchFailedPosts).mockResolvedValue({ items: [onHold('h'), failed('b', 'dead')], page: 0, size: 50, total: 2 })
    vi.mocked(retryPosts).mockResolvedValue([run({ kind: 'posts' })])
    const wrapper = await mountAdmin()
    const [held] = wrapper.findAll('.failed-posts li')
    const attempt = held!.find('.attempt')
    expect(attempt.text()).toContain('wstrzymany')
    expect(attempt.text()).toContain('błędy techniczne 3')
    expect(attempt.text()).not.toContain('próby')
    expect(attempt.find('.technical-error').text()).toBe('IllegalStateException: a bug in the mapper')
    const box = held!.find('input[type="checkbox"]')
    expect(box.attributes('disabled')).toBeUndefined()

    await box.setValue(true)
    await wrapper.findAll('button').find((button) => button.text() === 'Ponów zaznaczone (1)')!.trigger('click')
    await flushPromises()

    expect(retryPosts).toHaveBeenCalledWith(['h'])
  })

  it('shows both reasons for a failed post with technical errors, and the whole error in its title', async () => {
    const error = `LlmUnavailableException: ${'connection reset '.repeat(20)}`
    vi.mocked(fetchFailedPosts).mockResolvedValue({
      items: [failed('a', 'failed', { technicalFailures: 1, lastTechnicalError: error })],
      page: 0,
      size: 50,
      total: 1,
    })
    const wrapper = await mountAdmin()
    const attempt = wrapper.find('.failed-posts .attempt')
    expect(attempt.text()).toContain('próby 1')
    expect(attempt.text()).toContain('przekroczony czas')
    expect(attempt.text()).toContain('błędy techniczne 1')
    expect(attempt.text()).not.toContain('wstrzymany')
    expect(attempt.find('.technical-error').attributes('title')).toBe(error)
  })

  it('says the retry is under way while the server answers', async () => {
    vi.mocked(fetchFailedPosts).mockResolvedValue({ items: [failed('a', 'failed')], page: 0, size: 50, total: 1 })
    vi.mocked(retryPosts).mockReturnValue(never())
    const wrapper = await mountAdmin()
    await wrapper.find('.failed-posts input[type="checkbox"]').setValue(true)
    const retry = wrapper.findAll('button').find((button) => button.text() === 'Ponów zaznaczone (1)')!
    await retry.trigger('click')
    expect(retry.text()).toBe('Ponawianie…')
    expect(retry.attributes('aria-busy')).toBe('true')
  })

  describe('days without data', () => {
    /** The plan's example: today 2026-10-24, Wykop has no data on 19 and 20 October. */
    const example = {
      from: '2026-10-09',
      to: '2026-10-22',
      days: [
        { day: '2026-10-19', sources: ['wykop'] },
        { day: '2026-10-20', sources: ['wykop'] },
      ],
    }

    function section(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
      return wrapper.find('section[aria-labelledby="missing-title"]')
    }

    function fillButtons(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
      return section(wrapper).findAll('button').filter((button) => button.text() === 'Wpisz do formularza')
    }

    afterEach(() => {
      vi.useRealTimers()
    })

    it('shows consecutive days as one range with its sources, and the note about just after midnight', async () => {
      vi.mocked(fetchMissingDays).mockResolvedValue(example)
      const wrapper = await mountAdmin()
      const rows = section(wrapper).findAll('.missing-range')
      expect(rows.map((row) => row.find('.missing-days').text())).toEqual(['19.10.2026 – 20.10.2026 · Wykop'])
      expect(fillButtons(wrapper)).toHaveLength(1)
      expect(fillButtons(wrapper)[0]!.attributes('aria-label')).toBe(
        'Wpisz do formularza: 19.10.2026 – 20.10.2026 · Wykop',
      )
      expect(section(wrapper).find('.hint').text()).toContain('Tuż po północy')
      expect(section(wrapper).attributes('aria-busy')).toBeUndefined()
    })

    it('puts the range into the form, checks its sources, clears the last message and focuses Od', async () => {
      vi.mocked(fetchMissingDays).mockResolvedValue(example)
      vi.mocked(startRange).mockRejectedValue(new ApiError(409, 'pipeline_busy'))
      const wrapper = await mountAdmin({ attachTo: document.body })
      await wrapper.find('input[type="checkbox"][value="wykop"]').setValue(false)
      await wrapper.find('input[type="checkbox"][value="wykop"]').setValue(true)
      await wrapper.find('form').trigger('submit')
      await flushPromises()
      expect(wrapper.find('section[aria-labelledby="start-title"] [role="alert"]').exists()).toBe(true)
      await wrapper.find('input[type="checkbox"][value="wykop"]').setValue(false)

      await fillButtons(wrapper)[0]!.trigger('click')

      const [fromField, toField] = wrapper.findAll('input[type="date"]')
      expect((fromField!.element as HTMLInputElement).value).toBe('2026-10-19')
      expect((toField!.element as HTMLInputElement).value).toBe('2026-10-20')
      expect((wrapper.find('input[type="checkbox"][value="wykop"]').element as HTMLInputElement).checked).toBe(true)
      expect(wrapper.find('section[aria-labelledby="start-title"] [role="alert"]').exists()).toBe(false)
      expect(document.activeElement).toBe(fromField!.element)
    })

    it('checks only configured sources', async () => {
      vi.mocked(fetchMissingDays).mockResolvedValue(example)
      vi.mocked(fetchSources).mockResolvedValue([{ source: 'wykop', configured: false }])
      const wrapper = await mountAdmin()
      await fillButtons(wrapper)[0]!.trigger('click')
      expect((wrapper.find('input[type="checkbox"][value="wykop"]').element as HTMLInputElement).checked).toBe(false)
    })

    it('names the checked window when no day is missing', async () => {
      const wrapper = await mountAdmin()
      const empty = section(wrapper).find('.state')
      expect(empty.find('.state-title').text()).toBe('Brak dni bez danych')
      expect(empty.find('.state-title').element.tagName).toBe('P')
      expect(empty.find('.state-text').text()).toBe('Każdy dzień od 9.10.2026 do 22.10.2026 ma dane.')
      expect(empty.find('use').attributes('href')).toBe('#i-circle-check')
      expect(section(wrapper).find('.missing-range').exists()).toBe(false)
      expect(section(wrapper).find('.hint').exists()).toBe(false)
    })

    it('says it is loading until the API answers', async () => {
      vi.mocked(fetchMissingDays).mockReturnValue(never())
      const wrapper = await mountAdmin()
      expect(section(wrapper).find('.loading-state').classes()).toContain('lines')
      expect(section(wrapper).find('.loading-state').text()).toBe('Wczytywanie…')
      expect(section(wrapper).attributes('aria-busy')).toBe('true')
      expect(section(wrapper).find('.state').exists()).toBe(false)
    })

    it('says the days failed to load', async () => {
      vi.mocked(fetchMissingDays).mockRejectedValue(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin()
      const alert = section(wrapper).find('[role="alert"]')
      expect(alert.find('.state-title').text()).toBe('Nie udało się wczytać dni bez danych')
      expect(alert.find('.state-text').text()).toBe('Pozostałe sekcje działają osobno.')
      expect(section(wrapper).attributes('aria-busy')).toBeUndefined()
      expect(section(wrapper).find('.missing-range').exists()).toBe(false)
    })

    it('disables Wpisz do formularza while the form is not there, and enables it once the sources load', async () => {
      vi.mocked(fetchMissingDays).mockResolvedValue(example)
      vi.mocked(fetchSources).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin({ attachTo: document.body })
      expect(wrapper.find('form').exists()).toBe(false)
      expect(fillButtons(wrapper).length).toBeGreaterThan(0)
      expect(fillButtons(wrapper).map((fill) => fill.attributes('disabled'))).not.toContain(undefined)

      await wrapper.find('section[aria-labelledby="start-title"] .state-error button').trigger('click')
      await flushPromises()

      expect(wrapper.find('form').exists()).toBe(true)
      expect(fillButtons(wrapper).map((fill) => fill.attributes('disabled'))).toEqual(
        fillButtons(wrapper).map(() => undefined),
      )
    })

    it('reads the days again with Odśwież', async () => {
      vi.mocked(fetchMissingDays)
        .mockResolvedValueOnce(example)
        .mockResolvedValueOnce({ ...example, days: [] })
      const wrapper = await mountAdmin()
      expect(section(wrapper).findAll('.missing-range')).toHaveLength(1)

      await wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!.trigger('click')
      await flushPromises()

      expect(fetchMissingDays).toHaveBeenCalledTimes(2)
      expect(section(wrapper).find('.missing-range').exists()).toBe(false)
      expect(section(wrapper).find('.state-title').text()).toBe('Brak dni bez danych')
    })

    it('keeps the newest answer when an older read of the days ends later', async () => {
      let resolveOlder!: (days: MissingDays) => void
      let resolveNewer!: (days: MissingDays) => void
      vi.mocked(fetchMissingDays)
        .mockResolvedValueOnce(example)
        .mockReturnValueOnce(new Promise((resolve) => (resolveOlder = resolve)))
        .mockReturnValueOnce(new Promise((resolve) => (resolveNewer = resolve)))
      const wrapper = await mountAdmin()
      const refresh = wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!

      await refresh.trigger('click')
      await refresh.trigger('click')
      resolveNewer({ ...example, days: [] })
      await flushPromises()
      resolveOlder(example)
      await flushPromises()

      expect(section(wrapper).find('.missing-range').exists()).toBe(false)
      expect(section(wrapper).find('.state-title').text()).toBe('Brak dni bez danych')
    })

    it('reads the days again once a run of the list is no longer active', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
      vi.mocked(fetchRuns)
        .mockResolvedValueOnce(page([run({ status: 'running', finishedAt: null })], 1))
        .mockResolvedValueOnce(page([run({ status: 'running', finishedAt: null, assessedOk: 30 })], 1))
        .mockResolvedValue(page([run({ status: 'succeeded' })], 1))
      await mountAdmin()
      expect(fetchMissingDays).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(fetchMissingDays).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(fetchMissingDays).toHaveBeenCalledTimes(2)
    })
  })

  describe('assessment quality', () => {
    function section(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
      return wrapper.find('section[aria-labelledby="quality-title"]')
    }

    it('shows the window and, per source, its share of failed attempts, its classes, threshold and state', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQuality)
      const wrapper = await mountAdmin()
      expect(section(wrapper).find('h2').text()).toBe('Jakość ocen (7 dni)')
      expect(section(wrapper).find('.quality-window').text()).toContain('Od 6.03.2025 do 12.03.2025')
      const rows = section(wrapper).findAll('.quality-source')
      expect(rows.map((row) => row.find('strong').text())).toEqual(['Wykop', 'Reddit'])
      expect(rows.map((row) => row.find('.status-badge').text())).toEqual(['w normie', 'ponad progiem'])
      expect(rows.map((row) => row.find('.status-badge').attributes('data-tone'))).toEqual(['success', 'warning'])
      expect(rows.map((row) => row.find('.quality-failed').text())).toEqual([
        'nieudane 5,0% z 200 prób · próg 5%',
        'nieudane 33,3% z 120 prób · próg 25%',
      ])
      expect(rows.map((row) => row.find('.quality-classes').text())).toEqual([
        'niezgodność ze schematem 2,0% · zmyślony cytat 3,0%',
        'niezgodność ze schematem 16,7% · zmyślony cytat 12,5% · naruszenie reguł kontraktu 4,2%',
      ])
      expect(section(wrapper).attributes('aria-busy')).toBeUndefined()
    })

    it('names both minimums in the window', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQuality)
      const wrapper = await mountAdmin()
      const window = section(wrapper).find('.quality-window').text()
      expect(window).toContain('próg sprawdzany od 100 prób')
      expect(window).toContain('próg sprawdzany od 500 tez')
    })

    it('shows, per source, its share of theses in another language with their number, threshold and state', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQuality)
      const wrapper = await mountAdmin()
      const rows = section(wrapper).findAll('.quality-language')
      expect(rows.map((row) => row.find('span').text())).toEqual([
        'tezy w innym języku niż post 2,0% z 500 tez · próg 2%',
        'tezy w innym języku niż post 30,1% z 501 tez · próg 30%',
      ])
      expect(rows.map((row) => row.find('.status-badge').text())).toEqual(['w normie', 'ponad progiem'])
      expect(rows.map((row) => row.find('.status-badge').attributes('data-tone'))).toEqual(['success', 'warning'])
    })

    it('shows the share below minTheses like the failed attempts, in a neutral tone, and no share without a thesis', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQualityFewAttempts)
      const wrapper = await mountAdmin()
      const rows = section(wrapper).findAll('.quality-language')
      expect(rows.map((row) => row.find('span').text())).toEqual([
        'tezy w innym języku niż post 2,2% z 46 tez · próg 2%',
        'tezy w innym języku niż post: 0 tez · próg 30%',
      ])
      expect(rows.map((row) => row.find('.status-badge').text())).toEqual(['za mało danych', 'za mało danych'])
      expect(rows.map((row) => row.find('.status-badge').attributes('data-tone'))).toEqual(['neutral', 'neutral'])
    })

    it('says the theses could not be counted, without a state, and keeps the failed attempts', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQualityLanguageUnavailable)
      const wrapper = await mountAdmin()
      const rows = section(wrapper).findAll('.quality-source')
      expect(rows.map((row) => row.find('.quality-language').text())).toEqual([
        'tezy w innym języku niż post: nie udało się policzyć',
        'tezy w innym języku niż post: nie udało się policzyć',
      ])
      expect(rows.map((row) => row.find('.quality-language').find('.status-badge').exists())).toEqual([false, false])
      expect(rows.map((row) => row.find('.quality-failed').text())).toEqual([
        'nieudane 2,4% z 123 prób · próg 5%',
        'nieudane 0,0% z 2 prób · próg 25%',
      ])
      expect(rows.map((row) => row.find('.quality-head .status-badge').text())).toEqual(['w normie', 'za mało danych'])
    })

    it('says too few attempts in a neutral tone, and shows no classes without a failed attempt', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQualityFewAttempts)
      const wrapper = await mountAdmin()
      const rows = section(wrapper).findAll('.quality-source')
      expect(rows.map((row) => row.find('.status-badge').text())).toEqual(['za mało danych', 'za mało danych'])
      expect(rows.map((row) => row.find('.status-badge').attributes('data-tone'))).toEqual(['neutral', 'neutral'])
      expect(rows[0]!.find('.quality-failed').text()).toBe('nieudane 0,0% z 46 prób · próg 5%')
      expect(rows[0]!.find('.quality-classes').exists()).toBe(false)
      expect(rows[1]!.find('.quality-failed').text()).toBe('nieudane 33,3% z 3 prób · próg 25%')
    })

    it('says there is no assessment in the window', async () => {
      const wrapper = await mountAdmin()
      const empty = section(wrapper).find('.state')
      expect(empty.find('.state-title').text()).toBe('Brak ocen w ostatnich 7 dniach')
      expect(empty.find('.state-text').text()).toBe('Miary pojawią się po ocenach z kolejnego przebiegu.')
      expect(empty.find('use').attributes('href')).toBe('#i-inbox')
      expect(section(wrapper).find('.quality-window').text()).toContain('Od 16.10.2026 do 22.10.2026')
      expect(section(wrapper).find('.quality-source').exists()).toBe(false)
    })

    it('says it is loading until the API answers', async () => {
      vi.mocked(fetchAssessmentQuality).mockReturnValue(never())
      const wrapper = await mountAdmin()
      expect(section(wrapper).find('.loading-state').classes()).toContain('lines')
      expect(section(wrapper).find('.loading-state').text()).toBe('Wczytywanie…')
      expect(section(wrapper).attributes('aria-busy')).toBe('true')
      expect(section(wrapper).find('.state').exists()).toBe(false)
    })

    it('says the quality failed to load', async () => {
      vi.mocked(fetchAssessmentQuality).mockRejectedValue(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin()
      const alert = section(wrapper).find('[role="alert"]')
      expect(alert.find('.state-title').text()).toBe('Nie udało się wczytać jakości ocen')
      expect(alert.find('.state-text').text()).toBe('Pozostałe sekcje działają osobno.')
      expect(section(wrapper).find('.quality-source').exists()).toBe(false)
    })

    it('reads the window again with Odśwież', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValueOnce(sampleAssessmentQuality)
      const wrapper = await mountAdmin()
      expect(section(wrapper).findAll('.quality-source')).toHaveLength(2)

      await wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!.trigger('click')
      await flushPromises()

      expect(fetchAssessmentQuality).toHaveBeenCalledTimes(2)
      expect(section(wrapper).find('.state-title').text()).toBe('Brak ocen w ostatnich 7 dniach')
    })
  })

  describe('run history', () => {
    afterEach(() => {
      vi.useRealTimers()
    })

    it('shows the range, the duration and who started a run', async () => {
      vi.mocked(fetchRuns).mockResolvedValue(
        page(
          [
            run({ id: 'r1', scheduled: true }),
            run({ id: 'r2', rangeFrom: '2026-09-28T22:00:00Z', rangeTo: '2026-10-01T21:59:59Z' }),
            run({ id: 'r3', kind: 'retry', rangeFrom: null, rangeTo: null, startedAt: null, finishedAt: null, status: 'queued' }),
          ],
          3,
        ),
      )
      const wrapper = await mountAdmin()
      const [cron, admin, queued] = wrapper.findAll('.run')
      expect(cron!.find('.range').text()).toBe('zakres 1.10.2026')
      expect(cron!.find('.requester').text()).toBe('uruchomił cron')
      expect(cron!.find('.duration').text()).toBe('czas 19 min 59 s')
      expect(admin!.find('.range').text()).toBe('zakres 29.09.2026 – 1.10.2026')
      expect(admin!.find('.requester').text()).toBe('uruchomił admin')
      expect(queued!.find('.range').exists()).toBe(false)
      expect(queued!.find('.duration').exists()).toBe(false)
    })

    it('marks a run that needs attention, names the cause of an interruption and links the run', async () => {
      vi.mocked(fetchRuns).mockResolvedValue(
        page(
          [
            run({
              id: 'r1',
              status: 'interrupted',
              interruptionCause: 'deploy',
              needsAttention: true,
              attentionReasons: ['interrupted'],
            }),
            run({ id: 'r2', status: 'partial' }),
          ],
          2,
        ),
      )
      const wrapper = await mountAdmin()
      const [interrupted, partial] = wrapper.findAll('.run')
      expect(interrupted!.find('.status').text()).toBe('przerwany — nowe wdrożenie')
      expect(interrupted!.find('.status').attributes('data-tone')).toBe('warning')
      expect(interrupted!.find('.attention').text()).toBe('wymaga uwagi')
      expect(interrupted!.find('.attention').attributes('data-tone')).toBe('destructive')
      const link = interrupted!.find('a.details')
      expect(link.text()).toBe('Szczegóły')
      expect(link.attributes('href')).toBe('/admin/runs/r1')
      expect(link.attributes('aria-label')).toBe('Szczegóły przebiegu z 2.10.2026, 08:00')
      expect(partial!.find('.attention').exists()).toBe(false)
    })

    it('warns about a run whose source skipped elements and counts them', async () => {
      vi.mocked(fetchRuns).mockResolvedValue(
        page(
          [
            run({ id: 'r1', skipped: 3, needsAttention: true, attentionReasons: ['skipped'] }),
            run({ id: 'r2', status: 'partial', skipped: 1, needsAttention: true, attentionReasons: ['skipped'] }),
          ],
          2,
        ),
      )
      const wrapper = await mountAdmin()
      const [succeeded, partial] = wrapper.findAll('.run')
      expect(succeeded!.find('.status').text()).toBe('zakończony — pominięte: 3')
      expect(succeeded!.find('.status').attributes('data-tone')).toBe('warning')
      expect(succeeded!.find('.attention').text()).toBe('wymaga uwagi')
      expect(succeeded!.find('.counters').text()).toContain('pominięte 3')
      expect(partial!.find('.status').text()).toBe('częściowo — pominięte: 1')
      expect(partial!.find('.status').attributes('data-tone')).toBe('warning')
      expect(partial!.find('.attention').text()).toBe('wymaga uwagi')
    })

    it('shows an interrupted step in words', async () => {
      vi.mocked(fetchRuns).mockResolvedValue(
        page(
          [
            run({
              status: 'interrupted',
              interruptionCause: 'restart',
              steps: [
                { step: 'assess', status: 'interrupted', startedAt: '2026-10-02T06:00:01Z', finishedAt: '2026-10-02T06:05:00Z', itemsIn: 41, itemsOut: null, error: null },
              ],
            }),
          ],
          1,
        ),
      )
      const wrapper = await mountAdmin()
      expect(wrapper.find('.run .steps').text()).toBe('ocena (przerwany)')
      expect(wrapper.find('.run .steps [data-tone]').attributes('data-tone')).toBe('warning')
    })

    it('reads older runs with Pokaż starsze until all are on screen', async () => {
      vi.mocked(fetchRuns)
        .mockResolvedValueOnce(page(runsFrom(1, 20), 25))
        .mockResolvedValueOnce({ items: runsFrom(21, 5), page: 1, size: 20, total: 25 })
      const wrapper = await mountAdmin({ attachTo: document.body })
      expect(fetchRuns).toHaveBeenLastCalledWith(0, RUNS_PAGE_SIZE)
      const older = wrapper.findAll('button').find((button) => button.text() === 'Pokaż starsze')!
      expect(wrapper.findAll('.run')).toHaveLength(20)

      await older.trigger('click')
      await flushPromises()

      expect(fetchRuns).toHaveBeenLastCalledWith(1, RUNS_PAGE_SIZE)
      expect(wrapper.findAll('.run').map((row) => row.find('a.details').attributes('href'))).toEqual(
        Array.from({ length: 25 }, (_, at) => `/admin/runs/r${at + 1}`),
      )
      expect(wrapper.findAll('button').some((button) => button.text() === 'Pokaż starsze')).toBe(false)
      // The button is gone: the focus goes to the first row it added.
      expect(document.activeElement).toBe(wrapper.findAll('.run')[20]!.find('a.details').element)
    })

    it('has no Pokaż starsze when every run is on screen', async () => {
      vi.mocked(fetchRuns).mockResolvedValue(page(runsFrom(1, 3), 3))
      const wrapper = await mountAdmin()
      expect(wrapper.findAll('button').some((button) => button.text() === 'Pokaż starsze')).toBe(false)
    })

    it('says older runs failed to load and keeps the list', async () => {
      vi.mocked(fetchRuns)
        .mockResolvedValueOnce(page(runsFrom(1, 20), 25))
        .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin()
      await wrapper.findAll('button').find((button) => button.text() === 'Pokaż starsze')!.trigger('click')
      await flushPromises()
      expect(wrapper.find('section[aria-labelledby="runs-title"] [role="alert"]').text()).toBe(
        'Nie udało się wczytać starszych przebiegów.',
      )
      expect(wrapper.findAll('.run')).toHaveLength(20)
      expect(wrapper.findAll('button').some((button) => button.text() === 'Pokaż starsze')).toBe(true)
    })

    it('refreshes every 10 s while a run is queued or running, and stops when none is', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
      vi.mocked(fetchRuns)
        .mockResolvedValueOnce(page([run({ status: 'running', finishedAt: null })], 1))
        .mockResolvedValueOnce(page([run({ status: 'running', finishedAt: null, assessedOk: 30 })], 1))
        .mockResolvedValue(page([run({ status: 'succeeded' })], 1))
      const wrapper = await mountAdmin()
      expect(fetchRuns).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(fetchRuns).toHaveBeenCalledTimes(2)
      expect(wrapper.find('.run .counters').text()).toContain('ocenione 30')

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(fetchRuns).toHaveBeenCalledTimes(3)
      expect(wrapper.find('.run .status').text()).toBe('zakończony')

      await vi.advanceTimersByTimeAsync(60_000)
      await flushPromises()
      expect(fetchRuns).toHaveBeenCalledTimes(3)
    })

    it('does not refresh while the section shows its error', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
      vi.mocked(fetchRuns)
        .mockResolvedValueOnce(page([run({ status: 'running', finishedAt: null })], 1))
        .mockRejectedValue(new Error('down'))
      const wrapper = await mountAdmin()

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(fetchRuns).toHaveBeenCalledTimes(2)
      expect(wrapper.find('section[aria-labelledby="runs-title"] [role="alert"]').text()).toContain(
        'Nie udało się wczytać przebiegów',
      )

      await vi.advanceTimersByTimeAsync(60_000)
      await flushPromises()
      expect(fetchRuns).toHaveBeenCalledTimes(2)
    })

    it('does not refresh when no run is queued or running', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
      vi.mocked(fetchRuns).mockResolvedValue(page([run({ status: 'partial' }), run({ id: 'r2', status: 'failed' })], 2))
      await mountAdmin()
      await vi.advanceTimersByTimeAsync(60_000)
      await flushPromises()
      expect(fetchRuns).toHaveBeenCalledTimes(1)
    })

    it('keeps the older rows on a refresh, without duplicates, when a new run shifts the pages', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
      const running = run({ id: 'r1', status: 'running', finishedAt: null, createdAt: '2026-10-02T06:00:00Z' })
      const first = [running, ...runsFrom(2, 19)]
      const newRun = run({ id: 'r0', status: 'queued', startedAt: null, finishedAt: null, createdAt: '2026-10-02T07:00:00Z' })
      vi.mocked(fetchRuns)
        .mockResolvedValueOnce(page(first, 25))
        .mockResolvedValueOnce({ items: runsFrom(21, 5), page: 1, size: 20, total: 25 })
        // Page 0 as large as the rows on screen: the new run first, so the last of the 25 falls off it.
        .mockResolvedValueOnce(page([newRun, ...first, ...runsFrom(21, 4)], 26, 25))
      const wrapper = await mountAdmin()
      await wrapper.findAll('button').find((button) => button.text() === 'Pokaż starsze')!.trigger('click')
      await flushPromises()
      expect(wrapper.findAll('.run')).toHaveLength(25)

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()

      expect(fetchRuns).toHaveBeenLastCalledWith(0, 25)
      expect(wrapper.findAll('.run').map((row) => row.find('a.details').attributes('href'))).toEqual(
        ['r0', ...Array.from({ length: 25 }, (_, at) => `r${at + 1}`)].map((id) => `/admin/runs/${id}`),
      )
      expect(wrapper.findAll('button').some((button) => button.text() === 'Pokaż starsze')).toBe(false)
    })

    it('stops refreshing when the view is left', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
      vi.mocked(fetchRuns).mockResolvedValue(page([run({ status: 'running', finishedAt: null })], 1))
      const wrapper = await mountAdmin()
      wrapper.unmount()
      await vi.advanceTimersByTimeAsync(30_000)
      await flushPromises()
      expect(fetchRuns).toHaveBeenCalledTimes(1)
    })
  })
})
