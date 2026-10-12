import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { type PostItem, fetchPosts } from '../api/posts'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import FeedView from './FeedView.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/posts', () => ({ fetchPosts: vi.fn(), fetchFailedPosts: vi.fn() }))

function post(id: string, overrides: Partial<PostItem> = {}): PostItem {
  return {
    id,
    itemType: 'post',
    publishedAt: '2026-10-01T07:15:00Z',
    title: null,
    body: `Wpis ${id} o XTB`,
    author: 'uzytkownik1',
    webUrl: null,
    assessment: {
      output: {
        relevance: 'investment_related',
        modifiers: [],
        targets: [
          {
            id: 't1',
            kind: 'company',
            anchor: { document: 'item', field: 'body', quote: 'XTB' },
            stance: { direction: 'bullish', assertiveness: 'hedged', claim_ids: [] },
            reference: 'explicit',
          },
        ],
        claims: [],
        flags: ['possible_sarcasm'],
      },
      resultSource: 'model',
      assessedAt: '2026-10-02T08:00:00Z',
    },
    ...overrides,
  }
}

async function mountFeed(path = '/') {
  const router = createAppRouter(createMemoryHistory())
  await router.push(path)
  const wrapper = mount(FeedView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('FeedView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'user@example.com', role: 'user' })
    vi.mocked(fetchPosts).mockReset()
  })

  it('shows each post with its date, type, text and short assessment', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [post('1')], page: 0, size: 50, total: 1 })
    const { wrapper } = await mountFeed()
    const card = wrapper.find('article')
    expect(card.text()).toContain('Wpis 1 o XTB')
    expect(card.text()).toContain('wpis')
    expect(card.text()).toContain('inwestycyjny')
    expect(card.text()).toContain('XTB')
    expect(card.text()).toContain('wzrostowo (ostrożnie)')
    expect(card.text()).toContain('możliwy sarkazm')
    expect(card.find('time').attributes('datetime')).toBe('2026-10-01T07:15:00Z')
  })

  it('shows an empty state, not an error, when there are no posts', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [], page: 0, size: 50, total: 0 })
    const { wrapper } = await mountFeed()
    expect(wrapper.find('.empty').text()).toBe('Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('reads the page from the address and pages through the results', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [post('51')], page: 1, size: 50, total: 120 })
    const { wrapper, router } = await mountFeed('/?page=1')
    expect(fetchPosts).toHaveBeenLastCalledWith(1, 50)
    expect(wrapper.find('.position').text()).toBe('Strona 2 z 3')

    await wrapper.findAll('.pager button')[1]!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('2')
    expect(fetchPosts).toHaveBeenLastCalledWith(2, 50)

    await wrapper.findAll('.pager button')[0]!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.page).toBe('1')
  })

  it('disables paging past the ends', async () => {
    vi.mocked(fetchPosts).mockResolvedValue({ items: [post('1')], page: 0, size: 50, total: 1 })
    const { wrapper } = await mountFeed()
    const [newer, older] = wrapper.findAll('.pager button')
    expect(newer!.attributes('disabled')).toBeDefined()
    expect(older!.attributes('disabled')).toBeDefined()
  })

  it('shows an error when the posts cannot be loaded', async () => {
    vi.mocked(fetchPosts).mockRejectedValue(new TypeError('Failed to fetch'))
    const { wrapper } = await mountFeed()
    expect(wrapper.find('[role="alert"]').text()).toContain('Nie udało się wczytać postów')
  })
})
