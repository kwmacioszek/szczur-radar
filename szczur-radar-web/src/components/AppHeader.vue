<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { clearSession, currentUser } from '../auth/session'

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
    <button type="button" :disabled="loggingOut" @click="signOut">Wyloguj</button>
  </header>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
</template>

<style scoped>
.topbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  padding: 12px 16px;
  border-bottom: 1px solid color-mix(in srgb, var(--text) 25%, transparent);
}

.nav {
  display: flex;
  gap: 16px;
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
  font-size: 16px;
  overflow-wrap: anywhere;
}

.error {
  margin: 8px 16px;
  text-align: right;
  color: #d93025;
}
</style>
