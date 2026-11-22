<script setup lang="ts">
// Setting the first (or a reset) password through the link the operator's CLI prints:
// <web>/activate#<token>. The fragment never reaches a server or a Referer header; the token goes to
// the API only in a JSON body, and nothing here logs it.
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  activate,
  activationFailure,
  lookupActivation,
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
} from '../api/activation'
import { logout } from '../api/auth'
import { clearSession, ensureSession } from '../auth/session'
import AppButton from '../components/AppButton.vue'
import AuthLayout from '../components/AuthLayout.vue'
import FormField from '../components/FormField.vue'
import StatusMessage from '../components/StatusMessage.vue'
import { restoreFocus } from '../focus'

/** Checking the link, a link that cannot be used, a check that failed (network, 5xx), or the form. */
type Phase = 'checking' | 'invalid' | 'lookup_failed' | 'form'

/** Why the form was not accepted: by the check below or by the server. */
type FormProblem = 'too_short' | 'too_long' | 'mismatch' | 'password_policy' | 'unavailable'

const PROBLEM_MESSAGES: Record<FormProblem, string> = {
  too_short: `Hasło musi mieć co najmniej ${PASSWORD_MIN_LENGTH} znaków.`,
  too_long: `Hasło może mieć najwyżej ${PASSWORD_MAX_LENGTH} znaków.`,
  mismatch: 'Hasła nie są takie same.',
  password_policy: `Hasło musi mieć od ${PASSWORD_MIN_LENGTH} do ${PASSWORD_MAX_LENGTH} znaków.`,
  unavailable: 'Nie udało się ustawić hasła. Spróbuj ponownie.',
}

const route = useRoute()
const router = useRouter()

const token = route.hash.slice(1)

const phase = ref<Phase>(token ? 'checking' : 'invalid')
const email = ref('')
const password = ref('')
const repeat = ref('')
const submitting = ref(false)
const problem = ref<FormProblem | null>(null)
const passwordInput = ref<HTMLInputElement | null>(null)
const repeatInput = ref<HTMLInputElement | null>(null)

const passwordInvalid = computed(
  () => problem.value === 'too_short' || problem.value === 'too_long' || problem.value === 'password_policy',
)
const repeatInvalid = computed(() => problem.value === 'mismatch')

/** The policy as the API checks it; `[...text]` counts code points, not UTF-16 units. */
function formProblem(): FormProblem | null {
  const length = [...password.value].length
  if (length < PASSWORD_MIN_LENGTH) return 'too_short'
  if (length > PASSWORD_MAX_LENGTH) return 'too_long'
  if (password.value !== repeat.value) return 'mismatch'
  return null
}

async function check(): Promise<void> {
  phase.value = 'checking'
  try {
    email.value = (await lookupActivation(token)).email
  } catch (err) {
    phase.value = activationFailure(err) === 'link_invalid' ? 'invalid' : 'lookup_failed'
    return
  }
  phase.value = 'form'
  // The form replaces the "checking" line: take the focus there, so a screen reader announces it.
  await nextTick()
  passwordInput.value?.focus()
}

/**
 * An account waiting for its password has no session, so a session in this browser belongs to
 * another account, and /login would send it on to the feed. End it first.
 */
async function endOtherSession(): Promise<void> {
  if (!(await ensureSession())) return
  try {
    await logout()
  } catch {
    // Logging in replaces the session cookie anyway; the page only has to forget the other account.
  }
  clearSession()
}

/**
 * Puts the problem where it was made: the field it is about. `unavailable` is the form's: the focus goes back to where
 * the form was sent from (`restoreFocus` in `submit`).
 */
async function focusProblem(): Promise<void> {
  await nextTick()
  if (passwordInvalid.value) passwordInput.value?.focus()
  else if (repeatInvalid.value) repeatInput.value?.focus()
}

async function submit(): Promise<void> {
  if (submitting.value) return
  const sender = document.activeElement
  submitting.value = true
  // Clear first and let the page render: the same problem sent again appears anew in the field's live region and is
  // announced again.
  problem.value = null
  await nextTick()
  problem.value = formProblem()
  if (problem.value) {
    submitting.value = false
    await focusProblem()
    return
  }
  try {
    await activate(token, password.value)
  } catch (err) {
    const failure = activationFailure(err)
    if (failure === 'link_invalid') phase.value = 'invalid'
    else {
      problem.value = failure
      await focusProblem()
    }
    submitting.value = false
    await restoreFocus(sender)
    return
  }
  // The link is used up: the button stays busy until the login screen replaces this page.
  await endOtherSession()
  await router.replace({ name: 'login', state: { activatedEmail: email.value } })
}

onMounted(() => {
  if (!token) return
  // From here the token lives only in this page, not in the address bar or the tab's back/forward
  // entry, where it would stay usable until it expires. (A browser's global history may still have
  // recorded the visit.) A reload needs the operator's link again.
  void router.replace({ path: route.path, query: route.query })
  void check()
})
</script>

<template>
  <AuthLayout title="Ustawienie hasła" :busy="phase === 'checking'">
    <p v-if="phase === 'checking'">Sprawdzanie linku…</p>
    <StatusMessage v-else-if="phase === 'invalid'" tone="error">Link jest nieważny lub wygasł. Poproś administratora o nowy.</StatusMessage>
    <template v-else-if="phase === 'lookup_failed'">
      <StatusMessage tone="error">Nie udało się sprawdzić linku. Spróbuj ponownie.</StatusMessage>
      <AppButton @click="check">Spróbuj ponownie</AppButton>
    </template>
    <form v-else novalidate @submit.prevent="submit">
      <p class="intro">Ustaw hasło dla <strong>{{ email }}</strong>.</p>
      <!-- For password managers: the account the new password belongs to. -->
      <input type="email" name="email" autocomplete="username" :value="email" readonly hidden />
      <FormField
        v-slot="{ fieldProps }"
        label="Nowe hasło"
        :hint="`Od ${PASSWORD_MIN_LENGTH} do ${PASSWORD_MAX_LENGTH} znaków.`"
        :error="passwordInvalid && problem ? PROBLEM_MESSAGES[problem] : null"
      >
        <input
          ref="passwordInput"
          v-model="password"
          v-bind="fieldProps"
          type="password"
          name="password"
          autocomplete="new-password"
          required
        />
      </FormField>
      <FormField
        v-slot="{ fieldProps }"
        label="Powtórz hasło"
        :error="repeatInvalid && problem ? PROBLEM_MESSAGES[problem] : null"
      >
        <input
          ref="repeatInput"
          v-model="repeat"
          v-bind="fieldProps"
          type="password"
          name="password-repeat"
          autocomplete="new-password"
          required
        />
      </FormField>
      <StatusMessage v-if="problem === 'unavailable'" tone="error">{{ PROBLEM_MESSAGES.unavailable }}</StatusMessage>
      <AppButton type="submit" variant="primary" :busy="submitting" busy-label="Zapisywanie…">Ustaw hasło</AppButton>
    </form>
  </AuthLayout>
</template>

<style scoped>
.intro {
  overflow-wrap: anywhere;
}
</style>
