// The component catalogue /kitchen-sink (vite dev only) at 375 px in both themes: each section looks like its baseline
// kitchen-sink-<slug>.png, so a changed shared component changes only its section's file; the catalogue has no WCAG
// A/AA violation; and the hover and focus states of STATES (targets.ts) look like theirs. No click and no Escape before
// a screenshot: either closes the bell's open panels. No wait for aria-busy to clear: the catalogue shows it on purpose.
import { expect, test } from '../e2e/fixtures.ts'
import { expectNoAxeViolations } from './axe.ts'
import { NARROW_PROJECTS, STATES, catalogSections, openCatalog, waitForFonts } from './targets.ts'

// The router asks /api/auth/me on every navigation; the mock answers it (without it vite dev proxies it to :8000).
test.use({ modes: { AUTH: 'anonymous' } })

test.beforeEach(({}, testInfo) => {
  test.skip(!NARROW_PROJECTS.some((name) => name === testInfo.project.name), 'katalog tylko przy 375 px (PRD :330)')
})

test('sekcje katalogu komponentów nie zmieniają wyglądu', async ({ page }) => {
  const main = await openCatalog(page)
  await waitForFonts(page)
  for (const section of await catalogSections(main)) {
    await expect.soft(section.region).toHaveScreenshot(`kitchen-sink-${section.slug}.png`)
  }
})

test('katalog komponentów nie łamie dostępności', async ({ page }) => {
  await openCatalog(page)
  await expectNoAxeViolations(page)
})

for (const state of STATES) {
  test(state.risk, async ({ page }) => {
    const main = await openCatalog(page)
    await waitForFonts(page)
    const region = main.getByRole('region', { name: state.region, exact: true })
    const control = state.control(region)
    // The whole region in view first, so its screenshot does not scroll it away from under the pointer.
    await region.scrollIntoViewIfNeeded()

    if (state.state === 'hover') {
      await control.hover()
      expect(await control.evaluate((element) => element.matches(':hover')), 'kontrolka pod kursorem przed zrzutem').toBe(true)
      await expect(region).toHaveScreenshot(`${state.name}.png`)
      expect(await control.evaluate((element) => element.matches(':hover')), 'kontrolka pod kursorem po zrzucie').toBe(true)
    } else {
      // Focus without a pointer: Chromium shows the ring (:focus-visible) as after Tab; without the ring the baseline
      // would be a focused control that looks like the default one.
      await control.focus()
      expect(await control.evaluate((element) => element.matches(':focus-visible')), 'pierścień fokusu przed zrzutem').toBe(true)
      // The ring lies outside the control's outline, so the screenshot is the region's.
      await expect(region).toHaveScreenshot(`${state.name}.png`)
    }
  })
}
