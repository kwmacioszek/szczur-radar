// /api/auth/* — the contract of szczur-radar-api/app/auth/routes.py.
import { apiFetch } from './client'

export interface User {
  id: string
  email: string
  role: 'user' | 'admin'
}

/** Sets the session cookie. 401 = wrong credentials, 429 = too many attempts. */
export function login(email: string, password: string): Promise<User> {
  return apiFetch<User>(
    '/api/auth/login',
    { method: 'POST', body: JSON.stringify({ email, password }) },
    { handleUnauthorized: false },
  )
}

/** Clears the session cookie; idempotent. */
export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST' })
}

/** The logged-in account; 401 when there is no valid session. */
export function fetchMe(): Promise<User> {
  return apiFetch<User>('/api/auth/me', {}, { handleUnauthorized: false })
}
