import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { fetchMe } from '../api/auth'
import { ApiError } from '../api/client'
import { type Run, fetchRuns, fetchSources, retryPosts, startRange } from '../api/pipeline'
import { type FailedPost, fetchFailedPosts } from '../api/posts'
import { resetSession } from '../auth/session'
import { createAppRouter } from '../router'
import AdminView from './AdminView.vue'

vi.mock('../api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../api/posts', () => ({ fetchPosts: vi.fn(), fetchFailedPosts: vi.fn() }))
vi.mock('../api/pipeline', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/pipeline')>()),
  startRange: vi.fn(),
  retryPosts: vi.fn(),
  fetchRuns: vi.fn(),
  fetchSources: vi.fn(),
}))

function run(overrides: Partial<Run> = {}): Run {
  return {
    id: 'r1',
    kind: 'range',
    status: 'succeeded',
    rangeFrom: '2026-09-30T22:00:00Z',
    rangeTo: '2026-10-01T21:59:59Z',
    sources: ['wykop'],
    requestedPostIds: null,
    createdAt: '2026-10-02T06:00:00Z',
    startedAt: '2026-10-02T06:00:01Z',
    finishedAt: '2026-10-02T06:20:00Z',
    fetched: 270,
    newPosts: 268,
    assessedOk: 260,
    assessedFailed: 8,
    processingErrors: 0,
    dead: 0,
    skipped: 2,
    error: null,
    steps: [
      { step: 'fetch', status: 'succeeded', startedAt: '2026-10-02T06:00:01Z', finishedAt: null, itemsIn: null, itemsOut: 270, error: null },
    ],
    ...overrides,
  }
}

function failed(id: string, stage: FailedPost['stage']): FailedPost {
  return {
    id,
    itemType: 'post',
    publishedAt: '2026-10-01T07:00:00Z',
    title: null,
    body: `Wpis ${id}`,
    webUrl: null,
    stage,
    assessmentAttempts: stage === 'dead' ? 3 : 1,
    lastAttempt: { failureClass: 'timeout', violations: [], assessedAt: '2026-10-02T06:10:00Z', provider: 'gemini', model: 'm' },
  }
}

async function mountAdmin() {
  const router = createAppRouter(createMemoryHistory())
  await router.push('/admin')
  const wrapper = mount(AdminView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('AdminView', () => {
  beforeEach(() => {
    resetSession()
    vi.mocked(fetchMe).mockReset().mockResolvedValue({ id: '1', email: 'admin@example.com', role: 'admin' })
    vi.mocked(fetchSources).mockReset().mockResolvedValue([{ source: 'wykop', configured: true }])
    vi.mocked(fetchRuns).mockReset().mockResolvedValue({ items: [], page: 0, size: 20, total: 0 })
    vi.mocked(fetchFailedPosts).mockReset().mockResolvedValue({ items: [], page: 0, size: 50, total: 0 })
    vi.mocked(startRange).mockReset()
    vi.mocked(retryPosts).mockReset()
  })

  it('starts a range with the configured sources checked by default', async () => {
    vi.mocked(startRange).mockResolvedValue([run({ kind: 'retry' }), run()])
    const wrapper = await mountAdmin()
    await wrapper.findAll('input[type="date"]')[0]!.setValue('2026-10-01')
    await wrapper.findAll('input[type="date"]')[1]!.setValue('2026-10-02')
    expect((wrapper.find('input[type="checkbox"][value="wykop"]').element as HTMLInputElement).checked).toBe(true)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(startRange).toHaveBeenCalledWith('2026-10-01', '2026-10-02', ['wykop'])
    expect(wrapper.find('[role="status"]').text()).toBe('Uruchomiono: ponowienie resztek, zakres dat.')
    expect(fetchRuns).toHaveBeenCalledTimes(2)
  })

  it('blocks the button until the server answers', async () => {
    let answer: (runs: Run[]) => void = () => {}
    vi.mocked(startRange).mockReturnValue(new Promise((resolve) => (answer = resolve)))
    const wrapper = await mountAdmin()
    await wrapper.find('form').trigger('submit')
    expect(wrapper.find('form button').attributes('disabled')).toBeDefined()
    await wrapper.find('form').trigger('submit')
    expect(startRange).toHaveBeenCalledTimes(1)
    answer([run()])
    await flushPromises()
    expect(wrapper.find('form button').attributes('disabled')).toBeUndefined()
  })

  it('says a run is already going on', async () => {
    vi.mocked(startRange).mockRejectedValue(new ApiError(409, 'pipeline_busy'))
    const wrapper = await mountAdmin()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('Przebieg już trwa')
  })

  it('offers an unconfigured source unchecked and disabled', async () => {
    vi.mocked(fetchSources).mockResolvedValue([{ source: 'wykop', configured: false }])
    const wrapper = await mountAdmin()
    const box = wrapper.find('input[type="checkbox"][value="wykop"]')
    expect((box.element as HTMLInputElement).checked).toBe(false)
    expect(box.attributes('disabled')).toBeDefined()
    expect(wrapper.find('form button').attributes('disabled')).toBeDefined()
  })

  it('shows a partial run with its technical errors and error text', async () => {
    vi.mocked(fetchRuns).mockResolvedValue({
      items: [run({ status: 'partial', processingErrors: 1, error: '1 post(s) with a technical error: x (IllegalStateException)' })],
      page: 0,
      size: 20,
      total: 1,
    })
    const wrapper = await mountAdmin()
    const shown = wrapper.find('.run')
    expect(shown.find('.status').text()).toBe('częściowo')
    expect(shown.find('.alarm').text()).toBe('błędy techniczne 1')
    expect(shown.find('.run-error').text()).toContain('IllegalStateException')
    expect(shown.text()).toContain('pobranie (270)')
  })

  it('retries the checked posts, never a dead one', async () => {
    vi.mocked(fetchFailedPosts).mockResolvedValue({
      items: [failed('a', 'failed'), failed('b', 'dead'), failed('c', 'failed')],
      page: 0,
      size: 50,
      total: 3,
    })
    vi.mocked(retryPosts).mockResolvedValue([run({ kind: 'posts' })])
    const wrapper = await mountAdmin()
    const boxes = wrapper.findAll('.failed-posts input[type="checkbox"]')
    expect(boxes[1]!.attributes('disabled')).toBeDefined()
    expect(wrapper.find('.failed-posts').text()).toContain('martwy')
    const retry = wrapper.findAll('button').find((button) => button.text() === 'Ponów zaznaczone')!
    expect(retry.attributes('disabled')).toBeDefined()

    await boxes[0]!.setValue(true)
    await boxes[2]!.setValue(true)
    await retry.trigger('click')
    await flushPromises()

    expect(retryPosts).toHaveBeenCalledWith(['a', 'c'])
    expect(wrapper.find('[role="status"]').text()).toContain('Ponowienie 2')
  })
})
