async page => {
  const counts = new Map()
  const waiting = []
  const json = (route, body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  await page.route(u => u.pathname.startsWith('/api/'), async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/me') return json(route, { id: 'review', email: 'review@example.com', role: 'admin' })
    if (path === '/api/admin/pipeline-runs/sources') return json(route, [{ source: 'wykop', configured: true }])
    if (path === '/api/admin/pipeline-runs' || path === '/api/admin/posts/failed') {
      const count = (counts.get(path) ?? 0) + 1
      counts.set(path, count)
      if (count === 1) return json(route, { detail: 'internal_error' }, 500)
      await new Promise(resolve => waiting.push(resolve))
      return json(route, { items: [], page: 0, size: 20, total: 0 })
    }
    return json(route, { detail: 'not_found' }, 404)
  })
  await page.goto('http://127.0.0.1:5179/admin')
  await page.getByText('Nie udało się wczytać przebiegów.', { exact: true }).waitFor()
  await page.getByText('Nie udało się wczytać nieudanych postów.', { exact: true }).waitFor()
  await page.getByRole('button', { name: 'Odśwież', exact: true }).click()
  await page.getByText('Jeszcze nie było przebiegu.', { exact: true }).waitFor()
  await page.getByText('Brak nieudanych postów.', { exact: true }).waitFor()
  const result = {
    scenario: 'initial GET 500 -> refresh with both second GETs held pending',
    requests: Object.fromEntries(counts),
    falseEmptyRuns: await page.getByText('Jeszcze nie było przebiegu.', { exact: true }).isVisible(),
    falseEmptyPosts: await page.getByText('Brak nieudanych postów.', { exact: true }).isVisible(),
    busySections: await page.locator('section[aria-busy="true"]').count(),
    loadingMessages: await page.locator('.loading').count()
  }
  await page.screenshot({ path: '/private/tmp/admin-ui-review-false-empty.png', fullPage: true })
  waiting.forEach(resolve => resolve())
  return result
}
