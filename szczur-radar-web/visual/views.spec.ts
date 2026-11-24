// Every view with its data looks like its baseline and has no WCAG A/AA violation, in each of the four projects
// (theme × width). The views and their modes are the table VIEWS in targets.ts. A red screenshot does not stop the test
// (expect.soft), so the report carries axe's result too.
import { expect, test } from '../e2e/fixtures.ts'
import { expectNoAxeViolations } from './axe.ts'
import { VIEWS, postRows, waitForFonts } from './targets.ts'

for (const view of VIEWS) {
  test.describe(view.name, () => {
    test.use({ modes: view.modes })

    test(view.risk, async ({ page }) => {
      await page.goto(view.path)
      await view.ready(page)
      // Fonts loaded before the page is screenshotted and before the list's row is measured below.
      await waitForFonts(page)

      if (view.viewportOnly) {
        await expect.soft(page).toHaveScreenshot(`${view.name}.png`)
      } else if (view.clipToRow === undefined) {
        await expect.soft(page).toHaveScreenshot(`${view.name}.png`, { fullPage: true })
      } else {
        // From the top of the page to the top of that row (or of the pager past the last row), the full width: the
        // rows before it are the variants, the rest of the long list repeats them. The box is in page coordinates while
        // nothing is scrolled.
        expect(await page.evaluate(() => window.scrollY)).toBe(0)
        const main = page.getByRole('main')
        const rows = postRows(main)
        const below = view.clipToRow < (await rows.count())
          ? rows.nth(view.clipToRow)
          : main.getByRole('navigation', { name: 'Strony', exact: true })
        const box = await below.boundingBox()
        if (box === null) throw new Error(`${view.name}: wiersz ${view.clipToRow + 1} nie ma obrysu`)
        const width = page.viewportSize()?.width
        if (width === undefined) throw new Error(`${view.name}: strona nie ma rozmiaru okna`)
        await expect.soft(page).toHaveScreenshot(`${view.name}.png`, {
          fullPage: true,
          clip: { x: 0, y: 0, width, height: Math.round(box.y) },
        })
      }
      await expectNoAxeViolations(page)
    })
  })
}
