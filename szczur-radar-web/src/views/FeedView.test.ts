import { type VueWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
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

/** A promise with its resolve and reject, for a load that ends when the test says so. */
function deferred<T>() {
  let resolve: (value: T) => void = () => {}
  let reject: (reason: unknown) => void = () => {}
  const promise = new Promise<T>((onResolve, onReject) => {
    resolve = onResolve
    reject = onReject
  })
  return { promise, resolve, reject }
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
    const empty = wrapper.find('.posts .state')
    expect(empty.find('h2').text()).toBe('Brak ocenionych postów')
    expect(empty.find('.state-text').text()).toBe('Pojawią się po najbliższej aktualizacji danych.')
    expect(empty.find('button').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('nav[aria-label="Strony"]').exists()).toBe(false)
  })

  it('reads the page from the address and pages through the results', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [post('51')], page: 1, size: 50, total: 120 })
    const { wrapper, router } = await mountFeed('/?page=1')
    expect(fetchPosts).toHaveBeenLastCalledWith(1, 50, {})
    expect(wrapper.find('.position').text()).toBe('Strona 2 z 3')

    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('2')
    expect(fetchPosts).toHaveBeenLastCalledWith(2, 50, {})

    await button(wrapper, 'Nowsze').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('1')
  })

  it('draws no pager on a single page', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [post('1')], page: 0, size: 50, total: 1 })
    const { wrapper } = await mountFeed()
    expect(wrapper.find('article').exists()).toBe(true)
    expect(wrapper.find('nav[aria-label="Strony"]').exists()).toBe(false)
  })

  it('disables the newer page on the first one and the older page on the last one', async () => {
    vi.mocked(fetchPosts).mockResolvedValue(answer([post('1')], 120))
    const { wrapper, router } = await mountFeed()
    expect(button(wrapper, 'Nowsze').attributes('disabled')).toBeDefined()
    expect(button(wrapper, 'Starsze').attributes('disabled')).toBeUndefined()

    await router.push('/?page=2')
    await flushPromises()

    expect(wrapper.find('.position').text()).toBe('Strona 3 z 3')
    expect(button(wrapper, 'Nowsze').attributes('disabled')).toBeUndefined()
    expect(button(wrapper, 'Starsze').attributes('disabled')).toBeDefined()
  })

  it('shows an error when the posts cannot be loaded', async () => {
    vi.mocked(fetchPosts).mockRejectedValue(new TypeError('Failed to fetch'))
    const { wrapper } = await mountFeed()
    const alert = wrapper.find('.posts [role="alert"]')
    expect(alert.find('h2').text()).toBe('Nie udało się wczytać postów')
    expect(alert.find('.state-text').text()).toBe('Serwer nie odpowiedział.')
    expect(alert.find('button').text()).toBe('Spróbuj ponownie')
    expect(wrapper.find('nav[aria-label="Strony"]').exists()).toBe(false)
  })

  it('keeps the old list while the next page loads, with the button towards it busy', async () => {
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('1')], 120)).mockReturnValue(never())
    const { wrapper } = await mountFeed('/', document.body)
    const list = wrapper.find('section[aria-labelledby="feed-title"] .posts')
    expect(list.attributes('aria-busy')).toBeUndefined()

    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()

    expect(fetchPosts).toHaveBeenLastCalledWith(1, 50, {})
    expect(wrapper.find('article').text()).toContain('Wpis 1 o XTB')
    expect(wrapper.find('.loading-state').exists()).toBe(false)
    expect(list.attributes('aria-busy')).toBe('true')
    expect(wrapper.find('.position').text()).toBe('Strona 1 z 3')
    const busy = wrapper.find('nav[aria-label="Strony"] button[aria-busy="true"]')
    expect(busy.text()).toBe('Wczytywanie…')
    expect(wrapper.findAll('nav[aria-label="Strony"] button[aria-busy="true"]')).toHaveLength(1)
    expect(button(wrapper, 'Nowsze').attributes('aria-busy')).toBeUndefined()
    // Neither the top nor the heading before the page is there: the busy button would be hidden under the list.
    expect(window.scrollTo).not.toHaveBeenCalled()
    expect(document.activeElement).not.toBe(wrapper.find('h1').element)
    // The count belongs to the list that stays.
    expect(wrapper.find('[role="status"]').text()).toBe('Ocenione posty: 120')
  })

  it('makes the newer page’s button busy on a way back through history', async () => {
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('51')], 120)).mockReturnValue(never())
    const { wrapper, router } = await mountFeed('/?page=1')

    await router.push('/')
    await flushPromises()

    expect(wrapper.find('article').text()).toContain('Wpis 51 o XTB')
    expect(wrapper.find('nav[aria-label="Strony"] button[aria-busy="true"]').text()).toBe('Wczytywanie…')
    expect(button(wrapper, 'Starsze').attributes('aria-busy')).toBeUndefined()
  })

  it('fetches nothing when the way back returns to the page on screen while the next one loads', async () => {
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('1')], 120)).mockReturnValue(never())
    const { wrapper, router } = await mountFeed('/', document.body)
    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()
    expect(fetchPosts).toHaveBeenCalledTimes(2)

    router.back()
    await flushPromises()

    expect(fetchPosts).toHaveBeenCalledTimes(2)
    expect(wrapper.find('nav[aria-label="Strony"] button[aria-busy="true"]').exists()).toBe(false)
    expect(wrapper.find('.posts').attributes('aria-busy')).toBeUndefined()
    expect(wrapper.find('.position').text()).toBe('Strona 1 z 3')
    expect(window.scrollTo).toHaveBeenCalledWith({ top: 0 })
    expect(document.activeElement).toBe(wrapper.find('h1').element)
  })

  it('shows a skeleton of rows on the first entry, with the list busy', async () => {
    vi.mocked(fetchPosts).mockReturnValue(never())
    const { wrapper } = await mountFeed()
    const skeleton = wrapper.find('.posts .loading-state')
    expect(skeleton.classes()).toContain('rows')
    expect(skeleton.text()).toBe('Wczytywanie…')
    expect(wrapper.find('.posts').attributes('aria-busy')).toBe('true')
  })

  it('shows a skeleton, not the old list, while a new filter loads', async () => {
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('1')], 120)).mockReturnValue(never())
    const { wrapper, router } = await mountFeed()

    await router.push('/?q=xtb')
    await flushPromises()

    expect(wrapper.find('article').exists()).toBe(false)
    expect(wrapper.find('.posts .loading-state').exists()).toBe(true)
    expect(wrapper.find('nav[aria-label="Strony"]').exists()).toBe(false)
    expect(wrapper.find('.posts').attributes('aria-busy')).toBe('true')
  })

  it('scrolls to the top once the new page is loaded, not at the click and not on the first entry', async () => {
    const next = deferred<Page<PostItem>>()
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('1')], 120)).mockReturnValueOnce(next.promise)
    const { wrapper } = await mountFeed()
    expect(window.scrollTo).not.toHaveBeenCalled()

    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()
    expect(window.scrollTo).not.toHaveBeenCalled()

    next.resolve(answer([post('51')], 120))
    await flushPromises()
    expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 0 })
  })

  it('goes to the top and the heading when the next page fails, and shows the error', async () => {
    const next = deferred<Page<PostItem>>()
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([post('1')], 120)).mockReturnValueOnce(next.promise)
    const { wrapper } = await mountFeed('/', document.body)

    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()
    next.reject(new TypeError('Failed to fetch'))
    await flushPromises()

    expect(wrapper.find('article').exists()).toBe(false)
    expect(wrapper.find('.posts [role="alert"] h2').text()).toBe('Nie udało się wczytać postów')
    expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 0 })
    expect(document.activeElement).toBe(wrapper.find('h1').element)
  })

  it('shows a skeleton when the page changes with no list on screen, then goes to the top and the heading', async () => {
    const previous = deferred<Page<PostItem>>()
    vi.mocked(fetchPosts)
      .mockResolvedValueOnce(answer([post('1')], 120))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockReturnValueOnce(previous.promise)
    const { wrapper, router } = await mountFeed('/', document.body)
    await button(wrapper, 'Starsze').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    vi.mocked(window.scrollTo).mockClear()
    ;(document.activeElement as HTMLElement | null)?.blur()

    // Back after the error of the next page.
    await router.push('/')
    await flushPromises()

    expect(wrapper.find('.state-error').exists()).toBe(false)
    expect(wrapper.find('.posts .loading-state').exists()).toBe(true)
    expect(wrapper.find('.posts').attributes('aria-busy')).toBe('true')
    expect(window.scrollTo).not.toHaveBeenCalled()

    previous.resolve(answer([post('1')], 120))
    await flushPromises()

    expect(wrapper.find('article').text()).toContain('Wpis 1 o XTB')
    expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 0 })
    expect(document.activeElement).toBe(wrapper.find('h1').element)
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
    const last = deferred<Page<PostItem>>()
    vi.mocked(fetchPosts).mockResolvedValueOnce(answer([], 120)).mockReturnValueOnce(last.promise)
    const { wrapper, router } = await mountFeed('/?page=9', document.body)
    const empty = wrapper.find('.posts .state')
    expect(empty.find('h2').text()).toBe('Nie ma strony 10')
    expect(empty.find('.state-text').text()).toBe('Posty kończą się na stronie 3.')
    expect(wrapper.find('nav[aria-label="Strony"]').exists()).toBe(false)

    await button(wrapper, 'Przejdź do strony 3').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query.page).toBe('2')
    expect(fetchPosts).toHaveBeenLastCalledWith(2, 50, {})
    // No list to keep: the skeleton, and the top only once the page is there.
    expect(wrapper.find('.posts .loading-state').exists()).toBe(true)
    expect(window.scrollTo).not.toHaveBeenCalled()

    last.resolve(answer([post('101')], 120))
    await flushPromises()

    expect(wrapper.find('article').text()).toContain('Wpis 101 o XTB')
    expect(window.scrollTo).toHaveBeenCalledExactlyOnceWith({ top: 0 })
    expect(document.activeElement).toBe(wrapper.find('h1').element)
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

  it('offers a retry after an error, keeps the error busy until the answer and shows the posts when it succeeds', async () => {
    const retried = deferred<Page<PostItem>>()
    vi.mocked(fetchPosts).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockReturnValueOnce(retried.promise)
    const { wrapper } = await mountFeed('/', document.body)
    expect(wrapper.find('[role="alert"] h2').text()).toBe('Nie udało się wczytać postów')

    await button(wrapper, 'Spróbuj ponownie').trigger('click')
    await flushPromises()

    expect(fetchPosts).toHaveBeenCalledTimes(2)
    expect(fetchPosts).toHaveBeenLastCalledWith(0, 50, {})
    // The error stays, no longer an alert, with its button busy; no skeleton in its place.
    const error = wrapper.find('.posts .state-error')
    expect(error.find('h2').text()).toBe('Nie udało się wczytać postów')
    expect(error.attributes('role')).toBeUndefined()
    expect(error.find('button').attributes('aria-busy')).toBe('true')
    expect(error.find('button').text()).toBe('Wczytywanie…')
    expect(wrapper.find('.loading-state').exists()).toBe(false)
    expect(wrapper.find('.posts').attributes('aria-busy')).toBe('true')

    retried.resolve(answer([post('1')], 1))
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('.state-error').exists()).toBe(false)
    expect(wrapper.find('article').text()).toContain('Wpis 1 o XTB')
    expect(document.activeElement).toBe(wrapper.find('h1').element)
    expect(window.scrollTo).not.toHaveBeenCalled()
  })

  it('announces another failed retry again and gives the focus back to "Spróbuj ponownie"', async () => {
    const retried = deferred<Page<PostItem>>()
    vi.mocked(fetchPosts).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockReturnValueOnce(retried.promise)
    const { wrapper } = await mountFeed('/', document.body)
    const first = wrapper.find('[role="alert"]').element

    await button(wrapper, 'Spróbuj ponownie').trigger('click')
    await flushPromises()
    retried.reject(new TypeError('Failed to fetch'))
    await flushPromises()

    const alert = wrapper.find('.posts [role="alert"]')
    expect(alert.find('h2').text()).toBe('Nie udało się wczytać postów')
    // A new element: a region whose role only comes back is not announced again.
    expect(alert.element).not.toBe(first)
    expect(document.activeElement).toBe(alert.find('button').element)
    expect(wrapper.find('.posts').attributes('aria-busy')).toBeUndefined()
    expect(reportFeedLoaded).toHaveBeenLastCalledWith('error')
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
      const alert = failed.wrapper.find('[role="alert"]')
      expect(alert.find('h2').text()).toBe('Nie udało się wczytać postów')
      expect(alert.find('.state-text').text()).toBe('Serwer nie odpowiedział. Twoje filtry zostały.')
    })

    it('tells an empty filter result from an empty feed and clears the filter', async () => {
      vi.mocked(fetchPosts).mockResolvedValueOnce(answer([], 0)).mockResolvedValueOnce(answer([post('1')], 120))
      const { wrapper, router } = await mountFeed('/?q=zzz&kind=asset', document.body)
      expect(wrapper.find('[role="status"]').text()).toBe('Żaden post nie pasuje do filtra.')
      const empty = wrapper.find('.posts .state')
      expect(empty.find('h2').text()).toBe('Brak postów dla tych filtrów')
      expect(empty.find('.state-text').text()).toBe('Zmień filtr albo wyczyść go, żeby zobaczyć wszystkie posty.')
      expect(wrapper.text()).not.toContain('Brak ocenionych postów')
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
