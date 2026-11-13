// The single place that calls the API. Requests stay same-origin (/api/* is
// proxied by Caddy in production and by Vite locally), so the HttpOnly session
// cookie travels automatically and the SPA never sees the token.

/** The `detail` codes of the API's errors, in the order of the API's `config/ErrorCode` (`rules.json` `errorCodes`). */
export type ErrorCode =
  | 'invalid_credentials'
  | 'not_authenticated'
  | 'forbidden'
  | 'too_many_attempts'
  | 'cross_origin'
  | 'invalid_request'
  | 'unsupported_media_type'
  | 'activation_link_invalid'
  | 'password_policy'
  | 'pipeline_busy'
  | 'post_dead'
  | 'post_not_found'
  | 'source_not_configured'
  | 'not_found'
  | 'internal_error'

export class ApiError extends Error {
  readonly status: number
  readonly detail: ErrorCode | null
  readonly retryAfter?: number

  constructor(status: number, detail: ErrorCode | null, retryAfter?: number) {
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

/**
 * The `detail` of an error body, cast to `ErrorCode` at this boundary: a code of a newer API than this SPA is not
 * in the union at run time and lands in the default branch of the caller's `switch`.
 */
async function readDetail(response: Response): Promise<ErrorCode | null> {
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'detail' in body) {
      const { detail } = body as { detail: unknown }
      return typeof detail === 'string' ? (detail as ErrorCode) : null
    }
  } catch {
    // Non-JSON error body (e.g. a proxy error page).
  }
  return null
}

/**
 * The one request path of `apiFetch` and `apiFetchText`: same-origin, `Accept: application/json`
 * (the API's errors are JSON, and so is every body it sends), and an `ApiError` for anything but 2xx.
 */
async function request(
  path: string,
  init: RequestInit,
  { handleUnauthorized = true }: ApiFetchOptions,
): Promise<Response> {
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
  return response
}

export async function apiFetch<T>(path: string, init: RequestInit = {}, options: ApiFetchOptions = {}): Promise<T> {
  const response = await request(path, init, options)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/** The body as text, unparsed: for an answer shown as is (e.g. a post's raw source JSON). */
export async function apiFetchText(path: string, init: RequestInit = {}, options: ApiFetchOptions = {}): Promise<string> {
  const response = await request(path, init, options)
  return response.text()
}
