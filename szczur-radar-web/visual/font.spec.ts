// The font guard: the baselines are drawn with the app's own typefaces (public/fonts/, @font-face in src/style.css):
// Newsreader for the headings, Hanken Grotesk for the interface text. If a file is missing or does not load, or a config
// change makes the browser take a fallback (visual/fonts.conf maps only the fallbacks to Liberation), this is red instead
// of baselines quietly drawn in another font.
// Chromium's CSS.getPlatformFontsForNode names the fonts it really drew a node's text with.
import type { Locator, Page } from '@playwright/test'
import { expect, test } from '../e2e/fixtures.ts'
import { GUARD_PROJECT, waitForFonts } from './targets.ts'

test.use({ modes: { AUTH: 'anonymous' } })

/** The families Chromium drew the text of the locator's element with. */
async function platformFonts(page: Page, locator: Locator): Promise<string[]> {
  const cdp = await page.context().newCDPSession(page)
  try {
    await cdp.send('DOM.enable')
    await cdp.send('CSS.enable')
    // requestNode needs the document requested first; the element reaches CDP through a remote object.
    await cdp.send('DOM.getDocument', { depth: 0 })
    await locator.evaluate((element) => {
      ;(window as unknown as { fontProbe: Element }).fontProbe = element
    })
    const { result } = await cdp.send('Runtime.evaluate', { expression: 'window.fontProbe' })
    if (result.objectId === undefined) throw new Error('węzeł nie dotarł do CDP')
    const { nodeId } = await cdp.send('DOM.requestNode', { objectId: result.objectId })
    const { fonts } = await cdp.send('CSS.getPlatformFontsForNode', { nodeId })
    return fonts.map((font) => font.familyName)
  } finally {
    await cdp.detach()
  }
}

test('zrzuty rysuje tylko kroje aplikacji (Newsreader i Hanken Grotesk)', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== GUARD_PROJECT, `strażnik fontu działa tylko w projekcie ${GUARD_PROJECT}`)
  await page.goto('/login')
  const main = page.getByRole('main')
  const heading = main.getByRole('heading', { level: 1, name: 'Logowanie', exact: true })
  // A label with a Polish letter (ł): a font without it would show up here as a second family.
  const label = main.getByText('Hasło', { exact: true })
  await expect(heading).toBeVisible()
  await expect(label).toBeVisible()
  // The swap fallback is drawn first; the families are read only after the files are loaded.
  await waitForFonts(page)

  // CDP names the family from the font file, not from @font-face: Newsreader's file is a variable font with an optical
  // size axis, whose name Chromium reports as "Newsreader 16pt 16pt" (observed in Playwright's image).
  expect(await platformFonts(page, heading), 'fonty nagłówka „Logowanie”').toEqual(['Newsreader 16pt 16pt'])
  expect(await platformFonts(page, label), 'fonty etykiety „Hasło”').toEqual(['Hanken Grotesk'])
})
