/*
 * Mock /api/* for screenshots of /admin, the feed (/) and /activate without a running API (playwright-cli run-code),
 * kept for /10x-ui changes. Another mode = run a copy of this file with MODE, AUTH or ACTIVATION replaced.
 * MODE: 'data'         runs + failed posts; the feed has 120 posts (3 pages of 50),
 *       'loading'      admin calls and /api/posts hang 60 s,
 *       'error'        admin calls and /api/posts answer 500,
 *       'empty'        no runs, no failed posts, the feed's total is 0,
 *       'loading-next' feed page 0 answers, later pages hang 60 s (admin as in 'data'),
 *       'error-next'   feed page 0 answers, later pages answer 500 (admin as in 'data').
 * /api/posts answers like posts/api/PostController.java: page, size, total; a page past the end (e.g. /?page=9)
 * is 200 with items [] and the real total.
 * AUTH: 'admin' (/api/auth/me → the admin) or 'anonymous' (/api/auth/me → 401), for the public /activate and /login.
 * ACTIVATION (/activate#<any token>; the token is not checked): 'valid' (lookup → e-mail, activation → 204, then
 *   /login with the "password set" notice), 'invalid' (both → 400 activation_link_invalid), 'policy' (activation →
 *   400 password_policy), 'checking' (lookup hangs 60 s), 'sending' (activation hangs 60 s), 'error' (both → 500).
 *   Client-side validation errors need no mode: submit a short or mismatched password.
 * Only paths starting with /api/ are mocked — a '**\/api/**' glob would also catch Vite's /src/api/*.ts modules.
 * failureClass values are the API's FailureClass (agents/assessor/validation/FailureClass.java), lower case.
 * With AUTH = 'admin' the header shows "Panel admina" on /admin and the feed.
 * /kitchen-sink needs no mock: dev only, no session, fixed data.
 *
 *   cd szczur-radar-web && npx vite --port 5173 --strictPort &
 *   cd <repo root>   # playwright-cli open reads .playwright/cli.config.json (Chromium) from the cwd; the file is
 *                    # not in git, so from a git worktree add --config=<main checkout>/.playwright/cli.config.json
 *   playwright-cli open && playwright-cli resize 1280 900
 *   playwright-cli run-code --filename=szczur-radar-web/dev/mock-api.js
 *   playwright-cli goto http://localhost:5173/admin   # the feed: http://localhost:5173/ (?page=N, from 0);
 *                                                    # /activate#token with AUTH = 'anonymous'
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
    { id: 'p2', itemType: 'link', publishedAt: '2026-10-08T09:00:00Z', title: 'KNF ostrzega przed nowym schematem piramidy finansowej', body: null, webUrl: 'https://wykop.pl/link/2', stage: 'failed', assessmentAttempts: 2, lastAttempt: { failureClass: 'provider_error', violations: [], assessedAt: '2026-10-09T05:02:00Z', provider: 'openrouter', model: 'luna' } },
    { id: 'p3', itemType: 'post', publishedAt: '2026-10-07T18:00:00Z', title: null, body: 'Kupiłem PKO, zobaczymy', webUrl: 'https://wykop.pl/wpis/3', stage: 'dead', assessmentAttempts: 3, lastAttempt: { failureClass: 'fabricated_evidence', violations: [], assessedAt: '2026-10-09T05:02:00Z', provider: 'openrouter', model: 'luna' } },
  ]
  // The feed: post n is variant n % 6 — a long text (cut at 200 characters), a link without a body, a short post,
  // a comment with two targets, a bare URL without spaces (no content: the prefilter's output) and a link with four.
  const FEED_TOTAL = 120
  const feedTexts = [
    { itemType: 'post', title: null, body: 'CDR po wynikach -8% na otwarciu, ktoś jeszcze trzyma? Ja dobieram, bo wycena po spadku wygląda rozsądnie, a guidance na Q4 nie zmieniło się mimo opóźnienia premiery. Zobaczymy, czy rynek to doceni przed świętami, bo historycznie grudzień był dla nich mocny, a sentyment na forum jest teraz fatalny.' },
    { itemType: 'link', title: 'KNF ostrzega przed nowym schematem piramidy finansowej', body: null },
    { itemType: 'post', title: null, body: 'Kupiłem PKO, zobaczymy' },
    { itemType: 'comment', title: null, body: 'Ten wykres XTB to klasyczna głowa z ramionami, ja bym uważał. Allegro też słabo.' },
    { itemType: 'post', title: null, body: 'https://stooq.pl/q/?s=cdr&c=1d&t=l&a=lg&b=0&d=20261012&f=20261012&l=0&z=1&i=1&n=1&r=1' },
    { itemType: 'link', title: 'Orlen ogłasza buyback za 4 mld zł', body: 'Zarząd rekomenduje skup akcji własnych.' },
  ]
  const target = (id, quote, direction, assertiveness) => ({
    id, kind: 'company', anchor: { document: 'item', field: 'body', quote },
    stance: { direction, assertiveness, claim_ids: [] }, reference: 'explicit',
  })
  // assertiveness is null for mixed and not_expressed (table §7.2 of the contract).
  const feedAssessments = [
    { relevance: 'investment_related', targets: [target('t1', 'CDR', 'bullish', 'hedged')], flags: [] },
    { relevance: 'investment_related', targets: [], flags: ['missing_context'] },
    { relevance: 'investment_related', targets: [target('t1', 'PKO', 'bullish', 'unhedged')], flags: ['insufficient_content'] },
    { relevance: 'investment_related', targets: [target('t1', 'XTB', 'bearish', 'hedged'), target('t2', 'Allegro', 'bearish', 'unhedged')], flags: ['possible_sarcasm', 'missing_context'] },
    null, // the prefilter: no content, decided without the model
    { relevance: 'investment_related', targets: [target('t1', 'Orlen', 'bullish', 'unhedged'), target('t2', 'PKN', 'neutral', 'unhedged'), target('t3', 'Lotos', 'not_expressed', null), target('t4', 'PGE', 'mixed', null)], flags: [] },
  ]
  const feedItem = (n) => {
    const k = n % feedTexts.length
    const assessment = feedAssessments[k]
    return {
      id: `feed-${n}`, ...feedTexts[k], author: `uzytkownik${n}`, webUrl: `https://wykop.pl/wpis/${n}`,
      publishedAt: new Date(Date.UTC(2026, 9, 12, 20, 0) - n * 17 * 60000).toISOString(),
      assessment: assessment
        ? { output: { modifiers: [], claims: [], ...assessment }, resultSource: 'model', assessedAt: '2026-10-12T21:00:00Z' }
        : { output: { relevance: 'unclear', modifiers: [], targets: [], claims: [], flags: ['insufficient_content'] }, resultSource: 'prefilter', assessedAt: '2026-10-12T21:00:00Z' },
    }
  }
  const MODE = 'data'
  const AUTH = 'admin'
  const ACTIVATION = 'valid'
  const hang = () => new Promise((r) => setTimeout(r, 60000))
  const json = (route, body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  await page.unrouteAll().catch(() => {})
  await page.route((u) => u.pathname.startsWith('/api/'), async (route) => {
    const url = new URL(route.request().url())
    const p = url.pathname
    if (p === '/api/auth/me') {
      return AUTH === 'anonymous'
        ? json(route, { detail: 'not_authenticated' }, 401)
        : json(route, { id: 'u1', email: 'admin@szczur-radar.pl', role: 'admin' })
    }
    if (p === '/api/auth/logout') return route.fulfill({ status: 204 })
    if (p.startsWith('/api/auth/activation')) {
      const lookup = p === '/api/auth/activation/lookup'
      if (ACTIVATION === 'checking' && lookup) await hang()
      if (ACTIVATION === 'sending' && !lookup) await hang()
      if (ACTIVATION === 'error') return json(route, { detail: 'internal_error' }, 500)
      if (ACTIVATION === 'invalid') return json(route, { detail: 'activation_link_invalid' }, 400)
      if (lookup) return json(route, { email: 'jan.kowalski@example.com' })
      if (ACTIVATION === 'policy') return json(route, { detail: 'password_policy' }, 400)
      return route.fulfill({ status: 204 })
    }
    if (MODE === 'loading' && p.startsWith('/api/admin')) await hang()
    if (MODE === 'error' && p.startsWith('/api/admin')) return json(route, { detail: 'internal_error' }, 500)
    if (p === '/api/admin/pipeline-runs/sources') return json(route, [{ source: 'wykop', configured: true }])
    if (p === '/api/admin/pipeline-runs' && route.request().method() === 'GET') return json(route, { items: MODE === 'empty' ? [] : runs, page: 0, size: 20, total: MODE === 'empty' ? 0 : runs.length })
    if (p === '/api/admin/pipeline-runs') return json(route, { detail: 'pipeline_busy' }, 409)
    if (p === '/api/admin/posts/failed') return json(route, { items: MODE === 'empty' ? [] : failed, page: 0, size: 50, total: MODE === 'empty' ? 0 : failed.length })
    if (p === '/api/posts') {
      const pageNumber = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 50)
      const later = pageNumber > 0
      if (MODE === 'loading' || (MODE === 'loading-next' && later)) await new Promise((r) => setTimeout(r, 60000))
      if (MODE === 'error' || (MODE === 'error-next' && later)) return json(route, { detail: 'internal_error' }, 500)
      const total = MODE === 'empty' ? 0 : FEED_TOTAL
      const items = []
      for (let n = pageNumber * size; n < Math.min(total, (pageNumber + 1) * size); n++) items.push(feedItem(n))
      return json(route, { items, page: pageNumber, size, total })
    }
    return json(route, { detail: 'not_found' }, 404)
  })
}
