<script setup lang="ts">
// Setting the first (or a reset) password through the link the operator's CLI prints:
// <web>/activate#<token>. The fragment never reaches a server or a Referer header; the token goes to
// the API only in a JSON body, and nothing here logs it.
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { activate, activationFailure, lookupActivation } from '../api/activation'
import { logout } from '../api/auth'
import { clearSession, ensureSession } from '../auth/session'
import AppButton from '../components/AppButton.vue'
import StatusMessage from '../components/StatusMessage.vue'

/** The password policy of the API (auth/Passwords.java): 12–128 characters, counted as code points. */
const MIN_LENGTH = 12
const MAX_LENGTH = 128

/** Checking the link, a link that cannot be used, a check that failed (network, 5xx), or the form. */
type Phase = 'checking' | 'invalid' | 'lookup_failed' | 'form'

/** Why the form was not accepted: by the check below or by the server. */
type FormProblem = 'too_short' | 'too_long' | 'mismatch' | 'password_policy' | 'unavailable'

const PROBLEM_MESSAGES: Record<FormProblem, string> = {
  too_short: `Hasło musi mieć co najmniej ${MIN_LENGTH} znaków.`,
  too_long: `Hasło może mieć najwyżej ${MAX_LENGTH} znaków.`,
  mismatch: 'Hasła nie są takie same.',
  password_policy: `Hasło musi mieć od ${MIN_LENGTH} do ${MAX_LENGTH} znaków.`,
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

const passwordInvalid = computed(
  () => problem.value === 'too_short' || problem.value === 'too_long' || problem.value === 'password_policy',
)
const repeatInvalid = computed(() => problem.value === 'mismatch')

/** The policy as the API checks it; `[...text]` counts code points, not UTF-16 units. */
function formProblem(): FormProblem | null {
  const length = [...password.value].length
  if (length < MIN_LENGTH) return 'too_short'
  if (length > MAX_LENGTH) return 'too_long'
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

async function submit(): Promise<void> {
  if (submitting.value) return
  problem.value = formProblem()
  if (problem.value) return
  submitting.value = true
  try {
    await activate(token, password.value)
  } catch (err) {
    const failure = activationFailure(err)
    if (failure === 'link_invalid') phase.value = 'invalid'
    else problem.value = failure
    submitting.value = false
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
  <main class="page">
    <section
      class="card"
      aria-labelledby="activate-title"
      :aria-busy="phase === 'checking' ? 'true' : undefined"
    >
      <p class="eyebrow">Szczur Radar</p>
      <h1 id="activate-title">Ustawienie hasła</h1>
      <div class="content">
        <p v-if="phase === 'checking'" class="lead">Sprawdzanie linku…</p>
        <StatusMessage v-else-if="phase === 'invalid'" tone="error">Link jest nieważny lub wygasł. Poproś administratora o nowy.</StatusMessage>
        <template v-else-if="phase === 'lookup_failed'">
          <StatusMessage tone="error">Nie udało się sprawdzić linku. Spróbuj ponownie.</StatusMessage>
          <AppButton @click="check">Spróbuj ponownie</AppButton>
        </template>
        <form v-else class="form" novalidate @submit.prevent="submit">
          <p class="lead">Ustaw hasło dla <strong>{{ email }}</strong>.</p>
          <!-- For password managers: the account the new password belongs to. -->
          <input type="email" name="email" autocomplete="username" :value="email" readonly hidden />
          <div class="field">
            <label>
              Nowe hasło
              <input
                ref="passwordInput"
                v-model="password"
                type="password"
                name="password"
                autocomplete="new-password"
                required
                aria-describedby="password-hint"
                :aria-invalid="passwordInvalid ? 'true' : undefined"
              />
            </label>
            <span id="password-hint" class="hint">Od {{ MIN_LENGTH }} do {{ MAX_LENGTH }} znaków.</span>
          </div>
          <label>
            Powtórz hasło
            <input
              v-model="repeat"
              type="password"
              name="password-repeat"
              autocomplete="new-password"
              required
              :aria-invalid="repeatInvalid ? 'true' : undefined"
            />
          </label>
          <StatusMessage v-if="problem" tone="error" class="problem">{{ PROBLEM_MESSAGES[problem] }}</StatusMessage>
          <AppButton type="submit" variant="primary" :busy="submitting" busy-label="Zapisywanie…">
            Ustaw hasło
          </AppButton>
        </form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.content {
  text-align: left;
}

.lead {
  margin: 0;
  overflow-wrap: anywhere;
}

.form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.field,
label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

label {
  color: var(--text-h);
  font-size: var(--font-size-md);
}

input {
  font: inherit;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--bg);
  color: var(--text-h);
}

input[aria-invalid='true'] {
  border-color: var(--destructive);
}

.hint {
  font-size: var(--font-size-sm);
}

/* The form's gap spaces the message; the parent's selector outranks the message's own margin. */
.form .problem {
  margin: 0;
}
</style>
