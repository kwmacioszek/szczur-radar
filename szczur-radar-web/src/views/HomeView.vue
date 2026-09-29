<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { logout } from '../api/auth'
import { clearSession, currentUser } from '../auth/session'

const router = useRouter()
const loggingOut = ref(false)
const error = ref<string | null>(null)

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
    <span class="who">{{ currentUser?.email }}</span>
    <button type="button" :disabled="loggingOut" @click="signOut">Wyloguj</button>
  </header>
  <p v-if="error" class="error" role="alert">{{ error }}</p>
  <main class="page">
    <section class="card" aria-labelledby="title">
      <p class="eyebrow">Szczur Radar</p>
      <h1 id="title">W przygotowaniu</h1>
      <p class="lead">Trwają prace nad serwisem. Zapraszamy wkrótce.</p>
    </section>
  </main>
</template>

<style scoped>
.topbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
}

.who {
  color: var(--text-h);
  font-size: 16px;
}

button {
  font: inherit;
  font-size: 16px;
  padding: 6px 14px;
  border: 1px solid var(--accent);
  border-radius: 8px;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: progress;
}

.error {
  margin: 0 16px;
  text-align: right;
  color: #d93025;
}
</style>
