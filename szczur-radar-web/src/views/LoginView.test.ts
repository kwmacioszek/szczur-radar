import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe, login } from '../api/auth'
import { ApiError } from '../api/client'
import { currentUser, resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import LoginView from './LoginView.vue'

vi.mock('../api/auth', () => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

async function mountLogin(path = '/login') {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const wrapper = mount(LoginView, { global: { plugins: [router] } })
  await wrapper.find('input[type="email"]').setValue('admin@example.com')
  await wrapper.find('input[type="password"]').setValue('correct horse battery')
  return { wrapper, router }
}

async function submit(wrapper: Awaited<ReturnType<typeof mountLogin>>['wrapper']) {
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('LoginView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockRejectedValue(new ApiError(401, 'not_authenticated'))
    vi.mocked(login).mockReset()
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('shows a generic message for wrong credentials', async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(401, 'invalid_credentials'))
    const { wrapper } = await mountLogin()
    await submit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe('Nieprawidłowy e-mail lub hasło.')
  })

  it('shows when to retry after too many attempts', async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(429, 'too_many_attempts', 120))
    const { wrapper } = await mountLogin()
    await submit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe(
      'Zbyt wiele prób. Spróbuj ponownie za 2 min.',
    )
  })

  it('rounds a short wait up to one minute', async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(429, 'too_many_attempts', 5))
    const { wrapper } = await mountLogin()
    await submit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toContain('za 1 min.')
  })

  it('shows a generic failure for other errors', async () => {
    vi.mocked(login).mockRejectedValue(new TypeError('Failed to fetch'))
    const { wrapper } = await mountLogin()
    await submit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe(
      'Nie udało się zalogować. Spróbuj ponownie.',
    )
  })

  it('logs in and returns to the requested page', async () => {
    vi.mocked(login).mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    const { wrapper, router } = await mountLogin('/login?redirect=/?tab=2')
    await submit(wrapper)
    expect(login).toHaveBeenCalledWith('admin@example.com', 'correct horse battery')
    expect(currentUser.value?.email).toBe('admin@example.com')
    expect(router.currentRoute.value.fullPath).toBe('/?tab=2')
  })

  it('fills in the e-mail and confirms the password after an activation', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.replace({ name: 'login', state: { activatedEmail: 'jan@example.com' } })
    const wrapper = mount(LoginView, { global: { plugins: [router] } })
    expect((wrapper.find('input[type="email"]').element as HTMLInputElement).value).toBe('jan@example.com')
    expect(wrapper.find('[role="status"]').text()).toBe('Hasło ustawione. Zaloguj się.')
    expect(router.currentRoute.value.fullPath).toBe('/login')
  })

  it('drops the activation notice once the person tries to log in', async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(401, 'invalid_credentials'))
    const router = createAppRouter(createMemoryHistory())
    await router.replace({ name: 'login', state: { activatedEmail: 'jan@example.com' } })
    const wrapper = mount(LoginView, { global: { plugins: [router] } })
    await submit(wrapper)
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').text()).toBe('Nieprawidłowy e-mail lub hasło.')
  })

  it('shows no activation notice on a plain visit', async () => {
    const { wrapper } = await mountLogin()
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('ignores an off-site redirect after login', async () => {
    vi.mocked(login).mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    const { wrapper, router } = await mountLogin('/login?redirect=//evil.com')
    await submit(wrapper)
    expect(router.currentRoute.value.fullPath).toBe('/')
  })

  it('shows the brand and the theme switch of a public screen', async () => {
    const { wrapper } = await mountLogin()
    expect(wrapper.find('main').text()).toContain('Szczur Radar')
    expect(wrapper.find('button[aria-label="Ciemny motyw"]').exists()).toBe(true)
  })

  it('shows the error as a status message with its icon, announced at once', async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(401, 'invalid_credentials'))
    const { wrapper } = await mountLogin()
    await submit(wrapper)
    const alerts = wrapper.findAll('[role="alert"]')
    expect(alerts).toHaveLength(1)
    expect(alerts[0].classes()).toContain('status-message')
    expect(alerts[0].find('svg').exists()).toBe(true)
  })

  it('tells password managers which field is which', async () => {
    const { wrapper } = await mountLogin()
    expect(wrapper.find('input[type="email"]').attributes('autocomplete')).toBe('username')
    expect(wrapper.find('input[type="password"]').attributes('autocomplete')).toBe('current-password')
  })

  it('names the fields by their labels and tells that an administrator creates the account', async () => {
    const { wrapper } = await mountLogin()
    expect(wrapper.find('label[for="' + wrapper.find('input[type="email"]').attributes('id') + '"]').text()).toBe('E-mail')
    expect(wrapper.find('label[for="' + wrapper.find('input[type="password"]').attributes('id') + '"]').text()).toBe('Hasło')
    expect(wrapper.find('form').text()).toContain('Konto zakłada administrator.')
  })

  it('keeps no class of its own on the form (the old exception class is gone): the shared layout and the global field style do it', async () => {
    const { wrapper } = await mountLogin()
    expect(wrapper.find('form').classes()).toEqual([])
  })

  it('marks the button busy while the login is in progress', async () => {
    let finish: (user: { id: string; email: string; role: 'admin' }) => void = () => {}
    vi.mocked(login).mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const { wrapper } = await mountLogin()
    await wrapper.find('form').trigger('submit')
    const button = wrapper.find('button[type="submit"]')
    expect(button.attributes('aria-busy')).toBe('true')
    expect(button.text()).toBe('Logowanie…')
    expect(button.attributes('disabled')).toBeDefined()
    finish({ id: '1', email: 'admin@example.com', role: 'admin' })
    await flushPromises()
  })
})
