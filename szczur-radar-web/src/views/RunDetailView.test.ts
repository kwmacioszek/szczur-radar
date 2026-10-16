import { type VueWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { ApiError } from '../api/client'
import { fetchNotifications } from '../api/notifications'
import { type Run, type RunStep, fetchRun } from '../api/pipeline'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import RunDetailView from './RunDetailView.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/pipeline', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/pipeline')>()),
  fetchRun: vi.fn(),
}))
// The header shows an admin the notification bell.
vi.mock('../api/notifications', () => ({
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const MINUTE = 60 * 1000

function step(name: RunStep['step'], overrides: Partial<RunStep> = {}): RunStep {
  return {
    step: name,
    status: 'succeeded',
    startedAt: '2026-10-13T00:30:05Z',
    finishedAt: '2026-10-13T00:30:25Z',
    itemsIn: 270,
    itemsOut: 41,
    error: null,
    ...overrides,
  }
}

function run(overrides: Partial<Run> = {}): Run {
  return {
    id: 'r1',
    kind: 'range',
    status: 'succeeded',
    interruptionCause: null,
    needsAttention: false,
    scheduled: true,
    rangeFrom: '2026-10-10T22:00:00Z',
    rangeTo: '2026-10-12T21:59:59Z',
    sources: ['wykop'],
    requestedPostIds: null,
    createdAt: '2026-10-13T00:30:00Z',
    startedAt: '2026-10-13T00:30:01Z',
    finishedAt: '2026-10-13T00:43:51Z',
    lastActivityAt: '2026-10-13T00:43:51Z',
    fetched: 248,
    newPosts: 213,
    assessedOk: 209,
    assessedFailed: 4,
    processingErrors: 0,
    dead: 0,
    skipped: 0,
    llmRetries: 6,
    flexFallbacks: 2,
    inFlight: 0,
    error: null,
    steps: [step('fetch', { itemsIn: null, itemsOut: 248 }), step('assess', { itemsIn: 213, itemsOut: 209 })],
    ...overrides,
  }
}

/** A run assessing: 19 of 41 posts done, 4 in flight, last change 2 minutes ago. */
function running(overrides: Partial<Run> = {}): Run {
  return run({
    status: 'running',
    finishedAt: null,
    lastActivityAt: new Date(Date.now() - 2 * MINUTE - 5000).toISOString(),
    assessedOk: 17,
    assessedFailed: 2,
    dead: 1,
    inFlight: 4,
    llmRetries: 3,
    flexFallbacks: 1,
    steps: [
      step('fetch', { itemsIn: null, itemsOut: 270 }),
      step('store', { itemsOut: 41 }),
      step('select', { itemsIn: 41, itemsOut: 41 }),
      step('assess', { status: 'running', finishedAt: null, itemsIn: 41, itemsOut: null }),
    ],
    ...overrides,
  })
}

function never<T>(): Promise<T> {
  return new Promise(() => {})
}

/** `attachTo`: in the document, for focus. */
async function mountRun(path = '/admin/runs/r1', { attachTo }: { attachTo?: HTMLElement } = {}) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const wrapper = mount(RunDetailView, { global: { plugins: [router] }, attachTo })
  await flushPromises()
  return { wrapper, router }
}

/** The page without its header: the header's notification bell has a list (and states) of its own. */
function page(wrapper: VueWrapper) {
  return wrapper.find('main')
}

function button(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!found) throw new Error(`no button "${text}"`)
  return found
}

enableAutoUnmount(afterEach)

describe('RunDetailView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    vi.mocked(fetchRun).mockReset()
    vi.mocked(fetchNotifications).mockReset().mockResolvedValue({ unread: 0, items: [] })
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
    vi.useRealTimers()
  })

  it('says it is loading the run', async () => {
    vi.mocked(fetchRun).mockReturnValue(never())
    const { wrapper } = await mountRun()
    expect(fetchRun).toHaveBeenCalledWith('r1')
    expect(page(wrapper).find('.loading').text()).toBe('Wczytywanie przebiegu…')
    expect(wrapper.find('section[aria-labelledby="run-title"]').attributes('aria-busy')).toBe('true')
    expect(wrapper.find('h1').text()).toBe('Przebieg')
  })

  it.each([
    [404, 'not_found'],
    [400, 'invalid_request'],
  ])('says there is no such run on %i, with a link to the panel', async (status, detail) => {
    vi.mocked(fetchRun).mockRejectedValue(new ApiError(status, detail))
    const { wrapper } = await mountRun('/admin/runs/x')
    const message = page(wrapper).find('.empty')
    expect(message.text()).toContain('Nie ma takiego przebiegu.')
    expect(message.find('a').attributes('href')).toBe('/admin')
    expect(page(wrapper).find('[role="alert"]').exists()).toBe(false)
  })

  it('offers a retry after an error and shows the run when it succeeds', async () => {
    vi.mocked(fetchRun).mockRejectedValueOnce(new ApiError(500, 'internal_error')).mockResolvedValueOnce(run())
    const { wrapper } = await mountRun()
    expect(page(wrapper).find('[role="alert"]').text()).toBe('Nie udało się wczytać przebiegu.')
    expect(page(wrapper).find('.empty').exists()).toBe(false)

    await button(wrapper, 'Spróbuj ponownie').trigger('click')
    await flushPromises()

    expect(fetchRun).toHaveBeenCalledTimes(2)
    expect(page(wrapper).find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('h1').text()).toBe('Przebieg: zakres dat')
  })

  it('links back to the panel and focuses the heading once the run is loaded', async () => {
    vi.mocked(fetchRun).mockResolvedValue(run())
    const { wrapper } = await mountRun('/admin/runs/r1', { attachTo: document.body })
    const back = wrapper.find('a.back')
    expect(back.text()).toBe('← Panel admina')
    expect(back.attributes('href')).toBe('/admin')
    expect(document.activeElement).toBe(wrapper.find('h1').element)
  })

  it('shows who started the run, its range, times, counters and steps', async () => {
    vi.mocked(fetchRun).mockResolvedValue(run())
    const { wrapper } = await mountRun()
    expect(wrapper.find('.status').text()).toBe('zakończony')
    expect(wrapper.find('.status').attributes('data-tone')).toBe('success')
    expect(wrapper.find('.requester').text()).toBe('cron')
    expect(wrapper.find('.range').text()).toBe('11.10.2026 – 12.10.2026')
    expect(wrapper.find('.duration').text()).toBe('13 min 50 s')
    expect(wrapper.find('.meta').text()).toContain('Wykop')
    expect(wrapper.find('.meta').text()).toContain('13.10.2026, 02:43')
    expect(wrapper.find('.progress').exists()).toBe(false)
    expect(wrapper.find('.attention').exists()).toBe(false)
    const counters = wrapper.find('.counters').text()
    for (const figure of ['pobrane 248', 'nowe 213', 'ocenione 209', 'nieudane 4', 'ponowienia LLM 6', 'powroty z flex 2']) {
      expect(counters).toContain(figure)
    }
    const [fetchStep, assessStep] = wrapper.findAll('.step')
    expect(fetchStep!.find('.step-head strong').text()).toBe('pobranie')
    expect(fetchStep!.find('.step-head [data-tone]').text()).toBe('zakończony')
    expect(fetchStep!.find('.step-meta').text()).toBe('start 13.10.2026, 02:30 · czas 20 s · wejście — · wyjście 248')
    expect(assessStep!.find('.step-meta').text()).toContain('wejście 213 · wyjście 209')
  })

  it('says an admin started a run without a range', async () => {
    vi.mocked(fetchRun).mockResolvedValue(
      run({ kind: 'posts', scheduled: false, rangeFrom: null, rangeTo: null, sources: [], requestedPostIds: ['p1'] }),
    )
    const { wrapper } = await mountRun()
    expect(wrapper.find('h1').text()).toBe('Przebieg: wybrane posty')
    expect(wrapper.find('.requester').text()).toBe('admin')
    expect(wrapper.find('.range').exists()).toBe(false)
  })

  it('shows the progress of a running run', async () => {
    vi.mocked(fetchRun).mockResolvedValue(running())
    const { wrapper } = await mountRun()
    const progress = wrapper.find('.progress')
    expect(progress.find('.current-step').text()).toBe('Krok: ocena')
    const bar = progress.find('progress')
    expect(bar.attributes('value')).toBe('19')
    expect(bar.attributes('max')).toBe('41')
    expect(progress.find(`label[for="${bar.attributes('id')}"]`).text()).toBe('Ocena: 19 z 41')
    const figures = progress.find('.figures').text()
    for (const figure of ['w toku 4', 'ok 17', 'nieudane 2', 'martwe 1', 'błędy techniczne 0', 'ponowienia LLM 3', 'powroty z flex 1']) {
      expect(figures).toContain(figure)
    }
    expect(progress.find('.activity').text()).toBe('Ostatnia aktywność: 2 min temu')
    expect(wrapper.find('.status').text()).toBe('trwa')
    expect(wrapper.find('.step[data-status="running"] .step-meta').text()).toContain('wyjście —')
  })

  it('shows a queued run waiting, without a bar', async () => {
    vi.mocked(fetchRun).mockResolvedValue(
      run({ status: 'queued', startedAt: null, finishedAt: null, lastActivityAt: null, steps: [] }),
    )
    const { wrapper } = await mountRun()
    expect(wrapper.find('.current-step').text()).toBe('Krok: w kolejce')
    expect(wrapper.find('progress').exists()).toBe(false)
    expect(wrapper.find('.duration').text()).toBe('—')
    expect(wrapper.find('.steps').exists()).toBe(false)
    expect(page(wrapper).find('.empty').text()).toBe('Żaden krok się jeszcze nie zaczął.')
  })

  describe('why it needs attention', () => {
    it('gives a failed run’s error as it is', async () => {
      vi.mocked(fetchRun).mockResolvedValue(
        run({ status: 'failed', needsAttention: true, error: 'no progress for 39m 00s: the run was stopped' }),
      )
      const { wrapper } = await mountRun()
      const attention = wrapper.find('.attention')
      expect(attention.attributes('role')).toBe('alert')
      expect(attention.text()).toBe('Wymaga uwagi: no progress for 39m 00s: the run was stopped')
      expect(wrapper.find('.run-error').text()).toBe('no progress for 39m 00s: the run was stopped')
    })

    it('names the cause of an interruption', async () => {
      vi.mocked(fetchRun).mockResolvedValue(
        running({
          status: 'interrupted',
          interruptionCause: 'deploy',
          needsAttention: true,
          inFlight: 0,
          finishedAt: '2026-10-13T00:40:00Z',
          error: 'interrupted: a new deployment took over',
          steps: [step('fetch'), step('assess', { status: 'interrupted', finishedAt: '2026-10-13T00:40:00Z', itemsIn: 41, itemsOut: null })],
        }),
      )
      const { wrapper } = await mountRun()
      expect(wrapper.find('.status').text()).toBe('przerwany — nowe wdrożenie')
      expect(wrapper.find('.status').attributes('data-tone')).toBe('neutral')
      expect(wrapper.find('.attention').text()).toBe('Wymaga uwagi: przerwany: nowe wdrożenie')
      const interrupted = wrapper.find('.step[data-status="interrupted"] .step-head')
      expect(interrupted.find('strong').text()).toBe('ocena')
      expect(interrupted.find('[data-tone]').text()).toBe('przerwany')
      expect(interrupted.find('[data-tone]').attributes('data-tone')).toBe('neutral')
      expect(wrapper.find('.progress').exists()).toBe(false)
    })

    it('counts the dead posts and technical errors of a partial run', async () => {
      vi.mocked(fetchRun).mockResolvedValue(run({ status: 'partial', needsAttention: true, dead: 1, processingErrors: 2 }))
      const { wrapper } = await mountRun()
      expect(wrapper.find('.attention').text()).toBe('Wymaga uwagi: 1 post martwy; błędy techniczne: 2')
    })
  })

  describe('reading again', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
    })

    it('reads a running run every 10 s and stops once it has ended', async () => {
      vi.mocked(fetchRun)
        .mockResolvedValueOnce(running())
        .mockResolvedValueOnce(running({ assessedOk: 30 }))
        .mockResolvedValue(run({ status: 'partial', assessedOk: 38, assessedFailed: 3 }))
      const { wrapper } = await mountRun()
      expect(fetchRun).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(fetchRun).toHaveBeenCalledTimes(2)
      expect(wrapper.find('progress').attributes('value')).toBe('32')

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(fetchRun).toHaveBeenCalledTimes(3)
      expect(wrapper.find('.status').text()).toBe('częściowo')
      expect(wrapper.find('.progress').exists()).toBe(false)

      await vi.advanceTimersByTimeAsync(60_000)
      await flushPromises()
      expect(fetchRun).toHaveBeenCalledTimes(3)
    })

    it('does not read an ended run again', async () => {
      vi.mocked(fetchRun).mockResolvedValue(run())
      await mountRun()
      await vi.advanceTimersByTimeAsync(60_000)
      expect(fetchRun).toHaveBeenCalledTimes(1)
    })

    it('keeps the run on screen when a refresh fails, and says so', async () => {
      vi.mocked(fetchRun)
        .mockResolvedValueOnce(running())
        .mockRejectedValueOnce(new ApiError(503, null))
        .mockResolvedValue(running({ assessedOk: 20 }))
      const { wrapper } = await mountRun()

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(wrapper.find('.refresh-error').text()).toBe('Nie udało się odświeżyć przebiegu. Kolejna próba za 10 s.')
      expect(wrapper.find('progress').attributes('value')).toBe('19')

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()
      expect(wrapper.find('.refresh-error').exists()).toBe(false)
      expect(wrapper.find('progress').attributes('value')).toBe('22')
    })

    it('stops when the view is left', async () => {
      vi.mocked(fetchRun).mockResolvedValue(running())
      const { wrapper } = await mountRun()
      wrapper.unmount()
      await vi.advanceTimersByTimeAsync(60_000)
      expect(fetchRun).toHaveBeenCalledTimes(1)
    })
  })

  it('loads another run when the id changes and ignores the late answer of the previous one', async () => {
    const answers = new Map<string, (value: Run) => void>()
    vi.mocked(fetchRun).mockImplementation((id) => new Promise((resolve) => answers.set(id, resolve)))
    const { wrapper, router } = await mountRun()

    await router.push('/admin/runs/r2')
    await flushPromises()
    answers.get('r2')!(run({ id: 'r2', kind: 'retry' }))
    await flushPromises()
    answers.get('r1')!(run())
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Przebieg: ponowienie resztek')
  })
})
