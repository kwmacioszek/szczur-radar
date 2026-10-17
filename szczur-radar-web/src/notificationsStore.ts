// The admin's notifications for the bell, kept in the module like the session (auth/session.ts): every view renders
// its own AppHeader, so the bell is mounted again on each page and finds the list it had, without a loading state or
// another request. NotificationBell decides when to read it again; the session empties it when it ends, so the next
// account never sees the previous admin's notifications.
import { ref } from 'vue'
import {
  type AdminNotification,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from './api/notifications'

/** A list read less than this long ago is fresh: `refreshNotifications` does not read it again. */
export const FRESH_MS = 10 * 1000

/** `loading` until the first list (and on a retry after an error); a failed later read keeps `loaded`. */
export const notificationsState = ref<'loading' | 'error' | 'loaded'>('loading')
/** Unread notifications of the admin, also beyond `notifications`; null until known. */
export const unreadNotifications = ref<number | null>(null)
/** The newest notifications, newest first. */
export const notifications = ref<AdminNotification[]>([])
/** When the list was last read (ms since the epoch); the relative times count from it. 0 before the first read. */
export const notificationsReadAt = ref(0)

/** The number of the latest load; the answer or error of any earlier one, or of one before a reset, is dropped. */
let latestLoad = 0
/** The load under way, if any. */
let loading: Promise<void> | null = null
/** Moves on every reset: a mark answered after it changes nothing. */
let generation = 0

/** Reads the list now (also while another read is under way: the latest answer wins). */
export function loadNotifications(): Promise<void> {
  const current = ++latestLoad
  if (notificationsState.value !== 'loaded') notificationsState.value = 'loading'
  const load: Promise<void> = fetchNotifications()
    .then(
      (list) => {
        if (current !== latestLoad) return
        unreadNotifications.value = list.unread
        notifications.value = list.items
        notificationsReadAt.value = Date.now()
        notificationsState.value = 'loaded'
      },
      () => {
        if (current !== latestLoad) return
        if (notificationsState.value !== 'loaded') notificationsState.value = 'error'
      },
    )
    .finally(() => {
      if (loading === load) loading = null
    })
  loading = load
  return load
}

/** Reads the list unless a read is under way or the last one is fresh (`FRESH_MS`); a failed list is read again. */
export function refreshNotifications(): void {
  if (loading) return
  if (notificationsState.value === 'loaded' && Date.now() - notificationsReadAt.value < FRESH_MS) return
  void loadNotifications()
}

/** Marks one read on the server, then in the list. Throws when the server refuses; the list stays as it was. */
export async function markNotificationAsRead(id: string): Promise<void> {
  const current = generation
  await markNotificationRead(id)
  if (current !== generation) return
  const readAt = new Date().toISOString()
  notifications.value = notifications.value.map((item) => (item.id === id ? { ...item, readAt } : item))
  if (unreadNotifications.value) unreadNotifications.value--
}

/** Marks every one read on the server, then in the list. Throws when the server refuses; the list stays as it was. */
export async function markAllNotificationsAsRead(): Promise<void> {
  const current = generation
  await markAllNotificationsRead()
  if (current !== generation) return
  const readAt = new Date().toISOString()
  notifications.value = notifications.value.map((item) => (item.readAt ? item : { ...item, readAt }))
  unreadNotifications.value = 0
}

/** Forgets the list (the session ended, or a test starts); an answer still under way is dropped. */
export function resetNotifications(): void {
  latestLoad++
  generation++
  loading = null
  notificationsState.value = 'loading'
  unreadNotifications.value = null
  notifications.value = []
  notificationsReadAt.value = 0
}

/** The admin's pages, where the bell also reads the list every 60 s: `/admin` and `/admin/...`. */
export function isAdminPath(path: string): boolean {
  return path === '/admin' || path.startsWith('/admin/')
}
