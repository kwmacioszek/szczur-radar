// /api/admin/notifications — the contract of szczur-radar-api notifications/api/NotificationController.
import { apiFetch } from './client'
import type { InterruptionCause, RunKind, RunStatus } from './pipeline'

/** What a notification is about — notifications/NotificationKind. */
export type NotificationKind = 'run_attention'

/** What a notification says about its run now; the run's page (`fetchRun`) has the rest. */
export interface NotifiedRun {
  id: string
  kind: RunKind
  status: RunStatus
  interruptionCause: InterruptionCause | null
  finishedAt: string | null
  assessedFailed: number
  dead: number
  processingErrors: number
  /** Elements its source skipped (unexpected shape or not mappable); above 0 a succeeded run needs attention too. */
  skipped: number
  error: string | null
}

/** One notification of the logged-in admin. Named apart from the browser's own `Notification`. */
export interface AdminNotification {
  id: string
  kind: NotificationKind
  createdAt: string
  /** When the admin read it; null while unread. */
  readAt: string | null
  /** The run it is about; null if the API no longer finds it. */
  run: NotifiedRun | null
}

export interface NotificationList {
  /** How many of all the admin's notifications are unread, not only of `items`. */
  unread: number
  /** The 10 newest, newest first. */
  items: AdminNotification[]
}

/** The logged-in admin's newest notifications and the number still unread. */
export function fetchNotifications(): Promise<NotificationList> {
  return apiFetch<NotificationList>('/api/admin/notifications')
}

/** Marks one notification read (idempotent); 404 `not_found` for an unknown id or another admin's. */
export function markNotificationRead(id: string): Promise<void> {
  return apiFetch<void>(`/api/admin/notifications/${encodeURIComponent(id)}/read`, { method: 'POST' })
}

/** Marks every unread notification of the logged-in admin read, also those beyond the 10 listed. */
export function markAllNotificationsRead(): Promise<void> {
  return apiFetch<void>('/api/admin/notifications/read', { method: 'POST' })
}
