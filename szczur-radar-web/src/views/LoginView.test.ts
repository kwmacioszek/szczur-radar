import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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

  it('ignores an off-site redirect after login', async () => {
    vi.mocked(login).mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    const { wrapper, router } = await mountLogin('/login?redirect=//evil.com')
    await submit(wrapper)
    expect(router.currentRoute.value.fullPath).toBe('/')
  })
})
