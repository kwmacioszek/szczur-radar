<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { ApiError } from '../api/client'
import { setSession } from '../auth/session'
import { safeRedirect } from '../router/redirect'

const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)

function errorMessage(err: unknown): string {
  if (err instanceof ApiError && err.status === 401) {
    return 'Nieprawidłowy e-mail lub hasło.'
  }
  if (err instanceof ApiError && err.status === 429) {
    if (err.retryAfter === undefined) return 'Zbyt wiele prób. Spróbuj ponownie później.'
    const minutes = Math.max(1, Math.ceil(err.retryAfter / 60))
    return `Zbyt wiele prób. Spróbuj ponownie za ${minutes} min.`
  }
  return 'Nie udało się zalogować. Spróbuj ponownie.'
}

async function submit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  error.value = null
  try {
    setSession(await login(email.value, password.value))
    await router.replace(safeRedirect(route.query.redirect))
  } catch (err) {
    error.value = errorMessage(err)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="page">
    <section class="card" aria-labelledby="login-title">
      <p class="eyebrow">Szczur Radar</p>
      <h1 id="login-title">Logowanie</h1>
      <form class="form" novalidate @submit.prevent="submit">
        <label>
          E-mail
          <input v-model="email" type="email" name="email" autocomplete="username" required />
        </label>
        <label>
          Hasło
          <input
            v-model="password"
            type="password"
            name="password"
            autocomplete="current-password"
            required
          />
        </label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button type="submit" :disabled="submitting">
          {{ submitting ? 'Logowanie…' : 'Zaloguj się' }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
  text-align: left;
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--text-h);
  font-size: 16px;
}

input {
  font: inherit;
  padding: 10px 12px;
  border: 1px solid var(--text);
  border-radius: 8px;
  background: var(--bg);
  color: var(--text-h);
}

button {
  font: inherit;
  padding: 10px 16px;
  border: 0;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: progress;
}

.error {
  margin: 0;
  color: #d93025;
}
</style>
