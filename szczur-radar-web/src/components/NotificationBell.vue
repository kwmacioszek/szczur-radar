<script setup lang="ts">
// The admin's notifications in the header (AppShell renders it for an admin only). The list lives in
// notificationsStore, which lives with the session: the session's end empties it, and the shell mounts anew after the
// next login. It is read again on mount, after a page change and when the tab becomes visible, unless it is fresh
// (FRESH_MS); every 60 s only on the admin's pages (/admin, /admin/runs/:id) while the tab is visible, so an open
// feed tab lets the API sleep. A chosen notification is marked read and its run's page opens. A failed refresh keeps
// the list on screen; only without a list is it an error. NotificationMenu draws it. Opening or closing a post over the
// list is no page change: the posts screen stays (router/screens.ts), so the list is not read again.
import { onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import type { AdminNotification } from '../api/notifications'
import { currentUser } from '../auth/session'
import {
  isAdminPath,
  loadNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  notifications,
  notificationsReadAt,
  notificationsState,
  refreshNotifications,
  unreadNotifications,
} from '../notificationsStore'
import { useEvery } from '../polling'
import { withinPostsScreen } from '../router/screens'
import NotificationMenu from './NotificationMenu.vue'

/** How often the list is read again on the admin's pages while the tab is visible. */
const POLL_MS = 60 * 1000

const router = useRouter()
const menu = useTemplateRef<InstanceType<typeof NotificationMenu>>('menu')

const open = ref(false)
const markingAll = ref(false)
const markAllFailed = ref(false)

/** Reads the list again unless it is fresh, while an admin is logged in (not on the way to /login after a logout). */
function refresh(): void {
  if (currentUser.value?.role === 'admin') refreshNotifications()
}

function refreshWhenVisible(): void {
  if (document.visibilityState === 'visible') refresh()
}

/** Marks it read (a failure does not stop the page from opening), closes the list and opens the run. */
async function select(notification: AdminNotification): Promise<void> {
  const run = notification.run
  if (!run) return
  open.value = false
  if (!notification.readAt) {
    try {
      await markNotificationAsRead(notification.id)
    } catch {
      // Still unread on the server: the next read of the list shows it so.
    }
  }
  await router.push({ name: 'admin-run', params: { id: run.id } })
}

async function markAll(): Promise<void> {
  if (markingAll.value) return
  markingAll.value = true
  markAllFailed.value = false
  try {
    await markAllNotificationsAsRead()
    // The button goes away with the last unread notification; the focus goes back to the bell.
    menu.value?.focusToggle()
  } catch {
    markAllFailed.value = true
  } finally {
    markingAll.value = false
  }
}

let removeAfterEach: (() => void) | undefined

onMounted(() => {
  refresh()
  // A page change, also to another view: the shell, and with it this bell, stays mounted.
  removeAfterEach = router.afterEach((to, from, failure) => {
    if (!failure && !withinPostsScreen(to, from)) refresh()
  })
  document.addEventListener('visibilitychange', refreshWhenVisible)
})
onBeforeUnmount(() => {
  removeAfterEach?.()
  document.removeEventListener('visibilitychange', refreshWhenVisible)
})
useEvery(POLL_MS, () => {
  if (document.visibilityState !== 'hidden' && isAdminPath(router.currentRoute.value.path)) refresh()
})
</script>

<template>
  <NotificationMenu
    ref="menu"
    v-model:open="open"
    :state="notificationsState"
    :unread="unreadNotifications"
    :items="notifications"
    :now="notificationsReadAt"
    :marking-all="markingAll"
    :mark-all-failed="markAllFailed"
    @select="select"
    @retry="loadNotifications"
    @mark-all="markAll"
  />
</template>
