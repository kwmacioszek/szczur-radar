// /api/auth/activation/* — the contract of szczur-radar-api auth/activation/ActivationController.java.
// The token comes from the fragment of the link (/activate#<token>) and travels only in a JSON body:
// never in a path or a query, which would reach the proxy's access log. Both routes are public, set no
// cookie and never answer 401.
import { ApiError, apiFetch } from './client'

export interface ActivationLookup {
  /** The account the link sets a password for. */
  email: string
}

/** What a failed activation call means for the page. */
export type ActivationFailure = 'link_invalid' | 'password_policy' | 'unavailable'

/** 200 with the account's e-mail; 400 `activation_link_invalid` for an unknown, used, expired or replaced link. */
export function lookupActivation(token: string): Promise<ActivationLookup> {
  return apiFetch<ActivationLookup>('/api/auth/activation/lookup', {
    method: 'POST',
    body: JSON.stringify({ token }),
  })
}

/**
 * Sets the password and uses the link up (204); the person logs in afterwards. 400 `password_policy`
 * keeps the link usable, 400 `activation_link_invalid` as in the lookup.
 */
export function activate(token: string, password: string): Promise<void> {
  return apiFetch<void>('/api/auth/activation', {
    method: 'POST',
    body: JSON.stringify({ token, password }),
  })
}

/**
 * A failed call by the API's `detail`, not by its status: both refusals are 400. `invalid_request`
 * comes from a malformed token (the password is checked against the policy before it is sent), so it
 * is a bad link too. Anything else (network, 5xx, a proxy page) is `unavailable`: the link may be fine.
 */
export function activationFailure(err: unknown): ActivationFailure {
  if (err instanceof ApiError) {
    switch (err.detail) {
      case 'activation_link_invalid':
      case 'invalid_request':
        return 'link_invalid'
      case 'password_policy':
        return 'password_policy'
    }
  }
  return 'unavailable'
}
