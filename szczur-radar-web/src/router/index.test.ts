import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import { RouterView, createMemoryHistory } from 'vue-router'
import { fetchMe, type User } from '../api/auth'
import { ApiError } from '../api/client'
import { resetSession } from '../auth/session'
import { createAppRouter, routes, safeRedirect } from './index'

vi.mock('../api/auth', () => ({
  fetchMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

// The pages are empty and the shell only counts how often it is set up: how many times it mounted.
const mounted = vi.hoisted(() => ({ shell: 0 }))
vi.mock('../components/AppShell.vue', async () => {
  const { defineComponent, h } = await import('vue')
  const { RouterView } = await import('vue-router')
  return {
    default: defineComponent({
      setup() {
        mounted.shell++
        return () => h(RouterView)
      },
    }),
  }
})
const emptyPage = vi.hoisted(() => ({ render: () => null }))
vi.mock('../views/FeedView.vue', () => ({ default: emptyPage }))
vi.mock('../views/PostPanel.vue', () => ({ default: emptyPage }))
vi.mock('../views/AdminView.vue', () => ({ default: emptyPage }))
vi.mock('../views/RunDetailView.vue', () => ({ default: emptyPage }))

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
    mounted.shell = 0
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('sends an anonymous visitor on / to /login with a redirect back to the posts', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/')).toBe('/login?redirect=/posts')
  })

  it('sends an anonymous visitor on an unknown path to /login', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/some/deep/route')).toBe('/login?redirect=/posts')
  })

  it('treats a failed session probe as logged out', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new TypeError('Failed to fetch'))
    expect(await navigate('/')).toBe('/login?redirect=/posts')
  })

  it('lets a logged-in user in', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    expect(await navigate('/posts')).toBe('/posts')
  })

  it('leads the old address of the list to /posts with the same filter and page', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/?q=orlen&direction=bullish&page=1')
    expect(router.currentRoute.value.name).toBe('posts')
    expect(router.currentRoute.value.fullPath).toBe('/posts?q=orlen&direction=bullish&page=1')
  })

  it('sends a logged-in user away from /login to the posts', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    expect(await navigate('/login')).toBe('/posts')
  })

  it('sends an anonymous visitor on a post to /login with a redirect back', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/posts/x')).toBe('/login?redirect=/posts/x')
  })

  it('lets a logged-in user into a post', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/posts/x')
    expect(router.currentRoute.value.name).toBe('post')
    expect(router.currentRoute.value.params.id).toBe('x')
  })

  it('lets an admin into /admin', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    expect(await navigate('/admin')).toBe('/admin')
  })

  it('sends a plain user away from /admin to the posts', async () => {
    vi.mocked(fetchMe).mockResolvedValue({ ...ADMIN, role: 'user' })
    expect(await navigate('/admin')).toBe('/posts')
  })

  it("lets an admin into a run's page", async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/admin/runs/x')
    expect(router.currentRoute.value.name).toBe('admin-run')
    expect(router.currentRoute.value.params.id).toBe('x')
  })

  it("sends a plain user away from a run's page to the posts", async () => {
    vi.mocked(fetchMe).mockResolvedValue({ ...ADMIN, role: 'user' })
    expect(await navigate('/admin/runs/x')).toBe('/posts')
  })

  it("sends an anonymous visitor on a run's page to /login with a redirect back", async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/admin/runs/x')).toBe('/login?redirect=/admin/runs/x')
  })

  it('sends an anonymous visitor on /admin to /login', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/admin')).toBe('/login?redirect=/admin')
  })

  it('lets an anonymous visitor open an activation link', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/activate#token')).toBe('/activate#token')
  })

  it('lets a logged-in user open an activation link for another account', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    expect(await navigate('/activate#token')).toBe('/activate#token')
  })

  it('lets an anonymous visitor into the dev-only /kitchen-sink', async () => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    expect(await navigate('/kitchen-sink')).toBe('/kitchen-sink')
  })

  it('asks the server for the session only once', async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/posts')
    await router.push('/login')
    expect(fetchMe).toHaveBeenCalledTimes(1)
  })
})

describe('shell', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue(ADMIN)
    mounted.shell = 0
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it.each([
    ['/posts', 'posts', 'posts'],
    ['/admin', 'admin', 'admin'],
    ['/admin/runs/x', 'admin-run', 'admin'],
  ])('puts %s (%s) in the navigation section %s', async (path, name, section) => {
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.name).toBe(name)
    expect(router.currentRoute.value.meta.section).toBe(section)
    // Every page of the shell is a child of its one route.
    expect(router.currentRoute.value.matched).toHaveLength(2)
  })

  it('opens a post over the list: a child of the list, in the section of the posts', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/posts/x?page=1')
    expect(router.currentRoute.value.name).toBe('post')
    expect(router.currentRoute.value.meta.section).toBe('posts')
    expect(router.currentRoute.value.matched.map((record) => record.name)).toEqual([undefined, 'posts', 'post'])
    // Every record has its component: one without makes vue-router warn on every navigation.
    expect(router.currentRoute.value.matched.every((record) => record.components?.default !== undefined)).toBe(true)
  })

  it.each([
    ['/admin', 'wide'],
    ['/admin/runs/x', 'narrow'],
  ])('gives %s the %s column', async (path, column) => {
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.meta.column).toBe(column)
  })

  it.each(['/posts', '/posts/x'])("gives the compact list of posts on %s the shell's whole column", async (path) => {
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.meta.column).toBeUndefined()
  })

  it('gives every other page of the shell a column', () => {
    const shell = routes.find((route) => route.children)
    const pages = (shell?.children ?? []).filter((child) => child.name !== undefined && child.name !== 'posts')
    expect(pages.length).toBeGreaterThan(0)
    for (const child of pages) {
      expect(child.meta?.column, String(child.name)).toBeDefined()
    }
  })

  it.each(['/login', '/activate', '/kitchen-sink'])('keeps %s outside the shell, with no section', async (path) => {
    vi.mocked(fetchMe).mockRejectedValue(new ApiError(401, 'not_authenticated'))
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.matched).toHaveLength(1)
    expect(router.currentRoute.value.meta.section).toBeUndefined()
  })

  it('does not mount the shell again when the page changes between its children', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/posts')
    const app = mount({ render: () => h(RouterView) }, { global: { plugins: [router] } })
    expect(mounted.shell).toBe(1)

    await router.push('/posts/x')
    await router.push('/admin')
    await router.push('/admin/runs/x')
    await router.push('/posts')

    expect(mounted.shell).toBe(1)
    app.unmount()
  })

  it('mounts the shell again after leaving it for a public page', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/posts')
    const app = mount({ render: () => h(RouterView) }, { global: { plugins: [router] } })
    await router.push('/activate#token')
    await router.push('/posts')
    expect(mounted.shell).toBe(2)
    app.unmount()
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
