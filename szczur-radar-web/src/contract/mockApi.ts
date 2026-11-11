import mockSource from '../../dev/mock-api.js?raw'

// Runs dev/mock-api.js without a browser, for tests only. The file is a `playwright-cli run-code` script (one async
// arrow function, no export), so its text is evaluated here and given a fake `page` that records the route handler.

/** The mode constants of the mock (`const NAME = '…'` lines); a test replaces them in the text before it runs. */
export type MockConstant = 'MODE' | 'NOTIFICATIONS' | 'AUTH' | 'LOGIN' | 'START' | 'ACTIVATION' | 'FRESHNESS' | 'MISSING'

export interface MockResponse {
  status: number
  contentType: string | undefined
  headers: Record<string, string>
  body: unknown
}

export interface MockApi {
  call(method: string, path: string, body?: unknown): Promise<MockResponse>
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

const ORIGIN = 'http://localhost:5173'

/** Replaces `const NAME = '…'` in the mock's text; throws when no such line exists, so a renamed constant is loud. */
function withConstants(source: string, constants: Partial<Record<MockConstant, string>>): string {
  let text = source
  for (const [name, value] of Object.entries(constants)) {
    const line = new RegExp(`^(\\s*const ${name} = )'[^'\\n]*'`, 'm')
    if (!line.test(text)) throw new Error(`dev/mock-api.js has no line "const ${name} = '…'"`)
    text = text.replace(line, `$1'${value}'`)
  }
  return text
}

/** Installs the mock (optionally with other mode constants) and returns a way to call its routes. */
export async function installMock(constants: Partial<Record<MockConstant, string>> = {}): Promise<MockApi> {
  const script = (0, eval)(withConstants(mockSource, constants)) as (page: unknown) => Promise<void>
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
  await script(page)
  if (!matcher || !handler) throw new Error('dev/mock-api.js did not register a route handler')
  const installedMatcher = matcher
  const installedHandler = handler

  return {
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
