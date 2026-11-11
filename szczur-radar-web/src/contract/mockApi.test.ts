import { afterEach, describe, expect, it, vi } from 'vitest'
import { installMock, type MockApi, type MockConstant } from './mockApi'
import rules from './rules.json'

// Guard of dev/mock-api.js: the mock is executed without a browser (mockApi.ts) and every route of ROUTES is compared
// with the contract files the API's integration tests pin (see src/contract/rules.test.ts for the rules registry):
//  - keys: each object of the mock has exactly the keys of some contract object on the same path,
//  - types: the JSON type of each value (null included) occurs on that path in one of the record's files,
//  - enums: the values on the paths of ENUMS belong to the set of rules.json `values`.
// A variant the mock needs and no contract file has is added to the file through the API's integration test, not
// excused here.
// Beyond the shape, BEHAVIOR (S-43 phase 2) pins how the routes answer: the status and error code of each refusal, the
// session, the ids, the page limits, the start of a run; INVARIANTS pin three facts about the mock's data. A BEHAVIOR
// row marked `before` already held for the mock as it was before phase 2 (the others are red there).

type ValueSet = keyof typeof rules.values

/** A place of a record inside a body: `$` is the body, `.key` a member, `[]` every element of an array. */
type At = string

interface Ref {
  file: string
  at: At
}

interface Check {
  /** Where the checked records sit in the mock's body. */
  at: At
  /** The contract files (and where the same record sits in each) the records are compared with. */
  refs: Ref[]
  /** Paths (relative to a record) whose children are left to another check, e.g. the items of a page. */
  stop?: string[]
  /** Paths (relative to a record) that hold a value of a rules.json set. */
  enums?: Record<string, ValueSet>
}

interface Route {
  name: string
  fetch(api: MockApi): Promise<unknown[]>
  checks: Check[]
}

const contract: Record<string, unknown> = Object.fromEntries(
  Object.entries(import.meta.glob('./*.json', { eager: true, import: 'default' }))
    .map(([path, value]): [string, unknown] => [path.replace('./', ''), value])
    .filter(([file]) => file !== 'rules.json'),
)

/** Contract files without a route in ROUTES, each with the reason. A phase that gives the file a route removes it. */
const NOT_COVERED: Record<string, string> = {}

const RUN_ENUMS: Record<string, ValueSet> = {
  '$.kind': 'runKind',
  '$.status': 'runStatus',
  '$.interruptionCause': 'interruptionCause',
  '$.attentionReasons[]': 'attentionReason',
  '$.sources[]': 'source',
  '$.steps[].step': 'step',
  '$.steps[].status': 'stepStatus',
  '$.sourceResults[].source': 'source',
  '$.sourceResults[].status': 'sourceStatus',
}

const TARGETS = '$.assessment.output.targets[]'
const POST_ENUMS: Record<string, ValueSet> = {
  '$.source': 'source',
  '$.itemType': 'itemType',
  '$.assessment.output.relevance': 'relevance',
  '$.assessment.output.modifiers[]': 'modifier',
  '$.assessment.output.flags[]': 'flag',
  [`${TARGETS}.kind`]: 'targetKind',
  [`${TARGETS}.stance.direction`]: 'direction',
  [`${TARGETS}.reference`]: 'reference',
  '$.assessment.output.claims[].kind': 'claimKind',
  '$.assessment.output.claims[].attribution': 'attribution',
}

const FAILED_ENUMS: Record<string, ValueSet> = {
  '$.itemType': 'itemType',
  '$.lastAttempt.failureClass': 'failureClass',
  '$.lastAttempt.violations[].failureClass': 'failureClass',
}

const NOTIFICATION_ENUMS: Record<string, ValueSet> = {
  '$.run.kind': 'runKind',
  '$.run.status': 'runStatus',
  '$.run.interruptionCause': 'interruptionCause',
}

const runRefs: Ref[] = [
  { file: 'run.json', at: '$' },
  { file: 'runs-page.json', at: '$.items[]' },
]
const startedRefs: Ref[] = [...runRefs, { file: 'started-runs.json', at: '$[]' }]
const postRefs: Ref[] = [
  { file: 'post.json', at: '$' },
  { file: 'posts-page.json', at: '$.items[]' },
]
const notificationRefs: Ref[] = [
  { file: 'notifications.json', at: '$.items[]' },
  { file: 'notifications-without-run.json', at: '$.items[]' },
]

/** All pages of a list route, as the SPA reads them (page by page, from 0, until the total is read). */
async function pages(api: MockApi, path: string, size: number): Promise<{ bodies: unknown[]; items: unknown[] }> {
  const bodies: unknown[] = []
  const items: unknown[] = []
  for (let page = 0; ; page++) {
    const response = await api.call('GET', `${path}?page=${page}&size=${size}`)
    expect(response.status, `${path} page ${page}`).toBe(200)
    const body = response.body as { items: unknown[]; total: number }
    bodies.push(body)
    items.push(...body.items)
    if (items.length >= body.total) return { bodies, items }
  }
}

async function answered(api: MockApi, status: number, method: string, path: string, body?: unknown): Promise<unknown> {
  const response = await api.call(method, path, body)
  expect(response.status, `${method} ${path}`).toBe(status)
  return response.body
}

const ok = (api: MockApi, method: string, path: string, body?: unknown) => answered(api, 200, method, path, body)

// The mock's ids (see its header): 00000000-0000-4000-8000-<kind 4 digits><number 8 digits>.
const id = (kind: number, n: number) => `00000000-0000-4000-8000-${String(kind).padStart(4, '0')}${String(n).padStart(8, '0')}`
const RUN_1 = id(1, 1)
const NOTIFICATION_1 = id(2, 1)
const FAILED_1 = id(3, 1)
const FAILED_DEAD = id(3, 3)
const POST_0 = id(4, 0)
const POST_REDDIT = id(4, 2)
const UNKNOWN = id(9, 9)
const ADMIN_EMAIL = 'admin@szczur-radar.pl'

const warsawToday = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Warsaw' }).format(new Date())
const dMinus = (n: number) => new Date(Date.parse(`${warsawToday()}T00:00:00Z`) - n * 86_400_000).toISOString().slice(0, 10)

const ROUTES: Route[] = [
  {
    name: 'GET /api/auth/me',
    fetch: async (api) => [await ok(api, 'GET', '/api/auth/me')],
    checks: [{ at: '$', refs: [{ file: 'user.json', at: '$' }] }],
  },
  {
    name: 'POST /api/auth/login',
    fetch: async (api) => [await ok(api, 'POST', '/api/auth/login', { email: 'jan@example.com', password: 'x' })],
    checks: [{ at: '$', refs: [{ file: 'user.json', at: '$' }] }],
  },
  {
    name: 'POST /api/auth/activation/lookup',
    fetch: async (api) => [await ok(api, 'POST', '/api/auth/activation/lookup', { token: 'any' })],
    checks: [{ at: '$', refs: [{ file: 'activation-lookup.json', at: '$' }] }],
  },
  {
    name: 'GET /api/freshness',
    fetch: async (api) => [await ok(api, 'GET', '/api/freshness')],
    checks: [{ at: '$', refs: [{ file: 'freshness.json', at: '$' }], enums: { '$.sources[].source': 'source' } }],
  },
  {
    name: 'GET /api/admin/pipeline-runs/sources',
    fetch: async (api) => [await ok(api, 'GET', '/api/admin/pipeline-runs/sources')],
    checks: [{ at: '$[]', refs: [{ file: 'sources.json', at: '$[]' }], enums: { '$.source': 'source' } }],
  },
  {
    name: 'GET /api/admin/pipeline-runs/missing-days',
    fetch: async (api) => [await ok(api, 'GET', '/api/admin/pipeline-runs/missing-days')],
    checks: [{ at: '$', refs: [{ file: 'missing-days.json', at: '$' }], enums: { '$.days[].sources[]': 'source' } }],
  },
  {
    name: 'GET /api/admin/pipeline-runs (every page)',
    fetch: async (api) => (await pages(api, '/api/admin/pipeline-runs', 20)).bodies,
    checks: [
      { at: '$', refs: [{ file: 'runs-page.json', at: '$' }], stop: ['$.items[]'] },
      { at: '$.items[]', refs: runRefs, enums: RUN_ENUMS },
    ],
  },
  {
    name: 'GET /api/admin/pipeline-runs/{id} (every run of the list)',
    fetch: async (api) => {
      const { items } = await pages(api, '/api/admin/pipeline-runs', 20)
      return Promise.all(
        items.map((run) => ok(api, 'GET', `/api/admin/pipeline-runs/${(run as { id: string }).id}`)),
      )
    },
    checks: [{ at: '$', refs: runRefs, enums: RUN_ENUMS }],
  },
  {
    name: 'GET /api/posts (every page)',
    fetch: async (api) => (await pages(api, '/api/posts', 50)).bodies,
    checks: [
      { at: '$', refs: [{ file: 'posts-page.json', at: '$' }], stop: ['$.items[]'] },
      { at: '$.items[]', refs: postRefs, enums: POST_ENUMS },
    ],
  },
  {
    name: 'GET /api/posts/{id} (every post of the feed)',
    fetch: async (api) => {
      const { items } = await pages(api, '/api/posts', 50)
      return Promise.all(items.map((post) => ok(api, 'GET', `/api/posts/${(post as { id: string }).id}`)))
    },
    checks: [{ at: '$', refs: postRefs, enums: POST_ENUMS }],
  },
  {
    name: 'GET /api/admin/posts/failed',
    fetch: async (api) => [await ok(api, 'GET', '/api/admin/posts/failed')],
    checks: [
      { at: '$', refs: [{ file: 'failed-posts-page.json', at: '$' }], stop: ['$.items[]'] },
      { at: '$.items[]', refs: [{ file: 'failed-posts-page.json', at: '$.items[]' }], enums: FAILED_ENUMS },
    ],
  },
  {
    name: 'POST /api/admin/posts/restore',
    fetch: async (api) => [await ok(api, 'POST', '/api/admin/posts/restore', { postIds: [FAILED_DEAD] })],
    checks: [{ at: '$', refs: [{ file: 'restore-result.json', at: '$' }] }],
  },
  {
    name: 'POST /api/admin/pipeline-runs (a range: a retry run first, then the range)',
    fetch: async (api) => [await answered(api, 202, 'POST', '/api/admin/pipeline-runs', { from: dMinus(3), to: dMinus(1) })],
    checks: [{ at: '$[]', refs: startedRefs, enums: RUN_ENUMS }],
  },
  {
    name: 'POST /api/admin/pipeline-runs (postIds)',
    fetch: async (api) => [await answered(api, 202, 'POST', '/api/admin/pipeline-runs', { postIds: [POST_0] })],
    checks: [{ at: '$[]', refs: startedRefs, enums: RUN_ENUMS }],
  },
  {
    name: 'GET /api/admin/pipeline-runs/{id} (every started run)',
    fetch: async (api) => {
      const started = (await answered(api, 202, 'POST', '/api/admin/pipeline-runs', { from: dMinus(3), to: dMinus(1) })) as {
        id: string
      }[]
      return Promise.all(started.map((run) => ok(api, 'GET', `/api/admin/pipeline-runs/${run.id}`)))
    },
    checks: [{ at: '$', refs: startedRefs, enums: RUN_ENUMS }],
  },
  {
    name: 'GET /api/admin/pipeline-runs (every page, after a start)',
    fetch: async (api) => {
      await answered(api, 202, 'POST', '/api/admin/pipeline-runs', { from: dMinus(3), to: dMinus(1) })
      return (await pages(api, '/api/admin/pipeline-runs', 20)).bodies
    },
    checks: [
      { at: '$', refs: [{ file: 'runs-page.json', at: '$' }], stop: ['$.items[]'] },
      { at: '$.items[]', refs: startedRefs, enums: RUN_ENUMS },
    ],
  },
  {
    name: 'GET /api/admin/notifications',
    fetch: async (api) => [await ok(api, 'GET', '/api/admin/notifications')],
    checks: [
      {
        at: '$',
        refs: [
          { file: 'notifications.json', at: '$' },
          { file: 'notifications-without-run.json', at: '$' },
        ],
        stop: ['$.items[]'],
      },
      { at: '$.items[]', refs: notificationRefs, enums: NOTIFICATION_ENUMS },
    ],
  },
]

// --- walking ---------------------------------------------------------------------------------------------------

type JsonType = 'null' | 'string' | 'number' | 'boolean' | 'object' | 'array'

function typeOf(value: unknown): JsonType {
  if (value === null) return 'null'
  if (Array.isArray(value)) return 'array'
  const t = typeof value
  if (t === 'string' || t === 'number' || t === 'boolean' || t === 'object') return t
  throw new Error(`not a JSON value: ${String(value)}`)
}

/** The nodes `at` names inside `value`: `$` the value, `.key` a member, `[]` each element. */
function nodesAt(value: unknown, at: At): unknown[] {
  let nodes: unknown[] = [value]
  for (const token of at.slice(1).match(/\.[^.[]+|\[\]/g) ?? []) {
    nodes =
      token === '[]'
        ? nodes.flatMap((node) => (Array.isArray(node) ? node : []))
        : nodes.flatMap((node) =>
            typeof node === 'object' && node !== null && token.slice(1) in node
              ? [(node as Record<string, unknown>)[token.slice(1)]]
              : [],
          )
  }
  return nodes
}

interface PathInfo {
  types: Set<JsonType>
  keySets: Set<string>
  values: unknown[]
}

/** Every path under the roots (arrays folded to `[]`) with the types, key sets and values seen on it. */
function collect(roots: unknown[], stop: string[] = []): Map<string, PathInfo> {
  const paths = new Map<string, PathInfo>()
  const visit = (value: unknown, path: string) => {
    const info = paths.get(path) ?? { types: new Set<JsonType>(), keySets: new Set<string>(), values: [] }
    paths.set(path, info)
    info.types.add(typeOf(value))
    info.values.push(value)
    if (stop.includes(path)) return
    if (Array.isArray(value)) value.forEach((element) => visit(element, `${path}[]`))
    else if (typeof value === 'object' && value !== null) {
      info.keySets.add(Object.keys(value).sort().join(','))
      for (const [key, member] of Object.entries(value)) visit(member, `${path}.${key}`)
    }
  }
  roots.forEach((root) => visit(root, '$'))
  return paths
}

function referencePaths(check: Check): Map<string, PathInfo> {
  const roots = check.refs.flatMap((ref) => nodesAt(contract[ref.file], ref.at))
  return collect(roots, check.stop)
}

/** The differences between the mock's records and the contract's, as readable lines (empty = they agree). */
function compare(bodies: unknown[], check: Check): string[] {
  const problems: string[] = []
  const reference = referencePaths(check)
  const mock = collect(
    bodies.flatMap((body) => nodesAt(body, check.at)),
    check.stop,
  )
  if (mock.size === 0) return [`nothing at ${check.at} in the mock's answers`]

  for (const [path, info] of mock) {
    const known = reference.get(path)
    if (!known) {
      problems.push(`${path}: the contract has no such path`)
      continue
    }
    for (const type of info.types) {
      if (!known.types.has(type)) {
        problems.push(`${path}: the mock gives ${type}, the contract only ${[...known.types].join('|')}`)
      }
    }
    for (const keys of info.keySets) {
      if (!known.keySets.has(keys)) {
        problems.push(`${path}: the mock's keys {${keys}} are none of the contract's {${[...known.keySets].join('} {')}}`)
      }
    }
  }

  for (const [path, set] of Object.entries(check.enums ?? {})) {
    const known = reference.get(path)
    if (!known) {
      problems.push(`${path}: an enum entry of the guard names a path no contract file has`)
      continue
    }
    const allowed: readonly string[] = rules.values[set]
    for (const value of mock.get(path)?.values ?? []) {
      if (value !== null && !allowed.includes(value as string)) {
        problems.push(`${path}: "${String(value)}" is not in rules.json values.${set}`)
      }
    }
  }
  return problems
}

// --- tests -----------------------------------------------------------------------------------------------------

describe('dev/mock-api.js against the contract files', () => {
  it('has a route for every contract file (rules.json aside)', () => {
    const covered = new Set(ROUTES.flatMap((route) => route.checks.flatMap((check) => check.refs.map((ref) => ref.file))))
    const uncovered = Object.keys(contract).filter((file) => !covered.has(file) && !(file in NOT_COVERED))
    expect(uncovered, 'contract files with no route in ROUTES (add the route or a reasoned NOT_COVERED entry)').toEqual([])
    for (const file of Object.keys(NOT_COVERED)) {
      expect(file in contract, `NOT_COVERED names ${file}, which is not a contract file`).toBe(true)
      expect(covered.has(file), `${file} has a route now: drop its NOT_COVERED entry`).toBe(false)
    }
  })

  it('names only paths that exist in the contract files', () => {
    for (const route of ROUTES) {
      for (const check of route.checks) {
        for (const ref of check.refs) {
          expect(nodesAt(contract[ref.file], ref.at).length, `${route.name}: ${ref.file} at ${ref.at}`).toBeGreaterThan(0)
        }
      }
    }
  })

  it.each(ROUTES.map((route) => [route.name, route] as const))('%s has the contract shape', async (_name, route) => {
    const api = await installMock()
    const bodies = await route.fetch(api)
    expect(bodies.length).toBeGreaterThan(0)
    for (const check of route.checks) expect(compare(bodies, check), `${check.at}`).toEqual([])
  })
})

// --- behavior --------------------------------------------------------------------------------------------------

type Constants = Partial<Record<MockConstant, string>>

interface Row {
  name: string
  constants?: Constants
  method: string
  path: string
  body?: unknown
  status: number
  /** The error code of a refusal (`{"detail": code}`). */
  detail?: string
  /** The row already held for the mock as it was before S-43 phase 2. */
  before?: true
}

/** The routes behind a session: [method, path, body, needs an admin]. */
const SESSION_ROUTES: [string, string, unknown, boolean][] = [
  ['GET', '/api/auth/me', undefined, false],
  ['GET', '/api/freshness', undefined, false],
  ['GET', '/api/posts', undefined, false],
  ['GET', `/api/posts/${POST_0}`, undefined, false],
  ['GET', `/api/posts/${POST_0}/raw`, undefined, false],
  ['GET', '/api/admin/pipeline-runs', undefined, true],
  ['GET', `/api/admin/pipeline-runs/${RUN_1}`, undefined, true],
  ['GET', '/api/admin/pipeline-runs/sources', undefined, true],
  ['GET', '/api/admin/pipeline-runs/missing-days', undefined, true],
  ['POST', '/api/admin/pipeline-runs', { from: dMinus(3), to: dMinus(1) }, true],
  ['GET', '/api/admin/notifications', undefined, true],
  ['POST', '/api/admin/notifications/read', undefined, true],
  ['POST', `/api/admin/notifications/${NOTIFICATION_1}/read`, undefined, true],
  ['GET', '/api/admin/posts/failed', undefined, true],
  ['POST', '/api/admin/posts/restore', { postIds: [FAILED_DEAD] }, true],
]

const manyIds = (n: number) => Array.from({ length: n }, (_, i) => id(4, i))
const PIPELINE_RUNS = '/api/admin/pipeline-runs'
const START_REFUSALS: [string, unknown][] = [
  ['from after to', { from: dMinus(1), to: dMinus(2) }],
  ['to after today', { from: dMinus(1), to: dMinus(-1) }],
  ['a range of 8 days', { from: dMinus(7), to: dMinus(0) }],
  ['no from', { to: dMinus(1) }],
  ['no to', { from: dMinus(1) }],
  ['an empty request', {}],
  ['a request that is no object', null],
  ['a day that is no date', { from: '2026-13-40', to: dMinus(1) }],
  ['a range with postIds', { from: dMinus(2), to: dMinus(1), postIds: [POST_0] }],
  ['sources with postIds', { sources: ['wykop'], postIds: [POST_0] }],
  ['no postIds', { postIds: [] }],
  ['101 postIds', { postIds: manyIds(101) }],
  ['a null postId', { postIds: [POST_0, null] }],
  ['a postId that is no UUID', { postIds: ['p3'] }],
  ['an unknown source', { from: dMinus(2), to: dMinus(1), sources: ['nope'] }],
  ['a null source', { from: dMinus(2), to: dMinus(1), sources: [null] }],
]

const ROWS: Row[] = [
  // the session
  ...SESSION_ROUTES.map(([method, path, body]): Row => ({
    name: `anonymous: ${method} ${path}`, constants: { AUTH: 'anonymous' }, method, path, body, status: 401,
    detail: 'not_authenticated', before: path === '/api/auth/me' ? true : undefined,
  })),
  ...SESSION_ROUTES.filter(([, , , admin]) => admin).map(([method, path, body]): Row => ({
    name: `user: ${method} ${path}`, constants: { AUTH: 'user' }, method, path, body, status: 403, detail: 'forbidden',
  })),
  ...SESSION_ROUTES.filter(([, , , admin]) => !admin).map(([method, path, body]): Row => ({
    name: `user: ${method} ${path} answers`, constants: { AUTH: 'user' }, method, path, body, status: 200,
    before: path.includes('/api/posts/') ? undefined : true,
  })),
  { name: 'login: ok', constants: { LOGIN: 'ok' }, method: 'POST', path: '/api/auth/login', body: { email: ADMIN_EMAIL, password: 'x' }, status: 200 },
  { name: 'login: invalid', constants: { LOGIN: 'invalid' }, method: 'POST', path: '/api/auth/login', body: { email: 'a@b.pl', password: 'x' }, status: 401, detail: 'invalid_credentials' },
  { name: 'login: limited', constants: { LOGIN: 'limited' }, method: 'POST', path: '/api/auth/login', body: { email: 'a@b.pl', password: 'x' }, status: 429, detail: 'too_many_attempts' },
  ...[
    ['no body', undefined],
    ['no password', { email: 'a@b.pl' }],
    ['no e-mail', { password: 'x' }],
    ['an empty e-mail', { email: '', password: 'x' }],
    ['a number as password', { email: 'a@b.pl', password: 1 }],
  ].map(([what, body]): Row => ({
    name: `login: ${String(what)} is a bad request`, method: 'POST', path: '/api/auth/login', body, status: 400, detail: 'invalid_request',
  })),
  { name: 'logout answers 204', method: 'POST', path: '/api/auth/logout', status: 204, before: true },
  { name: 'logout answers 204 without a session', constants: { AUTH: 'anonymous' }, method: 'POST', path: '/api/auth/logout', status: 204, before: true },
  { name: 'activation needs no session', constants: { AUTH: 'anonymous' }, method: 'POST', path: '/api/auth/activation/lookup', body: { token: 'any' }, status: 200, before: true },

  // the ids
  { name: 'a run id that is no UUID', method: 'GET', path: `${PIPELINE_RUNS}/r-running`, status: 400, detail: 'invalid_request' },
  { name: 'an unknown run', method: 'GET', path: `${PIPELINE_RUNS}/${UNKNOWN}`, status: 404, detail: 'not_found', before: true },
  { name: 'a post id that is no UUID', method: 'GET', path: '/api/posts/feed-5', status: 400, detail: 'invalid_request' },
  { name: 'the raw data of a post id that is no UUID', method: 'GET', path: '/api/posts/feed-5/raw', status: 400, detail: 'invalid_request' },
  { name: 'an unknown post', method: 'GET', path: `/api/posts/${UNKNOWN}`, status: 404, detail: 'post_not_found', before: true },
  { name: 'the raw data of an unknown post', method: 'GET', path: `/api/posts/${UNKNOWN}/raw`, status: 404, detail: 'post_not_found', before: true },
  { name: 'a notification id that is no UUID', method: 'POST', path: '/api/admin/notifications/n-skipped/read', status: 400, detail: 'invalid_request' },
  { name: 'an unknown notification', method: 'POST', path: `/api/admin/notifications/${UNKNOWN}/read`, status: 404, detail: 'not_found', before: true },
  { name: 'a known notification is read', method: 'POST', path: `/api/admin/notifications/${NOTIFICATION_1}/read`, status: 204 },

  // the page limits of the three lists
  ...[
    ['/api/posts', true],
    [PIPELINE_RUNS, false],
    ['/api/admin/posts/failed', false],
  ].flatMap(([path, hasBefore]) => [
    ...['page=-1', 'page=21474837', 'page=1.5', 'page=abc', 'size=0', 'size=101', 'size=500', 'size=-1', 'size=x'].map(
      (query): Row => ({
        name: `${String(path)}?${query} is a bad request`, method: 'GET', path: `${String(path)}?${query}`, status: 400,
        detail: 'invalid_request', before: hasBefore && query === 'page=21474837' ? true : undefined,
      }),
    ),
    ...['page=0', 'page=21474836', 'size=1', 'size=100', 'page=1&size=2'].map(
      (query): Row => ({
        name: `${String(path)}?${query} is allowed`, method: 'GET', path: `${String(path)}?${query}`, status: 200,
        before: true,
      }),
    ),
  ]),

  // starting a run
  { name: 'start: a range', method: 'POST', path: PIPELINE_RUNS, body: { from: dMinus(3), to: dMinus(1) }, status: 202 },
  { name: 'start: a range of today', method: 'POST', path: PIPELINE_RUNS, body: { from: dMinus(0), to: dMinus(0) }, status: 202 },
  { name: 'start: a range of 7 days', method: 'POST', path: PIPELINE_RUNS, body: { from: dMinus(6), to: dMinus(0) }, status: 202 },
  { name: 'start: a range of one source', method: 'POST', path: PIPELINE_RUNS, body: { from: dMinus(2), to: dMinus(1), sources: ['reddit'] }, status: 202 },
  { name: 'start: posts', method: 'POST', path: PIPELINE_RUNS, body: { postIds: [POST_0, FAILED_1] }, status: 202 },
  { name: 'start: 100 posts', method: 'POST', path: PIPELINE_RUNS, body: { postIds: manyIds(100) }, status: 202 },
  { name: 'start: a dead post', method: 'POST', path: PIPELINE_RUNS, body: { postIds: [POST_0, FAILED_DEAD] }, status: 400, detail: 'post_dead' },
  ...START_REFUSALS.map(([what, body]): Row => ({
    name: `start: ${what}`, method: 'POST', path: PIPELINE_RUNS, body, status: 400, detail: 'invalid_request',
  })),
  { name: 'start: busy', constants: { START: 'busy' }, method: 'POST', path: PIPELINE_RUNS, body: { from: dMinus(3), to: dMinus(1) }, status: 409, detail: 'pipeline_busy' },
  { name: 'start: busy, posts', constants: { START: 'busy' }, method: 'POST', path: PIPELINE_RUNS, body: { postIds: [POST_0] }, status: 409, detail: 'pipeline_busy' },
  { name: 'start: busy outranked by a bad request', constants: { START: 'busy' }, method: 'POST', path: PIPELINE_RUNS, body: {}, status: 400, detail: 'invalid_request' },
  { name: 'start: busy, a dead post', constants: { START: 'busy' }, method: 'POST', path: PIPELINE_RUNS, body: { postIds: [FAILED_DEAD] }, status: 409, detail: 'pipeline_busy' },
  { name: 'start: source not configured', constants: { START: 'not_configured' }, method: 'POST', path: PIPELINE_RUNS, body: { from: dMinus(3), to: dMinus(1) }, status: 503, detail: 'source_not_configured' },
  { name: 'start: posts need no source', constants: { START: 'not_configured' }, method: 'POST', path: PIPELINE_RUNS, body: { postIds: [POST_0] }, status: 202 },

  // restoring dead posts
  { name: 'restore: a dead post', method: 'POST', path: '/api/admin/posts/restore', body: { postIds: [FAILED_DEAD] }, status: 200, before: true },
  { name: 'restore: an id that is no UUID', method: 'POST', path: '/api/admin/posts/restore', body: { postIds: ['p3'] }, status: 400, detail: 'invalid_request' },
  { name: 'restore: a number as an id', method: 'POST', path: '/api/admin/posts/restore', body: { postIds: [3] }, status: 400, detail: 'invalid_request' },
  { name: 'restore: no ids', method: 'POST', path: '/api/admin/posts/restore', body: { postIds: [] }, status: 400, detail: 'invalid_request', before: true },
  { name: 'restore: no body', method: 'POST', path: '/api/admin/posts/restore', status: 400, detail: 'invalid_request', before: true },
  { name: 'restore: a null id', method: 'POST', path: '/api/admin/posts/restore', body: { postIds: [null] }, status: 400, detail: 'invalid_request', before: true },
  { name: 'restore: 101 ids', method: 'POST', path: '/api/admin/posts/restore', body: { postIds: manyIds(101) }, status: 400, detail: 'invalid_request', before: true },

  // a page past the end of the feed stays a 200 (the API's), the filter keeps its 400
  { name: 'the feed past its end', method: 'GET', path: '/api/posts?page=9', status: 200, before: true },
  { name: 'the feed with an unknown kind', method: 'GET', path: '/api/posts?kind=nope', status: 400, detail: 'invalid_request', before: true },
]

describe('dev/mock-api.js behaves like the API', () => {
  it.each(ROWS.map((row) => [`${row.name}${row.before ? ' [holds before S-43 phase 2]' : ''}`, row] as const))(
    '%s',
    async (_name, row) => {
      const api = await installMock(row.constants)
      const response = await api.call(row.method, row.path, row.body)
      expect(response.status).toBe(row.status)
      if (row.detail !== undefined) expect(response.body).toEqual({ detail: row.detail })
      if (row.status === 429) expect(response.headers['Retry-After']).toMatch(/^\d+$/)
    },
  )

  it('every error code the table expects is one of the API\'s', () => {
    for (const row of ROWS) {
      if (row.detail !== undefined) expect(rules.errorCodes, row.name).toContain(row.detail)
    }
  })

  it('logs in as the account of the e-mail, and /me answers it', async () => {
    const api = await installMock({ AUTH: 'anonymous' })
    expect((await api.call('GET', '/api/auth/me')).status).toBe(401)
    const user = await api.call('POST', '/api/auth/login', { email: 'jan@example.com', password: 'x' })
    expect(user.body).toMatchObject({ email: 'jan@example.com', role: 'user' })
    expect((await api.call('GET', '/api/auth/me')).body).toEqual(user.body)
    expect((await api.call('GET', '/api/admin/notifications')).status).toBe(403)
    const admin = await api.call('POST', '/api/auth/login', { email: ADMIN_EMAIL, password: 'x' })
    expect(admin.body).toMatchObject({ email: ADMIN_EMAIL, role: 'admin' })
    expect((await api.call('GET', '/api/admin/notifications')).status).toBe(200)
  })

  it('a refused login leaves the session as it was', async () => {
    const api = await installMock({ AUTH: 'anonymous', LOGIN: 'invalid' })
    await api.call('POST', '/api/auth/login', { email: ADMIN_EMAIL, password: 'x' })
    expect((await api.call('GET', '/api/auth/me')).status).toBe(401)
  })

  it('a logout ends the session', async () => {
    const api = await installMock()
    expect((await api.call('GET', '/api/auth/me')).status).toBe(200)
    expect((await api.call('POST', '/api/auth/logout')).status).toBe(204)
    const me = await api.call('GET', '/api/auth/me')
    expect(me.status).toBe(401)
    expect(me.body).toEqual({ detail: 'not_authenticated' })
  })

  it('the user\'s /me says role user', async () => {
    const api = await installMock({ AUTH: 'user' })
    expect((await api.call('GET', '/api/auth/me')).body).toMatchObject({ role: 'user' })
  })

  it('the lists answer the page and size asked for, and the defaults of the API', async () => {
    const api = await installMock()
    const defaults: [string, number][] = [
      [PIPELINE_RUNS, 20],
      ['/api/posts', 50],
      ['/api/admin/posts/failed', 50],
    ]
    for (const [path, size] of defaults) {
      expect(await ok(api, 'GET', path), path).toMatchObject({ page: 0, size })
      expect(await ok(api, 'GET', `${path}?page=1&size=2`), path).toMatchObject({ page: 1, size: 2 })
    }
    const second = (await ok(api, 'GET', '/api/admin/posts/failed?page=1&size=1')) as { items: { id: string }[]; total: number }
    expect(second.items.map((item) => item.id)).toEqual([id(3, 2)])
    expect(second.total).toBe(4)
    const past = (await ok(api, 'GET', '/api/admin/posts/failed?page=3&size=2')) as { items: unknown[] }
    expect(past.items).toEqual([])
  })

  it('a started range is the newest run, behind a retry run when posts are left', async () => {
    const api = await installMock()
    const created = (await answered(api, 202, 'POST', PIPELINE_RUNS, { from: dMinus(3), to: dMinus(1) })) as {
      id: string
      kind: string
      status: string
      sources: string[]
    }[]
    expect(created.map((run) => [run.kind, run.status])).toEqual([['retry', 'queued'], ['range', 'queued']])
    expect(created[1].sources).toEqual(['wykop', 'reddit'])
    const list = (await ok(api, 'GET', `${PIPELINE_RUNS}?page=0&size=2`)) as { items: { id: string }[]; total: number }
    expect(list.items.map((run) => run.id)).toEqual([created[1].id, created[0].id])
    expect(list.total).toBe(34)
    expect(await ok(api, 'GET', `${PIPELINE_RUNS}/${created[0].id}`)).toEqual(created[0])
  })

  it('a started range takes the sources asked for, and its days are Warsaw days', async () => {
    const api = await installMock()
    const created = (await answered(api, 202, 'POST', PIPELINE_RUNS, {
      from: '2026-01-14', to: '2026-01-14', sources: ['reddit'],
    })) as { kind: string; sources: string[]; rangeFrom: string | null; rangeTo: string | null }[]
    const range = created.find((run) => run.kind === 'range')
    expect(range).toMatchObject({ sources: ['reddit'] })
    expect(range?.rangeFrom).toMatch(/^2026-01-13T23:00:00Z$/)
    expect(range?.rangeTo).toMatch(/^2026-01-14T22:59:59Z$/)
  })

  it('a started posts run names its posts, and a restored post may be started', async () => {
    const api = await installMock()
    await answered(api, 400, 'POST', PIPELINE_RUNS, { postIds: [FAILED_DEAD] })
    await ok(api, 'POST', '/api/admin/posts/restore', { postIds: [FAILED_DEAD] })
    const created = (await answered(api, 202, 'POST', PIPELINE_RUNS, { postIds: [FAILED_DEAD] })) as {
      kind: string
      requestedPostIds: string[]
    }[]
    expect(created).toHaveLength(1)
    expect(created[0]).toMatchObject({ kind: 'posts', requestedPostIds: [FAILED_DEAD] })
  })

  it('the sources are all configured, and the days without data name each source and both', async () => {
    const api = await installMock()
    expect(await ok(api, 'GET', `${PIPELINE_RUNS}/sources`)).toEqual([
      { source: 'wykop', configured: true },
      { source: 'reddit', configured: true },
    ])
    const days = ((await ok(api, 'GET', `${PIPELINE_RUNS}/missing-days`)) as { days: { sources: string[] }[] }).days
    const sets = new Set(days.map((day) => day.sources.join('+')))
    expect([...sets].sort()).toEqual(['reddit', 'wykop', 'wykop+reddit'])
  })
})

describe('the mock\'s suspended answers wait 60 s (fake clocks)', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  /** Whether the call has answered after `ms` of fake time. */
  async function answersAfter(api: MockApi, ms: number, method: string, path: string, body?: unknown): Promise<boolean> {
    vi.useFakeTimers()
    let answeredAlready = false
    const pending = api.call(method, path, body).then((response) => {
      answeredAlready = true
      return response
    })
    await vi.advanceTimersByTimeAsync(ms)
    const result = answeredAlready
    await vi.advanceTimersByTimeAsync(60_000)
    await pending
    return result
  }

  it('an admin call in MODE loading is answered at 60 s, not before', async () => {
    const api = await installMock({ MODE: 'loading' })
    expect(await answersAfter(api, 59_000, 'GET', `${PIPELINE_RUNS}/sources`)).toBe(false)
    vi.useFakeTimers()
    let done = false
    const pending = api.call('GET', `${PIPELINE_RUNS}/sources`).then(() => (done = true))
    await vi.advanceTimersByTimeAsync(60_000)
    await pending
    expect(done).toBe(true)
  })

  it('a call without a session or an admin\'s role is refused at once, even in MODE loading', async () => {
    const anonymous = await installMock({ MODE: 'loading', AUTH: 'anonymous' })
    expect(await answersAfter(anonymous, 0, 'GET', `${PIPELINE_RUNS}/sources`)).toBe(true)
    const user = await installMock({ MODE: 'loading', AUTH: 'user' })
    expect(await answersAfter(user, 0, 'GET', `${PIPELINE_RUNS}/sources`)).toBe(true)
  })

  it('the public activation lookup hangs 60 s in ACTIVATION checking, with no session', async () => {
    const api = await installMock({ ACTIVATION: 'checking', AUTH: 'anonymous' })
    expect(await answersAfter(api, 59_000, 'POST', '/api/auth/activation/lookup', { token: 'any' })).toBe(false)
  })

  it('a start in START busy does not hang', async () => {
    const api = await installMock({ START: 'busy' })
    expect(await answersAfter(api, 0, 'POST', PIPELINE_RUNS, { from: dMinus(3), to: dMinus(1) })).toBe(true)
  })
})

// --- invariants of the mock's data -----------------------------------------------------------------------------

describe('the mock\'s data is a state the API produces', () => {
  it('every last attempt carries at least one violation (AssessmentResult refuses none)', async () => {
    const api = await installMock()
    const page = (await ok(api, 'GET', '/api/admin/posts/failed')) as {
      items: { lastAttempt: { violations: unknown[] } | null }[]
    }
    const attempts = page.items.flatMap((item) => (item.lastAttempt ? [item.lastAttempt] : []))
    expect(attempts.length).toBeGreaterThan(0)
    for (const attempt of attempts) expect(attempt.violations.length).toBeGreaterThanOrEqual(1)
  })

  it('a provider error carries the call rule, like the API', async () => {
    const api = await installMock()
    const page = (await ok(api, 'GET', '/api/admin/posts/failed')) as {
      items: { lastAttempt: { failureClass: string; violations: { rule: string; path: string }[] } | null }[]
    }
    for (const item of page.items) {
      if (item.lastAttempt?.failureClass !== 'provider_error') continue
      expect(item.lastAttempt.violations.map(({ rule, path }) => [rule, path])).toEqual([['call', '$']])
    }
  })

  it('a source result ends inside the fetch step of its run', async () => {
    const api = await installMock()
    const { items } = await pages(api, PIPELINE_RUNS, 20)
    let checked = 0
    for (const run of items as {
      id: string
      steps: { step: string; startedAt: string; finishedAt: string | null }[]
      sourceResults: { source: string; finishedAt: string }[]
    }[]) {
      const fetch = run.steps.find((step) => step.step === 'fetch')
      if (!fetch?.finishedAt) continue
      for (const result of run.sourceResults) {
        expect(result.finishedAt >= fetch.startedAt, `${run.id} ${result.source} starts after its fetch`).toBe(true)
        expect(result.finishedAt <= fetch.finishedAt, `${run.id} ${result.source} ends after its fetch`).toBe(true)
        checked++
      }
    }
    expect(checked).toBeGreaterThan(30)
  })

  it('the raw data of a Reddit post names the post\'s channel as its subreddit, a Wykop post\'s is a Wykop entry', async () => {
    const api = await installMock()
    const { items } = await pages(api, '/api/posts', 50)
    let reddit = 0
    let wykop = 0
    for (const post of items as { id: string; source: string; channel: string }[]) {
      const raw = (await ok(api, 'GET', `/api/posts/${post.id}/raw`)) as Record<string, unknown>
      if (post.source === 'reddit') {
        expect(raw.subreddit, post.id).toBe(post.channel)
        expect(raw, post.id).not.toHaveProperty('slug')
        reddit++
      } else {
        expect(raw, post.id).toHaveProperty('slug')
        expect(raw, post.id).not.toHaveProperty('subreddit')
        wykop++
      }
    }
    expect(reddit).toBeGreaterThan(0)
    expect(wykop).toBeGreaterThan(0)
    const first = (await ok(api, 'GET', `/api/posts/${POST_REDDIT}/raw`)) as Record<string, unknown>
    expect(first.permalink).toMatch(/^\/r\//)
  })
})
