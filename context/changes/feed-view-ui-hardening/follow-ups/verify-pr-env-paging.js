// Run from the repository root in a logged-in playwright-cli session on PR #48:
// playwright-cli run-code --filename=context/changes/feed-view-ui-hardening/follow-ups/verify-pr-env-paging.js
// Requires at least 51 assessed posts. Reads the real API, navigates the UI and saves evidence;
// it does not intercept requests, create data, change PAGE_SIZE or deploy services.
// The CLI returns the JSON evidence; use --raw and save that output alongside the PNG files.
async (page) => {
  const origin = 'https://szczur-radar-web-szczur-radar-pr-48.up.railway.app'
  const output = 'context/changes/feed-view-ui-hardening/ui-after'
  const stamp = new Date().toISOString().replace(/[:.]/g, '-')
  const evidence = { date: new Date().toISOString(), origin, cases: [] }

  for (const theme of ['light', 'dark']) {
    for (const width of [1280, 375]) {
      await page.emulateMedia({ colorScheme: theme })
      await page.setViewportSize({ width, height: width === 375 ? 812 : 900 })
      await page.goto(`${origin}/`)
      const first = await page.evaluate(async () => {
        const response = await fetch('/api/posts?page=0&size=50', { credentials: 'same-origin' })
        if (!response.ok) throw new Error(`GET /api/posts: ${response.status}; sign in to PR #48 first`)
        return response.json()
      })
      if (first.total <= 50) throw new Error(`PR #48 has ${first.total} assessed posts; at least 51 are required`)
      if (first.page !== 0 || first.size !== 50 || first.items.length !== 50) {
        throw new Error('The real first page does not match the page=0, size=50 contract')
      }
      await page.locator('.pager').waitFor()
      const older = page.getByRole('button', { name: 'Starsze', exact: true })
      if (!await older.isEnabled()) throw new Error('Starsze is disabled despite more than one page')
      await older.scrollIntoViewIfNeeded()
      await older.focus()
      const beforeScrollY = await page.evaluate(() => window.scrollY)
      if (beforeScrollY <= 0) throw new Error('The first page must be scrolled down before changing pages')

      const nextResponse = page.waitForResponse((response) => {
        const url = new URL(response.url())
        return url.origin === origin && url.pathname === '/api/posts'
          && url.searchParams.get('page') === '1' && url.searchParams.get('size') === '50'
      })
      await page.keyboard.press('Enter')
      const response = await nextResponse
      if (response.status() !== 200) throw new Error(`Second page API returned ${response.status()}`)
      const second = await response.json()
      if (second.page !== 1 || second.size !== 50) throw new Error('The API did not return page=1, size=50')
      if (second.items.length === 0) throw new Error('The real second page is empty')
      const firstIds = new Set(first.items.map((post) => post.id))
      if (second.items.some((post) => firstIds.has(post.id))) {
        throw new Error('The API repeated posts from page 1; check pagination or concurrent ingestion')
      }
      const expectedPosition = `Strona 2 z ${Math.ceil(second.total / 50)}`
      await page.waitForFunction((position) =>
        document.querySelector('.position')?.textContent?.trim() === position
          && document.querySelector('section[aria-labelledby="feed-title"]')?.getAttribute('aria-busy') !== 'true'
          && document.activeElement?.id === 'feed-title' && window.scrollY === 0,
      expectedPosition)
      const expectedHeadings = second.items.map((post) => {
        const text = post.title ?? post.body ?? ''
        return (text.length > 200 ? `${text.slice(0, 200)}…` : text).trim()
      })
      const actualHeadings = (await page.locator('article h2').allTextContents()).map((text) => text.trim())
      if (JSON.stringify(actualHeadings) !== JSON.stringify(expectedHeadings)) {
        throw new Error('Rendered cards do not match the real second-page API response')
      }
      const state = await page.evaluate(() => ({
        scrollY: window.scrollY,
        focus: `${document.activeElement?.tagName}#${document.activeElement?.id}`,
        scrollWidth: document.documentElement.scrollWidth,
        clientWidth: document.documentElement.clientWidth,
      }))
      if (state.scrollWidth > state.clientWidth) throw new Error('The feed overflows horizontally')
      const screenshot = `f2-pr48-starsze-${width}-${theme}-${stamp}.png`
      await page.screenshot({ path: `${output}/${screenshot}` })
      evidence.cases.push({
        theme, width, total: second.total, count: second.items.length,
        page: 1, size: 50, url: page.url(), position: expectedPosition, beforeScrollY,
        ...state, screenshot, responseUrl: response.url(),
        postIds: second.items.map((post) => post.id),
      })
    }
  }
  return evidence
}
