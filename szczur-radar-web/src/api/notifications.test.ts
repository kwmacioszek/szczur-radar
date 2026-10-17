import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchNotifications, markAllNotificationsRead, markNotificationRead } from './notifications'

function respond(status: number, body?: unknown) {
  // A new Response per call: a body can be read only once.
  const fetchMock = vi.fn().mockImplementation(
    async () =>
      new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
      }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function sent(fetchMock: ReturnType<typeof respond>, call = 0): { path: string; method?: string; body?: unknown } {
  const [path, init] = fetchMock.mock.calls[call] as [string, RequestInit]
  return { path, method: init.method, body: init.body }
}

describe('notifications API', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('reads the newest notifications with the unread count', async () => {
    const list = { unread: 2, items: [] }
    const fetchMock = respond(200, list)
    await expect(fetchNotifications()).resolves.toEqual(list)
    expect(sent(fetchMock)).toEqual({ path: '/api/admin/notifications', method: undefined, body: undefined })
  })

  it('marks one notification read by its encoded id, without a body', async () => {
    const fetchMock = respond(204)
    await expect(markNotificationRead('a/b')).resolves.toBeUndefined()
    expect(sent(fetchMock)).toEqual({ path: '/api/admin/notifications/a%2Fb/read', method: 'POST', body: undefined })
  })

  it('marks every notification read', async () => {
    const fetchMock = respond(204)
    await expect(markAllNotificationsRead()).resolves.toBeUndefined()
    expect(sent(fetchMock)).toEqual({ path: '/api/admin/notifications/read', method: 'POST', body: undefined })
  })

  it("says another admin's or an unknown notification is not found", async () => {
    respond(404, { detail: 'not_found' })
    await expect(markNotificationRead('x')).rejects.toMatchObject({ status: 404, detail: 'not_found' })
  })
})
