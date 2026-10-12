import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { activate, lookupActivation } from '../api/activation'
import { fetchMe, logout } from '../api/auth'
import { ApiError } from '../api/client'
import { currentUser, resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import ActivateView from './ActivateView.vue'

vi.mock('../api/auth', () => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))
// The real activationFailure: the view's error handling is part of what is tested.
vi.mock('../api/activation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/activation')>()),
  lookupActivation: vi.fn(),
  activate: vi.fn(),
}))

const TOKEN = 'Abc-123_xyz'
const INVALID_LINK = 'Link jest nieważny lub wygasł. Poproś administratora o nowy.'
const PASSWORD = 'correct horse battery'

async function mountActivate(path = `/activate#${TOKEN}`) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const wrapper = mount(ActivateView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountActivate>>['wrapper']

async function fillAndSubmit(wrapper: Wrapper, password = PASSWORD, repeat = password) {
  const [first, second] = wrapper.findAll('input[autocomplete="new-password"]')
  await first.setValue(password)
  await second.setValue(repeat)
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

function never<T>(): Promise<T> {
  return new Promise(() => {})
}

describe('ActivateView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockRejectedValue(new ApiError(401, 'not_authenticated'))
    vi.mocked(logout).mockReset().mockResolvedValue(undefined)
    vi.mocked(lookupActivation).mockReset().mockResolvedValue({ email: 'jan@example.com' })
    vi.mocked(activate).mockReset().mockResolvedValue(undefined)
  })

  it('calls a link without a token invalid without asking the API', async () => {
    const { wrapper } = await mountActivate('/activate')
    expect(wrapper.find('[role="alert"]').text()).toBe(INVALID_LINK)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(lookupActivation).not.toHaveBeenCalled()
  })

  it('shows the check of the link while it runs', async () => {
    vi.mocked(lookupActivation).mockReturnValue(never())
    const { wrapper } = await mountActivate()
    expect(wrapper.text()).toContain('Sprawdzanie linku…')
    expect(wrapper.find('section').attributes('aria-busy')).toBe('true')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('checks the token from the fragment and shows the form for its account', async () => {
    const { wrapper } = await mountActivate()
    expect(lookupActivation).toHaveBeenCalledWith(TOKEN)
    expect(wrapper.find('section').attributes('aria-busy')).toBeUndefined()
    expect(wrapper.find('form').text()).toContain('Ustaw hasło dla jan@example.com.')
    const username = wrapper.find('input[autocomplete="username"]')
    expect((username.element as HTMLInputElement).value).toBe('jan@example.com')
    expect(wrapper.findAll('input[type="password"][autocomplete="new-password"]')).toHaveLength(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it.each(['activation_link_invalid', 'invalid_request'])('calls the link invalid on %s', async (detail) => {
    vi.mocked(lookupActivation).mockRejectedValue(new ApiError(400, detail))
    const { wrapper } = await mountActivate()
    expect(wrapper.find('[role="alert"]').text()).toBe(INVALID_LINK)
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('offers another check when the link could not be checked', async () => {
    vi.mocked(lookupActivation)
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce({ email: 'jan@example.com' })
    const { wrapper } = await mountActivate()
    expect(wrapper.find('[role="alert"]').text()).toBe('Nie udało się sprawdzić linku. Spróbuj ponownie.')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(lookupActivation).toHaveBeenCalledTimes(2)
    expect(wrapper.find('form').text()).toContain('jan@example.com')
  })

  it('refuses a password shorter than 12 characters, counted as code points', async () => {
    const { wrapper } = await mountActivate()
    // 11 characters, 22 UTF-16 units.
    await fillAndSubmit(wrapper, '😀'.repeat(11))
    expect(wrapper.find('[role="alert"]').text()).toBe('Hasło musi mieć co najmniej 12 znaków.')
    expect(wrapper.find('input[name="password"]').attributes('aria-invalid')).toBe('true')
    expect(activate).not.toHaveBeenCalled()
  })

  it('refuses a password longer than 128 characters', async () => {
    const { wrapper } = await mountActivate()
    await fillAndSubmit(wrapper, 'a'.repeat(129))
    expect(wrapper.find('[role="alert"]').text()).toBe('Hasło może mieć najwyżej 128 znaków.')
    expect(activate).not.toHaveBeenCalled()
  })

  it('accepts 12 characters that take more UTF-16 units', async () => {
    const { wrapper } = await mountActivate()
    await fillAndSubmit(wrapper, '😀'.repeat(12))
    expect(activate).toHaveBeenCalledWith(TOKEN, '😀'.repeat(12))
  })

  it('refuses two different passwords', async () => {
    const { wrapper } = await mountActivate()
    await fillAndSubmit(wrapper, PASSWORD, `${PASSWORD}!`)
    expect(wrapper.find('[role="alert"]').text()).toBe('Hasła nie są takie same.')
    expect(wrapper.find('input[name="password-repeat"]').attributes('aria-invalid')).toBe('true')
    expect(wrapper.find('input[name="password"]').attributes('aria-invalid')).toBeUndefined()
    expect(activate).not.toHaveBeenCalled()
  })

  it("shows the server's password policy and keeps the form", async () => {
    vi.mocked(activate).mockRejectedValue(new ApiError(400, 'password_policy'))
    const { wrapper } = await mountActivate()
    await fillAndSubmit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe('Hasło musi mieć od 12 do 128 znaków.')
    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it.each([
    ['a network error', new TypeError('Failed to fetch')],
    ['a server error', new ApiError(500, null)],
  ])('shows a generic failure on %s', async (_, error) => {
    vi.mocked(activate).mockRejectedValue(error)
    const { wrapper } = await mountActivate()
    await fillAndSubmit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe('Nie udało się ustawić hasła. Spróbuj ponownie.')
    expect(wrapper.find('form').exists()).toBe(true)
  })

  it('calls the link invalid when it was used up before the form was sent', async () => {
    vi.mocked(activate).mockRejectedValue(new ApiError(400, 'activation_link_invalid'))
    const { wrapper } = await mountActivate()
    await fillAndSubmit(wrapper)
    expect(wrapper.find('[role="alert"]').text()).toBe(INVALID_LINK)
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('keeps the button busy while the password is being set', async () => {
    vi.mocked(activate).mockReturnValue(never())
    const { wrapper } = await mountActivate()
    await fillAndSubmit(wrapper)
    const button = wrapper.find('button[type="submit"]')
    expect(button.attributes('aria-busy')).toBe('true')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toBe('Zapisywanie…')
  })

  it('sets the password and goes to the login screen with the account in the state', async () => {
    const { wrapper, router } = await mountActivate()
    await fillAndSubmit(wrapper)
    expect(activate).toHaveBeenCalledWith(TOKEN, PASSWORD)
    expect(logout).not.toHaveBeenCalled()
    expect(router.currentRoute.value.fullPath).toBe('/login')
    expect(router.options.history.state.activatedEmail).toBe('jan@example.com')
  })

  it("ends another account's session in this browser before the login screen", async () => {
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '9', email: 'admin@example.com', role: 'admin' })
    const { wrapper, router } = await mountActivate()
    expect(router.currentRoute.value.name).toBe('activate')
    await fillAndSubmit(wrapper)
    expect(logout).toHaveBeenCalledTimes(1)
    expect(currentUser.value).toBeNull()
    expect(router.currentRoute.value.fullPath).toBe('/login')
    expect(router.options.history.state.activatedEmail).toBe('jan@example.com')
  })

  it("still reaches the login screen when ending the other account's session fails", async () => {
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '9', email: 'admin@example.com', role: 'admin' })
    vi.mocked(logout).mockRejectedValue(new TypeError('Failed to fetch'))
    const { wrapper, router } = await mountActivate()
    await fillAndSubmit(wrapper)
    expect(currentUser.value).toBeNull()
    expect(router.currentRoute.value.fullPath).toBe('/login')
  })
})
