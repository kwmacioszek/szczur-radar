import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { type RouterHistory, createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { ApiError, type ErrorCode } from '../api/client'
import { type PostItem, fetchPost, fetchPostRaw } from '../api/posts'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import PostPanel from './PostPanel.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/posts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/posts')>()),
  fetchPost: vi.fn(),
  fetchPostRaw: vi.fn(),
  fetchPosts: vi.fn(),
  fetchFailedPosts: vi.fn(),
}))

function post(id: string, overrides: Partial<PostItem> = {}): PostItem {
  return {
    id,
    source: 'wykop',
    channel: 'gielda',
    itemType: 'post',
    publishedAt: '2026-10-01T07:15:00Z',
    title: null,
    body: `Wpis ${id} o CDR`,
    author: `uzytkownik${id}`,
    webUrl: null,
    assessment: null,
    ...overrides,
  }
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

/** A memory history whose entries say `back` is the entry before them, as the browser's history does. */
function historyWithBack(back: string | null): RouterHistory {
  const history = createMemoryHistory()
  return new Proxy(history, {
    get: (target, key) => (key === 'state' ? { ...target.state, back } : Reflect.get(target, key)),
  })
}

/** The panel on `path` with the rows `items`, in the document (it moves itself to the body), with #app to make inert. */
async function mountPanel(path: string, { items = [] as PostItem[], history = createMemoryHistory() } = {}) {
  const app = document.createElement('div')
  app.id = 'app'
  document.body.append(app)
  const router = createAppRouter(history)
  await router.push(path)
  const wrapper = mount(PostPanel, { props: { items }, global: { plugins: [router] }, attachTo: app })
  await flushPromises()
  return { wrapper, router }
}

function dialog(): HTMLElement {
  const found = document.querySelector<HTMLElement>('[role="dialog"]')
  if (!found) throw new Error('no dialog')
  return found
}

/** The dialog's name: the text of the element its aria-labelledby points at. */
function dialogName(): string | undefined {
  return document.getElementById(dialog().getAttribute('aria-labelledby') ?? '')?.textContent?.trim()
}

function button(name: string): HTMLButtonElement {
  const found = [...dialog().querySelectorAll('button')].find(
    (candidate) => (candidate.getAttribute('aria-label') ?? candidate.textContent?.trim()) === name,
  )
  if (!found) throw new Error(`no button "${name}"`)
  return found
}

enableAutoUnmount(afterEach)

describe('PostPanel', () => {
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
    document.body.innerHTML = ''
    document.body.removeAttribute('style')
  })

  it('shows a row of the page on screen at once, without a request, named and focused by its title', async () => {
    await mountPanel('/posts/2', { items: [post('1'), post('2', { title: 'Orlen ogłasza buyback' })] })
    expect(fetchPost).not.toHaveBeenCalled()
    expect(dialogName()).toBe('Orlen ogłasza buyback')
    expect(document.activeElement).toBe(dialog().querySelector('h2'))
    expect(dialog().querySelector('.meta')?.textContent).toContain('Wykop · #gielda')
    expect(dialog().getAttribute('aria-modal')).toBe('true')
  })

  it('reads a post that is not on the page from the API, busy until it comes', async () => {
    const answer = deferred<PostItem>()
    vi.mocked(fetchPost).mockReturnValue(answer.promise)
    await mountPanel('/posts/7', { items: [post('1')] })
    expect(fetchPost).toHaveBeenCalledExactlyOnceWith('7')
    const content = dialog().querySelector('.post-panel')!
    expect(content.getAttribute('aria-busy')).toBe('true')
    expect(content.querySelector('.loading-state')?.textContent).toBe('Wczytywanie…')
    expect(dialogName()).toBe('Wczytywanie posta')
    // The bar has only its close button until the post is there.
    expect(dialog().querySelector('.meta')).toBeNull()
    button('Zamknij post')

    answer.resolve(post('7'))
    await flushPromises()

    expect(dialogName()).toBe('Wpis użytkownika uzytkownik7')
    expect(content.getAttribute('aria-busy')).toBeNull()
    expect(document.activeElement).toBe(dialog().querySelector('h2'))
  })

  it('reads the post itself while the list still loads, and ignores the list when it comes later', async () => {
    const answer = deferred<PostItem>()
    vi.mocked(fetchPost).mockReturnValue(answer.promise)
    const { wrapper } = await mountPanel('/posts/2')
    expect(fetchPost).toHaveBeenCalledTimes(1)

    await wrapper.setProps({ items: [post('2', { title: 'Z listy' })] })
    answer.resolve(post('2', { title: 'Z API' }))
    await flushPromises()

    expect(fetchPost).toHaveBeenCalledTimes(1)
    expect(dialogName()).toBe('Z API')
  })

  it('opens another post when the id changes and drops the late answer of the previous one', async () => {
    const answers = new Map<string, (value: PostItem) => void>()
    vi.mocked(fetchPost).mockImplementation((id) => new Promise((resolve) => answers.set(id, resolve)))
    const { router } = await mountPanel('/posts/1')

    await router.push('/posts/2')
    await flushPromises()
    answers.get('2')!(post('2', { title: 'Drugi post' }))
    await flushPromises()
    answers.get('1')!(post('1', { title: 'Pierwszy post' }))
    await flushPromises()

    expect(dialogName()).toBe('Drugi post')
  })

  it('drops the answer of a request for a post that a row of the page has replaced', async () => {
    const answer = deferred<PostItem>()
    vi.mocked(fetchPost).mockReturnValue(answer.promise)
    const { wrapper, router } = await mountPanel('/posts/9')
    await wrapper.setProps({ items: [post('2', { title: 'Z listy' })] })

    await router.push('/posts/2')
    await flushPromises()
    answer.resolve(post('9', { title: 'Spóźniony' }))
    await flushPromises()

    expect(dialogName()).toBe('Z listy')
  })

  it('offers a retry after an error, busy until the answer, and shows the post when it succeeds', async () => {
    const retried = deferred<PostItem>()
    vi.mocked(fetchPost).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockReturnValueOnce(retried.promise)
    await mountPanel('/posts/7')
    const alert = dialog().querySelector('[role="alert"]')!
    expect(alert.querySelector('h2')?.textContent).toBe('Nie udało się wczytać posta')
    expect(dialogName()).toBe('Nie udało się wczytać posta')

    button('Spróbuj ponownie').click()
    await flushPromises()

    expect(fetchPost).toHaveBeenCalledTimes(2)
    expect(dialog().querySelector('.post-panel')?.getAttribute('aria-busy')).toBe('true')
    expect(dialog().querySelector('.state-error')?.getAttribute('role')).toBeNull()

    retried.resolve(post('7'))
    await flushPromises()

    expect(dialog().querySelector('.state-error')).toBeNull()
    expect(document.activeElement).toBe(dialog().querySelector('h2'))
  })

  it('gives the focus back to "Spróbuj ponownie" after another failed retry', async () => {
    vi.mocked(fetchPost).mockRejectedValue(new TypeError('Failed to fetch'))
    await mountPanel('/posts/7')

    button('Spróbuj ponownie').click()
    await flushPromises()

    expect(document.activeElement).toBe(button('Spróbuj ponownie'))
  })

  it.each<[number, ErrorCode]>([
    [404, 'post_not_found'],
    [400, 'invalid_request'],
  ])('says there is no such post on %i', async (status, detail) => {
    vi.mocked(fetchPost).mockRejectedValue(new ApiError(status, detail))
    await mountPanel('/posts/x')
    expect(dialogName()).toBe('Nie ma takiego posta')
    expect(dialog().querySelector('.state-text')?.textContent?.trim()).toBe('Post nie istnieje albo nie został jeszcze oceniony.')
    expect(dialog().querySelector('[role="alert"]')).toBeNull()
  })

  it('treats a server error as an error, not as a missing post', async () => {
    vi.mocked(fetchPost).mockRejectedValue(new ApiError(500, 'internal_error'))
    await mountPanel('/posts/7')
    expect(dialogName()).toBe('Nie udało się wczytać posta')
  })

  describe('closing', () => {
    it('goes back in history when the entry before is the same list', async () => {
      const { router } = await mountPanel('/posts/1?page=1&direction=bullish', {
        items: [post('1')],
        history: historyWithBack('/posts?page=1&direction=bullish'),
      })
      const back = vi.spyOn(router, 'back').mockImplementation(() => {})
      const replace = vi.spyOn(router, 'replace')

      button('Zamknij post').click()

      expect(back).toHaveBeenCalledTimes(1)
      expect(replace).not.toHaveBeenCalled()
    })

    it.each([
      ['after a link to the post', null],
      ['when the entry before is another page', '/admin'],
      ['when the entry before is the list with another query', '/posts?page=2'],
    ])('replaces the address with the list and its query %s', async (_, previous) => {
      const { router } = await mountPanel('/posts/1?page=1', { items: [post('1')], history: historyWithBack(previous) })
      const back = vi.spyOn(router, 'back')

      button('Zamknij post').click()
      await flushPromises()

      expect(back).not.toHaveBeenCalled()
      expect(router.currentRoute.value.fullPath).toBe('/posts?page=1')
    })

    it('closes on Escape and on the scrim as on its button', async () => {
      const { router } = await mountPanel('/posts/1', { items: [post('1')] })
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await flushPromises()
      expect(router.currentRoute.value.fullPath).toBe('/posts')

      await router.push('/posts/1')
      await flushPromises()
      document.querySelector<HTMLElement>('.scrim')?.click()
      await flushPromises()
      expect(router.currentRoute.value.fullPath).toBe('/posts')
    })

    it('says which post it showed when it goes', async () => {
      const { wrapper } = await mountPanel('/posts/1', { items: [post('1')] })
      wrapper.unmount()
      expect(wrapper.emitted('closed')).toEqual([['1']])
    })
  })
})
