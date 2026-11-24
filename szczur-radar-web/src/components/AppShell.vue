<script setup lang="ts">
// The shell of the logged-in pages, the parent route of the list of posts (with a post over it), /admin and a run
// (router/index.ts): it is mounted once and stays while the child pages change, so the bar does not blink and keeps its
// data. It joins the session, the router and /api/freshness to AppBar and gives the page its column.
// The main navigation's item is the page's section (`meta.section`), not the exact address: a run's page is still
// "Panel admina". The date of the data is read on mount, after every page change and when the tab becomes visible,
// unless the last read is fresher than FRESHNESS_FRESH_MS (no polling in the background: an idle tab lets the API
// sleep). While it is read again the previous date stays, so the height of the bar does not change under the list's
// scroll position while a post opens and closes over it (router/scroll.ts).
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { type Freshness, fetchFreshness } from '../api/freshness'
import { clearSession, currentUser } from '../auth/session'
import type { AppSection } from '../router'
import AppBar, { type NavItem } from './AppBar.vue'
import NotificationBell from './NotificationBell.vue'
import StatusMessage from './StatusMessage.vue'

/** A date of the data read less than this long ago is fresh: it is not read again. */
const FRESHNESS_FRESH_MS = 60_000

const route = useRoute()
const router = useRouter()

const column = computed(() => route.meta.column)

const isAdmin = computed(() => currentUser.value?.role === 'admin')

const items = computed<NavItem[]>(() => {
  const entries: { label: string; name: string; section: AppSection }[] = [
    { label: 'Posty', name: 'posts', section: 'posts' },
  ]
  if (isAdmin.value) entries.push({ label: 'Panel admina', name: 'admin', section: 'admin' })
  return entries.map((entry) => ({
    label: entry.label,
    to: { name: entry.name },
    current: route.meta.section === entry.section,
  }))
})

// --- sign out ------------------------------------------------------------------------------
const loggingOut = ref(false)
const logoutError = ref<string | null>(null)

async function signOut(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  logoutError.value = null
  try {
    await logout()
  } catch {
    // The cookie may still be set: do not pretend the user is logged out.
    logoutError.value = 'Nie udało się wylogować. Spróbuj ponownie.'
    loggingOut.value = false
    return
  }
  clearSession()
  await router.replace({ name: 'login' })
}

// --- the date of the data --------------------------------------------------------------------
/** `null`: no label (not read yet, or it could not be read). */
const freshness = ref<Freshness | null>(null)
/** When the last successful read ended (ms since the epoch); null before the first one. */
let readAt: number | null = null
let reading = false
let mounted = false

/**
 * Reads the date again unless a read is under way or the last one is fresh, while someone is logged in. One read at a
 * time: the API client has no time limit, so a request that hangs holds the next reads until the browser ends it.
 */
function refreshFreshness(): void {
  // Logging out runs the page change to /login while the shell is still mounted: no one to ask for.
  if (!mounted || reading || !currentUser.value) return
  if (readAt !== null && Date.now() - readAt < FRESHNESS_FRESH_MS) return
  reading = true
  fetchFreshness()
    .then(
      (result) => {
        // API and web deploy independently: an older API answers with its old shape, which gives no label.
        freshness.value = Array.isArray(result?.sources) ? result : null
        readAt = Date.now()
      },
      () => {
        // The previous date stays; without one there is no label.
      },
    )
    .finally(() => {
      reading = false
    })
}

function refreshWhenVisible(): void {
  if (document.visibilityState === 'visible') refreshFreshness()
}

let removeAfterEach: (() => void) | undefined

onMounted(() => {
  mounted = true
  refreshFreshness()
  removeAfterEach = router.afterEach((_to, _from, failure) => {
    if (!failure) refreshFreshness()
  })
  document.addEventListener('visibilitychange', refreshWhenVisible)
})
onBeforeUnmount(() => {
  mounted = false
  removeAfterEach?.()
  document.removeEventListener('visibilitychange', refreshWhenVisible)
})
</script>

<template>
  <AppBar
    :items="items"
    :freshness="freshness"
    :email="currentUser?.email ?? null"
    :logging-out="loggingOut"
    @logout="signOut"
  >
    <template #notifications><NotificationBell v-if="isAdmin" /></template>
  </AppBar>
  <!-- The message has no margin of its own: the container spaces it from the bar and from the page below. -->
  <div v-if="logoutError" class="notice">
    <StatusMessage tone="error">{{ logoutError }}</StatusMessage>
  </div>
  <main class="page">
    <!-- The page's own width comes from its route (`meta.column`); a route without one spans the whole column. -->
    <div :class="column ? `column column-${column}` : undefined">
      <RouterView />
    </div>
  </main>
</template>

<style scoped>
/* The column of every page: the width and the margins of the design's `.page`. */
.page {
  box-sizing: border-box;
  max-width: var(--width-content);
  margin: 0 auto;
  padding: var(--space-7) var(--space-5) var(--space-9);
}

.notice {
  box-sizing: border-box;
  max-width: var(--width-content);
  margin: 0 auto;
  padding: var(--space-3) var(--space-5) 0;
}

/* The widths of the pages' columns (`meta.column`), inside the shell's column. */
.column {
  margin: 0 auto;
}

.column-narrow {
  max-width: 48rem;
}

.column-wide {
  max-width: 56rem;
}

/* The root font size is 16px at every width; above 1024px the width is what 18px gave (design-tokens-and-components). */
@media (min-width: 1025px) {
  .column-narrow {
    max-width: 54rem;
  }

  .column-wide {
    max-width: 63rem;
  }
}

@media (max-width: 899px) {
  .page {
    padding: var(--space-5) var(--space-3) var(--space-8);
  }

  .notice {
    padding: var(--space-3) var(--space-3) 0;
  }
}
</style>
