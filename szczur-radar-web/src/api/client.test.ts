import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentUser, resetSession, setSession } from '../auth/session'
import { ApiError, apiFetch, apiFetchText, onUnauthorized } from './client'

function respond(status: number, body?: unknown, headers: Record<string, string> = {}) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json', ...headers },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('apiFetch', () => {
  let handler: ReturnType<typeof vi.fn<() => void>>
  let unsubscribe: () => void

  beforeEach(() => {
    resetSession()
    setSession({ id: '1', email: 'admin@example.com', role: 'admin' })
    handler = vi.fn<() => void>()
    unsubscribe = onUnauthorized(handler)
  })

  afterEach(() => {
    unsubscribe()
    vi.unstubAllGlobals()
  })

  it('sends same-origin JSON requests', async () => {
    const fetchMock = respond(200, { ok: true })
    await expect(apiFetch('/api/x', { method: 'POST', body: '{}' })).resolves.toEqual({
      ok: true,
    })
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/x')
    expect(init.credentials).toBe('same-origin')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })

  it('on 401 clears the session and calls the handler', async () => {
    respond(401, { detail: 'not_authenticated' })
    await expect(apiFetch('/api/x')).rejects.toMatchObject({
      status: 401,
      detail: 'not_authenticated',
    })
    expect(currentUser.value).toBeNull()
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('leaves the session alone for calls that expect 401', async () => {
    respond(401, { detail: 'invalid_credentials' })
    await expect(
      apiFetch('/api/auth/login', { method: 'POST' }, { handleUnauthorized: false }),
    ).rejects.toBeInstanceOf(ApiError)
    expect(currentUser.value).not.toBeNull()
    expect(handler).not.toHaveBeenCalled()
  })

  it('exposes Retry-After on 429', async () => {
    respond(429, { detail: 'too_many_attempts' }, { 'Retry-After': '120' })
    await expect(apiFetch('/api/x')).rejects.toMatchObject({
      status: 429,
      detail: 'too_many_attempts',
      retryAfter: 120,
    })
    expect(handler).not.toHaveBeenCalled()
  })

  it('returns undefined for 204', async () => {
    respond(204)
    await expect(apiFetch('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('tolerates a non-JSON error body', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('<html>502</html>', { status: 502 })),
    )
    await expect(apiFetch('/api/x')).rejects.toMatchObject({ status: 502, detail: null })
  })
})

describe('apiFetchText', () => {
  let handler: ReturnType<typeof vi.fn<() => void>>
  let unsubscribe: () => void

  beforeEach(() => {
    resetSession()
    setSession({ id: '1', email: 'admin@example.com', role: 'admin' })
    handler = vi.fn<() => void>()
    unsubscribe = onUnauthorized(handler)
  })

  afterEach(() => {
    unsubscribe()
    vi.unstubAllGlobals()
  })

  it('returns the body as it came, unparsed', async () => {
    const body = '{\n  "id" : 81234567,\n  "content" : "Zażółć gęślą jaźń"\n}'
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(body, { status: 200, headers: { 'Content-Type': 'application/json' } }),
    )
    vi.stubGlobal('fetch', fetchMock)
    await expect(apiFetchText('/api/posts/1/raw')).resolves.toBe(body)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/posts/1/raw')
    expect(init.credentials).toBe('same-origin')
  })

  it('throws an ApiError with the detail on 404', async () => {
    respond(404, { detail: 'post_not_found' })
    await expect(apiFetchText('/api/posts/x/raw')).rejects.toMatchObject({ status: 404, detail: 'post_not_found' })
    expect(handler).not.toHaveBeenCalled()
  })

  it('on 401 calls the handler', async () => {
    respond(401, { detail: 'not_authenticated' })
    await expect(apiFetchText('/api/posts/1/raw')).rejects.toBeInstanceOf(ApiError)
    expect(currentUser.value).toBeNull()
    expect(handler).toHaveBeenCalledTimes(1)
  })
})
