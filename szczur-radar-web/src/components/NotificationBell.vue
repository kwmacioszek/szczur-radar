<script setup lang="ts">
// The admin's notifications in the header (AppHeader renders it for an admin only): read on mount, then every
// 60 s while the tab is visible; a chosen notification is marked read and its run's page opens. A failed refresh
// keeps the list on screen; only without a list is it an error. NotificationMenu draws it.
import { onMounted, ref, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import {
  type AdminNotification,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notifications'
import { useEvery } from '../polling'
import NotificationMenu from './NotificationMenu.vue'

/** How often the list is read again while the tab is visible. */
const POLL_MS = 60 * 1000

const router = useRouter()
const menu = useTemplateRef<InstanceType<typeof NotificationMenu>>('menu')

const state = ref<'loading' | 'error' | 'loaded'>('loading')
const unread = ref<number | null>(null)
const items = ref<AdminNotification[]>([])
const open = ref(false)
const now = ref(Date.now())
const markingAll = ref(false)
const markAllFailed = ref(false)
/** The number of the latest load; the answer or error of any earlier one is dropped. */
let latestLoad = 0

async function load(): Promise<void> {
  const current = ++latestLoad
  if (state.value !== 'loaded') state.value = 'loading'
  try {
    const list = await fetchNotifications()
    if (current !== latestLoad) return
    unread.value = list.unread
    items.value = list.items
    now.value = Date.now()
    state.value = 'loaded'
  } catch {
    if (current !== latestLoad) return
    if (state.value !== 'loaded') state.value = 'error'
  }
}

/** Marks it read (a failure does not stop the page from opening), closes the list and opens the run. */
async function select(notification: AdminNotification): Promise<void> {
  const run = notification.run
  if (!run) return
  open.value = false
  if (!notification.readAt) {
    try {
      await markNotificationRead(notification.id)
      items.value = items.value.map((item) =>
        item.id === notification.id ? { ...item, readAt: new Date().toISOString() } : item,
      )
      if (unread.value) unread.value--
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
    await markAllNotificationsRead()
    const readAt = new Date().toISOString()
    items.value = items.value.map((item) => (item.readAt ? item : { ...item, readAt }))
    unread.value = 0
    // The button goes away with the last unread notification; the focus goes back to the bell.
    menu.value?.focusToggle()
  } catch {
    markAllFailed.value = true
  } finally {
    markingAll.value = false
  }
}

onMounted(load)
useEvery(POLL_MS, () => {
  if (document.visibilityState !== 'hidden') void load()
})
</script>

<template>
  <NotificationMenu
    ref="menu"
    v-model:open="open"
    :state="state"
    :unread="unread"
    :items="items"
    :now="now"
    :marking-all="markingAll"
    :mark-all-failed="markAllFailed"
    @select="select"
    @retry="load"
    @mark-all="markAll"
  />
</template>
