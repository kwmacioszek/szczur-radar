import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h } from 'vue'
import { RouterView, createMemoryHistory } from 'vue-router'
import { fetchMe } from '../../api/auth'
import { ApiError } from '../../api/client'
import { type FailedPost, type Page, fetchFailedPosts } from '../../api/posts'
import { resetSession } from '../../auth/session'
import { createAppRouter } from '../../router'
import type { AdminPanel } from './adminPanel'

vi.mock('../../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../../api/posts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/posts')>()),
  fetchFailedPosts: vi.fn(),
}))
// The shell only renders its page; each part is an empty page that hands the test the panel it was given.
const parts = vi.hoisted(() => ({ panels: [] as AdminPanel[] }))
vi.mock('../../components/AppShell.vue', async () => {
  const { defineComponent, h } = await import('vue')
  const { RouterView } = await import('vue-router')
  return { default: defineComponent({ setup: () => () => h(RouterView) }) }
})
vi.mock('./AdminRunsView.vue', async () => ({ default: await partStub('Przebiegi') }))
vi.mock('./AdminQualityView.vue', async () => ({ default: await partStub('Jakość ocen') }))
vi.mock('./AdminFailedPostsView.vue', async () => ({ default: await partStub('Nieudane posty') }))
vi.mock('../RunDetailView.vue', () => ({ default: { render: () => null } }))

async function partStub(name: string) {
  const { defineComponent, h, inject } = await import('vue')
  const { ADMIN_PANEL_KEY } = await import('./adminPanel')
  return defineComponent({
    setup() {
      parts.panels.push(inject(ADMIN_PANEL_KEY)!)
      return () => h('p', { class: 'part' }, name)
    },
  })
}

function count(total: number): Page<FailedPost> {
  return { items: [], page: 0, size: 1, total }
}

async function mountPanel(path: string) {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const wrapper = mount({ render: () => h(RouterView) }, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

function links(wrapper: Awaited<ReturnType<typeof mountPanel>>['wrapper']) {
  return wrapper.findAll('nav[aria-label="Panel admina"] a')
}

/** The panel the part on screen was given. */
function panel(): AdminPanel {
  return parts.panels.at(-1)!
}

enableAutoUnmount(afterEach)

describe('AdminPanelView', () => {
  beforeEach(() => {
    resetSession()
    parts.panels = []
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    vi.mocked(fetchFailedPosts).mockReset().mockResolvedValue(count(4))
    // The router scrolls to the top on a path change (router/scroll.ts); jsdom does not implement scrolling.
    vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.mocked(window.scrollTo).mockRestore()
  })

  it('has the heading "Panel admina", the bar of the three parts and the part of the address', async () => {
    const { wrapper } = await mountPanel('/admin/quality')
    expect(wrapper.findAll('h1').map((heading) => heading.text())).toEqual(['Panel admina'])
    expect(links(wrapper).map((link) => link.text())).toEqual(['Przebiegi', 'Jakość ocen', 'Nieudane posty 4'])
    expect(links(wrapper).map((link) => link.attributes('href'))).toEqual(['/admin', '/admin/quality', '/admin/posts'])
    expect(wrapper.find('.part').text()).toBe('Jakość ocen')
  })

  it.each([
    ['/admin', 0],
    ['/admin/quality', 1],
    ['/admin/posts', 2],
  ])('marks on %s only its part with aria-current="page"', async (path, current) => {
    const { wrapper } = await mountPanel(path)
    expect(links(wrapper).map((link) => link.attributes('aria-current'))).toEqual(
      [0, 1, 2].map((at) => (at === current ? 'page' : undefined)),
    )
  })

  it('counts the failed posts by the total of a page of one', async () => {
    const { wrapper } = await mountPanel('/admin')
    expect(fetchFailedPosts).toHaveBeenCalledWith(0, 1)
    expect(links(wrapper)[2]!.find('.status-badge').text()).toBe('4')
  })

  it.each([
    ['no failed post', () => vi.mocked(fetchFailedPosts).mockResolvedValue(count(0))],
    ['a failed read', () => vi.mocked(fetchFailedPosts).mockRejectedValue(new ApiError(500, 'internal_error'))],
  ])('has no count with %s, and no message', async (_case, arrange) => {
    arrange()
    const { wrapper } = await mountPanel('/admin')
    expect(links(wrapper)[2]!.text()).toBe('Nieudane posty')
    expect(links(wrapper)[2]!.find('.status-badge').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('reads the count again on every part change, keeping the old count while it is read', async () => {
    const { wrapper, router } = await mountPanel('/admin')
    let answer: (value: Page<FailedPost>) => void = () => {}
    vi.mocked(fetchFailedPosts).mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))

    await router.push('/admin/posts')
    await flushPromises()

    expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
    expect(links(wrapper)[2]!.text()).toBe('Nieudane posty 4')
    expect(links(wrapper)[2]!.attributes('aria-current')).toBe('page')

    answer(count(3))
    await flushPromises()

    expect(links(wrapper)[2]!.text()).toBe('Nieudane posty 3')
  })

  it('keeps the newest count when an older read ends later', async () => {
    let older: (value: Page<FailedPost>) => void = () => {}
    let newer: (value: Page<FailedPost>) => void = () => {}
    vi.mocked(fetchFailedPosts)
      .mockReturnValueOnce(new Promise((resolve) => (older = resolve)))
      .mockReturnValueOnce(new Promise((resolve) => (newer = resolve)))
    const { wrapper } = await mountPanel('/admin/posts')

    panel().refreshFailedCount()
    newer(count(1))
    await flushPromises()
    older(count(4))
    await flushPromises()

    expect(links(wrapper)[2]!.text()).toBe('Nieudane posty 1')
  })

  it('reads the count again when a part asks', async () => {
    const { wrapper } = await mountPanel('/admin/posts')
    vi.mocked(fetchFailedPosts).mockResolvedValue(count(2))

    panel().refreshFailedCount()
    await flushPromises()

    expect(fetchFailedPosts).toHaveBeenCalledTimes(2)
    expect(fetchFailedPosts).toHaveBeenLastCalledWith(0, 1)
    expect(links(wrapper)[2]!.text()).toBe('Nieudane posty 2')
  })

  it('does not read the count when leaving the panel for a run', async () => {
    const { router } = await mountPanel('/admin')
    await router.push('/admin/runs/x')
    await flushPromises()
    expect(fetchFailedPosts).toHaveBeenCalledTimes(1)
  })

  describe('the focus on the heading', () => {
    it('goes to the heading at the first request of the part opened on entry', async () => {
      const { wrapper } = await mountPanel('/admin/quality')
      expect(document.activeElement).not.toBe(wrapper.get('h1').element)

      panel().focusTitle()

      expect(wrapper.get('h1').attributes('tabindex')).toBe('-1')
      expect(document.activeElement).toBe(wrapper.get('h1').element)
    })

    it('stays where it is at a later request', async () => {
      const { wrapper } = await mountPanel('/admin/quality')
      panel().focusTitle()
      const link = links(wrapper)[0]!.element as HTMLElement
      link.focus()

      panel().focusTitle()

      expect(document.activeElement).toBe(link)
    })

    it('stays on the clicked link of the bar after a part change', async () => {
      const { wrapper } = await mountPanel('/admin')
      panel().focusTitle()
      const link = links(wrapper)[1]!

      ;(link.element as HTMLElement).focus()
      await link.trigger('click')
      await flushPromises()
      expect(wrapper.find('.part').text()).toBe('Jakość ocen')
      panel().focusTitle()

      expect(document.activeElement).toBe(link.element)
      expect(link.attributes('aria-current')).toBe('page')
    })

    it('stays on the clicked link also when the first part had not asked yet', async () => {
      const { wrapper, router } = await mountPanel('/admin')
      const link = links(wrapper)[2]!
      ;(link.element as HTMLElement).focus()

      await router.push('/admin/posts')
      await flushPromises()
      panel().focusTitle()

      expect(document.activeElement).toBe(link.element)
    })
  })
})
