async page => {
  const base = 'https://szczur-radar-web-szczur-radar-pr-42.up.railway.app'
  const output = '/Users/krzysztofmacioszek/Develop/Projects/10xDevs/szczur-radar/.claude/worktrees/admin-panel-ui-hardening/context/changes/admin-panel-ui-hardening/ui-after'
  const result = []
  try {
    for (const width of [1280, 375]) {
      for (const theme of ['light', 'dark']) {
        await page.setViewportSize({ width, height: width === 1280 ? 900 : 812 })
        await page.emulateMedia({ colorScheme: theme })
        await page.goto(base + '/', { waitUntil: 'networkidle' })
        const geometry = await page.evaluate(() => ({ clientWidth: document.documentElement.clientWidth, scrollWidth: document.documentElement.scrollWidth }))
        if (geometry.scrollWidth > geometry.clientWidth) throw new Error('Horizontal overflow in the feed')
        const filename = `pr42-feed-${width}-${theme}-viewport.png`
        await page.screenshot({ path: output + '/' + filename })
        result.push({ width, theme, filename, ...geometry })
      }
    }
  } finally {
    await page.setViewportSize({ width: 1280, height: 900 })
    await page.emulateMedia({ colorScheme: 'light' })
    await page.goto(base + '/admin', { waitUntil: 'networkidle' })
  }
  return result
}
