// The baselines guard: every file under visual/__screenshots__/<project>/ belongs to a current target and every target
// has its file, both ways, so a view or section rebuilt away leaves no orphan baseline that guards nothing. The expected
// files come from the same tables (VIEWS, STATES) and the same enumeration of the catalogue as the screenshot tests.
import { readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { expect, test } from '../e2e/fixtures.ts'
import { GUARD_PROJECT, NARROW_PROJECTS, PROJECTS, STATES, VIEWS, catalogSections, openCatalog } from './targets.ts'

const SCREENSHOTS = fileURLToPath(new URL('./__screenshots__/', import.meta.url))

// The catalogue is opened to enumerate its sections; the router's /api/auth/me goes to the mock.
test.use({ modes: { AUTH: 'anonymous' } })

test('każdy wzorzec należy do celu zrzutu i każdy cel ma wzorzec', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== GUARD_PROJECT, `strażnik wzorców działa tylko w projekcie ${GUARD_PROJECT}`)
  const sections = await catalogSections(await openCatalog(page))

  const expected = new Set<string>()
  for (const project of PROJECTS) {
    for (const view of VIEWS) expected.add(`${project.name}/${view.name}.png`)
  }
  for (const project of NARROW_PROJECTS) {
    for (const section of sections) expected.add(`${project}/kitchen-sink-${section.slug}.png`)
    for (const state of STATES) expected.add(`${project}/${state.name}.png`)
  }

  const projectNames: readonly string[] = PROJECTS.map((project) => project.name)
  const directories = readdirSync(SCREENSHOTS, { withFileTypes: true }).filter((entry) => entry.isDirectory())
  const unknownProjects = directories.map((entry) => entry.name).filter((name) => !projectNames.includes(name))
  const actual = new Set<string>()
  for (const directory of directories) {
    if (!projectNames.includes(directory.name)) continue
    for (const file of readdirSync(`${SCREENSHOTS}${directory.name}`)) {
      if (file.endsWith('.png')) actual.add(`${directory.name}/${file}`)
    }
  }

  const orphans = [...actual].filter((file) => !expected.has(file)).sort()
  const missing = [...expected].filter((file) => !actual.has(file)).sort()
  const problems = [
    ...unknownProjects.map((name) => `nieznany projekt (katalog do usunięcia): visual/__screenshots__/${name}/`),
    ...orphans.map((file) => `wzorzec osierocony (do usunięcia): visual/__screenshots__/${file}`),
    ...missing.map((file) => `brak wzorca (npm run visual:update): visual/__screenshots__/${file}`),
  ]
  expect(problems, `wzorce nie zgadzają się z celami zrzutów:\n${problems.join('\n')}`).toEqual([])
})
