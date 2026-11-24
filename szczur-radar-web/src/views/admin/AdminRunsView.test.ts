import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchMe } from '../../api/auth'
import { ApiError } from '../../api/client'
import { type MissingDays, RUNS_PAGE_SIZE, type Run, fetchMissingDays, fetchRuns, fetchSources, retryPosts, startRange } from '../../api/pipeline'
import { fetchFailedPosts, restorePosts } from '../../api/posts'
import { fetchAssessmentQuality } from '../../api/quality'
import { resetSession } from '../../auth/session'
import AdminRunsView from './AdminRunsView.vue'
import { mountPart, never, page, run, runsFrom } from './testing'

vi.mock('../../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../../api/posts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/posts')>()),
  fetchPosts: vi.fn(),
  fetchFailedPosts: vi.fn(),
  restorePosts: vi.fn(),
}))
vi.mock('../../api/quality', () => ({ fetchAssessmentQuality: vi.fn() }))
vi.mock('../../api/pipeline', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/pipeline')>()),
  startRange: vi.fn(),
  retryPosts: vi.fn(),
  fetchRuns: vi.fn(),
  fetchSources: vi.fn(),
  fetchMissingDays: vi.fn(),
}))

/** The part "Przebiegi" on /admin. `attachTo`: in the document, for focus. */
async function mountAdmin({ attachTo }: { attachTo?: HTMLElement } = {}) {
  return (await mountPart(AdminRunsView, '/admin', { attachTo })).wrapper
}

function refreshButton(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
  return wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!
}

/** The plan's example: today 2026-10-24, Wykop has no data on 19 and 20 October. */
const MISSING_EXAMPLE: MissingDays = {
  from: '2026-10-09',
  to: '2026-10-22',
  days: [
    { day: '2026-10-19', sources: ['wykop'] },
    { day: '2026-10-20', sources: ['wykop'] },
  ],
}

enableAutoUnmount(afterEach)

describe('AdminRunsView', () => {
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

  it('puts the started run at the top of the table, labels the fields and leads Uruchom with its icon', async () => {
    const started = run({ id: 'new', status: 'queued', startedAt: null, finishedAt: null, createdAt: '2026-10-02T07:00:00Z' })
    vi.mocked(fetchRuns)
      .mockResolvedValueOnce(page([run({ id: 'old' })], 1))
      .mockResolvedValue(page([started, run({ id: 'old' })], 2))
    vi.mocked(startRange).mockResolvedValue([started])
    const wrapper = await mountAdmin()
    const [fromField, toField] = wrapper.findAll('input[type="date"]')
    expect(wrapper.find(`label[for="${fromField!.attributes('id')}"]`).text()).toBe('Od')
    expect(wrapper.find(`label[for="${toField!.attributes('id')}"]`).text()).toBe('Do')
    expect(wrapper.find('form legend').text()).toBe('Źródła')
    expect(wrapper.find('form button[type="submit"] use').attributes('href')).toBe('#i-play')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const rows = wrapper.findAll('.run')
    expect(rows.map((row) => row.find('a.details').attributes('href'))).toEqual(['/admin/runs/new', '/admin/runs/old'])
    expect(rows[0]!.find('.status').text()).toBe('w kolejce')
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
    const wrapper = await mountAdmin()
    expect(wrapper.text()).not.toContain('Jeszcze nie było przebiegu.')
    const skeletons = wrapper.findAll('.loading-state')
    expect(skeletons.map((shown) => shown.classes()[1])).toEqual(['form', 'rows'])
    expect(skeletons.map((shown) => shown.text())).toEqual(['Wczytywanie…', 'Wczytywanie…'])
    expect(wrapper.findAll('section[aria-busy="true"]')).toHaveLength(2)
    expect(wrapper.find('#start-hint').exists()).toBe(false)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(refreshButton(wrapper).attributes('disabled')).toBeDefined()
  })

  it('has every section loading at once, each marked busy, and Odśwież disabled', async () => {
    vi.mocked(fetchSources).mockReturnValue(never())
    vi.mocked(fetchMissingDays).mockReturnValue(never())
    vi.mocked(fetchRuns).mockReturnValue(never())
    const wrapper = await mountAdmin()
    const sections = wrapper.findAll('section')
    expect(sections).toHaveLength(3)
    expect(sections.map((shown) => shown.attributes('aria-busy'))).toEqual(['true', 'true', 'true'])
    expect(sections.map((shown) => shown.find('.loading-state').classes()[1])).toEqual(['form', 'lines', 'rows'])
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
    let answerMissing: (value: MissingDays) => void = () => {}
    vi.mocked(fetchRuns)
      .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      .mockReturnValueOnce(new Promise((resolve) => (answerRuns = resolve)))
    vi.mocked(fetchMissingDays)
      .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      .mockReturnValueOnce(new Promise((resolve) => (answerMissing = resolve)))
    const wrapper = await mountAdmin()
    const runsSection = wrapper.find('section[aria-labelledby="runs-title"]')
    const missingSection = wrapper.find('section[aria-labelledby="missing-title"]')
    expect(runsSection.find('[role="alert"] .state-title').text()).toBe('Nie udało się wczytać przebiegów')
    expect(runsSection.find('[role="alert"] .state-text').text()).toBe('Pozostałe sekcje działają osobno.')
    expect(missingSection.find('[role="alert"] .state-title').text()).toBe('Nie udało się wczytać dni bez danych')
    expect(refreshButton(wrapper).attributes('disabled')).toBeUndefined()

    await refreshButton(wrapper).trigger('click')

    expect(fetchRuns).toHaveBeenCalledTimes(2)
    expect(fetchMissingDays).toHaveBeenCalledTimes(2)
    for (const section of [runsSection, missingSection]) {
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
    expect(missingSection.find('.state-error button').attributes('aria-busy')).toBe('true')

    answerMissing(hasItems ? MISSING_EXAMPLE : { ...MISSING_EXAMPLE, days: [] })
    await flushPromises()

    expect(missingSection.find('.state-error').exists()).toBe(false)
    expect(missingSection.findAll('.missing-range')).toHaveLength(hasItems ? 1 : 0)
    expect(missingSection.find('.state-title').exists()).toBe(!hasItems)
    expect(missingSection.attributes('aria-busy')).toBeUndefined()
  })

  it('shows an empty list of runs as the shared empty state', async () => {
    const wrapper = await mountAdmin()
    const runsEmpty = wrapper.find('section[aria-labelledby="runs-title"] .state')
    expect(runsEmpty.find('.state-title').text()).toBe('Jeszcze nie było przebiegu')
    expect(runsEmpty.find('.state-title').element.tagName).toBe('P')
    expect(runsEmpty.find('.state-text').text()).toBe('Pierwszy uruchomisz w sekcji „Nowy przebieg”.')
    expect(runsEmpty.find('use').attributes('href')).toBe('#i-inbox')
    expect(wrapper.find('section[aria-busy="true"]').exists()).toBe(false)
  })

  it('keeps the runs on screen while refreshing', async () => {
    vi.mocked(fetchRuns).mockResolvedValueOnce({ items: [run()], page: 0, size: 20, total: 1 }).mockReturnValue(never())
    const wrapper = await mountAdmin()
    await wrapper.findAll('button').find((button) => button.text() === 'Odśwież')!.trigger('click')
    expect(fetchRuns).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('.run')).toHaveLength(1)
    expect(wrapper.find('.loading').exists()).toBe(false)
  })

  it('reads with Odśwież only the sections of this part, not the assessment quality nor the failed posts', async () => {
    const wrapper = await mountAdmin()
    await refreshButton(wrapper).trigger('click')
    await flushPromises()
    expect(fetchRuns).toHaveBeenCalledTimes(2)
    expect(fetchMissingDays).toHaveBeenCalledTimes(2)
    expect(fetchAssessmentQuality).not.toHaveBeenCalled()
    expect(fetchFailedPosts).not.toHaveBeenCalled()
  })

  describe('the focus on the panel title', () => {
    it('is asked for once, after the first section is on screen', async () => {
      vi.mocked(fetchSources).mockReturnValue(never())
      vi.mocked(fetchRuns).mockReturnValue(never())
      let answer: (value: MissingDays) => void = () => {}
      vi.mocked(fetchMissingDays).mockReturnValue(new Promise((resolve) => (answer = resolve)))
      const { panel } = await mountPart(AdminRunsView, '/admin')
      expect(panel.focusTitle).not.toHaveBeenCalled()

      answer(MISSING_EXAMPLE)
      await flushPromises()

      expect(panel.focusTitle).toHaveBeenCalledTimes(1)
    })

    it('is not asked for again by the other sections, a refresh or a retry', async () => {
      vi.mocked(fetchMissingDays).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const { wrapper, panel } = await mountPart(AdminRunsView, '/admin')
      expect(panel.focusTitle).toHaveBeenCalledTimes(1)

      await wrapper.find('section[aria-labelledby="missing-title"] .state-error button').trigger('click')
      await refreshButton(wrapper).trigger('click')
      await flushPromises()

      expect(panel.focusTitle).toHaveBeenCalledTimes(1)
    })
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

  // Before S-50 each run was an item of a list with its counters, steps and error; the table of the design shows the
  // run's row (the counters, steps and error are on the run's page).
  it('shows the runs as a table in the order of the design, each run a row headed by its link', async () => {
    vi.mocked(fetchRuns).mockResolvedValue(page([run({ assessedOk: 12345 })], 1))
    const wrapper = await mountAdmin()
    const frame = wrapper.find('section[aria-labelledby="runs-title"] .data-table')
    expect(frame.find('caption').text()).toBe('Ostatnie przebiegi, od najnowszego')
    expect(frame.findAll('thead th').map((cell) => [cell.text(), cell.attributes('scope'), cell.classes('num')])).toEqual([
      ['Przebieg', 'col', false],
      ['Rodzaj', 'col', false],
      ['Stan', 'col', false],
      ['Zakres', 'col', false],
      ['Ocenione', 'col', true],
      ['Nieudane', 'col', true],
      ['Martwe', 'col', true],
      ['Czas', 'col', true],
    ])
    const row = frame.find('tbody tr.run')
    const head = row.find('th')
    expect(head.attributes('scope')).toBe('row')
    expect(head.find('a.details').text()).toBe('2.10.2026, 08:00')
    expect(head.find('a.details').attributes('aria-label')).toBe('Szczegóły przebiegu z 2.10.2026, 08:00')
    expect(row.findAll('td').map((cell) => cell.text())).toEqual([
      'zakres dat',
      'zakończony',
      '1.10.2026',
      '12\u00a0345',
      '8',
      '0',
      '19 min 59 s',
    ])
  })

  it('mutes a count of 0 and gives failed and dead posts above 0 the destructive tone', async () => {
    vi.mocked(fetchRuns).mockResolvedValue(
      page([run({ id: 'bad', assessedOk: 0, assessedFailed: 4, dead: 2 }), run({ id: 'good', assessedOk: 165, assessedFailed: 0, dead: 0 })], 2),
    )
    const wrapper = await mountAdmin()
    const [bad, good] = wrapper.findAll('.run')
    const cells = (row: typeof bad) => ['.assessed', '.failed', '.dead'].map((cell) => row!.find(cell).classes())
    expect(cells(bad)).toEqual([
      ['num', 'assessed', 'zero'],
      ['num', 'failed', 'down'],
      ['num', 'dead', 'down'],
    ])
    expect(cells(good)).toEqual([
      ['num', 'assessed'],
      ['num', 'failed', 'zero'],
      ['num', 'dead', 'zero'],
    ])
  })

  it('leaves the range and the time empty for a run without them', async () => {
    vi.mocked(fetchRuns).mockResolvedValue(
      page([run({ kind: 'retry', rangeFrom: null, rangeTo: null, status: 'queued', startedAt: null, finishedAt: null })], 1),
    )
    const wrapper = await mountAdmin()
    const row = wrapper.find('.run')
    expect(row.find('.kind').text()).toBe('ponowienie resztek')
    expect(row.find('.range').text()).toBe('')
    expect(row.find('.duration').text()).toBe('')
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

  describe('a section retries on its own', () => {
    const SECTIONS = [
      ['start-title', 'Nowy przebieg', 'sources'],
      ['missing-title', 'Dni bez danych', 'missing'],
      ['runs-title', 'Ostatnie przebiegi', 'runs'],
    ] as const

    /** Every read of the panel, also those of the other parts: none of them may follow a retry here. */
    function reads() {
      return { sources: fetchSources, missing: fetchMissingDays, quality: fetchAssessmentQuality, runs: fetchRuns, failed: fetchFailedPosts }
    }

    afterEach(() => {
      vi.useRealTimers()
    })

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

    // The quality section, which these cases used before S-50, is in its own part now, without "Odśwież": the days
    // without data are this part's section that "Odśwież" reads besides the runs.
    it('leaves the focus on Odśwież when it supersedes a retry', async () => {
      let answer: (value: MissingDays) => void = () => {}
      vi.mocked(fetchMissingDays)
        .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
        .mockReturnValueOnce(new Promise(() => {}))
        .mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
      const wrapper = await mountAdmin({ attachTo: document.body })
      await wrapper.find('section[aria-labelledby="missing-title"] .state-error button').trigger('click')
      const refresh = refreshButton(wrapper)
      ;(refresh.element as HTMLElement).focus()

      await refresh.trigger('click')
      answer({ from: '2026-10-09', to: '2026-10-22', days: [] })
      await flushPromises()

      expect(wrapper.find('section[aria-labelledby="missing-title"] .state-error').exists()).toBe(false)
      expect(document.activeElement).toBe(refresh.element)
    })

    it('leaves the focus on Odśwież when it retries a section in error', async () => {
      vi.mocked(fetchMissingDays).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin({ attachTo: document.body })
      const refresh = refreshButton(wrapper)
      ;(refresh.element as HTMLElement).focus()

      await refresh.trigger('click')
      await flushPromises()

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(document.activeElement).toBe(refresh.element)
    })

    it('keeps the error busy until the answer when the retry comes from Odśwież, and leaves the focus', async () => {
      let answer: (value: MissingDays) => void = () => {}
      vi.mocked(fetchMissingDays)
        .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
        .mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
      const wrapper = await mountAdmin({ attachTo: document.body })
      const refresh = refreshButton(wrapper)
      ;(refresh.element as HTMLElement).focus()

      await refresh.trigger('click')

      const error = wrapper.find('section[aria-labelledby="missing-title"] .state-error')
      expect(error.attributes('role')).toBeUndefined()
      expect(error.find('button').attributes('aria-busy')).toBe('true')

      answer({ from: '2026-10-09', to: '2026-10-22', days: [] })
      await flushPromises()

      expect(wrapper.find('section[aria-labelledby="missing-title"] .state-error').exists()).toBe(false)
      expect(document.activeElement).toBe(refresh.element)
    })

    it('leaves the focus when another failure follows a retry from Odśwież', async () => {
      vi.mocked(fetchMissingDays).mockRejectedValue(new ApiError(500, 'internal_error'))
      const wrapper = await mountAdmin({ attachTo: document.body })
      const refresh = refreshButton(wrapper)
      ;(refresh.element as HTMLElement).focus()

      await refresh.trigger('click')
      await flushPromises()

      expect(wrapper.find('section[aria-labelledby="missing-title"] [role="alert"]').exists()).toBe(true)
      expect(document.activeElement).toBe(refresh.element)
    })
  })

  describe('days without data', () => {
    const example = MISSING_EXAMPLE

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
      expect(rows.map((row) => [row.find('.missing-days').text(), row.find('.missing-sources').text()])).toEqual([
        ['19.10.2026 – 20.10.2026', 'Wykop'],
      ])
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
    // Before S-50 the assessment quality stood on the same page and was read again too; its part reads it on entry.
    it('does not read the assessment quality when a run of the list ends', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
      vi.mocked(fetchRuns)
        .mockResolvedValueOnce(page([run({ status: 'running', finishedAt: null })], 1))
        .mockResolvedValue(page([run({ status: 'succeeded' })], 1))
      await mountAdmin()

      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()

      expect(fetchMissingDays).toHaveBeenCalledTimes(2)
      expect(fetchAssessmentQuality).not.toHaveBeenCalled()
    })
  })

  describe('run history', () => {
    afterEach(() => {
      vi.useRealTimers()
    })

    it('shows the range and the duration of a run, none for a queued one', async () => {
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
      expect(cron!.find('.range').text()).toBe('1.10.2026')
      expect(cron!.find('.duration').text()).toBe('19 min 59 s')
      expect(admin!.find('.range').text()).toBe('29.09.2026 – 1.10.2026')
      expect(queued!.find('.range').text()).toBe('')
      expect(queued!.find('.duration').text()).toBe('')
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
      expect(interrupted!.find('.attention use').attributes('href')).toBe('#i-circle-alert')
      const link = interrupted!.find('a.details')
      expect(link.text()).toBe('2.10.2026, 08:00')
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
      expect(partial!.find('.status').text()).toBe('częściowo — pominięte: 1')
      expect(partial!.find('.status').attributes('data-tone')).toBe('warning')
      expect(partial!.find('.attention').text()).toBe('wymaga uwagi')
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
      expect(wrapper.find('.run .assessed').text()).toBe('30')

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
