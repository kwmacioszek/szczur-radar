import { describe, expect, it } from 'vitest'
import { installMock, type MockApi } from './mockApi'
import rules from './rules.json'

// Guard of dev/mock-api.js: the mock is executed without a browser (mockApi.ts) and every route of ROUTES is compared
// with the contract files the API's integration tests pin (see src/contract/rules.test.ts for the rules registry):
//  - keys: each object of the mock has exactly the keys of some contract object on the same path,
//  - types: the JSON type of each value (null included) occurs on that path in one of the record's files,
//  - enums: the values on the paths of ENUMS belong to the set of rules.json `values`.
// A variant the mock needs and no contract file has is added to the file through the API's integration test, not
// excused here.

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
const NOT_COVERED: Record<string, string> = {
  'started-runs.json': 'the mock answers POST /api/admin/pipeline-runs with 409 for now; S-43 phase 2 starts runs (202)',
}

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

async function ok(api: MockApi, method: string, path: string, body?: unknown): Promise<unknown> {
  const response = await api.call(method, path, body)
  expect(response.status, `${method} ${path}`).toBe(200)
  return response.body
}

const ROUTES: Route[] = [
  {
    name: 'GET /api/auth/me',
    fetch: async (api) => [await ok(api, 'GET', '/api/auth/me')],
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
    fetch: async (api) => [await ok(api, 'POST', '/api/admin/posts/restore', { postIds: ['p3'] })],
    checks: [{ at: '$', refs: [{ file: 'restore-result.json', at: '$' }] }],
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
