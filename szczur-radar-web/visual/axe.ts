// The accessibility check of the visual tests (F-06): axe-core on the page as it is, WCAG 2.0, 2.1 and 2.2 A/AA rules
// only (no best-practice rules). A violation fails the test; a result axe cannot decide ("needs review",
// `incomplete`) does not. An exception would be a change of this code with its reason, never a silent filter.
import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'

const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

export async function expectNoAxeViolations(page: Page): Promise<void> {
  const { violations } = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
  const report = violations.map((violation) =>
    `${violation.id} (${violation.impact ?? 'brak wpływu'}): ${violation.help}\n`
    + violation.nodes.map((node) => `  ${node.target.join(' ')}`).join('\n'))
  expect(report, `naruszenia axe (WCAG A/AA):\n${report.join('\n')}`).toEqual([])
}
