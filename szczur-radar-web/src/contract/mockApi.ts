import mockSource from '../../dev/mock-api.js?raw'
import type { MockControl, MockModes } from './mockModes'

// Runs dev/mock-api.js without a browser, for tests only. The file is a `playwright-cli run-code` script (one async
// arrow function `(page, modes) => control`, no export), so its text is evaluated here once and each installation
// calls it with a fake `page` that records the route handler, and with its modes.

export interface MockResponse {
  status: number
  contentType: string | undefined
  headers: Record<string, string>
  body: unknown
}

export interface MockApi {
  call(method: string, path: string, body?: unknown): Promise<MockResponse>
  /** What the mock returned: endSession(), dispose() and its table of modes. */
  control: MockControl
}

interface FakeRequest {
  url(): string
  method(): string
  postDataJSON(): unknown
}

interface FulfillOptions {
  status?: number
  contentType?: string
  headers?: Record<string, string>
  body?: string
}

interface FakeRoute {
  request(): FakeRequest
  fulfill(options: FulfillOptions): Promise<void>
}

type RouteHandler = (route: FakeRoute) => Promise<unknown>
type UrlMatcher = (url: URL) => boolean
type MockScript = (page: unknown, modes: MockModes) => Promise<MockControl>

const ORIGIN = 'http://localhost:5173'

const script = (0, eval)(mockSource) as MockScript

/** Installs the mock with its modes (absent = the defaults) and returns a way to call its routes. */
export async function installMock(modes: MockModes = {}): Promise<MockApi> {
  let matcher: UrlMatcher | undefined
  let handler: RouteHandler | undefined
  const page = {
    unrouteAll: () => Promise.resolve(),
    route: (match: UrlMatcher, h: RouteHandler) => {
      matcher = match
      handler = h
      return Promise.resolve()
    },
  }
  const control = await script(page, modes)
  if (!matcher || !handler) throw new Error('dev/mock-api.js did not register a route handler')
  const installedMatcher = matcher
  const installedHandler = handler

  return {
    control,
    async call(method, path, body) {
      const url = new URL(path, ORIGIN)
      if (!installedMatcher(url)) throw new Error(`the mock does not handle ${method} ${path}`)
      let fulfilled: FulfillOptions | undefined
      const route: FakeRoute = {
        request: () => ({
          url: () => url.href,
          method: () => method,
          postDataJSON: () => body,
        }),
        fulfill: (options) => {
          fulfilled = options
          return Promise.resolve()
        },
      }
      await installedHandler(route)
      if (!fulfilled) throw new Error(`the mock did not answer ${method} ${path}`)
      const text = fulfilled.body
      return {
        status: fulfilled.status ?? 200,
        contentType: fulfilled.contentType,
        headers: fulfilled.headers ?? {},
        body: text === undefined || text === '' ? null : (JSON.parse(text) as unknown),
      }
    },
  }
}
