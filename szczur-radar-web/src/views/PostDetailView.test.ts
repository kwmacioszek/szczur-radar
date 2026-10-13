import { type VueWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { type RouterHistory, createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { ApiError } from '../api/client'
import { type PostItem, fetchPost, fetchPostRaw } from '../api/posts'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import PostDetailView from './PostDetailView.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/posts', () => ({
  fetchPost: vi.fn(),
  fetchPostRaw: vi.fn(),
  fetchPosts: vi.fn(),
  fetchFailedPosts: vi.fn(),
}))

const LONG_BODY =
  'CDR po wynikach -8% na otwarciu, ktoś jeszcze trzyma?\nJa dobieram, bo wycena po spadku wygląda rozsądnie, a guidance na Q4 nie zmieniło się mimo opóźnienia premiery. Zobaczymy, czy rynek to doceni przed świętami, bo historycznie grudzień był dla nich mocny.'

function post(overrides: Partial<PostItem> = {}): PostItem {
  return {
    id: '1',
    itemType: 'post',
    publishedAt: '2026-10-01T07:15:00Z',
    title: null,
    body: LONG_BODY,
    author: 'uzytkownik1',
    webUrl: 'https://wykop.pl/wpis/81234567',
    assessment: {
      output: {
        relevance: 'investment_related',
        modifiers: [],
        targets: [
          {
            id: 't1',
            kind: 'company',
            anchor: { document: 'item', field: 'body', quote: 'CDR' },
            stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: ['c1'] },
            reference: 'explicit',
          },
        ],
        claims: [
          {
            id: 'c1',
            kind: 'trade_action',
            attribution: 'author',
            target_ids: ['t1'],
            evidence: { document: 'item', field: 'body', quote: 'Ja dobieram' },
            statement: 'Autor dokupuje akcje CDR po spadku.',
          },
        ],
        flags: [],
      },
      resultSource: 'model',
      assessedAt: '2026-10-02T08:00:00Z',
    },
    ...overrides,
  }
}

const RAW = '{\n  "id" : 81234567,\n  "content" : "Zażółć gęślą jaźń"\n}'

function never<T>(): Promise<T> {
  return new Promise(() => {})
}

/** A memory history whose entries say `back` is the entry before them, as the browser's history does. */
function historyWithBack(back: string | null): RouterHistory {
  const history = createMemoryHistory()
  return new Proxy(history, {
    get: (target, key) => (key === 'state' ? { ...target.state, back } : Reflect.get(target, key)),
  })
}

/** `attachTo`: in the document, for focus. */
async function mountPost(path = '/posts/1', { history = createMemoryHistory(), attachTo }: { history?: RouterHistory; attachTo?: HTMLElement } = {}) {
  const router = createAppRouter(history)
  await router.push(path)
  const wrapper = mount(PostDetailView, { global: { plugins: [router] }, attachTo })
  await flushPromises()
  return { wrapper, router }
}

function button(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!found) throw new Error(`no button "${text}"`)
  return found
}

/** Opens or closes the source data section the way a click on its summary does. */
async function toggleRaw(wrapper: VueWrapper, open: boolean): Promise<void> {
  const details = wrapper.find('details')
  ;(details.element as HTMLDetailsElement).open = open
  await details.trigger('toggle')
  await flushPromises()
}

enableAutoUnmount(afterEach)

describe('PostDetailView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'user@example.com', role: 'user' })
    vi.mocked(fetchPost).mockReset()
    vi.mocked(fetchPostRaw).mockReset()
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('says it is loading the post', async () => {
    vi.mocked(fetchPost).mockReturnValue(never())
    const { wrapper } = await mountPost()
    expect(fetchPost).toHaveBeenCalledWith('1')
    expect(wrapper.find('.loading').text()).toBe('Wczytywanie posta…')
    expect(wrapper.find('section[aria-labelledby="post-title"]').attributes('aria-busy')).toBe('true')
  })

  it('offers a retry after an error and shows the post when it succeeds', async () => {
    vi.mocked(fetchPost).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(post())
    const { wrapper } = await mountPost()
    expect(wrapper.find('[role="alert"]').text()).toBe('Nie udało się wczytać posta.')

    await button(wrapper, 'Spróbuj ponownie').trigger('click')
    await flushPromises()

    expect(fetchPost).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('h1').text()).toBe('Wpis użytkownika uzytkownik1')
    expect(wrapper.find('section').attributes('aria-busy')).toBeUndefined()
  })

  it('treats a server error as an error, not as a missing post', async () => {
    vi.mocked(fetchPost).mockRejectedValue(new ApiError(500, 'internal_error'))
    const { wrapper } = await mountPost()
    expect(wrapper.find('[role="alert"]').text()).toBe('Nie udało się wczytać posta.')
    expect(wrapper.find('.empty').exists()).toBe(false)
  })

  it.each([
    [404, 'post_not_found'],
    [400, 'invalid_request'],
  ])('says there is no such post on %i, with a link to the list', async (status, detail) => {
    vi.mocked(fetchPost).mockRejectedValue(new ApiError(status, detail))
    const { wrapper } = await mountPost('/posts/x')
    const message = wrapper.find('.empty')
    expect(message.text()).toContain('Nie ma takiego posta albo nie został jeszcze oceniony.')
    expect(message.find('a').attributes('href')).toBe('/')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('button').text()).toBe('Wyloguj')
  })

  it.each([
    ['the title', post({ itemType: 'link', title: 'Orlen ogłasza buyback za 4 mld zł' }), 'Orlen ogłasza buyback za 4 mld zł'],
    ['the type and author without a title', post(), 'Wpis użytkownika uzytkownik1'],
    ['the type alone without an author', post({ itemType: 'comment', author: null }), 'Komentarz'],
  ])('heads the post with %s', async (_, item, heading) => {
    vi.mocked(fetchPost).mockResolvedValue(item)
    const { wrapper } = await mountPost()
    expect(wrapper.find('h1').text()).toBe(heading)
  })

  it('shows the date, type and author', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    const { wrapper } = await mountPost()
    const meta = wrapper.find('.meta')
    expect(meta.find('time').attributes('datetime')).toBe('2026-10-01T07:15:00Z')
    expect(meta.find('time').text()).toBe('1.10.2026, 09:15')
    expect(meta.find('.type').text()).toBe('wpis')
    expect(meta.find('.author').text()).toBe('uzytkownik1')
  })

  it('shows the full text with its line breaks', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    const { wrapper } = await mountPost()
    expect(wrapper.find('.body').element.textContent).toBe(LONG_BODY)
  })

  it('shows a link description under its title', async () => {
    vi.mocked(fetchPost).mockResolvedValue(
      post({ itemType: 'link', title: 'Orlen ogłasza buyback', body: 'Zarząd rekomenduje skup akcji własnych.' }),
    )
    const { wrapper } = await mountPost()
    expect(wrapper.find('h1').text()).toBe('Orlen ogłasza buyback')
    expect(wrapper.find('.body').text()).toBe('Zarząd rekomenduje skup akcji własnych.')
  })

  it('links the original in a new tab', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    const { wrapper } = await mountPost()
    const link = wrapper.find('a.original')
    expect(link.text()).toBe('Otwórz oryginał na wykop.pl')
    expect(link.attributes('href')).toBe('https://wykop.pl/wpis/81234567')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
    expect(link.attributes('aria-label')).toBe('Otwórz oryginał na wykop.pl (otwiera się w nowej karcie)')
  })

  it.each([null, 'javascript:alert(1)', 'ftp://wykop.pl/wpis/1', 'not a url'])(
    'has no link to the original for the address %s',
    async (webUrl) => {
      vi.mocked(fetchPost).mockResolvedValue(post({ webUrl }))
      const { wrapper } = await mountPost()
      expect(wrapper.find('a.original').exists()).toBe(false)
      expect(wrapper.find('h1').text()).toBe('Wpis użytkownika uzytkownik1')
    },
  )

  it('shows the assessment, or says there is none', async () => {
    vi.mocked(fetchPost).mockResolvedValueOnce(post()).mockResolvedValueOnce(post({ id: '2', assessment: null }))
    const { wrapper, router } = await mountPost()
    expect(wrapper.find('#assessment-title').text()).toBe('Ocena')
    expect(wrapper.find('.post-assessment').text()).toContain('Autor dokupuje akcje CDR po spadku.')

    await router.push('/posts/2')
    await flushPromises()

    expect(wrapper.find('.post-assessment').exists()).toBe(false)
    expect(wrapper.find('.part .empty').text()).toBe('Brak oceny.')
  })

  it('focuses the heading once the post is loaded', async () => {
    vi.mocked(fetchPost).mockResolvedValue(post())
    const { wrapper } = await mountPost('/posts/1', { attachTo: document.body })
    expect(document.activeElement).toBe(wrapper.find('h1').element)
  })

  it('loads another post when the id changes and ignores the late answer of the previous one', async () => {
    const answers = new Map<string, (value: PostItem) => void>()
    vi.mocked(fetchPost).mockImplementation((id) => new Promise((resolve) => answers.set(id, resolve)))
    const { wrapper, router } = await mountPost()

    await router.push('/posts/2')
    await flushPromises()
    answers.get('2')!(post({ id: '2', title: 'Drugi post' }))
    await flushPromises()
    answers.get('1')!(post({ title: 'Pierwszy post' }))
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Drugi post')
  })

  describe('source data', () => {
    it('is collapsed and fetched only on the first opening', async () => {
      vi.mocked(fetchPost).mockResolvedValue(post())
      vi.mocked(fetchPostRaw).mockResolvedValue(RAW)
      const { wrapper } = await mountPost()
      expect(wrapper.find('summary').text()).toBe('Dane dla nerdów: surowy JSON źródła')
      expect((wrapper.find('details').element as HTMLDetailsElement).open).toBe(false)
      expect(fetchPostRaw).not.toHaveBeenCalled()

      await toggleRaw(wrapper, true)
      expect(fetchPostRaw).toHaveBeenCalledExactlyOnceWith('1')
      expect(wrapper.find('pre').element.textContent).toBe(RAW)

      await toggleRaw(wrapper, false)
      await toggleRaw(wrapper, true)
      expect(fetchPostRaw).toHaveBeenCalledTimes(1)
    })

    it('says it is loading the data', async () => {
      vi.mocked(fetchPost).mockResolvedValue(post())
      vi.mocked(fetchPostRaw).mockReturnValue(never())
      const { wrapper } = await mountPost()
      await toggleRaw(wrapper, true)
      expect(wrapper.find('details .loading').text()).toBe('Wczytywanie danych…')
    })

    it('offers a retry after an error', async () => {
      vi.mocked(fetchPost).mockResolvedValue(post())
      vi.mocked(fetchPostRaw).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(RAW)
      const { wrapper } = await mountPost()
      await toggleRaw(wrapper, true)
      expect(wrapper.find('details [role="alert"]').text()).toBe('Nie udało się wczytać danych źródła.')

      await wrapper.find('details button').trigger('click')
      await flushPromises()

      expect(fetchPostRaw).toHaveBeenCalledTimes(2)
      expect(wrapper.find('details [role="alert"]').exists()).toBe(false)
      expect(wrapper.find('pre').element.textContent).toBe(RAW)
    })

    it('keeps the focus in the section when the retry button goes away', async () => {
      vi.mocked(fetchPost).mockResolvedValue(post())
      vi.mocked(fetchPostRaw).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockReturnValue(new Promise(() => {}))
      const { wrapper } = await mountPost('/posts/1', { attachTo: document.body })
      await toggleRaw(wrapper, true)

      await wrapper.find('details button').trigger('click')
      await flushPromises()

      expect(wrapper.find('details .loading').exists()).toBe(true)
      expect(document.activeElement).toBe(wrapper.find('summary').element)
    })
  })

  describe('the link back to the list', () => {
    it('goes back in history when the previous entry is the list', async () => {
      vi.mocked(fetchPost).mockResolvedValue(post())
      const { wrapper, router } = await mountPost('/posts/1', { history: historyWithBack('/?page=1') })
      const back = vi.spyOn(router, 'back').mockImplementation(() => {})
      const link = wrapper.find('a.back')
      expect(link.text()).toBe('← Posty')
      expect(link.find('span[aria-hidden="true"]').text()).toBe('←')
      expect(link.attributes('href')).toBe('/?page=1')

      await link.trigger('click')

      expect(back).toHaveBeenCalledTimes(1)
    })

    it('goes to the list after a deep link', async () => {
      vi.mocked(fetchPost).mockResolvedValue(post())
      const { wrapper, router } = await mountPost()
      const back = vi.spyOn(router, 'back')
      expect(wrapper.find('a.back').attributes('href')).toBe('/')

      await wrapper.find('a.back').trigger('click')
      await flushPromises()

      expect(back).not.toHaveBeenCalled()
      expect(router.currentRoute.value.fullPath).toBe('/')
    })

    it('goes to the list when the previous entry is another page', async () => {
      vi.mocked(fetchPost).mockResolvedValue(post())
      const { wrapper, router } = await mountPost('/posts/1', { history: historyWithBack('/admin') })
      const back = vi.spyOn(router, 'back')

      await wrapper.find('a.back').trigger('click')
      await flushPromises()

      expect(back).not.toHaveBeenCalled()
      expect(router.currentRoute.value.fullPath).toBe('/')
    })
  })
})
