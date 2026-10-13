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
vi.mock('../api/posts', () => ({ fetchPosts: vi.fn(), fetchFailedPosts: vi.fn() }))
vi.mock('../api/freshness', () => ({ fetchFreshness: vi.fn() }))
vi.mock('../router/scroll', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../router/scroll')>()),
  reportFeedLoaded: vi.fn(),
}))

function post(id: string, overrides: Partial<PostItem> = {}): PostItem {
  return {
    id,
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
            stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: [] },
            reference: 'explicit',
          },
        ],
        claims: [],
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
    expect(fetchPosts).toHaveBeenLastCalledWith(1, 50)
    expect(wrapper.find('.position').text()).toBe('Strona 2 z 3')

    await wrapper.findAll('.pager button')[1]!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('2')
    expect(fetchPosts).toHaveBeenLastCalledWith(2, 50)

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

    expect(fetchPosts).toHaveBeenLastCalledWith(1, 50)
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
    expect(fetchPosts).toHaveBeenLastCalledWith(2, 50)
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
    expect(fetchPosts).toHaveBeenLastCalledWith(0, 50)
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

      expect(fetchPosts).toHaveBeenLastCalledWith(1, 50)
      expect(fetchFreshness).toHaveBeenCalledTimes(1)
      expect(wrapper.find('.freshness').exists()).toBe(true)
    })
  })
})
