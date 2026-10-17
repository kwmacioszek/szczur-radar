import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api/client'
import {
  type AdminNotification,
  type NotificationList,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from './api/notifications'
import { clearSession, resetSession, setSession } from './auth/session'
import {
  FRESH_MS,
  isAdminPath,
  loadNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  notifications,
  notificationsState,
  refreshNotifications,
  resetNotifications,
  unreadNotifications,
} from './notificationsStore'

vi.mock('./api/auth', () => ({ fetchMe: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('./api/notifications', () => ({
  fetchNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}))

const ADMIN = { id: '1', email: 'admin@example.com', role: 'admin' } as const

function notification(id: string, readAt: string | null = null): AdminNotification {
  return { id, kind: 'run_attention', createdAt: '2026-10-15T07:00:00Z', readAt, run: null }
}

function list(items: AdminNotification[], unread = items.filter((item) => !item.readAt).length): NotificationList {
  return { unread, items }
}

/** Lets the answers given so far arrive (a real timer: only Date is fake here). */
function settle(): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

/** A read whose answer the test gives. */
function pending(): { answer: (value: NotificationList) => void } {
  const control = { answer: (_value: NotificationList) => {} }
  vi.mocked(fetchNotifications).mockReturnValueOnce(new Promise((resolve) => (control.answer = resolve)))
  return control
}

describe('notificationsStore', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    resetSession()
    vi.mocked(fetchNotifications).mockReset().mockResolvedValue(list([notification('a')]))
    vi.mocked(markNotificationRead).mockReset().mockResolvedValue(undefined)
    vi.mocked(markAllNotificationsRead).mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('reads once while a read is under way, then not again while the list is fresh', async () => {
    const read = pending()
    refreshNotifications()
    refreshNotifications()
    expect(notificationsState.value).toBe('loading')
    read.answer(list([notification('a')]))
    await settle()
    expect(notificationsState.value).toBe('loaded')
    expect(unreadNotifications.value).toBe(1)

    vi.advanceTimersByTime(FRESH_MS - 1)
    refreshNotifications()
    expect(fetchNotifications).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(1)
    refreshNotifications()
    expect(fetchNotifications).toHaveBeenCalledTimes(2)
  })

  it('reads a list that failed to load again on the next refresh, and keeps a list when a later read fails', async () => {
    vi.mocked(fetchNotifications).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
    await loadNotifications()
    expect(notificationsState.value).toBe('error')

    refreshNotifications()
    await settle()
    expect(notificationsState.value).toBe('loaded')
    expect(fetchNotifications).toHaveBeenCalledTimes(2)

    vi.mocked(fetchNotifications).mockRejectedValueOnce(new ApiError(503, null))
    await loadNotifications()
    expect(notificationsState.value).toBe('loaded')
    expect(notifications.value.map((item) => item.id)).toEqual(['a'])
  })

  it('keeps the latest answer when reads overlap', async () => {
    const older = pending()
    const newer = pending()
    void loadNotifications()
    void loadNotifications()
    newer.answer(list([notification('new')]))
    older.answer(list([notification('old')]))
    await settle()
    expect(notificationsState.value).toBe('loaded')
    expect(notifications.value.map((item) => item.id)).toEqual(['new'])
  })

  it('marks one or every notification read in the list once the server did', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(list([notification('a'), notification('b'), notification('c')], 7))
    await loadNotifications()

    await markNotificationAsRead('b')
    expect(notifications.value.map((item) => item.readAt !== null)).toEqual([false, true, false])
    expect(unreadNotifications.value).toBe(6)

    vi.mocked(markNotificationRead).mockRejectedValueOnce(new ApiError(500, 'internal_error'))
    await expect(markNotificationAsRead('a')).rejects.toThrow(ApiError)
    expect(unreadNotifications.value).toBe(6)

    await markAllNotificationsAsRead()
    expect(notifications.value.every((item) => item.readAt !== null)).toBe(true)
    expect(unreadNotifications.value).toBe(0)
  })

  it('forgets the list on a reset and drops an answer or a mark that arrives after it', async () => {
    await loadNotifications()
    const late = pending()
    void loadNotifications()
    let marked: () => void = () => {}
    vi.mocked(markAllNotificationsRead).mockReturnValueOnce(new Promise((resolve) => (marked = () => resolve())))
    const marking = markAllNotificationsAsRead()

    resetNotifications()
    late.answer(list([notification('late')]))
    marked()
    await marking

    expect(notificationsState.value).toBe('loading')
    expect(unreadNotifications.value).toBeNull()
    expect(notifications.value).toEqual([])
    // Not fresh after a reset: the next refresh reads.
    refreshNotifications()
    expect(fetchNotifications).toHaveBeenCalledTimes(3)
  })

  it('is forgotten when the session ends or another account logs in, not when the same one does', async () => {
    setSession(ADMIN)
    await loadNotifications()
    setSession(ADMIN)
    expect(notifications.value).toHaveLength(1)

    setSession({ id: '2', email: 'other-admin@example.com', role: 'admin' })
    expect(notifications.value).toEqual([])

    await loadNotifications()
    clearSession()
    expect(notifications.value).toEqual([])
    expect(notificationsState.value).toBe('loading')
  })

  it.each([
    ['/admin', true],
    ['/admin/runs/r1', true],
    ['/', false],
    ['/posts/p1', false],
    ['/administration', false],
  ])('treats %s as an admin page: %s', (path, admin) => {
    expect(isAdminPath(path)).toBe(admin)
  })
})
