import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './client'
import { fetchRun, fetchRuns, fetchSources, retryPosts, startErrorMessage, startRange } from './pipeline'
import { fetchFailedPosts, fetchPosts } from './posts'

function respond(status: number, body: unknown) {
  // A new Response per call: a body can be read only once.
  const fetchMock = vi.fn().mockImplementation(
    async () =>
      new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function sent(fetchMock: ReturnType<typeof respond>): { path: string; method?: string; body?: unknown } {
  const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
  return { path, method: init.method, body: init.body ? JSON.parse(init.body as string) : undefined }
}

describe('pipeline and posts API', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts a range with the chosen sources', async () => {
    const fetchMock = respond(202, [])
    await startRange('2026-10-01', '2026-10-02', ['wykop'])
    expect(sent(fetchMock)).toEqual({
      path: '/api/admin/pipeline-runs',
      method: 'POST',
      body: { from: '2026-10-01', to: '2026-10-02', sources: ['wykop'] },
    })
  })

  it('retries the given posts', async () => {
    const fetchMock = respond(202, [])
    await retryPosts(['a', 'b'])
    expect(sent(fetchMock).body).toEqual({ postIds: ['a', 'b'] })
  })

  it('reads pages of posts, failed posts and runs, and the sources', async () => {
    const fetchMock = respond(200, { items: [], page: 2, size: 50, total: 0 })
    await fetchPosts(2)
    await fetchFailedPosts()
    await fetchRuns()
    await fetchSources()
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/posts?page=2&size=50',
      '/api/admin/posts/failed?page=0&size=50',
      '/api/admin/pipeline-runs?page=0&size=20',
      '/api/admin/pipeline-runs/sources',
    ])
  })

  it('reads a page of runs of the given size and one run by its encoded id', async () => {
    const fetchMock = respond(200, { items: [], page: 1, size: 40, total: 0 })
    await fetchRuns(1, 40)
    await fetchRun('3f2a/..?x')
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/admin/pipeline-runs?page=1&size=40',
      '/api/admin/pipeline-runs/3f2a%2F..%3Fx',
    ])
  })

  it('says an unknown run is not found', async () => {
    respond(404, { detail: 'not_found' })
    await expect(fetchRun('r1')).rejects.toMatchObject({ status: 404, detail: 'not_found' })
  })

  it('sends only the given conditions of the feed filter, encoded', async () => {
    const fetchMock = respond(200, { items: [], page: 0, size: 50, total: 0 })
    await fetchPosts(0, 50, { q: 'żabka', kind: 'company', direction: 'bullish' })
    await fetchPosts(1, 50, { direction: 'not_expressed' })
    await fetchPosts(0, 50, {})
    expect(fetchMock.mock.calls.map((call) => call[0])).toEqual([
      '/api/posts?page=0&size=50&q=%C5%BCabka&kind=company&direction=bullish',
      '/api/posts?page=1&size=50&direction=not_expressed',
      '/api/posts?page=0&size=50',
    ])
  })

  it.each([
    ['pipeline_busy', 'Przebieg już trwa.'],
    ['post_dead', 'nie można już ponowić'],
    ['source_not_configured', 'nie jest skonfigurowane'],
    ['invalid_request', 'maks. 7 dni'],
  ])('explains %s in Polish', (detail, text) => {
    expect(startErrorMessage(new ApiError(400, detail))).toContain(text)
  })

  it('falls back to a generic message', () => {
    expect(startErrorMessage(new TypeError('Failed to fetch'))).toBe(
      'Nie udało się uruchomić przebiegu. Spróbuj ponownie.',
    )
  })
})
