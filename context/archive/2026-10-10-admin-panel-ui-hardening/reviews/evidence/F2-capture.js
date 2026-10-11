async page => {
  const base = 'https://szczur-radar-web-szczur-radar-pr-42.up.railway.app'
  const output = '/Users/krzysztofmacioszek/Develop/Projects/10xDevs/szczur-radar/.claude/worktrees/admin-panel-ui-hardening/context/changes/admin-panel-ui-hardening/ui-after'
  const result = { verifiedAt: new Date().toISOString(), base, auth: null, api: [], screens: [] }
  const meResponse = await page.request.get(base + '/api/auth/me')
  const me = meResponse.ok() ? await meResponse.json() : null
  result.auth = { status: meResponse.status(), role: me?.role ?? null }
  if (result.auth.status !== 200 || result.auth.role !== 'admin') throw new Error('An authenticated administrator session is required')

  for (const path of ['/api/admin/pipeline-runs/sources', '/api/admin/pipeline-runs?page=0&size=20', '/api/admin/posts/failed?page=0&size=50', '/api/posts?page=0&size=50']) {
    const response = await page.request.get(base + path)
    if (!response.ok()) throw new Error(`${path}: HTTP ${response.status()}`)
    const body = await response.json()
    result.api.push({ path, status: response.status(), count: Array.isArray(body) ? body.length : body.items.length, total: Array.isArray(body) ? body.length : body.total })
  }

  const browser = page.context().browser()
  if (!browser) throw new Error('Cannot create an anonymous browser context')
  const anonymous = await browser.newContext()
  const loginPage = await anonymous.newPage()
  try {
    const anonymousMe = await loginPage.request.get(base + '/api/auth/me')
    if (anonymousMe.status() !== 401) throw new Error(`Anonymous auth/me expected 401, received ${anonymousMe.status()}`)
    for (const view of ['admin', 'feed', 'login']) {
      const current = view === 'login' ? loginPage : page
      const route = view === 'feed' ? '/' : '/' + view
      for (const width of [1280, 375]) {
        for (const theme of ['light', 'dark']) {
          await current.setViewportSize({ width, height: width === 1280 ? 900 : 812 })
          await current.emulateMedia({ colorScheme: theme })
          await current.goto(base + route, { waitUntil: 'networkidle', timeout: 30000 })
          await current.getByRole('heading', { name: view === 'admin' ? 'Panel admina' : view === 'feed' ? 'Posty' : 'Logowanie', exact: true }).waitFor()
          if (view === 'admin') await current.waitForFunction(() => document.querySelectorAll('.loading').length === 0)
          const details = await current.evaluate(() => {
            const root = document.documentElement
            const css = getComputedStyle(root)
            const button = document.querySelector('button.primary, form button')
            const buttonStyle = button ? getComputedStyle(button) : null
            return {
              lang: root.lang,
              viewport: innerWidth,
              scrollWidth: Math.max(root.scrollWidth, document.body.scrollWidth),
              accent: css.getPropertyValue('--accent').trim(),
              background: css.getPropertyValue('--bg').trim(),
              alertCount: document.querySelectorAll('[role="alert"]').length,
              primaryBackground: buttonStyle?.backgroundColor ?? null,
              primaryText: buttonStyle?.color ?? null,
              runCount: document.querySelectorAll('.run').length,
              postCount: document.querySelectorAll('.failed-posts li').length
            }
          })
          if (details.lang !== 'pl') throw new Error(`${view}: document language is not Polish`)
          if (details.accent !== (theme === 'light' ? '#2a5fc4' : '#86aef0')) throw new Error(`${view}: wrong accent in ${theme}`)
          if (details.scrollWidth > width) throw new Error(`${view}: horizontal overflow at ${width}px`)
          if (details.alertCount !== 0) throw new Error(`${view}: unexpected alert`)
          const filename = `pr42-${view}-${width}-${theme}.png`
          await current.screenshot({ path: output + '/' + filename, fullPage: true })
          result.screens.push({ view, route, width, height: width === 1280 ? 900 : 812, theme, filename, url: current.url(), ...details })
        }
      }
    }
  } finally {
    await anonymous.close()
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.emulateMedia({ colorScheme: 'light' })
    await page.goto(base + '/admin', { waitUntil: 'networkidle' })
  }
  return result
}
