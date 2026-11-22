<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../api/auth'
import { ApiError } from '../api/client'
import { setSession } from '../auth/session'
import AppButton from '../components/AppButton.vue'
import AuthLayout from '../components/AuthLayout.vue'
import FormField from '../components/FormField.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { restoreFocus } from '../focus'
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
  const sender = document.activeElement
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
  // A refusal: a clicked button lost the focus while it was busy. After a login the page is gone: nothing to do.
  await restoreFocus(sender)
}
</script>

<template>
  <AuthLayout title="Logowanie">
    <form novalidate @submit.prevent="submit">
      <StatusMessage v-if="activated" tone="ok">Hasło ustawione. Zaloguj się.</StatusMessage>
      <StatusMessage v-if="error" tone="error">{{ error }}</StatusMessage>
      <FormField v-slot="{ fieldProps }" label="E-mail">
        <input v-model="email" v-bind="fieldProps" type="email" name="email" autocomplete="username" required />
      </FormField>
      <FormField v-slot="{ fieldProps }" label="Hasło">
        <input
          v-model="password"
          v-bind="fieldProps"
          type="password"
          name="password"
          autocomplete="current-password"
          required
        />
      </FormField>
      <AppButton type="submit" variant="primary" :busy="submitting" busy-label="Logowanie…">Zaloguj się</AppButton>
      <p class="note">Konto zakłada administrator.</p>
    </form>
  </AuthLayout>
</template>

<style scoped>
.note {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}
</style>
