/*
 * Mock /api/* for screenshots of /admin, the feed (/), a post (/posts/:id) and /activate without a running API
 * (playwright-cli run-code), kept for /10x-ui changes. Another mode = run a copy of this file with MODE, AUTH or
 * ACTIVATION replaced.
 * MODE: 'data'         runs + failed posts; the feed has 120 posts (3 pages of 50), each with its page and raw data,
 *       'loading'      admin calls, /api/posts and /api/posts/:id (with /raw) hang 60 s,
 *       'error'        admin calls, /api/posts and /api/posts/:id (with /raw) answer 500,
 *       'empty'        no runs, no failed posts, the feed's total is 0, every post id is 404,
 *       'loading-next' feed page 0 answers, later pages hang 60 s (admin as in 'data'),
 *       'error-next'   feed page 0 answers, later pages answer 500 (admin as in 'data'),
 *       'raw-loading'  /api/posts/:id/raw hangs 60 s (the rest as in 'data'),
 *       'raw-error'    /api/posts/:id/raw answers 500 (the rest as in 'data').
 * FRESHNESS (independent of MODE; another variant = a copy of this file with FRESHNESS replaced), the label above the
 * feed — /api/freshness answers like pipeline/api/FreshnessController.java:
 *       'date'  { lastUpdatedAt: '2026-10-13T03:12:00Z' } (05:12 in Warsaw), the default,
 *       'none'  { lastUpdatedAt: null }: no update yet,
 *       'error' 500; every other route answers as MODE says.
 * /api/posts answers like posts/api/PostController.java: page, size, total; a page past the end (e.g. /?page=9)
 * is 200 with items [] and the real total. /api/posts/feed-<n> (n from 0 to 119) is the feed's post n, any other
 * id is 404 {"detail":"post_not_found"}; /api/posts/feed-<n>/raw is indented JSON text shaped like a Wykop entry.
 * AUTH: 'admin' (/api/auth/me → the admin) or 'anonymous' (/api/auth/me → 401), for the public /activate and /login.
 * ACTIVATION (/activate#<any token>; the token is not checked): 'valid' (lookup → e-mail, activation → 204, then
 *   /login with the "password set" notice), 'invalid' (both → 400 activation_link_invalid), 'policy' (activation →
 *   400 password_policy), 'checking' (lookup hangs 60 s), 'sending' (activation hangs 60 s), 'error' (both → 500).
 *   Client-side validation errors need no mode: submit a short or mismatched password.
 * The filter (FR-011a) has the API's meaning: q, kind and direction keep the posts with one target meeting every
 * given condition — its quote contains q (Polish lower case, q trimmed, blank = no condition), its kind, its stance's
 * direction; total counts them; an unknown kind or direction (or a q over 100 characters) is 400 invalid_request.
 * E.g. /?q=orlen&direction=bullish, /?kind=macro_topic, and an empty filter result: /?q=zzz. In 'empty' mode total is
 * 0 with or without a filter.
 * Only paths starting with /api/ are mocked — a '**\/api/**' glob would also catch Vite's /src/api/*.ts modules.
 * failureClass values are the API's FailureClass (agents/assessor/validation/FailureClass.java), lower case.
 * With AUTH = 'admin' the header shows "Panel admina" on every view.
 * /kitchen-sink needs no mock: dev only, no session, fixed data.
 *
 *   cd szczur-radar-web && npx vite --port 5173 --strictPort &
 *   cd <repo root>   # playwright-cli open reads .playwright/cli.config.json (Chromium) from the cwd; the file is
 *                    # not in git, so from a git worktree add --config=<main checkout>/.playwright/cli.config.json
 *   playwright-cli open && playwright-cli resize 1280 900
 *   playwright-cli run-code --filename=szczur-radar-web/dev/mock-api.js
 *   playwright-cli goto http://localhost:5173/admin   # the feed: http://localhost:5173/ (?page=N, from 0);
 *                                                    # a post: http://localhost:5173/posts/feed-5;
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
  // a comment with two targets, a bare URL without spaces (no content: the prefilter's output) and a link with five
  // (a card shows a link's title, so its body is seen only on the post's page). The targets cover four kinds:
  // company, macro_topic (rynek), asset (ropa) and sector (banki).
  const FEED_TOTAL = 120
  const feedTexts = [
    { itemType: 'post', title: null, body: 'CDR po wynikach -8% na otwarciu, ktoś jeszcze trzyma? Ja dobieram, bo wycena po spadku wygląda rozsądnie, a guidance na Q4 nie zmieniło się mimo opóźnienia premiery. Zobaczymy, czy rynek to doceni przed świętami, bo historycznie grudzień był dla nich mocny, a sentyment na forum jest teraz fatalny.' },
    { itemType: 'link', title: 'KNF ostrzega przed nowym schematem piramidy finansowej', body: null },
    { itemType: 'post', title: null, body: 'Kupiłem PKO, zobaczymy, banki i tak zarobią na stopach' },
    { itemType: 'comment', title: null, body: 'Ten wykres XTB to klasyczna głowa z ramionami, ja bym uważał. Allegro też słabo.' },
    { itemType: 'post', title: null, body: 'https://stooq.pl/q/?s=cdr&c=1d&t=l&a=lg&b=0&d=20261012&f=20261012&l=0&z=1&i=1&n=1&r=1' },
    { itemType: 'link', title: 'Orlen ogłasza buyback za 4 mld zł', body: 'Zarząd rekomenduje skup akcji własnych, ropa tanieje.\nDla Orlenu to dobra wiadomość, PKN wyceniam bez zmian, Lotos to już historia, a PGE ma swoje plusy i minusy.\nRynek i tak żyje teraz tylko stopami.' },
  ]
  const target = (id, quote, direction, assertiveness, claimIds = [], kind = 'company') => ({
    id, kind, anchor: { document: 'item', field: 'body', quote },
    stance: { direction, assertiveness, claim_ids: claimIds }, reference: 'explicit',
  })
  const claim = (id, kind, attribution, targetIds, quote, statement, field = 'body') => ({
    id, kind, attribution, target_ids: targetIds, evidence: { document: 'item', field, quote }, statement,
  })
  // Contract V2 §7.2: assertiveness is null for mixed, unclear and not_expressed; a bullish, bearish, neutral or
  // mixed stance rests on 1–4 claims (claim_ids), at least one of them the author's; not_expressed rests on none.
  // Every claim in claim_ids has the target in its target_ids. Quotes are verbatim fragments of the post.
  const feedAssessments = [
    {
      relevance: 'investment_related',
      targets: [target('t1', 'CDR', 'bullish', 'hedged', ['c1', 'c2']), target('t2', 'rynek', 'unclear', null, [], 'macro_topic')],
      claims: [
        claim('c1', 'trade_action', 'author', ['t1'], 'Ja dobieram', 'Autor dokupuje akcje CDR po spadku.'),
        claim('c2', 'investment_thesis', 'author', ['t1'], 'wycena po spadku wygląda rozsądnie', 'Wycena CDR po spadku jest rozsądna.'),
        claim('c3', 'factual_claim', 'author', ['t1'], 'guidance na Q4 nie zmieniło się mimo opóźnienia premiery', 'Prognoza CDR na Q4 nie zmieniła się mimo opóźnienia premiery.'),
      ],
      flags: [],
    },
    {
      relevance: 'investment_related',
      targets: [],
      claims: [claim('c1', 'factual_claim', 'reported', [], 'KNF ostrzega przed nowym schematem piramidy finansowej', 'KNF ostrzega przed nowym schematem piramidy finansowej.', 'title')],
      flags: ['missing_context'],
    },
    {
      relevance: 'investment_related',
      targets: [target('t1', 'PKO', 'bullish', 'unhedged', ['c1']), target('t2', 'banki', 'bullish', 'hedged', ['c2'], 'sector')],
      claims: [
        claim('c1', 'trade_action', 'author', ['t1'], 'Kupiłem PKO', 'Autor kupił akcje PKO.'),
        claim('c2', 'opinion', 'author', ['t2'], 'banki i tak zarobią na stopach', 'Banki i tak zarobią na stopach procentowych.'),
      ],
      flags: ['insufficient_content'],
    },
    {
      relevance: 'investment_related',
      targets: [target('t1', 'XTB', 'bearish', 'hedged', ['c1']), target('t2', 'Allegro', 'bearish', 'unhedged', ['c2'])],
      claims: [
        claim('c1', 'investment_thesis', 'author', ['t1'], 'Ten wykres XTB to klasyczna głowa z ramionami', 'Wykres XTB tworzy formację głowy z ramionami.'),
        claim('c2', 'opinion', 'author', ['t2'], 'Allegro też słabo', 'Allegro radzi sobie słabo.'),
      ],
      flags: ['possible_sarcasm', 'missing_context'],
    },
    null, // the prefilter: no content, decided without the model
    {
      relevance: 'investment_related',
      targets: [
        target('t1', 'Orlen', 'bullish', 'unhedged', ['c1', 'c2']),
        target('t2', 'PKN', 'neutral', 'unhedged', ['c2']),
        target('t3', 'Lotos', 'not_expressed', null),
        target('t4', 'PGE', 'mixed', null, ['c3']),
        target('t5', 'ropa', 'bearish', 'unhedged', ['c5'], 'asset'),
      ],
      claims: [
        claim('c1', 'factual_claim', 'author', ['t1'], 'Orlen ogłasza buyback za 4 mld zł', 'Orlen ogłosił skup akcji własnych za 4 mld zł.', 'title'),
        // One claim about two targets.
        claim('c2', 'investment_thesis', 'author', ['t1', 't2'], 'Dla Orlenu to dobra wiadomość, PKN wyceniam bez zmian', 'Skup akcji to dobra wiadomość dla Orlenu, a wycena PKN się nie zmienia.'),
        claim('c3', 'opinion', 'author', ['t4'], 'PGE ma swoje plusy i minusy', 'PGE ma zarówno zalety, jak i wady.'),
        // A general claim: no target.
        claim('c4', 'opinion', 'author', [], 'Rynek i tak żyje teraz tylko stopami', 'Rynek reaguje teraz wyłącznie na stopy procentowe.'),
        claim('c5', 'factual_claim', 'author', ['t5'], 'ropa tanieje', 'Ropa tanieje.'),
      ],
      flags: [],
    },
  ]
  const feedItem = (n) => {
    const k = n % feedTexts.length
    const assessment = feedAssessments[k]
    return {
      id: `feed-${n}`, ...feedTexts[k], author: `uzytkownik${n}`, webUrl: `https://wykop.pl/wpis/${n}`,
      publishedAt: new Date(Date.UTC(2026, 9, 12, 20, 0) - n * 17 * 60000).toISOString(),
      assessment: assessment
        ? { output: { modifiers: [], ...assessment }, resultSource: 'model', assessedAt: '2026-10-12T21:00:00Z' }
        : { output: { relevance: 'unclear', modifiers: [], targets: [], claims: [], flags: ['insufficient_content'] }, resultSource: 'prefilter', assessedAt: '2026-10-12T21:00:00Z' },
    }
  }
  // The stored source item of post n, shaped like a Wykop entry (src/test/resources/fixtures/wykop/entry.json of the
  // API), indented like the API's /raw answer.
  const feedRaw = (n) => {
    const item = feedItem(n)
    return JSON.stringify({
      id: 81230000 + n, slug: `wpis-${n}`,
      author: { username: item.author, gender: null, company: false, avatar: '<url>', status: 'active', color: 'orange', verified: false },
      created_at: item.publishedAt.slice(0, 19).replace('T', ' '),
      title: item.title, content: item.body,
      media: { photo: null, embed: null, photos: [], survey: null },
      tags: ['gielda', 'inwestycje'], votes: { up: 12, down: 1, users: [] },
      comments: { items: [], count: 3 }, resource: item.itemType === 'link' ? 'link' : 'entry', status: 'visible',
    }, null, 2)
  }
  const KINDS = ['company', 'asset', 'sector', 'macro_topic', 'other', 'unknown']
  const DIRECTIONS = ['bullish', 'bearish', 'neutral', 'mixed', 'not_expressed', 'unclear']
  // One target meets every given condition, as in posts/PostRepository.java (null = no condition).
  const matches = (item, q, kind, direction) => item.assessment.output.targets.some((t) =>
    (q === null || t.anchor.quote.toLocaleLowerCase('pl').includes(q))
    && (kind === null || t.kind === kind)
    && (direction === null || t.stance.direction === direction))
  const MODE = 'data'
  const AUTH = 'admin'
  const ACTIVATION = 'valid'
  const FRESHNESS = 'date'
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
    if (p === '/api/freshness') {
      if (FRESHNESS === 'error') return json(route, { detail: 'internal_error' }, 500)
      return json(route, { lastUpdatedAt: FRESHNESS === 'none' ? null : '2026-10-13T03:12:00Z' })
    }
    if (p === '/api/posts') {
      const pageNumber = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 50)
      const later = pageNumber > 0
      if (MODE === 'loading' || (MODE === 'loading-next' && later)) await new Promise((r) => setTimeout(r, 60000))
      if (MODE === 'error' || (MODE === 'error-next' && later)) return json(route, { detail: 'internal_error' }, 500)
      const rawQ = url.searchParams.get('q')
      const kind = url.searchParams.get('kind')
      const direction = url.searchParams.get('direction')
      if ((kind !== null && !KINDS.includes(kind)) || (direction !== null && !DIRECTIONS.includes(direction))
        || (rawQ !== null && rawQ.length > 100)) return json(route, { detail: 'invalid_request' }, 400)
      const q = rawQ === null || rawQ.trim() === '' ? null : rawQ.trim().toLocaleLowerCase('pl')
      const all = []
      for (let n = 0; n < (MODE === 'empty' ? 0 : FEED_TOTAL); n++) all.push(feedItem(n))
      const filtered = q === null && kind === null && direction === null ? all : all.filter((item) => matches(item, q, kind, direction))
      const items = filtered.slice(pageNumber * size, (pageNumber + 1) * size)
      return json(route, { items, page: pageNumber, size, total: filtered.length })
    }
    const detail = p.match(/^\/api\/posts\/([^/]+)(\/raw)?$/)
    if (detail) {
      const raw = detail[2] !== undefined
      if (MODE === 'loading' || (MODE === 'raw-loading' && raw)) await new Promise((r) => setTimeout(r, 60000))
      if (MODE === 'error' || (MODE === 'raw-error' && raw)) return json(route, { detail: 'internal_error' }, 500)
      const n = Number(decodeURIComponent(detail[1]).match(/^feed-(\d+)$/)?.[1])
      if (MODE === 'empty' || !(n < FEED_TOTAL)) return json(route, { detail: 'post_not_found' }, 404)
      return raw ? route.fulfill({ status: 200, contentType: 'application/json', body: feedRaw(n) }) : json(route, feedItem(n))
    }
    return json(route, { detail: 'not_found' }, 404)
  })
}
