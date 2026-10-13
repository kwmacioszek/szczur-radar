import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { ApiError } from '../api/client'
import { type Run, fetchRuns, fetchSources, retryPosts, startRange } from '../api/pipeline'
import { type FailedPost, fetchFailedPosts } from '../api/posts'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import AdminView from './AdminView.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/posts', () => ({ fetchPosts: vi.fn(), fetchFailedPosts: vi.fn() }))
vi.mock('../api/pipeline', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/pipeline')>()),
  startRange: vi.fn(),
  retryPosts: vi.fn(),
  fetchRuns: vi.fn(),
  fetchSources: vi.fn(),
}))

function run(overrides: Partial<Run> = {}): Run {
  return {
    id: 'r1',
    kind: 'range',
    status: 'succeeded',
    rangeFrom: '2026-09-30T22:00:00Z',
    rangeTo: '2026-10-01T21:59:59Z',
    sources: ['wykop'],
    requestedPostIds: null,
    createdAt: '2026-10-02T06:00:00Z',
    startedAt: '2026-10-02T06:00:01Z',
    finishedAt: '2026-10-02T06:20:00Z',
    fetched: 270,
    newPosts: 268,
    assessedOk: 260,
    assessedFailed: 8,
    processingErrors: 0,
    dead: 0,
    skipped: 2,
    error: null,
    steps: [
      { step: 'fetch', status: 'succeeded', startedAt: '2026-10-02T06:00:01Z', finishedAt: null, itemsIn: null, itemsOut: 270, error: null },
    ],
    ...overrides,
  }
}

function failed(id: string, stage: FailedPost['stage']): FailedPost {
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
  }
}

async function mountAdmin() {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/admin')
  const wrapper = mount(AdminView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

function never<T>(): Promise<T> {
  return new Promise(() => {})
}

describe('AdminView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    vi.mocked(fetchSources).mockReset().mockResolvedValue([{ source: 'wykop', configured: true }])
    vi.mocked(fetchRuns).mockReset().mockResolvedValue({ items: [], page: 0, size: 20, total: 0 })
    vi.mocked(fetchFailedPosts).mockReset().mockResolvedValue({ items: [], page: 0, size: 50, total: 0 })
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
    wrapper.unmount()
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
})
