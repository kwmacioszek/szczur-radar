import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe, type User } from '../api/auth'
import { ApiError } from '../api/client'
import { resetSession } from '../auth/session'
import { createAppRouter, safeRedirect } from './index'

vi.mock('../api/auth', () => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const ADMIN: User = { id: '1', email: 'admin@example.com', role: 'admin' }

async function navigate(path: string): Promise<string> {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  return router.currentRoute.value.fullPath
}

describe('route guard', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset()
  })

  it('sends an anonymous visitor on / to /login with a redirect back', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/')).toBe('/login?redirect=/')
  })

  it('sends an anonymous visitor on an unknown path to /login', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/some/deep/route')).toBe('/login?redirect=/')
  })

  it('treats a failed session probe as logged out', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new TypeError('Failed to fetch'))
    expect(await navigate('/')).toBe('/login?redirect=/')
  })

  it('lets a logged-in user in', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    expect(await navigate('/')).toBe('/')
  })

  it('sends a logged-in user away from /login', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    expect(await navigate('/login')).toBe('/')
  })

  it('asks the server for the session only once', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    await router.push('/login')
    expect(fetchMe).toHaveBeenCalledTimes(1)
  })
})

describe('safeRedirect', () => {
  it.each(['/', '/posts?page=2', '/a/b#c'])('keeps the same-site path %s', (path) => {
    expect(safeRedirect(path)).toBe(path)
  })

  it.each([
    '//evil.com',
    '/\\evil.com',
    'https://evil.com',
    'javascript:alert(1)',
    'evil.com',
    '',
    undefined,
    ['/'],
  ])('replaces %s with /', (value) => {
    expect(safeRedirect(value)).toBe('/')
  })
})
