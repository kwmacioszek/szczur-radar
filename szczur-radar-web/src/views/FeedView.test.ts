import { type VueWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { fetchFreshness } from '../api/freshness'
import { type Page, type PostItem, fetchPosts } from '../api/posts'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import { reportFeedLoaded } from '../router/scroll'
import FeedView from './FeedView.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
// The value sets (TARGET_KINDS, DIRECTIONS) stay real: the filter reads the address against them.
vi.mock('../api/posts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/posts')>()),
  fetchPosts: vi.fn(),
  fetchFailedPosts: vi.fn(),
}))
vi.mock('../api/freshness', () => ({ fetchFreshness: vi.fn() }))
vi.mock('../router/scroll', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../router/scroll')>()),
  reportFeedLoaded: vi.fn(),
}))

function post(id: string, overrides: Partial<PostItem> = {}): PostItem {
  return {
    id,
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-01T07:15:00Z',
    title: null,
    body: `Wpis ${id} o XTB`,
    author: 'uzytkownik1',
    webUrl: null,
    assessment: {
      output: {
        relevance: 'investment_related',
        modifiers: [],
        targets: [
          {
            id: 't1',
            kind: 'company',
            anchor: { document: 'item', field: 'body', quote: 'XTB' },
            stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] },
            reference: 'explicit',
          },
        ],
        claims: [
          {
            id: 'c1',
            kind: 'investment_thesis',
            attribution: 'author',
            target_ids: ['t1'],
            evidence: { document: 'item', field: 'body', quote: 'XTB' },
            statement: 'Teza testowa.',
          },
        ],
        flags: ['possible_sarcasm'],
      },
      resultSource: 'model',
      assessedAt: '2026-10-02T08:00:00Z',
    },
    ...overrides,
  }
}

/** A response of `total` posts with the given items. */
function answer(items: PostItem[], total: number): Page<PostItem> {
  return { items, page: 0, size: 50, total }
}

function never<T>(): Promise<T> {
  return new Promise(() => {})
}

/** `attachTo`: in the document, for focus. */
async function mountFeed(path = '/', attachTo?: HTMLElement) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const wrapper = mount(FeedView, { global: { plugins: [router] }, attachTo })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!found) throw new Error(`no button "${text}"`)
  return found
}

enableAutoUnmount(afterEach)

describe('FeedView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'user@example.com', role: 'user' })
    vi.mocked(fetchPosts).mockReset()
    vi.mocked(fetchFreshness).mockReset().mockResolvedValue({ lastUpdatedAt: '2026-10-13T03:12:00Z' })
    vi.mocked(reportFeedLoaded).mockReset()
    // jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('shows each post with its date, type, text and short assessment', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [post('1')], page: 0, size: 50, total: 1 })
    const { wrapper } = await mountFeed()
    const card = wrapper.find('article')
    expect(card.text()).toContain('Wpis 1 o XTB')
    expect(card.text()).toContain('wpis')
    expect(card.text()).toContain('inwestycyjny')
    expect(card.text()).toContain('XTB')
    expect(card.text()).toContain('wzrostowo (ostrożnie)')
    expect(card.text()).toContain('możliwy sarkazm')
    expect(card.find('time').attributes('datetime')).toBe('2026-10-01T07:15:00Z')
  })

  it('shows an empty state, not an error, when there are no posts', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 })
    const { wrapper } = await mountFeed()
    expect(wrapper.find('.empty').text()).toBe('Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('reads the page from the address and pages through the results', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [post('51')], page: 1, size: 50, total: 120 })
    const { wrapper, router } = await mountFeed('/?page=1')
    expect(fetchPosts).toHaveBeenLastCalledWith(1, 50, {})
    expect(wrapper.find('.position').text()).toBe('Strona 2 z 3')

    await wrapper.findAll('.pager button')[1]!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('2')
    expect(fetchPosts).toHaveBeenLastCalledWith(2, 50, {})

    await wrapper.findAll('.pager button')[0]!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('1')
  })

  it('disables paging past the ends', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [post('1')], page: 0, size: 50, total: 1 })
    const { wrapper } = await mountFeed()
    const [newer, older] = wrapper.findAll('.pager button')
    expect(newer!.attributes('disabled')).toBeDefined()
    expect(older!.attributes('disabled')).toBeDefined()
  })

  it('shows an error when the posts cannot be loaded', async () => {
    vi.mocked(fetchPosts).mockRejectedValue(new TypeError('Failed to fetch'))
    const { wrapper } = await mountFeed()
    expect(wrapper.find('[role="alert"]').text()).toContain('Nie udało się wczytać postów')
  })

  it('replaces the list with the loading of the next page, not the old posts under a new number', async () => {
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('1')], 120)).mockReturnValue(never())
    const { wrapper } = await mountFeed()
    const list = wrapper.find('section[aria-labelledby="feed-title"] .posts')
    expect(list.attributes('aria-busy')).toBeUndefined()

    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()

    expect(fetchPosts).toHaveBeenLastCalledWith(1, 50, {})
    expect(wrapper.find('article').exists()).toBe(false)
    expect(wrapper.find('.loading').text()).toBe('Wczytywanie strony 2…')
    expect(list.attributes('aria-busy')).toBe('true')
    expect(wrapper.find('.pager').exists()).toBe(false)
  })

  it('says only that it is loading on the first entry', async () => {
    vi.mocked(fetchPosts).mockReturnValue(never())
    const { wrapper } = await mountFeed()
    expect(wrapper.find('.loading').text()).toBe('Wczytywanie…')
    expect(wrapper.find('.posts').attributes('aria-busy')).toBe('true')
  })

  it('scrolls to the top when the page changes, not on the first entry', async () => {
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('1')], 120)).mockReturnValue(never())
    const { wrapper } = await mountFeed()
    expect(window.scrollTo).not.toHaveBeenCalled()

    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()

    expect(window.scrollTo).toHaveBeenCalledWith({ top: 0 })
  })

  it('focuses the heading once the new page is loaded, not on the first entry', async () => {
    vi.mocked(fetchPosts)
      .mockResolvedValueOnce(answer([post('1')], 120))
      .mockResolvedValueOnce(answer([post('51')], 120))
    const { wrapper } = await mountFeed('/', document.body)
    const heading = wrapper.find('h1').element
    expect(document.activeElement).not.toBe(heading)

    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()

    expect(wrapper.find('article').text()).toContain('Wpis 51 o XTB')
    expect(document.activeElement).toBe(heading)
  })

  it('says where the posts end for a page past the last one and goes there', async () => {
    vi.mocked(fetchPosts).mockResolvedValue(answer([], 120))
    const { wrapper, router } = await mountFeed('/?page=9')
    expect(wrapper.find('.empty').text()).toBe('Nie ma strony 10. Posty kończą się na stronie 3.')
    expect(wrapper.find('.pager').exists()).toBe(false)

    await button(wrapper, 'Przejdź do strony 3').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('2')
    expect(fetchPosts).toHaveBeenLastCalledWith(2, 50, {})
  })

  it('ignores the answer of an earlier load that arrives after the latest one', async () => {
    const answers = new Map<number, (value: Page<PostItem>) => void>()
    vi.mocked(fetchPosts)
      .mockResolvedValueOnce(answer([post('1')], 120))
      .mockImplementation((page) => new Promise((resolve) => answers.set(page, resolve)))
    const { wrapper, router } = await mountFeed()

    await router.push('/?page=1')
    await flushPromises()
    await router.push('/?page=2')
    await flushPromises()
    answers.get(2)!(answer([post('101')], 120))
    await flushPromises()
    answers.get(1)!(answer([post('51')], 120))
    await flushPromises()

    expect(wrapper.findAll('article')).toHaveLength(1)
    expect(wrapper.find('article').text()).toContain('Wpis 101 o XTB')
    expect(wrapper.find('.position').text()).toBe('Strona 3 z 3')
    expect(wrapper.find('.posts').attributes('aria-busy')).toBeUndefined()
  })

  it('reports a loaded page once its list is on screen', async () => {
    vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 1))
    let listShown = false
    vi.mocked(reportFeedLoaded).mockImplementation(() => {
      listShown = document.querySelector('article') !== null
    })
    await mountFeed('/', document.body)
    expect(reportFeedLoaded).toHaveBeenCalledExactlyOnceWith('ok')
    expect(listShown).toBe(true)
  })

  it('reports a loaded page only once the freshness label is settled', async () => {
    let settle: (value: { lastUpdatedAt: string | null }) => void = () => {}
    vi.mocked(fetchFreshness).mockImplementation(() => new Promise((resolve) => (settle = resolve)))
    vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 1))
    let labelShown = false
    vi.mocked(reportFeedLoaded).mockImplementation(() => {
      labelShown = document.querySelector('.freshness') !== null
    })
    const { wrapper } = await mountFeed('/', document.body)
    expect(wrapper.find('article').exists()).toBe(true)
    expect(reportFeedLoaded).not.toHaveBeenCalled()

    settle({ lastUpdatedAt: '2026-10-13T03:12:00Z' })
    await flushPromises()
    expect(reportFeedLoaded).toHaveBeenCalledExactlyOnceWith('ok')
    expect(labelShown).toBe(true)
  })

  it('reports a loaded page when the freshness label fails', async () => {
    vi.mocked(fetchFreshness).mockRejectedValue(new TypeError('Failed to fetch'))
    vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 1))
    await mountFeed()
    expect(reportFeedLoaded).toHaveBeenCalledExactlyOnceWith('ok')
  })

  it('reports a failed load', async () => {
    vi.mocked(fetchPosts).mockRejectedValue(new TypeError('Failed to fetch'))
    await mountFeed()
    expect(reportFeedLoaded).toHaveBeenCalledExactlyOnceWith('error')
  })

  it('stays silent about a load that ends after it was unmounted', async () => {
    let finish: (value: Page<PostItem>) => void = () => {}
    vi.mocked(fetchPosts).mockImplementation(() => new Promise((resolve) => (finish = resolve)))
    const { wrapper } = await mountFeed()

    wrapper.unmount()
    finish(answer([post('1')], 1))
    await flushPromises()

    expect(reportFeedLoaded).not.toHaveBeenCalled()
  })

  it('does not report a load overtaken by a later one', async () => {
    const answers = new Map<number, (value: Page<PostItem>) => void>()
    vi.mocked(fetchPosts)
      .mockResolvedValueOnce(answer([post('1')], 120))
      .mockImplementation((page) => new Promise((resolve) => answers.set(page, resolve)))
    const { router } = await mountFeed()
    vi.mocked(reportFeedLoaded).mockClear()

    await router.push('/?page=1')
    await flushPromises()
    await router.push('/?page=2')
    await flushPromises()
    answers.get(1)!(answer([post('51')], 120))
    await flushPromises()
    expect(reportFeedLoaded).not.toHaveBeenCalled()

    answers.get(2)!(answer([post('101')], 120))
    await flushPromises()
    expect(reportFeedLoaded).toHaveBeenCalledExactlyOnceWith('ok')
  })

  it('offers a retry after an error and shows the posts when it succeeds', async () => {
    vi.mocked(fetchPosts)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(answer([post('1')], 1))
    const { wrapper } = await mountFeed()
    expect(wrapper.find('[role="alert"]').text()).toBe('Nie udało się wczytać postów.')
    expect(wrapper.find('.pager').exists()).toBe(false)

    await button(wrapper, 'Spróbuj ponownie').trigger('click')
    await flushPromises()

    expect(fetchPosts).toHaveBeenCalledTimes(2)
    expect(fetchPosts).toHaveBeenLastCalledWith(0, 50, {})
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('article').text()).toContain('Wpis 1 o XTB')
  })

  describe('freshness label', () => {
    it('says when the data was last updated, with the instant from the API', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 1))
      const { wrapper } = await mountFeed()
      const label = wrapper.find('.freshness')
      expect(label.text()).toContain('Dane zaktualizowane:')
      expect(label.find('time').attributes('datetime')).toBe('2026-10-13T03:12:00Z')
      expect(label.find('time').text()).toBe('13.10.2026, 05:12')
      expect(label.attributes('role')).toBeUndefined()
      // The region "Posty" starts with its heading; only the list under the label is busy while paging.
      expect(wrapper.find('section[aria-labelledby="feed-title"] > h1#feed-title + .freshness').exists()).toBe(true)
      expect(wrapper.find('[aria-busy] .freshness').exists()).toBe(false)
    })

    it('says the data was never updated before the first update', async () => {
      vi.mocked(fetchFreshness).mockResolvedValue({ lastUpdatedAt: null })
      vi.mocked(fetchPosts).mockResolvedValue(answer([], 0))
      const { wrapper } = await mountFeed()
      expect(wrapper.find('.freshness').text()).toBe('Dane nie były jeszcze aktualizowane.')
      expect(wrapper.find('.freshness time').exists()).toBe(false)
    })

    it('shows nothing while loading', async () => {
      vi.mocked(fetchFreshness).mockReturnValue(never())
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 1))
      const { wrapper } = await mountFeed()
      expect(wrapper.find('.freshness').exists()).toBe(false)
    })

    it('shows nothing when it cannot be read, and the posts still render', async () => {
      vi.mocked(fetchFreshness).mockRejectedValue(new TypeError('Failed to fetch'))
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 1))
      const { wrapper } = await mountFeed()
      expect(wrapper.find('.freshness').exists()).toBe(false)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.find('article').text()).toContain('Wpis 1 o XTB')
    })

    it('is read once on entry, not again when the page changes', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 120))
      const { wrapper } = await mountFeed()
      expect(fetchFreshness).toHaveBeenCalledTimes(1)

      await button(wrapper, 'Starsze').trigger('click')
      await flushPromises()

      expect(fetchPosts).toHaveBeenLastCalledWith(1, 50, {})
      expect(fetchFreshness).toHaveBeenCalledTimes(1)
      expect(wrapper.find('.freshness').exists()).toBe(true)
    })
  })

  describe('filter', () => {
    function field(wrapper: VueWrapper, selector: string): HTMLInputElement | HTMLSelectElement {
      return wrapper.find(`form[role="search"] ${selector}`).element as HTMLInputElement | HTMLSelectElement
    }

    it('reads the filter from the address and fills the form', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 3))
      const { wrapper } = await mountFeed('/?q=cdr&direction=bullish')
      expect(fetchPosts).toHaveBeenLastCalledWith(0, 50, { q: 'cdr', direction: 'bullish' })
      expect(field(wrapper, 'input[type="search"]').value).toBe('cdr')
      expect(wrapper.findAll('form select').map((select) => (select.element as HTMLSelectElement).value)).toEqual([
        '',
        'bullish',
      ])
    })

    it('applies a filter from the first page, without scrolling or moving the focus', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('101')], 120))
      const { wrapper, router } = await mountFeed('/?page=2', document.body)
      const heading = wrapper.find('h1').element

      const text = wrapper.find('form input[type="search"]')
      await text.setValue('xtb')
      await wrapper.findAll('form select')[0]!.setValue('company')
      ;(text.element as HTMLInputElement).focus()
      await wrapper.find('form button[type="submit"]').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.query).toEqual({ q: 'xtb', kind: 'company' })
      expect(fetchPosts).toHaveBeenLastCalledWith(0, 50, { q: 'xtb', kind: 'company' })
      expect(window.scrollTo).not.toHaveBeenCalled()
      expect(document.activeElement).not.toBe(heading)
    })

    it('submits an unchanged filter from a later page without scrolling or moving the focus', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('101')], 120))
      const { wrapper, router } = await mountFeed('/?q=xtb&page=2', document.body)
      const heading = wrapper.find('h1').element
      const text = wrapper.find('form input[type="search"]').element as HTMLInputElement
      text.focus()

      await wrapper.find('form button[type="submit"]').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.query).toEqual({ q: 'xtb' })
      expect(fetchPosts).toHaveBeenLastCalledWith(0, 50, { q: 'xtb' })
      expect(window.scrollTo).not.toHaveBeenCalled()
      expect(document.activeElement).not.toBe(heading)

      // The pager still goes to the top afterwards.
      await button(wrapper, 'Starsze').trigger('click')
      await flushPromises()
      expect(window.scrollTo).toHaveBeenCalledWith({ top: 0 })
      expect(document.activeElement).toBe(heading)
    })

    it('keeps the filter when paging', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 120))
      const { wrapper, router } = await mountFeed('/?q=xtb&kind=company')

      await button(wrapper, 'Starsze').trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.query).toEqual({ q: 'xtb', kind: 'company', page: '1' })
      expect(fetchPosts).toHaveBeenLastCalledWith(1, 50, { q: 'xtb', kind: 'company' })
    })

    it('says how many posts there are, or how many match the filter, in a status region', async () => {
      vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('1')], 120)).mockResolvedValueOnce(answer([post('1')], 3))
      const { wrapper, router } = await mountFeed()
      const status = wrapper.find('[role="status"]')
      expect(status.text()).toBe('Ocenione posty: 120')

      await router.push('/?q=xtb')
      await flushPromises()

      // The same element: a region added together with its text is not announced reliably.
      expect(wrapper.find('[role="status"]').element).toBe(status.element)
      expect(status.text()).toBe('Pasujące do filtra: 3')
    })

    it('keeps the status region empty while loading and after an error', async () => {
      vi.mocked(fetchPosts).mockReturnValue(never())
      const { wrapper } = await mountFeed('/?q=xtb')
      expect(wrapper.find('[role="status"]').exists()).toBe(true)
      expect(wrapper.find('[role="status"]').text()).toBe('')

      vi.mocked(fetchPosts).mockRejectedValue(new TypeError('Failed to fetch'))
      const failed = await mountFeed('/?q=xtb')
      expect(failed.wrapper.find('[role="status"]').text()).toBe('')
      expect(failed.wrapper.find('[role="alert"]').text()).toBe('Nie udało się wczytać postów.')
    })

    it('tells an empty filter result from an empty feed and clears the filter', async () => {
      vi.mocked(fetchPosts).mockResolvedValueOnce(answer([], 0)).mockResolvedValueOnce(answer([post('1')], 120))
      const { wrapper, router } = await mountFeed('/?q=zzz&kind=asset', document.body)
      expect(wrapper.find('[role="status"]').text()).toBe('Żaden post nie pasuje do filtra.')
      expect(wrapper.find('.empty').exists()).toBe(false)
      const clear = wrapper.findAll('.posts button')
      expect(clear.map((candidate) => candidate.text())).toEqual(['Wyczyść filtry'])

      await clear[0]!.trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.fullPath).toBe('/')
      expect(fetchPosts).toHaveBeenLastCalledWith(0, 50, {})
      expect(wrapper.find('[role="status"]').text()).toBe('Ocenione posty: 120')
      expect(field(wrapper, 'input[type="search"]').value).toBe('')
      // The button is gone with the empty result: the focus goes to the start of the list.
      expect(document.activeElement).toBe(wrapper.find('h1').element)
    })

    it('clears the filter from the form', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 3))
      const { wrapper, router } = await mountFeed('/?q=xtb&page=1')

      await wrapper.findAll('form button').find((candidate) => candidate.text() === 'Wyczyść filtry')!.trigger('click')
      await flushPromises()

      expect(router.currentRoute.value.fullPath).toBe('/')
      expect(fetchPosts).toHaveBeenLastCalledWith(0, 50, {})
      expect(wrapper.findAll('form button').map((candidate) => candidate.text())).toEqual(['Filtruj'])
    })

    it('drops an unknown value from the address instead of sending it', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 120))
      const { wrapper } = await mountFeed('/?kind=xyz')
      expect(fetchPosts).toHaveBeenLastCalledWith(0, 50, {})
      expect(wrapper.find('[role="status"]').text()).toBe('Ocenione posty: 120')
    })

    it('ignores the answer of an earlier filter that arrives after the latest one', async () => {
      const answers = new Map<string, (value: Page<PostItem>) => void>()
      vi.mocked(fetchPosts)
        .mockResolvedValueOnce(answer([post('1')], 120))
        .mockImplementation((_page, _size, filter) => new Promise((resolve) => answers.set(filter?.q ?? '', resolve)))
      const { wrapper, router } = await mountFeed()

      await router.push('/?q=cdr')
      await flushPromises()
      await router.push('/?q=xtb')
      await flushPromises()
      answers.get('xtb')!(answer([post('x')], 1))
      await flushPromises()
      answers.get('cdr')!(answer([post('c1'), post('c2')], 2))
      await flushPromises()

      expect(wrapper.findAll('article')).toHaveLength(1)
      expect(wrapper.find('article').text()).toContain('Wpis x o XTB')
      expect(wrapper.find('[role="status"]').text()).toBe('Pasujące do filtra: 1')
      expect(wrapper.find('.posts').attributes('aria-busy')).toBeUndefined()
    })

    it('does not reload when the address changes outside the page and the filter', async () => {
      vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 120))
      const { router } = await mountFeed('/?q=xtb')
      await router.push('/?q=xtb&utm_source=link')
      await flushPromises()
      expect(fetchPosts).toHaveBeenCalledTimes(1)
    })
  })
})
