import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchMe } from '../../api/auth'
import { ApiError } from '../../api/client'
import { fetchMissingDays, fetchRuns, fetchSources } from '../../api/pipeline'
import { fetchFailedPosts } from '../../api/posts'
import { fetchAssessmentQuality } from '../../api/quality'
import { resetSession } from '../../auth/session'
import {
  sampleAssessmentQuality,
  sampleAssessmentQualityFewAttempts,
  sampleAssessmentQualityLanguageUnavailable,
} from '../../contract/samples'
import AdminQualityView from './AdminQualityView.vue'
import { mountPart, never } from './testing'

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

/** The part "Jakość ocen" on /admin/quality. `attachTo`: in the document, for focus. */
async function mountAdmin({ attachTo }: { attachTo?: HTMLElement } = {}) {
  return (await mountPart(AdminQualityView, '/admin/quality', { attachTo })).wrapper
}

enableAutoUnmount(afterEach)

describe('AdminQualityView', () => {
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
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('has its one section loading, marked busy, until the API answers', async () => {
    vi.mocked(fetchAssessmentQuality).mockReturnValue(never())
    const wrapper = await mountAdmin()
    const sections = wrapper.findAll('section')
    expect(sections).toHaveLength(1)
    expect(sections.map((shown) => shown.attributes('aria-busy'))).toEqual(['true'])
    expect(sections.map((shown) => shown.find('.loading-state').classes()[1])).toEqual(['lines'])
  })

  describe('the focus on the panel title', () => {
    it('is asked for once, after the section is on screen', async () => {
      let answer: (value: Awaited<ReturnType<typeof fetchAssessmentQuality>>) => void = () => {}
      vi.mocked(fetchAssessmentQuality).mockReturnValue(new Promise((resolve) => (answer = resolve)))
      const { panel } = await mountPart(AdminQualityView, '/admin/quality')
      expect(panel.focusTitle).not.toHaveBeenCalled()

      answer(sampleAssessmentQuality)
      await flushPromises()

      expect(panel.focusTitle).toHaveBeenCalledTimes(1)
    })

    it('is not asked for again by a retry', async () => {
      vi.mocked(fetchAssessmentQuality).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const { wrapper, panel } = await mountPart(AdminQualityView, '/admin/quality')
      expect(panel.focusTitle).toHaveBeenCalledTimes(1)

      await wrapper.find('.state-error button').trigger('click')
      await flushPromises()

      expect(panel.focusTitle).toHaveBeenCalledTimes(1)
    })
  })

  describe('a section retries on its own', () => {
    const SECTIONS = [['quality-title', 'Jakość ocen (7 dni)', 'quality']] as const

    /** Every read of the panel, also those of the other parts: none of them may follow a retry here. */
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

    // Before S-50 "Odśwież" of the one admin page read the window again; the part has no "Odśwież" and reads it anew
    // on every entry instead, so a run that ended meanwhile counts.
    it('reads the window again on every entry into the part', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValueOnce(sampleAssessmentQuality)
      const first = await mountAdmin()
      expect(section(first).findAll('.quality-source')).toHaveLength(2)
      first.unmount()

      const wrapper = await mountAdmin()

      expect(fetchAssessmentQuality).toHaveBeenCalledTimes(2)
      expect(section(wrapper).find('.state-title').text()).toBe('Brak ocen w ostatnich 7 dniach')
    })
  })
})
