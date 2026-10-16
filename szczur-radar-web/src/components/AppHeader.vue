<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { clearSession, currentUser } from '../auth/session'
import AppButton from './AppButton.vue'
import NotificationBell from './NotificationBell.vue'
import StatusMessage from './StatusMessage.vue'

const router = useRouter()
const loggingOut = ref(false)
const error = ref<string | null>(null)
const isAdmin = computed(() => currentUser.value?.role === 'admin')

async function signOut(): Promise<void> {
  if (loggingOut.value) return
  loggingOut.value = true
  error.value = null
  try {
    await logout()
  } catch {
    // The cookie may still be set: do not pretend the user is logged out.
    error.value = 'Nie udało się wylogować. Spróbuj ponownie.'
    loggingOut.value = false
    return
  }
  clearSession()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <header class="topbar">
    <nav class="nav">
      <RouterLink :to="{ name: 'home' }">Posty</RouterLink>
      <RouterLink v-if="isAdmin" :to="{ name: 'admin' }">Panel admina</RouterLink>
    </nav>
    <span class="who">{{ currentUser?.email }}</span>
    <NotificationBell v-if="isAdmin" />
    <AppButton :busy="loggingOut" @click="signOut">Wyloguj</AppButton>
  </header>
  <div v-if="error" class="notice">
    <StatusMessage tone="error">{{ error }}</StatusMessage>
  </div>
</template>

<style scoped>
/* Positioned: the bell's panel opens below the bar, aligned to its right edge. */
.topbar {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border);
}

.nav {
  display: flex;
  gap: var(--space-4);
  margin-right: auto;
}

.nav a {
  color: var(--accent);
  text-decoration: none;
}

.nav a.router-link-exact-active {
  color: var(--text-h);
  font-weight: 600;
}

.who {
  color: var(--text-h);
  font-size: var(--font-size-md);
  overflow-wrap: anywhere;
}

.notice {
  padding: 0 var(--space-4);
}
</style>
