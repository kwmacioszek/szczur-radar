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

// The pages are empty, and the shell and the admin panel's layout only count how often they are set up: how many
// times each mounted.
const mounted = vi.hoisted(() => ({ shell: 0, panel: 0 }))
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
vi.mock('../views/admin/AdminPanelView.vue', async () => {
  const { defineComponent, h } = await import('vue')
  const { RouterView } = await import('vue-router')
  return {
    default: defineComponent({
      setup() {
        mounted.panel++
        return () => h(RouterView)
      },
    }),
  }
})
const emptyPage = vi.hoisted(() => ({ render: () => null }))
vi.mock('../views/FeedView.vue', () => ({ default: emptyPage }))
vi.mock('../views/PostDetailView.vue', () => ({ default: emptyPage }))
vi.mock('../views/admin/AdminRunsView.vue', () => ({ default: emptyPage }))
vi.mock('../views/admin/AdminQualityView.vue', () => ({ default: emptyPage }))
vi.mock('../views/admin/AdminFailedPostsView.vue', () => ({ default: emptyPage }))
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
    mounted.panel = 0
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
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

  it('sends a plain user away from /admin to the feed', async () => {
    vi.mocked(fetchMe).mockResolvedValue({ ...ADMIN, role: 'user' })
    expect(await navigate('/admin')).toBe('/')
  })

  it.each([
    ['/admin/quality', 'admin-quality'],
    ['/admin/posts', 'admin-posts'],
  ])('lets an admin into the part %s (%s)', async (path, name) => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.fullPath).toBe(path)
    expect(router.currentRoute.value.name).toBe(name)
  })

  it.each(['/admin/quality', '/admin/posts'])('sends a plain user away from the part %s to the feed', async (path) => {
    vi.mocked(fetchMe).mockResolvedValue({ ...ADMIN, role: 'user' })
    expect(await navigate(path)).toBe('/')
  })

  it("lets an admin into a run's page", async () => {
    vi.mocked(fetchMe).mockResolvedValue(ADMIN)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/admin/runs/x')
    expect(router.currentRoute.value.name).toBe('admin-run')
    expect(router.currentRoute.value.params.id).toBe('x')
  })

  it("sends a plain user away from a run's page to the feed", async () => {
    vi.mocked(fetchMe).mockResolvedValue({ ...ADMIN, role: 'user' })
    expect(await navigate('/admin/runs/x')).toBe('/')
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
    await router.push('/')
    await router.push('/login')
    expect(fetchMe).toHaveBeenCalledTimes(1)
  })
})

describe('shell', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue(ADMIN)
    mounted.shell = 0
    mounted.panel = 0
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it.each([
    ['/', 'home', 'posts', 2],
    ['/posts/x', 'post', 'posts', 2],
    ['/admin', 'admin', 'admin', 3],
    ['/admin/quality', 'admin-quality', 'admin', 3],
    ['/admin/posts', 'admin-posts', 'admin', 3],
    ['/admin/runs/x', 'admin-run', 'admin', 2],
  ])('puts %s (%s) in the navigation section %s', async (path, name, section, depth) => {
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.name).toBe(name)
    expect(router.currentRoute.value.meta.section).toBe(section)
    // Every page of the shell is a child of its one route; a part of the admin panel also of the panel's layout.
    expect(router.currentRoute.value.matched).toHaveLength(depth)
  })

  it.each([
    ['/', 'narrow'],
    ['/posts/x', 'narrow'],
    ['/admin', 'wide'],
    ['/admin/quality', 'wide'],
    ['/admin/posts', 'wide'],
    ['/admin/runs/x', 'narrow'],
  ])('gives %s the %s column', async (path, column) => {
    const router = createAppRouter(createMemoryHistory())
    await router.push(path)
    expect(router.currentRoute.value.meta.column).toBe(column)
  })

  it('gives every page of the shell a column', () => {
    const shell = routes.find((route) => route.children)
    expect(shell?.children?.length).toBeGreaterThan(0)
    for (const child of shell?.children ?? []) {
      // A layout's children (the admin panel's parts) inherit its column, or have their own.
      const pages = child.children ?? [child]
      expect(pages.length, String(child.path)).toBeGreaterThan(0)
      for (const page of pages) {
        expect(page.meta?.column ?? child.meta?.column, String(page.name)).toBeDefined()
      }
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
    await router.push('/')
    const app = mount({ render: () => h(RouterView) }, { global: { plugins: [router] } })
    expect(mounted.shell).toBe(1)

    await router.push('/posts/x')
    await router.push('/admin')
    await router.push('/admin/runs/x')
    await router.push('/')

    expect(mounted.shell).toBe(1)
    app.unmount()
  })

  it('mounts neither the shell nor the admin panel again when the part of the panel changes', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/admin')
    const app = mount({ render: () => h(RouterView) }, { global: { plugins: [router] } })
    expect(mounted.shell).toBe(1)
    expect(mounted.panel).toBe(1)

    await router.push('/admin/quality')
    await router.push('/admin/posts')
    await router.push('/admin')

    expect(mounted.shell).toBe(1)
    expect(mounted.panel).toBe(1)
    app.unmount()
  })

  it('mounts the shell again after leaving it for a public page', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    const app = mount({ render: () => h(RouterView) }, { global: { plugins: [router] } })
    await router.push('/activate#token')
    await router.push('/')
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
