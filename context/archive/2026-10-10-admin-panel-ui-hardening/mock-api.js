/*
 * Mock /api/* for screenshots of /admin without a running API (playwright-cli run-code).
 * MODE: 'data' (runs + failed posts), 'loading' (admin calls hang 60 s), 'error' (admin calls 500), 'empty'.
 * Only paths starting with /api/ are mocked — a '**\/api/**' glob would also catch Vite's /src/api/*.ts modules.
 *
 *   cd szczur-radar-web && npx vite --port 5173 --strictPort &
 *   cd <repo root>   # playwright-cli reads .playwright/cli.config.json (Chromium) from the cwd
 *   playwright-cli open && playwright-cli resize 1280 900
 *   playwright-cli run-code --filename=context/changes/admin-panel-ui-hardening/mock-api.js
 *   playwright-cli goto http://localhost:5173/admin
 *   playwright-cli set-color-scheme light|dark && playwright-cli screenshot --full-page --filename=<png>
 */
async page => {
  const steps = (fail) => [
    { step: 'fetch', status: 'succeeded', startedAt: '2026-10-09T05:00:01Z', finishedAt: '2026-10-09T05:00:20Z', itemsIn: null, itemsOut: 270, error: null },
    { step: 'store', status: 'succeeded', startedAt: '2026-10-09T05:00:20Z', finishedAt: '2026-10-09T05:00:22Z', itemsIn: 270, itemsOut: 41, error: null },
    { step: 'select', status: 'succeeded', startedAt: '2026-10-09T05:00:22Z', finishedAt: '2026-10-09T05:00:22Z', itemsIn: 41, itemsOut: 41, error: null },
    { step: 'assess', status: fail ? 'failed' : 'succeeded', startedAt: '2026-10-09T05:00:22Z', finishedAt: '2026-10-09T05:03:40Z', itemsIn: 41, itemsOut: fail ? null : 38, error: fail ? 'boom' : null },
  ]
  const run = (o) => ({ id: o.id, kind: 'range', status: 'succeeded', rangeFrom: '2026-10-08', rangeTo: '2026-10-08', sources: ['wykop'], requestedPostIds: null,
    createdAt: '2026-10-09T05:00:00Z', startedAt: '2026-10-09T05:00:01Z', finishedAt: '2026-10-09T05:04:00Z',
    fetched: 270, newPosts: 41, assessedOk: 38, assessedFailed: 3, processingErrors: 0, dead: 0, skipped: 0, error: null, steps: steps(false), ...o })
  const runs = [
    run({ id: 'r5', kind: 'range', status: 'running', createdAt: '2026-10-10T07:12:00Z', finishedAt: null, fetched: 120, newPosts: 18, assessedOk: 4, assessedFailed: 0, steps: steps(false).slice(0, 3) }),
    run({ id: 'r4', kind: 'retry', status: 'queued', createdAt: '2026-10-10T07:12:00Z', startedAt: null, finishedAt: null, fetched: 0, newPosts: 0, assessedOk: 0, assessedFailed: 0, steps: [] , sources: []}),
    run({ id: 'r3', status: 'partial', processingErrors: 1, error: 'java.lang.IllegalStateException: Post 9f1c… has no content after NUL stripping (pl.szczurradar.api.pipeline.step.PostStore.store:88)', steps: steps(true) }),
    run({ id: 'r2', status: 'failed', fetched: 0, newPosts: 0, assessedOk: 0, assessedFailed: 0, error: 'Wykop GET search answered HTTP 503', steps: [{ step: 'fetch', status: 'failed', startedAt: '2026-10-08T05:00:01Z', finishedAt: '2026-10-08T05:00:31Z', itemsIn: null, itemsOut: null, error: 'HTTP 503' }], createdAt: '2026-10-08T05:00:00Z' }),
    run({ id: 'r1', status: 'interrupted', createdAt: '2026-10-07T05:00:00Z', assessedOk: 12, assessedFailed: 0 }),
  ]
  const failed = [
    { id: 'p1', itemType: 'post', publishedAt: '2026-10-08T12:00:00Z', title: null, body: 'CDR po wynikach -8% na otwarciu, ktoś jeszcze trzyma? Ja dobieram, bo wycena po spadku wygląda rozsądnie, a guidance na Q4 nie zmieniło się mimo opóźnienia premiery.', webUrl: 'https://wykop.pl/wpis/1', stage: 'failed', assessmentAttempts: 1, lastAttempt: { failureClass: 'schema_violation', violations: [], assessedAt: '2026-10-09T05:02:00Z', provider: 'openrouter', model: 'luna' } },
    { id: 'p2', itemType: 'link', publishedAt: '2026-10-08T09:00:00Z', title: 'KNF ostrzega przed nowym schematem piramidy finansowej', body: null, webUrl: 'https://wykop.pl/link/2', stage: 'failed', assessmentAttempts: 2, lastAttempt: { failureClass: 'transport_error', violations: [], assessedAt: '2026-10-09T05:02:00Z', provider: 'openrouter', model: 'luna' } },
    { id: 'p3', itemType: 'post', publishedAt: '2026-10-07T18:00:00Z', title: null, body: 'Kupiłem PKO, zobaczymy', webUrl: 'https://wykop.pl/wpis/3', stage: 'dead', assessmentAttempts: 3, lastAttempt: { failureClass: 'quote_not_fragment', violations: [], assessedAt: '2026-10-09T05:02:00Z', provider: 'openrouter', model: 'luna' } },
  ]
  const MODE = 'data'
  const json = (route, body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  await page.unrouteAll().catch(() => {})
  await page.route((u) => u.pathname.startsWith('/api/'), async (route) => {
    const url = new URL(route.request().url())
    const p = url.pathname
    if (p === '/api/auth/me') return json(route, { id: 'u1', email: 'admin@szczur-radar.pl', role: 'admin' })
    if (MODE === 'loading' && p.startsWith('/api/admin')) await new Promise((r) => setTimeout(r, 60000))
    if (MODE === 'error' && p.startsWith('/api/admin')) return json(route, { detail: 'internal_error' }, 500)
    if (p === '/api/admin/pipeline-runs/sources') return json(route, [{ source: 'wykop', configured: true }])
    if (p === '/api/admin/pipeline-runs' && route.request().method() === 'GET') return json(route, { items: MODE === 'empty' ? [] : runs, page: 0, size: 20, total: MODE === 'empty' ? 0 : runs.length })
    if (p === '/api/admin/pipeline-runs') return json(route, { detail: 'pipeline_busy' }, 409)
    if (p === '/api/admin/posts/failed') return json(route, { items: MODE === 'empty' ? [] : failed, page: 0, size: 50, total: MODE === 'empty' ? 0 : failed.length })
    return json(route, { detail: 'not_found' }, 404)
  })
}
