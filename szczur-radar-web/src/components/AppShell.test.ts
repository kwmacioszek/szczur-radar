import { type VueWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import { type Router, RouterView, createMemoryHistory } from 'vue-router'
import { type User, fetchMe, logout } from '../api/auth'
import { type Freshness, fetchFreshness } from '../api/freshness'
import { fetchNotifications } from '../api/notifications'
import { currentUser, resetSession } from '../auth/session'
import { createAppRouter } from '../router'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/freshness', () => ({ fetchFreshness: vi.fn() }))
vi.mock('../api/notifications', () => ({
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))
// The pages inside the shell are not under test: each is an empty component, the routes stay the real ones.
const page = vi.hoisted(() => ({ render: () => null }))
vi.mock('../views/FeedView.vue', () => ({ default: page }))
vi.mock('../views/PostPanel.vue', () => ({ default: page }))
vi.mock('../views/AdminView.vue', () => ({ default: page }))
vi.mock('../views/RunDetailView.vue', () => ({ default: page }))
vi.mock('../views/LoginView.vue', () => ({ default: page }))

const MINUTE = 60 * 1000
const START = new Date('2026-10-13T08:00:00Z')
const WYKOP: Freshness = { sources: [{ source: 'wykop', lastUpdatedAt: '2026-10-13T03:12:00Z' }] }

/**
 * The shell on `path`, logged in as `role`, in the router of the application: a host that only renders its view.
 * `prepare` sees the router before the shell mounts.
 */
async function mountShell(role: User['role'], path = '/posts', prepare?: (router: Router) => void) {
  vi.mocked(fetchMe).mockResolvedValue({ id: '1', email: `${role}@example.com`, role })
  const router = createAppRouter(createMemoryHistory())
  prepare?.(router)
  await router.push(path)
  const wrapper = mount({ render: () => h(RouterView) }, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

/** The text of the buttons of the bar, by their names. */
function names(wrapper: VueWrapper): string[] {
  return wrapper.findAll('header button').map((button) => button.attributes('aria-label') ?? button.text())
}

function link(wrapper: VueWrapper, name: string) {
  const found = wrapper.findAll('nav a').find((candidate) => candidate.text() === name)
  if (!found) throw new Error(`no link "${name}"`)
  return found
}

function visibility(state: DocumentVisibilityState) {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state)
}

enableAutoUnmount(afterEach)

describe('AppShell', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'], now: START })
    resetSession()
    vi.mocked(fetchMe).mockReset()
    vi.mocked(logout).mockReset().mockResolvedValue(undefined)
    vi.mocked(fetchFreshness).mockReset().mockResolvedValue(WYKOP)
    vi.mocked(fetchNotifications).mockReset().mockResolvedValue({ unread: 1, items: [] })
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  describe('structure', () => {
    it('has one banner, one navigation and one main around the page', async () => {
      const { wrapper } = await mountShell('user')
      expect(wrapper.findAll('header')).toHaveLength(1)
      expect(wrapper.findAll('nav[aria-label="Główna"]')).toHaveLength(1)
      expect(wrapper.findAll('main')).toHaveLength(1)
    })

    it.each([
      ['/admin', 'column-wide'],
      ['/admin/runs/1', 'column-narrow'],
    ])('puts %s in the %s container, inside main', async (path, expected) => {
      const { wrapper } = await mountShell('admin', path)
      const container = wrapper.get('main > div')
      expect(container.classes()).toContain('column')
      expect(container.classes()).toContain(expected)
    })

    it('follows the column of the route when the page changes', async () => {
      const { wrapper, router } = await mountShell('admin', '/admin/runs/1')
      expect(wrapper.get('main > div').classes()).toContain('column-narrow')

      await router.push('/admin')

      expect(wrapper.get('main > div').classes()).toContain('column-wide')
      expect(wrapper.get('main > div').classes()).not.toContain('column-narrow')

      await router.push('/posts')

      expect(wrapper.get('main > div').classes()).toEqual([])
    })

    it.each([
      ['/posts', 'posts'],
      ['/posts/1', 'post'],
    ])('gives a route without a column, the list of posts on %s, the whole column of the shell', async (path, name) => {
      const { wrapper, router } = await mountShell('admin', path)

      expect(router.currentRoute.value.name).toBe(name)
      expect(router.currentRoute.value.meta.column).toBeUndefined()
      expect(wrapper.get('main > div').classes()).toEqual([])
    })

    it('keeps the bar when the page changes between its children', async () => {
      const { wrapper, router } = await mountShell('admin')
      const bar = wrapper.get('header').element

      await router.push('/posts/1')
      await router.push('/admin')
      await router.push('/admin/runs/1')

      expect(wrapper.get('header').element).toBe(bar)
    })
  })

  describe('navigation', () => {
    it('gives a plain user only "Posty"', async () => {
      const { wrapper } = await mountShell('user')
      expect(wrapper.findAll('nav a').map((item) => item.text())).toEqual(['Posty'])
    })

    it('gives an admin "Posty" and "Panel admina"', async () => {
      const { wrapper } = await mountShell('admin')
      expect(wrapper.findAll('nav a').map((item) => item.text())).toEqual(['Posty', 'Panel admina'])
    })

    it.each([
      ['/posts', 'Posty'],
      ['/posts/1', 'Posty'],
      ['/admin', 'Panel admina'],
      ['/admin/runs/1', 'Panel admina'],
    ])('marks the section of %s as the current page: %s, and no other item', async (path, current) => {
      const { wrapper } = await mountShell('admin', path)
      const marked = wrapper.findAll('nav a[aria-current="page"]')
      expect(marked.map((item) => item.text())).toEqual([current])
    })

    it('moves the mark when another section is opened', async () => {
      const { wrapper } = await mountShell('admin')
      expect(link(wrapper, 'Posty').attributes('aria-current')).toBe('page')

      await link(wrapper, 'Panel admina').trigger('click')
      await flushPromises()

      expect(link(wrapper, 'Panel admina').attributes('aria-current')).toBe('page')
      expect(link(wrapper, 'Posty').attributes('aria-current')).toBeUndefined()
    })
  })

  describe('session controls', () => {
    it('shows an admin the notification bell, the theme button and the e-mail', async () => {
      const { wrapper } = await mountShell('admin')
      expect(wrapper.find('button[aria-controls]').attributes('aria-label')).toBe('Powiadomienia, 1 nieprzeczytane')
      expect(fetchNotifications).toHaveBeenCalledTimes(1)
      expect(wrapper.get('header').text()).toContain('admin@example.com')
    })

    it('has the theme button next to the logout button', async () => {
      const { wrapper } = await mountShell('user')
      expect(names(wrapper)).toContain('Ciemny motyw')
      expect(names(wrapper)).toContain('Wyloguj')
    })

    it('shows a plain user no bell and asks for no notifications', async () => {
      const { wrapper } = await mountShell('user')
      expect(wrapper.find('button[aria-controls]').exists()).toBe(false)
      expect(names(wrapper).some((name) => name.startsWith('Powiadomienia'))).toBe(false)
      expect(fetchNotifications).not.toHaveBeenCalled()
    })

    it('signs out: ends the session on the server, forgets it and opens /login', async () => {
      const { wrapper, router } = await mountShell('user')

      await wrapper.get('header').findAll('button').find((button) => button.text() === 'Wyloguj')!.trigger('click')
      await flushPromises()

      expect(logout).toHaveBeenCalledTimes(1)
      expect(currentUser.value).toBeNull()
      expect(router.currentRoute.value.name).toBe('login')
    })

    it('stays logged in and says so when signing out fails', async () => {
      vi.mocked(logout).mockRejectedValue(new TypeError('Failed to fetch'))
      const { wrapper, router } = await mountShell('user')

      await wrapper.get('header').findAll('button').find((button) => button.text() === 'Wyloguj')!.trigger('click')
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toBe('Nie udało się wylogować. Spróbuj ponownie.')
      expect(currentUser.value).not.toBeNull()
      expect(router.currentRoute.value.name).toBe('posts')
    })
  })

  describe('date of the data', () => {
    it('shows each source with its day and hour from the API, with the instant in the time element', async () => {
      const { wrapper } = await mountShell('user')
      expect(fetchFreshness).toHaveBeenCalledTimes(1)
      expect(wrapper.get('header').text()).toContain('Dane:')
      expect(wrapper.get('header').text()).toContain('Wykop 13.10.2026, 05:12')
      expect(wrapper.get('header time').attributes('datetime')).toBe('2026-10-13T03:12:00Z')
    })

    it('says the data was never updated before the first update', async () => {
      vi.mocked(fetchFreshness).mockResolvedValue({ sources: [] })
      const { wrapper } = await mountShell('user')
      expect(wrapper.get('header').text()).toContain('Dane nie były jeszcze aktualizowane.')
      expect(wrapper.find('header time').exists()).toBe(false)
    })

    it('reads it again after a page change once the last read is a minute old', async () => {
      const { router } = await mountShell('user')
      vi.setSystemTime(START.getTime() + MINUTE)

      await router.push('/posts/1')
      await flushPromises()

      expect(fetchFreshness).toHaveBeenCalledTimes(2)
    })

    it('does not read it again after a page change within the minute', async () => {
      const { router } = await mountShell('user')
      vi.setSystemTime(START.getTime() + MINUTE - 1)

      await router.push('/posts/1')
      await flushPromises()

      expect(fetchFreshness).toHaveBeenCalledTimes(1)
    })

    it('reads it again when the tab becomes visible after a minute, not while it is hidden or fresh', async () => {
      await mountShell('user')
      vi.setSystemTime(START.getTime() + MINUTE)

      visibility('hidden')
      document.dispatchEvent(new Event('visibilitychange'))
      await flushPromises()
      expect(fetchFreshness).toHaveBeenCalledTimes(1)

      visibility('visible')
      document.dispatchEvent(new Event('visibilitychange'))
      await flushPromises()
      expect(fetchFreshness).toHaveBeenCalledTimes(2)

      document.dispatchEvent(new Event('visibilitychange'))
      await flushPromises()
      expect(fetchFreshness).toHaveBeenCalledTimes(2)
    })

    it('does not ask for it on the way to /login after signing out', async () => {
      const { wrapper } = await mountShell('user')
      vi.setSystemTime(START.getTime() + MINUTE)

      await wrapper.get('header').findAll('button').find((button) => button.text() === 'Wyloguj')!.trigger('click')
      await flushPromises()

      expect(fetchFreshness).toHaveBeenCalledTimes(1)
    })

    it('does not ask for it after a navigation that did not happen', async () => {
      const { router } = await mountShell('user')
      vi.setSystemTime(START.getTime() + MINUTE)

      // The same address again: the router ends it with a failure.
      const failure = await router.push('/posts')
      await flushPromises()

      expect(failure).toBeTruthy()
      expect(fetchFreshness).toHaveBeenCalledTimes(1)
    })

    it('keeps the previous date when a later read fails', async () => {
      const { wrapper, router } = await mountShell('user')
      vi.mocked(fetchFreshness).mockRejectedValue(new TypeError('Failed to fetch'))
      vi.setSystemTime(START.getTime() + MINUTE)

      await router.push('/posts/1')
      await flushPromises()

      expect(fetchFreshness).toHaveBeenCalledTimes(2)
      expect(wrapper.get('header').text()).toContain('Wykop 13.10.2026, 05:12')
    })

    it('keeps the previous date while the next read is under way', async () => {
      const { wrapper, router } = await mountShell('user')
      vi.mocked(fetchFreshness).mockReturnValue(new Promise(() => {}))
      vi.setSystemTime(START.getTime() + MINUTE)

      await router.push('/posts/1')
      await flushPromises()

      expect(fetchFreshness).toHaveBeenCalledTimes(2)
      expect(wrapper.get('header').text()).toContain('Wykop 13.10.2026, 05:12')
    })

    it('asks only once while a read is under way', async () => {
      vi.mocked(fetchFreshness).mockReturnValue(new Promise(() => {}))
      const { router } = await mountShell('user')

      await router.push('/posts/1')
      await router.push('/posts')
      visibility('visible')
      document.dispatchEvent(new Event('visibilitychange'))
      await flushPromises()

      expect(fetchFreshness).toHaveBeenCalledTimes(1)
    })

    it('shows the new date when a later read succeeds', async () => {
      const { wrapper, router } = await mountShell('user')
      vi.mocked(fetchFreshness).mockResolvedValue({ sources: [{ source: 'wykop', lastUpdatedAt: '2026-10-14T03:12:00Z' }] })
      vi.setSystemTime(START.getTime() + MINUTE)

      await router.push('/posts/1')
      await flushPromises()

      expect(wrapper.get('header').text()).toContain('Wykop 14.10.2026, 05:12')
    })

    it('has no label when it cannot be read, and tries again on the next page change', async () => {
      vi.mocked(fetchFreshness).mockRejectedValue(new TypeError('Failed to fetch'))
      const { wrapper, router } = await mountShell('user')
      expect(wrapper.get('header').text()).not.toContain('Dane')
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)

      await router.push('/posts/1')
      await flushPromises()

      expect(fetchFreshness).toHaveBeenCalledTimes(2)
    })

    it('has no label for an answer without sources', async () => {
      // An API not deployed yet answers with its own shape (API and web deploy independently).
      vi.mocked(fetchFreshness).mockResolvedValue({ lastUpdatedAt: '2026-10-13T03:12:00Z' } as unknown as Freshness)
      const { wrapper } = await mountShell('user')
      expect(wrapper.get('header').text()).not.toContain('Dane')
    })

    it('stops listening when it is unmounted', async () => {
      // The router's own hooks are registered before `prepare`: what is recorded here is the shell's.
      const removers: (() => void)[] = []
      const added = vi.spyOn(document, 'addEventListener')
      const removed = vi.spyOn(document, 'removeEventListener')
      const { wrapper, router } = await mountShell('user', '/', (target) => {
        const register = target.afterEach.bind(target)
        vi.spyOn(target, 'afterEach').mockImplementation((guard) => {
          const remove = vi.fn(register(guard))
          removers.push(remove)
          return remove
        })
      })
      const listener = added.mock.calls.find(([type]) => type === 'visibilitychange')?.[1]
      expect(listener).toBeTypeOf('function')
      expect(removers).toHaveLength(1)

      wrapper.unmount()

      for (const remove of removers) expect(remove).toHaveBeenCalledTimes(1)
      expect(removed).toHaveBeenCalledWith('visibilitychange', listener)
      vi.setSystemTime(START.getTime() + MINUTE)

      await router.push('/posts/1')
      visibility('visible')
      document.dispatchEvent(new Event('visibilitychange'))
      await flushPromises()

      expect(fetchFreshness).toHaveBeenCalledTimes(1)
    })
  })
})
