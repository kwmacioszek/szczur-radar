<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { ApiError } from '../api/client'
import { setSession } from '../auth/session'
import StatusMessage from '../components/StatusMessage.vue'
import { safeRedirect } from '../router/redirect'

const route = useRoute()
const router = useRouter()

// ActivateView arrives here with the account in the router's history state (never in the query, which
// would stay in the address and reach the proxy's log): fill in the e-mail and confirm the password.
const { activatedEmail: fromActivation } = router.options.history.state
const activatedEmail = typeof fromActivation === 'string' ? fromActivation : null

const email = ref(activatedEmail ?? '')
const password = ref('')
const submitting = ref(false)
const error = ref<string | null>(null)
/** The "password set" notice, until the first login attempt. */
const activated = ref(activatedEmail !== null)

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
  activated.value = false
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
        <StatusMessage v-if="activated" tone="ok" class="notice">Hasło ustawione. Zaloguj się.</StatusMessage>
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
  color: var(--on-accent);
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: progress;
}

.error {
  margin: 0;
  color: var(--destructive);
}

/* The form's gap spaces the notice; the parent's selector outranks the message's own margin. */
.form .notice {
  margin: 0;
}
</style>
