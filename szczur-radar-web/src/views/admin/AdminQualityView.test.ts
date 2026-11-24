import { enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchMe } from '../../api/auth'
import { ApiError, type ErrorCode } from '../../api/client'
import { fetchMissingDays, fetchRuns, fetchSources } from '../../api/pipeline'
import { fetchFailedPosts } from '../../api/posts'
import { type QualityWindow, fetchAssessmentQuality } from '../../api/quality'
import { resetSession } from '../../auth/session'
import {
  sampleAssessmentQuality,
  sampleAssessmentQualityFewAttempts,
  sampleAssessmentQualityLanguageUnavailable,
} from '../../contract/samples'
import { installMock } from '../../contract/mockApi'
import type { MockModes } from '../../contract/mockModes'
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
    type Wrapper = Awaited<ReturnType<typeof mountAdmin>>

    function section(wrapper: Wrapper) {
      return wrapper.find('section[aria-labelledby="quality-title"]')
    }

    function cards(wrapper: Wrapper) {
      return section(wrapper).findAll('.quality-source')
    }

    /** A measure as its card shows it: name, value, counts, state, the bar's name and fill. */
    function measure(card: ReturnType<typeof cards>[number], kind: 'failed' | 'language') {
      const shown = card.find(`.quality-${kind}`)
      const meter = shown.find('.meter')
      return {
        name: shown.find('.measure-name').text(),
        value: shown.find('.measure-value').text(),
        counts: shown.find('.measure-counts').text(),
        status: [shown.find('.measure-meta .status-badge').text(), shown.find('.measure-meta .status-badge').attributes('data-tone')],
        bar: [meter.attributes('role'), meter.attributes('aria-label'), meter.find('.meter-fill').attributes('style')],
      }
    }

    it('shows a card per source with its name as a heading and the badge of its worst measure', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQuality)
      const wrapper = await mountAdmin()
      expect(section(wrapper).find('h2').text()).toBe('Jakość ocen (7 dni)')
      expect(section(wrapper).find('.quality-window').text()).toBe('6.03.2025 – 12.03.2025')
      expect(cards(wrapper).map((card) => card.find('h3').text())).toEqual(['Wykop', 'Reddit'])
      for (const card of cards(wrapper)) {
        expect(card.element.tagName).toBe('SECTION')
        expect(card.attributes('aria-labelledby')).toBe(card.find('h3').attributes('id'))
      }
      expect(cards(wrapper).map((card) => [card.find('.source-status').text(), card.find('.source-status').attributes('data-tone')])).toEqual([
        ['w normie', 'success'],
        ['ponad progiem', 'destructive'],
      ])
      expect(cards(wrapper)[1]!.find('.source-status use').attributes('href')).toBe('#i-circle-alert')
      expect(cards(wrapper)[0]!.find('.source-status use').exists()).toBe(false)
      expect(section(wrapper).attributes('aria-busy')).toBeUndefined()
    })

    it('shows each measure with its value, counts, threshold, state in words and a bar named by the same text', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQuality)
      const wrapper = await mountAdmin()
      const [wykop, reddit] = cards(wrapper)
      expect(measure(wykop!, 'failed')).toEqual({
        name: 'Nieudane próby oceny',
        value: '5,0%',
        counts: '10 z 200 prób · próg 5%',
        status: ['w normie', 'success'],
        // 5% on a scale of 1.5 × 5%: two thirds, at the threshold's mark.
        bar: ['img', 'Nieudane próby oceny: 5,0%, 10 z 200 prób · próg 5%', 'width: 66.7%;'],
      })
      expect(measure(wykop!, 'language')).toEqual({
        name: 'Tezy w innym języku niż post',
        value: '2,0%',
        counts: '10 z 500 tez · próg 2%',
        status: ['w normie', 'success'],
        bar: ['img', 'Tezy w innym języku niż post: 2,0%, 10 z 500 tez · próg 2%', 'width: 66.7%;'],
      })
      expect(measure(reddit!, 'failed')).toEqual({
        name: 'Nieudane próby oceny',
        value: '33,3%',
        counts: '40 z 120 prób · próg 25%',
        status: ['ponad progiem', 'destructive'],
        bar: ['img', 'Nieudane próby oceny: 33,3%, 40 z 120 prób · próg 25%', 'width: 88.9%;'],
      })
      expect(measure(reddit!, 'language')).toEqual({
        name: 'Tezy w innym języku niż post',
        value: '30,1%',
        counts: '151 z 501 tez · próg 30%',
        status: ['ponad progiem', 'destructive'],
        bar: ['img', 'Tezy w innym języku niż post: 30,1%, 151 z 501 tez · próg 30%', 'width: 67%;'],
      })
      expect(reddit!.find('.quality-failed').classes()).toContain('measure-over')
      expect(wykop!.find('.quality-failed').classes()).not.toContain('measure-over')
    })

    it('fills the bar at most to its end, however far above the threshold', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue({
        ...sampleAssessmentQuality,
        sources: [{ ...sampleAssessmentQuality.sources[0]!, failed: 100, classes: [], status: 'above_threshold' }],
      })
      const wrapper = await mountAdmin()
      expect(measure(cards(wrapper)[0]!, 'failed').bar[2]).toBe('width: 100%;')
    })

    it('shows the classes of the failed attempts under their measure, as shares of the attempts', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQuality)
      const wrapper = await mountAdmin()
      expect(cards(wrapper).map((card) => card.find('.quality-failed .quality-classes').text())).toEqual([
        'niezgodność ze schematem 2,0% · zmyślony cytat 3,0%',
        'niezgodność ze schematem 16,7% · zmyślony cytat 12,5% · naruszenie reguł kontraktu 4,2%',
      ])
    })

    it('names both minimums and the threshold mark in the note under the cards', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQuality)
      const wrapper = await mountAdmin()
      expect(section(wrapper).find('.quality-note').text().replace(/\s+/g, ' ')).toBe(
        'Próg jest sprawdzany od 100 prób i od 500 tez. Kreska na pasku to próg.',
      )
    })

    it('shows the measures below their minimums in a neutral tone with a muted bar, and a dash without a thesis', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQualityFewAttempts)
      const wrapper = await mountAdmin()
      const [wykop, reddit] = cards(wrapper)
      expect(measure(wykop!, 'failed')).toMatchObject({ value: '0,0%', counts: '0 z 46 prób · próg 5%', status: ['za mało danych', 'neutral'] })
      expect(measure(wykop!, 'language')).toMatchObject({ value: '2,2%', counts: '1 z 46 tez · próg 2%', status: ['za mało danych', 'neutral'] })
      expect(measure(reddit!, 'failed')).toMatchObject({ value: '33,3%', counts: '1 z 3 prób · próg 25%' })
      expect(measure(reddit!, 'language')).toEqual({
        name: 'Tezy w innym języku niż post',
        value: '–',
        counts: '0 tez · próg 30%',
        status: ['za mało danych', 'neutral'],
        bar: ['img', 'Tezy w innym języku niż post: 0 tez · próg 30%', 'width: 0%;'],
      })
      expect(wykop!.find('.quality-failed').classes()).toContain('measure-idle')
      expect(cards(wrapper).map((card) => card.find('.source-status').text())).toEqual(['za mało danych', 'za mało danych'])
      // No failed attempt, no classes.
      expect(wykop!.find('.quality-classes').exists()).toBe(false)
    })

    it('says the theses could not be counted, without a state or a bar, and keeps the failed attempts', async () => {
      vi.mocked(fetchAssessmentQuality).mockResolvedValue(sampleAssessmentQualityLanguageUnavailable)
      const wrapper = await mountAdmin()
      expect(cards(wrapper).map((card) => card.find('.quality-language').text())).toEqual([
        'Tezy w innym języku niż post: nie udało się policzyć',
        'Tezy w innym języku niż post: nie udało się policzyć',
      ])
      expect(cards(wrapper).map((card) => card.find('.quality-language .status-badge').exists())).toEqual([false, false])
      expect(cards(wrapper).map((card) => card.find('.quality-language .meter').exists())).toEqual([false, false])
      expect(cards(wrapper).map((card) => card.findAll('.meter'))).toHaveLength(2)
      expect(cards(wrapper).map((card) => measure(card, 'failed').counts)).toEqual(['3 z 123 prób · próg 5%', '0 z 2 prób · próg 25%'])
      expect(cards(wrapper).map((card) => card.find('.source-status').text())).toEqual(['w normie', 'za mało danych'])
    })

    it('says there is no assessment in the window', async () => {
      const wrapper = await mountAdmin()
      const empty = section(wrapper).find('.state')
      expect(empty.find('.state-title').text()).toBe('Brak ocen w ostatnich 7 dniach')
      expect(empty.find('.state-text').text()).toBe('Miary pojawią się po ocenach z kolejnego przebiegu.')
      expect(empty.find('use').attributes('href')).toBe('#i-inbox')
      expect(section(wrapper).find('.quality-window').text()).toBe('16.10.2026 – 22.10.2026')
      expect(section(wrapper).find('.quality-source').exists()).toBe(false)
      expect(section(wrapper).find('.quality-note').exists()).toBe(false)
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
      expect(cards(first)).toHaveLength(2)
      first.unmount()

      const wrapper = await mountAdmin()

      expect(fetchAssessmentQuality).toHaveBeenCalledTimes(2)
      expect(section(wrapper).find('.state-title').text()).toBe('Brak ocen w ostatnich 7 dniach')
    })

    // The part on each answer of the mock's QUALITY modes (dev/mock-api.js), what the screenshots and E2E tests see.
    describe('on the mock', () => {
      async function fromMock(quality: NonNullable<MockModes['QUALITY']>) {
        const answer = await (await installMock({ QUALITY: quality, now: '2026-10-20T10:00:00Z' })).call('GET', '/api/admin/assessment-quality')
        if (answer.status === 200) vi.mocked(fetchAssessmentQuality).mockResolvedValue(answer.body as QualityWindow)
        else vi.mocked(fetchAssessmentQuality).mockRejectedValue(new ApiError(answer.status, (answer.body as { detail: ErrorCode }).detail))
        return mountAdmin()
      }

      function summary(wrapper: Wrapper) {
        return cards(wrapper).map((card) => ({
          source: card.find('h3').text(),
          badge: card.find('.source-status').text(),
          failed: card.find('.quality-failed .meter').attributes('aria-label'),
          language: card.find('.quality-language .meter').exists()
            ? card.find('.quality-language .meter').attributes('aria-label')
            : card.find('.quality-language').text(),
        }))
      }

      it('data: both sources, Reddit above both thresholds', async () => {
        const wrapper = await fromMock('data')
        expect(section(wrapper).find('.quality-window').text()).toBe('14.10.2026 – 20.10.2026')
        expect(summary(wrapper)).toEqual([
          {
            source: 'Wykop',
            badge: 'w normie',
            failed: 'Nieudane próby oceny: 2,6%, 82 z 3134 prób · próg 5%',
            language: 'Tezy w innym języku niż post: 0,3%, 11 z 3476 tez · próg 2%',
          },
          {
            source: 'Reddit',
            badge: 'ponad progiem',
            failed: 'Nieudane próby oceny: 27,4%, 164 z 598 prób · próg 25%',
            language: 'Tezy w innym języku niż post: 31,0%, 886 z 2857 tez · próg 30%',
          },
        ])
      })

      it('few: Reddit below both minimums, neither threshold checked', async () => {
        const wrapper = await fromMock('few')
        expect(summary(wrapper)[1]).toEqual({
          source: 'Reddit',
          badge: 'za mało danych',
          failed: 'Nieudane próby oceny: 15,2%, 7 z 46 prób · próg 25%',
          language: 'Tezy w innym języku niż post: 17,5%, 21 z 120 tez · próg 30%',
        })
        expect(cards(wrapper)[1]!.findAll('.measure-idle')).toHaveLength(2)
      })

      it('nolang: the theses of no source could be counted, the failed attempts still decide the badge', async () => {
        const wrapper = await fromMock('nolang')
        expect(summary(wrapper).map(({ badge, language }) => [badge, language])).toEqual([
          ['w normie', 'Tezy w innym języku niż post: nie udało się policzyć'],
          ['ponad progiem', 'Tezy w innym języku niż post: nie udało się policzyć'],
        ])
      })

      it('none: the empty state under the window', async () => {
        const wrapper = await fromMock('none')
        expect(section(wrapper).find('.state-title').text()).toBe('Brak ocen w ostatnich 7 dniach')
        expect(cards(wrapper)).toHaveLength(0)
      })

      it('error: the part\'s error with its retry', async () => {
        const wrapper = await fromMock('error')
        const alert = section(wrapper).find('[role="alert"]')
        expect(alert.find('.state-title').text()).toBe('Nie udało się wczytać jakości ocen')
        expect(alert.find('button').text()).toBe('Spróbuj ponownie')
      })
    })
  })
})
