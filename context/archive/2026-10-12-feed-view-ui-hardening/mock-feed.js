/*
 * Mock /api/* for screenshots of the feed (/) without a running API (playwright-cli run-code). Research evidence of
 * feed-view-ui-hardening; szczur-radar-web/dev/mock-api.js covers /admin only (it answers /api/posts with 404).
 * MODE: 'data' (120 posts, 3 pages of 50), 'loading' (/api/posts hangs 60 s), 'error' (/api/posts 500),
 *       'empty' (total 0), 'error-next' (page 0 answers, later pages 500), 'loading-next'
 *       (page 0 answers, later pages hang 60 s), 'slow' (data after 1.5 s).
 * A page past the end (e.g. /?page=9) answers like the API: 200, items [], the real total.
 * Only paths starting with /api/ are mocked — a '**\/api/**' glob would also catch Vite's /src/api/*.ts modules.
 *
 *   cd szczur-radar-web && npx vite --port 5181 --strictPort &
 *   playwright-cli open --config=<main checkout>/.playwright/cli.config.json && playwright-cli resize 1280 900
 *   playwright-cli run-code --filename=context/changes/feed-view-ui-hardening/mock-feed.js   (a copy with MODE replaced)
 *   playwright-cli goto http://localhost:5181/
 *   playwright-cli set-color-scheme light|dark && playwright-cli screenshot --full-page --filename=<png>
 */
async page => {
  const MODE = 'data'
  const TOTAL = 120
  const texts = [
    { itemType: 'post', title: null, body: 'CDR po wynikach -8% na otwarciu, ktoś jeszcze trzyma? Ja dobieram, bo wycena po spadku wygląda rozsądnie, a guidance na Q4 nie zmieniło się mimo opóźnienia premiery. Zobaczymy, czy rynek to doceni przed świętami, bo historycznie grudzień był dla nich mocny, a sentyment na forum jest teraz fatalny.' },
    { itemType: 'link', title: 'KNF ostrzega przed nowym schematem piramidy finansowej', body: null },
    { itemType: 'post', title: null, body: 'Kupiłem PKO, zobaczymy' },
    { itemType: 'comment', title: null, body: 'Ten wykres XTB to klasyczna głowa z ramionami, ja bym uważał. Allegro też słabo.' },
    { itemType: 'post', title: null, body: 'https://stooq.pl/q/?s=cdr&c=1d&t=l&a=lg&b=0&d=20261012&f=20261012&l=0&z=1&i=1&n=1&r=1' },
    { itemType: 'link', title: 'Orlen ogłasza buyback za 4 mld zł', body: 'Zarząd rekomenduje skup akcji własnych.' },
  ]
  const target = (id, quote, direction, assertiveness = 'unhedged') => ({
    id, kind: 'company', anchor: { document: 'item', field: 'body', quote },
    stance: { direction, assertiveness, claim_ids: [] }, reference: 'explicit',
  })
  const assessments = [
    { relevance: 'investment_related', targets: [target('t1', 'CDR', 'bullish', 'hedged')], flags: [] },
    { relevance: 'investment_related', targets: [], flags: ['missing_context'] },
    { relevance: 'investment_related', targets: [target('t1', 'PKO', 'bullish')], flags: ['insufficient_content'] },
    { relevance: 'investment_related', targets: [target('t1', 'XTB', 'bearish', 'hedged'), target('t2', 'Allegro', 'bearish')], flags: ['possible_sarcasm', 'missing_context'] },
    null, // prefilter: no content, decided without the model
    { relevance: 'investment_related', targets: [target('t1', 'Orlen', 'bullish'), target('t2', 'PKN', 'neutral'), target('t3', 'Lotos', 'not_expressed'), target('t4', 'PGE', 'mixed')], flags: [] },
  ]
  const item = (n) => {
    const k = n % texts.length
    const assessment = assessments[k]
    return {
      id: `p${n}`, ...texts[k], author: `uzytkownik${n}`, webUrl: `https://wykop.pl/wpis/${n}`,
      publishedAt: new Date(Date.UTC(2026, 9, 12, 20, 0) - n * 17 * 60000).toISOString(),
      assessment: assessment
        ? { output: { modifiers: [], claims: [], ...assessment }, resultSource: 'model', assessedAt: '2026-10-12T21:00:00Z' }
        : { output: { relevance: 'unclear', modifiers: [], targets: [], claims: [], flags: ['insufficient_content'] }, resultSource: 'prefilter', assessedAt: '2026-10-12T21:00:00Z' },
    }
  }
  const json = (route, body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  await page.unrouteAll().catch(() => {})
  await page.route((u) => u.pathname.startsWith('/api/'), async (route) => {
    const url = new URL(route.request().url())
    const p = url.pathname
    if (p === '/api/auth/me') return json(route, { id: 'u1', email: 'czytelnik@szczur-radar.pl', role: 'user' })
    if (p === '/api/posts') {
      const page = Number(url.searchParams.get('page') ?? 0)
      const size = Number(url.searchParams.get('size') ?? 50)
      if (MODE === 'loading' || (MODE === 'loading-next' && page > 0)) await new Promise((r) => setTimeout(r, 60000))
      if (MODE === 'slow') await new Promise((r) => setTimeout(r, 1500))
      if (MODE === 'error' || (MODE === 'error-next' && page > 0)) return json(route, { detail: 'internal_error' }, 500)
      const total = MODE === 'empty' ? 0 : TOTAL
      const items = []
      for (let n = page * size; n < Math.min(total, (page + 1) * size); n++) items.push(item(n))
      return json(route, { items, page, size, total })
    }
    return json(route, { detail: 'not_found' }, 404)
  })
}
