import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import { RouterView, createMemoryHistory } from 'vue-router'
import { fetchMe } from '../../api/auth'
import { ApiError } from '../../api/client'
import { fetchMissingDays, fetchRuns, fetchSources, retryPosts } from '../../api/pipeline'
import { FAILED_PAGE_SIZE, fetchFailedPosts, restorePosts } from '../../api/posts'
import { fetchAssessmentQuality } from '../../api/quality'
import { resetSession } from '../../auth/session'
import { createAppRouter } from '../../router'
import AdminFailedPostsView from './AdminFailedPostsView.vue'
import { failed, mountPart, never, onHold, run } from './testing'

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
// Only for the part inside the real layout: the shell only renders its page.
vi.mock('../../components/AppShell.vue', async () => {
  const { defineComponent, h } = await import('vue')
  const { RouterView } = await import('vue-router')
  return { default: defineComponent({ setup: () => () => h(RouterView) }) }
})

/** The part "Nieudane posty" on /admin/posts. `attachTo`: in the document, for focus. */
async function mountAdmin({ attachTo }: { attachTo?: HTMLElement } = {}) {
  return (await mountPart(AdminFailedPostsView, '/admin/posts', { attachTo })).wrapper
}

enableAutoUnmount(afterEach)

describe('AdminFailedPostsView', () => {
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
    vi.mocked(retryPosts).mockReset()
    vi.mocked(restorePosts).mockReset()
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('says it is loading, not that there is nothing, until the API answers', async () => {
    vi.mocked(fetchFailedPosts).mockReturnValue(never())
    const wrapper = await mountAdmin()
    expect(wrapper.text()).not.toContain('Brak nieudanych postów.')
    const sections = wrapper.findAll('section')
    expect(sections).toHaveLength(1)
    expect(sections.map((shown) => shown.attributes('aria-busy'))).toEqual(['true'])
    const skeletons = wrapper.findAll('.loading-state')
    expect(skeletons.map((shown) => shown.classes()[1])).toEqual(['rows'])
    expect(skeletons.map((shown) => shown.text())).toEqual(['Wczytywanie…'])
  })

  it.each(['empty', 'data'] as const)('keeps the error with a busy button while recovering, then the %s response', async (response) => {
    let answerFailed: (value: Awaited<ReturnType<typeof fetchFailedPosts>>) => void = () => {}
    vi.mocked(fetchFailedPosts)
      .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      .mockReturnValueOnce(new Promise((resolve) => (answerFailed = resolve)))
    const wrapper = await mountAdmin()
    const failedSection = wrapper.find('section[aria-labelledby="failed-title"]')
    expect(failedSection.find('[role="alert"] .state-title').text()).toBe('Nie udało się wczytać nieudanych postów')
    expect(failedSection.find('[role="alert"] .state-text').text()).toBe('Pozostałe sekcje działają osobno.')

    await failedSection.find('.state-error button').trigger('click')

    expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
    const error = failedSection.find('.state-error')
    expect(error.exists()).toBe(true)
    expect(error.attributes('role')).toBeUndefined()
    expect(error.find('button').attributes('aria-busy')).toBe('true')
    expect(error.find('button').text()).toBe('Wczytywanie…')
    expect(failedSection.find('.loading-state').exists()).toBe(false)
    expect(failedSection.attributes('aria-busy')).toBe('true')

    const hasItems = response === 'data'
    answerFailed({ items: hasItems ? [failed('a', 'failed')] : [], page: 0, size: 50, total: hasItems ? 1 : 0 })
    await flushPromises()

    expect(failedSection.find('.state-error').exists()).toBe(false)
    expect(failedSection.findAll('.failed-post')).toHaveLength(hasItems ? 1 : 0)
    expect(failedSection.find('.state-title').exists()).toBe(!hasItems)
    expect(failedSection.attributes('aria-busy')).toBeUndefined()
  })

  it('shows an empty list of failed posts as the shared empty state, with the way to the runs', async () => {
    const wrapper = await mountAdmin()
    const failedEmpty = wrapper.find('section[aria-labelledby="failed-title"] .state')
    expect(failedEmpty.find('.state-title').text()).toBe('Brak nieudanych postów')
    expect(failedEmpty.find('.state-text').text()).toBe('Żaden post nie czeka na ponowienie ani nie jest martwy.')
    expect(failedEmpty.find('use').attributes('href')).toBe('#i-circle-check')
    const link = failedEmpty.find('.state-action a')
    expect(link.text()).toBe('Pokaż przebiegi')
    expect(link.attributes('href')).toBe('/admin')
    expect(wrapper.find('section[aria-busy="true"]').exists()).toBe(false)
    expect(wrapper.find('.data-table').exists()).toBe(false)
    expect(wrapper.find('nav[aria-label="Strony"]').exists()).toBe(false)
    expect(wrapper.find('.total').exists()).toBe(false)
  })

  describe('the focus on the panel title', () => {
    it('is asked for once, after the list is on screen', async () => {
      let answer: (value: Awaited<ReturnType<typeof fetchFailedPosts>>) => void = () => {}
      vi.mocked(fetchFailedPosts).mockReturnValue(new Promise((resolve) => (answer = resolve)))
      const { panel } = await mountPart(AdminFailedPostsView, '/admin/posts')
      expect(panel.focusTitle).not.toHaveBeenCalled()

      answer({ items: [failed('a', 'failed')], page: 0, size: 50, total: 1 })
      await flushPromises()

      expect(panel.focusTitle).toHaveBeenCalledTimes(1)
    })

    it('is not asked for again by a retry', async () => {
      vi.mocked(fetchFailedPosts).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      const { wrapper, panel } = await mountPart(AdminFailedPostsView, '/admin/posts')
      expect(panel.focusTitle).toHaveBeenCalledTimes(1)

      await wrapper.find('.state-error button').trigger('click')
      await flushPromises()

      expect(panel.focusTitle).toHaveBeenCalledTimes(1)
    })
  })

  describe('a section retries on its own', () => {
    const SECTIONS = [['failed-title', 'Nieudane posty', 'failed']] as const

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

  describe('the table', () => {
    it('has the columns of the design, the title as the row header and the origin of each post', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue({
        items: [
          failed('t', 'failed', { source: 'reddit', channel: 'stocks', title: 'Thoughts on the semiconductor cycle', body: null, webUrl: 'https://www.reddit.com/r/stocks/comments/1/' }),
          failed('w', 'dead', { title: null, body: 'Kupiłem PKO, zobaczymy', webUrl: null }),
        ],
        page: 0,
        size: 50,
        total: 2,
      })
      const wrapper = await mountAdmin()
      const frame = wrapper.find('section[aria-labelledby="failed-title"] .data-table')
      expect(frame.find('caption').text()).toBe('Posty, których ocena się nie udała')
      expect(frame.findAll('thead th').map((cell) => cell.text())).toEqual(['Zaznacz', 'Post', 'Źródło', 'Stan', 'Próby', 'Ostatni błąd'])
      expect(frame.find('thead th .visually-hidden').text()).toBe('Zaznacz')
      const [titled, untitled] = frame.findAll('.failed-post')
      expect(titled!.find('th').attributes('scope')).toBe('row')
      expect(titled!.find('.origin').text()).toBe('Reddit · r/stocks')
      expect(untitled!.find('.origin').text()).toBe('Wykop · #gielda')
      expect(titled!.find('input[type="checkbox"]').attributes('aria-label')).toBe('Zaznacz post: Thoughts on the semiconductor cycle')
      expect(untitled!.find('input[type="checkbox"]').attributes('aria-label')).toBe('Zaznacz post: Kupiłem PKO, zobaczymy')
      expect(titled!.find('th').classes()).not.toContain('untitled')
      expect(untitled!.find('th').classes()).toContain('untitled')
      expect(wrapper.find('.total').text()).toBe('2 posty')
    })

    it('links the title to the original in a new tab, and says so', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue({
        items: [failed('t', 'failed', { title: 'XTB po wynikach', webUrl: 'https://wykop.pl/link/1' })],
        page: 0,
        size: 50,
        total: 1,
      })
      const wrapper = await mountAdmin()
      const link = wrapper.find('.failed-post th a')
      expect(link.text()).toBe('XTB po wynikach')
      expect(link.attributes('href')).toBe('https://wykop.pl/link/1')
      expect(link.attributes('target')).toBe('_blank')
      expect(link.attributes('rel')).toBe('noopener noreferrer')
      expect(link.attributes('aria-label')).toBe('XTB po wynikach (otwiera się w nowej karcie)')
      expect(link.find('use').attributes('href')).toBe('#i-external-link')
    })

    it('shows the title as plain text without the original\'s address', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue({ items: [failed('t', 'failed', { title: 'Bez adresu', webUrl: null })], page: 0, size: 50, total: 1 })
      const wrapper = await mountAdmin()
      const head = wrapper.find('.failed-post th')
      expect(head.find('a').exists()).toBe(false)
      expect(head.text()).toBe('Bez adresu')
    })
  })

  describe('pages', () => {
    /** `count` failed posts `p<from>`… */
    function posts(from: number, count: number) {
      return Array.from({ length: count }, (_, at) => failed(`p${from + at}`, 'failed'))
    }

    function onPage(target: number, total = 57) {
      const items = posts(target * FAILED_PAGE_SIZE, Math.max(0, Math.min(FAILED_PAGE_SIZE, total - target * FAILED_PAGE_SIZE)))
      return { items, page: target, size: FAILED_PAGE_SIZE, total }
    }

    function pager(wrapper: Awaited<ReturnType<typeof mountAdmin>>) {
      return wrapper.find('nav[aria-label="Strony"]')
    }

    function pagerButton(wrapper: Awaited<ReturnType<typeof mountAdmin>>, name: string) {
      return pager(wrapper).findAll('button').find((button) => button.text() === name)!
    }

    it('reads the page of the address, with the pager in the words of an order', async () => {
      vi.mocked(fetchFailedPosts).mockImplementation(async (target = 0) => onPage(target))
      const { wrapper } = await mountPart(AdminFailedPostsView, '/admin/posts?page=1')
      expect(fetchFailedPosts).toHaveBeenCalledWith(1, FAILED_PAGE_SIZE)
      expect(wrapper.findAll('.failed-post')).toHaveLength(7)
      expect(pager(wrapper).find('.position').text()).toBe('Strona 2 z 2')
      expect(pagerButton(wrapper, 'Poprzednie').attributes('disabled')).toBeUndefined()
      expect(pagerButton(wrapper, 'Następne').attributes('disabled')).toBeDefined()
      expect(wrapper.find('.total').text()).toBe('57 postów')
    })

    it('has no pager with one page', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(onPage(0, 4))
      const wrapper = await mountAdmin()
      expect(pager(wrapper).exists()).toBe(false)
    })

    it('puts the next page in the address, reads it, clears the selection and focuses the heading', async () => {
      let answer: (value: ReturnType<typeof onPage>) => void = () => {}
      vi.mocked(fetchFailedPosts)
        .mockResolvedValueOnce(onPage(0))
        .mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
      const { wrapper, router } = await mountPart(AdminFailedPostsView, '/admin/posts', { attachTo: document.body })
      await wrapper.find('.failed-post input[type="checkbox"]').setValue(true)
      expect(wrapper.find('.chosen').text()).toBe('Zaznaczone: 1')

      await pagerButton(wrapper, 'Następne').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.fullPath).toBe('/admin/posts?page=1')
      expect(fetchFailedPosts).toHaveBeenLastCalledWith(1, FAILED_PAGE_SIZE)
      // The page on screen stays while the next one is read; the pager's button towards it is busy.
      expect(wrapper.findAll('.failed-post')).toHaveLength(50)
      expect(pagerButton(wrapper, 'Wczytywanie…').attributes('aria-busy')).toBe('true')
      expect(wrapper.find('section[aria-labelledby="failed-title"]').attributes('aria-busy')).toBe('true')
      expect(wrapper.find('.chosen').text()).toBe('Zaznaczone: 0')

      answer(onPage(1))
      await flushPromises()

      expect(wrapper.findAll('.failed-post')).toHaveLength(7)
      expect(pager(wrapper).find('.position').text()).toBe('Strona 2 z 2')
      expect(document.activeElement).toBe(wrapper.find('#failed-title').element)
      expect(wrapper.find('section[aria-labelledby="failed-title"]').attributes('aria-busy')).toBeUndefined()
    })

    it('takes the page out of the address on the first page', async () => {
      vi.mocked(fetchFailedPosts).mockImplementation(async (target = 0) => onPage(target))
      const { wrapper, router } = await mountPart(AdminFailedPostsView, '/admin/posts?page=1')

      await pagerButton(wrapper, 'Poprzednie').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.fullPath).toBe('/admin/posts')
      expect(fetchFailedPosts).toHaveBeenLastCalledWith(0, FAILED_PAGE_SIZE)
      expect(wrapper.findAll('.failed-post')).toHaveLength(50)
    })

    it('goes to the last page when a restore emptied the page on screen', async () => {
      // Page 1 holds 7 posts, all dead; restoring them leaves 50 posts, one page.
      const dead = { items: Array.from({ length: 7 }, (_, at) => failed(`d${at}`, 'dead')), page: 1, size: FAILED_PAGE_SIZE, total: 57 }
      vi.mocked(fetchFailedPosts)
        .mockResolvedValueOnce(dead)
        .mockResolvedValueOnce({ items: [], page: 1, size: FAILED_PAGE_SIZE, total: 50 })
        .mockResolvedValue(onPage(0, 50))
      vi.mocked(restorePosts).mockResolvedValue({ restored: 7 })
      const { wrapper, router, panel } = await mountPart(AdminFailedPostsView, '/admin/posts?page=1')
      for (const box of wrapper.findAll('.failed-post input[type="checkbox"]')) await box.setValue(true)

      await wrapper.findAll('button').find((button) => button.text() === 'Przywróć martwe (7)')!.trigger('click')
      await flushPromises()

      expect(panel.refreshFailedCount).toHaveBeenCalledTimes(1)
      expect(vi.mocked(fetchFailedPosts).mock.calls).toEqual([
        [1, FAILED_PAGE_SIZE],
        [1, FAILED_PAGE_SIZE],
        [0, FAILED_PAGE_SIZE],
      ])
      expect(router.currentRoute.value.fullPath).toBe('/admin/posts')
      expect(wrapper.findAll('.failed-post')).toHaveLength(50)
      expect(pager(wrapper).exists()).toBe(false)
      expect(wrapper.find('[role="status"]').text()).toBe('Przywrócono 7 post(ów) do oceny. Oceni je kolejny przebieg.')
    })
  })

  it('names the failure class of the last attempt in Polish', async () => {
    vi.mocked(fetchFailedPosts).mockResolvedValue({ items: [failed('a', 'failed')], page: 0, size: 50, total: 1 })
    const wrapper = await mountAdmin()
    const attempt = wrapper.find('.failed-post .last-error').text()
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
      return section(wrapper).findAll('.failed-post input[type="checkbox"]')
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
      expect(section(wrapper).find('.data-table').text()).toContain('martwy')
      expect(section(wrapper).find('.chosen').text()).toBe('Zaznaczone: 0')
      expect(boxes(wrapper).every((box) => box.attributes('disabled') === undefined)).toBe(true)
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (0)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (0)')
      expect(retryButton(wrapper).attributes('disabled')).toBeDefined()
      expect(restoreButton(wrapper).attributes('disabled')).toBeDefined()

      await boxes(wrapper)[1]!.setValue(true)

      expect(section(wrapper).find('.chosen').text()).toBe('Zaznaczone: 1')
      expect(section(wrapper).findAll('.failed-post').map((row) => row.classes('is-selected'))).toEqual([false, true, false, false])
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

    // Before S-50 a reload kept the checked posts still on the list; the selection now covers the page on screen and a
    // restore clears it, as a retry and a page change do.
    it('clears the whole selection after a restore, also the posts to retry', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValueOnce(mixed)
      vi.mocked(restorePosts).mockResolvedValue({ restored: 1 })
      const wrapper = await mountAdmin()
      await boxes(wrapper)[0]!.setValue(true)
      await boxes(wrapper)[1]!.setValue(true)
      await boxes(wrapper)[2]!.setValue(true)
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (2)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (1)')

      // b is restored; a left the list (assessed meanwhile); c and d are still there.
      vi.mocked(fetchFailedPosts).mockResolvedValue({ ...mixed, items: [mixed.items[2]!, mixed.items[3]!], total: 2 })
      await restoreButton(wrapper).trigger('click')
      await flushPromises()

      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (0)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (0)')
      expect(boxes(wrapper).map((box) => (box.element as HTMLInputElement).checked)).toEqual([false, false])
      expect(section(wrapper).find('.chosen').text()).toBe('Zaznaczone: 0')
    })

    it('retries only the checked posts that are not dead, links the new run and clears the selection', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(retryPosts).mockResolvedValue([run({ id: 'posts-run', kind: 'posts' })])
      const wrapper = await mountAdmin()
      for (const box of boxes(wrapper)) await box.setValue(true)
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (2)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (2)')

      await retryButton(wrapper).trigger('click')
      await flushPromises()

      expect(retryPosts).toHaveBeenCalledWith(['a', 'c'])
      expect(restorePosts).not.toHaveBeenCalled()
      const message = section(wrapper).find('[role="status"]')
      expect(message.text().replace(/\s+/g, ' ')).toBe('Uruchomiono ponowienie: 2 posty. Szczegóły przebiegu z 2.10.2026, 08:00')
      const link = message.find('a')
      expect(link.text()).toBe('Szczegóły przebiegu z 2.10.2026, 08:00')
      expect(link.attributes('href')).toBe('/admin/runs/posts-run')
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (0)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (0)')
      expect(boxes(wrapper).every((box) => !(box.element as HTMLInputElement).checked)).toBe(true)
    })

    it.each([
      [1, 'Uruchomiono ponowienie: 1 post.'],
      [5, 'Uruchomiono ponowienie: 5 postów.'],
    ])('counts %i retried post(s) in words', async (count, text) => {
      const items = Array.from({ length: count }, (_, at) => failed(`p${at}`, 'failed'))
      vi.mocked(fetchFailedPosts).mockResolvedValue({ items, page: 0, size: 50, total: count })
      vi.mocked(retryPosts).mockResolvedValue([run({ kind: 'posts' })])
      const wrapper = await mountAdmin()
      for (const box of boxes(wrapper)) await box.setValue(true)

      await retryButton(wrapper).trigger('click')
      await flushPromises()

      expect(section(wrapper).find('[role="status"]').text()).toContain(text)
    })

    it('says why the retry was refused and keeps the selection and the focus on its button', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(retryPosts).mockRejectedValue(new ApiError(400, 'post_dead'))
      const wrapper = await mountAdmin({ attachTo: document.body })
      await boxes(wrapper)[0]!.setValue(true)

      // As in Chromium, the busy (disabled) button has dropped the focus to <body>.
      await retryButton(wrapper).trigger('click')
      await flushPromises()

      expect(section(wrapper).find('[role="alert"]').text()).toContain('Wśród zaznaczonych jest post, którego nie można już ponowić')
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (1)')
      expect(document.activeElement).toBe(retryButton(wrapper).element)
    })

    it('puts the focus on the message after a retry, its button being disabled with the selection cleared', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(retryPosts).mockResolvedValue([run({ kind: 'posts' })])
      const wrapper = await mountAdmin({ attachTo: document.body })
      await boxes(wrapper)[0]!.setValue(true)

      // As in Chromium, the busy (disabled) button has dropped the focus to <body>.
      await retryButton(wrapper).trigger('click')
      await flushPromises()

      const message = section(wrapper).find('[role="status"]')
      expect(message.attributes('tabindex')).toBe('-1')
      expect(document.activeElement).toBe(message.element)
    })

    // Before S-50 the runs stood on the same page and were read again after a retry; they are in their own part now.
    it('reads neither the runs nor the list again after a retry', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValue(mixed)
      vi.mocked(retryPosts).mockResolvedValue([run({ kind: 'posts' })])
      const { wrapper, panel } = await mountPart(AdminFailedPostsView, '/admin/posts')
      await boxes(wrapper)[0]!.setValue(true)

      await retryButton(wrapper).trigger('click')
      await flushPromises()

      expect(retryPosts).toHaveBeenCalledWith(['a'])
      expect(fetchRuns).not.toHaveBeenCalled()
      expect(fetchFailedPosts).toHaveBeenCalledTimes(1)
      expect(panel.refreshFailedCount).not.toHaveBeenCalled()
    })

    it('keeps the list on screen while it is read again after a restore', async () => {
      vi.mocked(fetchFailedPosts).mockResolvedValueOnce(mixed).mockReturnValue(never())
      vi.mocked(restorePosts).mockResolvedValue({ restored: 1 })
      const wrapper = await mountAdmin()
      await boxes(wrapper)[1]!.setValue(true)

      await restoreButton(wrapper).trigger('click')
      await flushPromises()

      expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
      expect(section(wrapper).findAll('.failed-post')).toHaveLength(4)
      expect(section(wrapper).find('.loading-state').exists()).toBe(false)
    })

    it('restores only the checked dead posts, says so and reads the page again without them', async () => {
      vi.mocked(fetchFailedPosts)
        .mockResolvedValueOnce(mixed)
        .mockResolvedValue({ items: [failed('a', 'failed'), failed('c', 'failed')], page: 0, size: 50, total: 2 })
      vi.mocked(restorePosts).mockResolvedValue({ restored: 2 })
      const { wrapper, panel } = await mountPart(AdminFailedPostsView, '/admin/posts', { attachTo: document.body })
      for (const box of boxes(wrapper)) await box.setValue(true)

      await restoreButton(wrapper).trigger('click')
      await flushPromises()

      expect(restorePosts).toHaveBeenCalledWith(['b', 'd'])
      expect(retryPosts).not.toHaveBeenCalled()
      const message = section(wrapper).find('[role="status"]')
      expect(message.text()).toBe('Przywrócono 2 post(ów) do oceny. Oceni je kolejny przebieg.')
      expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
      expect(fetchFailedPosts).toHaveBeenLastCalledWith(0, FAILED_PAGE_SIZE)
      expect(panel.refreshFailedCount).toHaveBeenCalledTimes(1)
      expect(section(wrapper).findAll('.failed-post')).toHaveLength(2)
      expect(boxes(wrapper).map((box) => (box.element as HTMLInputElement).checked)).toEqual([false, false])
      expect(retryButton(wrapper).text()).toBe('Ponów zaznaczone (0)')
      expect(restoreButton(wrapper).text()).toBe('Przywróć martwe (0)')
      expect(restoreButton(wrapper).attributes('disabled')).toBeDefined()
      // Both buttons are disabled now: the focus is on the outcome.
      expect(document.activeElement).toBe(message.element)
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
      const { wrapper, panel } = await mountPart(AdminFailedPostsView, '/admin/posts')
      await boxes(wrapper)[1]!.setValue(true)

      await restoreButton(wrapper).trigger('click')
      await flushPromises()

      expect(section(wrapper).find('[role="alert"]').text()).toBe('Nie udało się przywrócić postów.')
      expect(section(wrapper).find('[role="status"]').exists()).toBe(false)
      expect(fetchFailedPosts).toHaveBeenCalledTimes(1)
      expect(panel.refreshFailedCount).not.toHaveBeenCalled()
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
    const [held] = wrapper.findAll('.failed-post')
    expect(held!.find('.state').text()).toBe('wstrzymany')
    expect(held!.find('.state').attributes('data-tone')).toBe('warning')
    const lastError = held!.find('.last-error')
    expect(lastError.text()).toContain('błędy techniczne: 3')
    // A stored post on hold has no stored attempt since it was stored: no failure class.
    expect(lastError.text()).not.toContain('przekroczony czas')
    expect(held!.find('.attempts').text()).toBe('0')
    expect(held!.find('.attempts').classes()).toContain('zero')
    expect(lastError.find('.technical-error').text()).toBe('IllegalStateException: a bug in the mapper')
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
    const row = wrapper.find('.failed-post')
    expect(row.find('.attempts').text()).toBe('1')
    expect(row.find('.state').text()).toBe('do ponowienia')
    expect(row.find('.state').attributes('data-tone')).toBe('neutral')
    const lastError = row.find('.last-error')
    expect(lastError.text()).toContain('przekroczony czas · błędy techniczne: 1')
    expect(lastError.find('.technical-error').attributes('title')).toBe(error)
  })

  it('shows a dead post as dead, with its failure class and without its technical errors', async () => {
    vi.mocked(fetchFailedPosts).mockResolvedValue({
      items: [failed('d', 'dead', { technicalFailures: 2, lastTechnicalError: 'x' })],
      page: 0,
      size: 50,
      total: 1,
    })
    const wrapper = await mountAdmin()
    const row = wrapper.find('.failed-post')
    expect(row.find('.state').text()).toBe('martwy')
    expect(row.find('.state').attributes('data-tone')).toBe('destructive')
    expect(row.find('.attempts').text()).toBe('3')
    expect(row.find('.last-error').text()).toBe('przekroczony czas')
  })

  it('says the retry is under way while the server answers', async () => {
    vi.mocked(fetchFailedPosts).mockResolvedValue({ items: [failed('a', 'failed')], page: 0, size: 50, total: 1 })
    vi.mocked(retryPosts).mockReturnValue(never())
    const wrapper = await mountAdmin()
    await wrapper.find('.failed-post input[type="checkbox"]').setValue(true)
    const retry = wrapper.findAll('button').find((button) => button.text() === 'Ponów zaznaczone (1)')!
    await retry.trigger('click')
    expect(retry.text()).toBe('Ponawianie…')
    expect(retry.attributes('aria-busy')).toBe('true')
  })

  it('makes the panel read the count in its bar again after a restore', async () => {
    const mixed = { items: [failed('a', 'failed'), failed('b', 'dead')], page: 0, size: 50, total: 2 }
    vi.mocked(fetchFailedPosts).mockImplementation(async (page = 0, size = 50) =>
      size === 1 ? { items: [], page, size, total: vi.mocked(restorePosts).mock.calls.length ? 1 : 2 } : mixed,
    )
    vi.mocked(restorePosts).mockResolvedValue({ restored: 1 })
    const router = createAppRouter(createMemoryHistory())
    await router.push('/admin/posts')
    const wrapper = mount({ render: () => h(RouterView) }, { global: { plugins: [router] } })
    await flushPromises()
    const countLink = () => wrapper.get('nav[aria-label="Panel admina"] a[aria-current="page"]')
    expect(countLink().text()).toBe('Nieudane posty 2')

    await wrapper.findAll('.failed-post input[type="checkbox"]')[1]!.setValue(true)
    await wrapper.findAll('button').find((button) => button.text() === 'Przywróć martwe (1)')!.trigger('click')
    await flushPromises()

    expect(vi.mocked(fetchFailedPosts).mock.calls.filter(([, size]) => size === 1)).toHaveLength(2)
    expect(countLink().text()).toBe('Nieudane posty 1')
  })
})
