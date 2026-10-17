// Who is logged in — one reactive value, no token in the browser (the session
// is an HttpOnly cookie). The server is asked once per page load; after that
// login/logout/401 keep this state in sync. When the session ends or another
// account logs in, the admin's notifications are forgotten too.
import { ref } from 'vue'
import { fetchMe, type User } from '../api/auth'
import { ApiError, onUnauthorized } from '../api/client'
import { resetNotifications } from '../notificationsStore'

export const currentUser = ref<User | null>(null)

let known = false
let pending: Promise<User | null> | null = null

/** Resolve the session once (GET /api/auth/me); 401 → `null`. */
export function ensureSession(): Promise<User | null> {
  if (known) return Promise.resolve(currentUser.value)
  pending ??= fetchMe()
    .then((user) => {
      setSession(user)
      return user
    })
    .catch((error: unknown) => {
      if (error instanceof ApiError && error.status === 401) clearSession()
      // Any other failure (network, 5xx): treat as logged out for now, but ask
      // the server again on the next navigation.
      return null
    })
    .finally(() => {
      pending = null
    })
  return pending
}

export function setSession(user: User): void {
  if (currentUser.value?.id !== user.id) resetNotifications()
  currentUser.value = user
  known = true
}

export function clearSession(): void {
  resetNotifications()
  currentUser.value = null
  known = true
}

/** Forget what we know so the next `ensureSession()` asks the server (tests). */
export function resetSession(): void {
  resetNotifications()
  currentUser.value = null
  known = false
  pending = null
}

// Any API call answering 401 means the session is gone.
onUnauthorized(clearSession)
