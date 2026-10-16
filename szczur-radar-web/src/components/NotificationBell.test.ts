import { type VueWrapper, enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { ApiError } from '../api/client'
import {
  type AdminNotification,
  type NotificationList,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notifications'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import NotificationBell from './NotificationBell.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/notifications', () => ({
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const MINUTE = 60 * 1000

function notification(id: string, overrides: Partial<AdminNotification> = {}): AdminNotification {
  return {
    id,
    kind: 'run_attention',
    createdAt: new Date(Date.now() - 5 * MINUTE).toISOString(),
    readAt: null,
    run: {
      id: `run-${id}`,
      kind: 'range',
      status: 'interrupted',
      interruptionCause: 'deploy',
      finishedAt: '2026-10-15T07:00:00Z',
      assessedFailed: 0,
      dead: 0,
      processingErrors: 0,
      error: 'interrupted: a new deployment took over',
    },
    ...overrides,
  }
}

function list(items: AdminNotification[], unread = items.filter((item) => !item.readAt).length): NotificationList {
  return { unread, items }
}

function never<T>(): Promise<T> {
  return new Promise(() => {})
}

/** `attachTo`: in the document, for focus. */
async function mountBell({ attachTo }: { attachTo?: HTMLElement } = {}) {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/admin')
  const wrapper = mount(NotificationBell, { global: { plugins: [router] }, attachTo })
  await flushPromises()
  return { wrapper, router }
}

function bell(wrapper: VueWrapper) {
  return wrapper.find('button[aria-controls]')
}

function panel(wrapper: VueWrapper) {
  return wrapper.find('[role="region"]')
}

function button(wrapper: VueWrapper, text: string) {
  const found = wrapper.findAll('button').find((candidate) => candidate.text() === text)
  if (!found) throw new Error(`no button "${text}"`)
  return found
}

async function open(wrapper: VueWrapper): Promise<void> {
  await bell(wrapper).trigger('click')
}

enableAutoUnmount(afterEach)

describe('NotificationBell', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    vi.mocked(fetchNotifications).mockReset().mockResolvedValue(list([]))
    vi.mocked(markNotificationRead).mockReset().mockResolvedValue(undefined)
    vi.mocked(markAllNotificationsRead).mockReset().mockResolvedValue(undefined)
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it.each([
    [3, 'Powiadomienia, 3 nieprzeczytane'],
    [1, 'Powiadomienia, 1 nieprzeczytane'],
    [5, 'Powiadomienia, 5 nieprzeczytanych'],
    [12, 'Powiadomienia, 12 nieprzeczytanych'],
  ])('shows %i unread on the bell and says so in its name', async (unread, name) => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a')], unread))
    const { wrapper } = await mountBell()
    expect(bell(wrapper).attributes('aria-label')).toBe(name)
    expect(bell(wrapper).find('.count').text()).toBe(String(unread))
    expect(bell(wrapper).find('.count').attributes('aria-hidden')).toBe('true')
    expect(bell(wrapper).find('svg').attributes('aria-hidden')).toBe('true')
  })

  it('hides the count when nothing is unread', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a', { readAt: '2026-10-15T07:30:00Z' })]))
    const { wrapper } = await mountBell()
    expect(bell(wrapper).find('.count').exists()).toBe(false)
    expect(bell(wrapper).attributes('aria-label')).toBe('Powiadomienia, 0 nieprzeczytanych')
  })

  it('opens the list below the bell and closes it with Escape, the focus back on the bell', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(
      list([notification('a'), notification('b', { readAt: '2026-10-15T07:30:00Z', run: { ...notification('b').run!, status: 'partial', interruptionCause: null, dead: 1 } })]),
    )
    const { wrapper } = await mountBell({ attachTo: document.body })
    expect(bell(wrapper).attributes('aria-expanded')).toBe('false')
    expect(bell(wrapper).attributes('aria-controls')).toBe(panel(wrapper).attributes('id'))
    expect(panel(wrapper).isVisible()).toBe(false)

    await open(wrapper)

    expect(bell(wrapper).attributes('aria-expanded')).toBe('true')
    expect(panel(wrapper).isVisible()).toBe(true)
    const [unread, read] = wrapper.findAll('a.item')
    expect(unread!.find('.new').text()).toBe('nowe')
    expect(unread!.find('[data-tone]').text()).toBe('przerwany — nowe wdrożenie')
    expect(unread!.find('.item-meta').text()).toBe('zakres dat · 5 min temu')
    expect(unread!.attributes('href')).toBe('/admin/runs/run-a')
    expect(read!.find('.new').exists()).toBe(false)
    expect(read!.find('[data-tone]').text()).toBe('częściowo')

    ;(unread!.element as HTMLElement).focus()
    await unread!.trigger('keydown', { key: 'Escape' })

    expect(bell(wrapper).attributes('aria-expanded')).toBe('false')
    expect(panel(wrapper).isVisible()).toBe(false)
    expect(document.activeElement).toBe(bell(wrapper).element)
  })

  it('closes on a click outside and puts back a focus left nowhere', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a')]))
    const { wrapper } = await mountBell({ attachTo: document.body })
    await open(wrapper)
    ;(wrapper.find('a.item').element as HTMLElement).focus()

    document.body.click()
    await flushPromises()

    expect(bell(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(bell(wrapper).element)
  })

  it('leaves the focus on another control clicked outside', async () => {
    const other = document.createElement('button')
    document.body.append(other)
    const { wrapper } = await mountBell({ attachTo: document.body })
    await open(wrapper)

    other.focus()
    other.click()
    await flushPromises()

    expect(bell(wrapper).attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(other)
  })

  it('stays open on a click inside the list', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([]))
    const { wrapper } = await mountBell({ attachTo: document.body })
    await open(wrapper)
    ;(wrapper.find('.empty').element as HTMLElement).click()
    await flushPromises()
    expect(bell(wrapper).attributes('aria-expanded')).toBe('true')
  })

  it('marks a chosen notification read, closes the list and opens its run', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a'), notification('b')]))
    const { wrapper, router } = await mountBell()
    await open(wrapper)

    await wrapper.find('a.item').trigger('click')
    await flushPromises()

    expect(markNotificationRead).toHaveBeenCalledExactlyOnceWith('a')
    expect(router.currentRoute.value.name).toBe('admin-run')
    expect(router.currentRoute.value.params.id).toBe('run-a')
    expect(bell(wrapper).attributes('aria-expanded')).toBe('false')
    expect(bell(wrapper).attributes('aria-label')).toBe('Powiadomienia, 1 nieprzeczytane')
    expect(wrapper.findAll('.new')).toHaveLength(1)
  })

  it('opens the run even when marking it read fails', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a')]))
    vi.mocked(markNotificationRead).mockRejectedValue(new ApiError(500, 'internal_error'))
    const { wrapper, router } = await mountBell()
    await open(wrapper)

    await wrapper.find('a.item').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/admin/runs/run-a')
    expect(bell(wrapper).attributes('aria-label')).toBe('Powiadomienia, 1 nieprzeczytane')
  })

  it('does not mark a read notification again', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a', { readAt: '2026-10-15T07:30:00Z' })]))
    const { wrapper, router } = await mountBell()
    await open(wrapper)

    await wrapper.find('a.item').trigger('click')
    await flushPromises()

    expect(markNotificationRead).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/admin/runs/run-a')
  })

  it('leaves a click with a modifier key to the browser', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a')]))
    const { wrapper, router } = await mountBell()
    await open(wrapper)
    const link = wrapper.find('a.item')
    // Runs after the link's own handler: jsdom cannot open a new tab.
    link.element.addEventListener('click', (event) => event.preventDefault())

    await link.trigger('click', { ctrlKey: true })
    await flushPromises()

    expect(markNotificationRead).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/admin')
  })

  it('says it is loading until the first answer', async () => {
    vi.mocked(fetchNotifications).mockReturnValue(never())
    const { wrapper } = await mountBell()
    await open(wrapper)
    expect(bell(wrapper).attributes('aria-label')).toBe('Powiadomienia')
    expect(panel(wrapper).attributes('aria-busy')).toBe('true')
    expect(panel(wrapper).find('.loading').text()).toBe('Wczytywanie…')
  })

  it('says there are no notifications', async () => {
    const { wrapper } = await mountBell()
    await open(wrapper)
    expect(panel(wrapper).find('.empty').text()).toBe('Brak powiadomień.')
    expect(wrapper.findAll('button').some((candidate) => candidate.text().startsWith('Oznacz wszystkie'))).toBe(false)
  })

  it('says the list failed to load and loads it again on request', async () => {
    let answer: (value: NotificationList) => void = () => {}
    vi.mocked(fetchNotifications)
      .mockRejectedValueOnce(new ApiError(500, 'internal_error'))
      .mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
    const { wrapper } = await mountBell({ attachTo: document.body })
    await open(wrapper)
    expect(panel(wrapper).find('[role="alert"]').text()).toBe('Nie udało się wczytać powiadomień.')

    await button(wrapper, 'Spróbuj ponownie').trigger('click')

    expect(fetchNotifications).toHaveBeenCalledTimes(2)
    expect(panel(wrapper).find('.loading').exists()).toBe(true)
    expect(document.activeElement).toBe(bell(wrapper).element)

    answer(list([notification('a')]))
    await flushPromises()

    expect(panel(wrapper).find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.findAll('a.item')).toHaveLength(1)
  })

  it('marks every notification read with Oznacz wszystkie jako przeczytane', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a'), notification('b')], 14))
    const { wrapper } = await mountBell({ attachTo: document.body })
    await open(wrapper)
    const markAll = button(wrapper, 'Oznacz wszystkie jako przeczytane')
    expect(markAll.classes()).toContain('secondary')

    await markAll.trigger('click')
    await flushPromises()

    expect(markAllNotificationsRead).toHaveBeenCalledTimes(1)
    expect(bell(wrapper).find('.count').exists()).toBe(false)
    expect(bell(wrapper).attributes('aria-label')).toBe('Powiadomienia, 0 nieprzeczytanych')
    expect(wrapper.findAll('.new')).toHaveLength(0)
    expect(wrapper.findAll('button').some((candidate) => candidate.text().startsWith('Oznacz wszystkie'))).toBe(false)
    expect(document.activeElement).toBe(bell(wrapper).element)
  })

  it('says when marking every notification read failed and keeps the count', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a')]))
    vi.mocked(markAllNotificationsRead).mockRejectedValue(new ApiError(500, 'internal_error'))
    const { wrapper } = await mountBell()
    await open(wrapper)

    await button(wrapper, 'Oznacz wszystkie jako przeczytane').trigger('click')
    await flushPromises()

    expect(panel(wrapper).find('[role="alert"]').text()).toBe('Nie udało się oznaczyć powiadomień jako przeczytanych.')
    expect(bell(wrapper).find('.count').text()).toBe('1')
  })

  describe('reading again', () => {
    beforeEach(() => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'] })
    })

    it('reads the list on mount, then every 60 s while the tab is visible', async () => {
      vi.mocked(fetchNotifications)
        .mockResolvedValueOnce(list([notification('a')]))
        .mockResolvedValue(list([notification('b'), notification('a')]))
      const { wrapper } = await mountBell()
      expect(fetchNotifications).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(59_000)
      expect(fetchNotifications).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(1_000)
      await flushPromises()
      expect(fetchNotifications).toHaveBeenCalledTimes(2)
      expect(bell(wrapper).find('.count').text()).toBe('2')

      const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden')
      await vi.advanceTimersByTimeAsync(120_000)
      expect(fetchNotifications).toHaveBeenCalledTimes(2)

      visibility.mockRestore()
      await vi.advanceTimersByTimeAsync(60_000)
      await flushPromises()
      expect(fetchNotifications).toHaveBeenCalledTimes(3)
    })

    it('stops when it is unmounted', async () => {
      const { wrapper } = await mountBell()
      wrapper.unmount()
      await vi.advanceTimersByTimeAsync(180_000)
      expect(fetchNotifications).toHaveBeenCalledTimes(1)
    })

    it('keeps the list on screen when a later read fails', async () => {
      vi.mocked(fetchNotifications)
        .mockResolvedValueOnce(list([notification('a')]))
        .mockRejectedValue(new ApiError(503, null))
      const { wrapper } = await mountBell()
      await open(wrapper)

      await vi.advanceTimersByTimeAsync(60_000)
      await flushPromises()

      expect(fetchNotifications).toHaveBeenCalledTimes(2)
      expect(wrapper.findAll('a.item')).toHaveLength(1)
      expect(panel(wrapper).find('[role="alert"]').exists()).toBe(false)
      expect(bell(wrapper).find('.count').text()).toBe('1')
    })
  })
})
