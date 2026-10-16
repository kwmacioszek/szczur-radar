import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe, type User } from '../api/auth'
import { fetchNotifications } from '../api/notifications'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import AppHeader from './AppHeader.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/notifications', () => ({
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

async function mountHeader(role: User['role']) {
  vi.mocked(fetchMe).mockResolvedValue({ id: '1', email: `${role}@example.com`, role })
  const router = createAppRouter(createMemoryHistory())
  await router.push('/')
  const wrapper = mount(AppHeader, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

enableAutoUnmount(afterEach)

describe('AppHeader', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset()
    vi.mocked(fetchNotifications).mockReset().mockResolvedValue({ unread: 1, items: [] })
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('shows an admin the notification bell', async () => {
    const wrapper = await mountHeader('admin')
    expect(wrapper.find('button[aria-controls]').attributes('aria-label')).toBe('Powiadomienia, 1 nieprzeczytane')
    expect(fetchNotifications).toHaveBeenCalledTimes(1)
  })

  it('shows a plain user no bell and asks for no notifications', async () => {
    const wrapper = await mountHeader('user')
    expect(wrapper.find('button[aria-controls]').exists()).toBe(false)
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual(['Wyloguj'])
    expect(fetchNotifications).not.toHaveBeenCalled()
  })
})
