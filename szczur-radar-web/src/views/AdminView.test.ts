import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { ApiError } from '../api/client'
import { fetchNotifications } from '../api/notifications'
import { type Run, fetchMissingDays, fetchRuns, fetchSources, retryPosts, startRange } from '../api/pipeline'
import { type FailedPost, fetchFailedPosts } from '../api/posts'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import AdminView from './AdminView.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/posts', () => ({ fetchPosts: vi.fn(), fetchFailedPosts: vi.fn() }))
// The header shows an admin the notification bell.
vi.mock('../api/notifications', () => ({
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))
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
    llmRetries: 0,
    flexFallbacks: 0,
    inFlight: 0,
    error: null,
    steps: [
      { step: 'fetch', status: 'succeeded', startedAt: '2026-10-02T06:00:01Z', finishedAt: null, itemsIn: null, itemsOut: 270, error: null },
    ],
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
    vi.mocked(fetchNotifications).mockReset().mockResolvedValue({ unread: 0, items: [] })
    vi.mocked(fetchMissingDays).mockReset().mockResolvedValue({ from: '2026-10-09', to: '2026-10-22', days: [] })
    vi.mocked(startRange).mockReset()
    vi.mocked(retryPosts).mockReset()
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
    expect(wrapper.findAll('.loading').map((shown) => shown.text())).toEqual(['Wczytywanie…', 'Wczytywanie…', 'Wczytywanie…'])
    expect(wrapper.findAll('[aria-busy="true"]')).toHaveLength(3)
    expect(wrapper.find('#start-hint').exists()).toBe(false)
  })

  it.each(['empty', 'data'] as const)('shows loading while recovering from an error, then the %s response', async (response) => {
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
    expect(runsSection.find('[role="alert"]').text()).toBe('Nie udało się wczytać przebiegów.')
    expect(failedSection.find('[role="alert"]').text()).toBe('Nie udało się wczytać nieudanych postów.')

    await wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!.trigger('click')

    expect(fetchRuns).toHaveBeenCalledTimes(2)
    expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
    for (const section of [runsSection, failedSection]) {
      expect(section.find('.loading').text()).toBe('Wczytywanie…')
      expect(section.attributes('aria-busy')).toBe('true')
      expect(section.find('.empty').exists()).toBe(false)
      expect(section.find('[role="alert"]').exists()).toBe(false)
    }

    const hasItems = response === 'data'
    answerRuns({ items: hasItems ? [run()] : [], page: 0, size: 20, total: hasItems ? 1 : 0 })
    await flushPromises()

    expect(runsSection.find('.loading').exists()).toBe(false)
    expect(runsSection.attributes('aria-busy')).toBeUndefined()
    expect(runsSection.findAll('.run')).toHaveLength(hasItems ? 1 : 0)
    expect(runsSection.find('.empty').exists()).toBe(!hasItems)
    expect(failedSection.find('.loading').text()).toBe('Wczytywanie…')
    expect(failedSection.attributes('aria-busy')).toBe('true')
    expect(failedSection.find('.empty').exists()).toBe(false)

    answerFailed({ items: hasItems ? [failed('a', 'failed')] : [], page: 0, size: 50, total: hasItems ? 1 : 0 })
    await flushPromises()

    expect(failedSection.find('.loading').exists()).toBe(false)
    expect(failedSection.attributes('aria-busy')).toBeUndefined()
    expect(failedSection.findAll('.failed-posts li')).toHaveLength(hasItems ? 1 : 0)
    expect(failedSection.find('.empty').exists()).toBe(!hasItems)
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
    expect(wrapper.find('.sources [role="alert"]').text()).toBe('Nie udało się wczytać listy źródeł.')
    expect(wrapper.find('form button[type="submit"]').attributes('disabled')).toBeDefined()

    await wrapper.findAll('button').find((button) => button.text() === name)!.trigger('click')
    await flushPromises()

    expect(fetchSources).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect((wrapper.find('input[type="checkbox"][value="wykop"]').element as HTMLInputElement).checked).toBe(true)
    expect(wrapper.find('form button[type="submit"]').attributes('disabled')).toBeUndefined()
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

  it('retries the checked posts, never a dead one', async () => {
    vi.mocked(fetchFailedPosts).mockResolvedValue({
      items: [failed('a', 'failed'), failed('b', 'dead'), failed('c', 'failed')],
      page: 0,
      size: 50,
      total: 3,
    })
    vi.mocked(retryPosts).mockResolvedValue([run({ kind: 'posts' })])
    const wrapper = await mountAdmin()
    const boxes = wrapper.findAll('.failed-posts input[type="checkbox"]')
    expect(boxes[1]!.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.failed-posts').text()).toContain('martwy')
    const retry = wrapper.findAll('button').find((button) => button.text() === 'Ponów zaznaczone')!
    expect(retry.attributes('disabled')).toBeDefined()

    await boxes[0]!.setValue(true)
    await boxes[2]!.setValue(true)
    await retry.trigger('click')
    await flushPromises()

    expect(retryPosts).toHaveBeenCalledWith(['a', 'c'])
    expect(wrapper.find('[role="status"]').text()).toContain('Ponowienie 2')
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
    await wrapper.findAll('button').find((button) => button.text() === 'Ponów zaznaczone')!.trigger('click')
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
    const retry = wrapper.findAll('button').find((button) => button.text() === 'Ponów zaznaczone')!
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
      expect(section(wrapper).find('.empty').text()).toBe('Brak dni bez danych od 9.10.2026 do 22.10.2026.')
      expect(section(wrapper).find('.missing-range').exists()).toBe(false)
      expect(section(wrapper).find('.hint').exists()).toBe(false)
    })

    it('says it is loading until the API answers', async () => {
      vi.mocked(fetchMissingDays).mockReturnValue(never())
      const wrapper = await mountAdmin()
      expect(section(wrapper).find('.loading').text()).toBe('Wczytywanie…')
      expect(section(wrapper).attributes('aria-busy')).toBe('true')
      expect(section(wrapper).find('.empty').exists()).toBe(false)
    })

    it('says the days failed to load', async () => {
      vi.mocked(fetchMissingDays).mockRejectedValue(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin()
      expect(section(wrapper).find('[role="alert"]').text()).toBe('Nie udało się wczytać dni bez danych.')
      expect(section(wrapper).find('.empty').exists()).toBe(false)
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
      expect(section(wrapper).find('.empty').text()).toBe('Brak dni bez danych od 9.10.2026 do 22.10.2026.')
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
            run({ id: 'r1', status: 'interrupted', interruptionCause: 'deploy', needsAttention: true }),
            run({ id: 'r2', status: 'partial' }),
          ],
          2,
        ),
      )
      const wrapper = await mountAdmin()
      const [interrupted, partial] = wrapper.findAll('.run')
      expect(interrupted!.find('.status').text()).toBe('przerwany — nowe wdrożenie')
      expect(interrupted!.find('.status').attributes('data-tone')).toBe('neutral')
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
        page([run({ id: 'r1', skipped: 3, needsAttention: true }), run({ id: 'r2', status: 'partial', skipped: 1, needsAttention: true })], 2),
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
      expect(fetchRuns).toHaveBeenLastCalledWith(0, 20)
      const older = wrapper.findAll('button').find((button) => button.text() === 'Pokaż starsze')!
      expect(wrapper.findAll('.run')).toHaveLength(20)

      await older.trigger('click')
      await flushPromises()

      expect(fetchRuns).toHaveBeenLastCalledWith(1, 20)
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
