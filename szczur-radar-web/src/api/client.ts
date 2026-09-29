// The single place that calls the API. Requests stay same-origin (/api/* is
// proxied by Caddy in production and by Vite locally), so the HttpOnly session
// cookie travels automatically and the SPA never sees the token.

export class ApiError extends Error {
  readonly status: number
  readonly detail: string | null
  readonly retryAfter?: number

  constructor(status: number, detail: string | null, retryAfter?: number) {
    super(`API ${status}${detail ? `: ${detail}` : ''}`)
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
    this.retryAfter = retryAfter
  }
}

type UnauthorizedListener = () => void

const unauthorizedListeners = new Set<UnauthorizedListener>()

/** Run `listener` whenever an API call answers 401 (session missing or expired). */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

export interface ApiFetchOptions {
  /**
   * `false` for calls where 401 is an expected answer rather than an expired
   * session: the login form (wrong credentials) and the session probe.
   */
  handleUnauthorized?: boolean
}

function parseRetryAfter(value: string | null): number | undefined {
  if (value === null) return undefined
  const seconds = Number.parseInt(value, 10)
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined
}

async function readDetail(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'detail' in body) {
      const { detail } = body as { detail: unknown }
      return typeof detail === 'string' ? detail : null
    }
  } catch {
    // Non-JSON error body (e.g. a proxy error page).
  }
  return null
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  { handleUnauthorized = true }: ApiFetchOptions = {},
): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' })

  if (!response.ok) {
    if (response.status === 401 && handleUnauthorized) {
      unauthorizedListeners.forEach((listener) => listener())
    }
    throw new ApiError(
      response.status,
      await readDetail(response),
      parseRetryAfter(response.headers.get('Retry-After')),
    )
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
