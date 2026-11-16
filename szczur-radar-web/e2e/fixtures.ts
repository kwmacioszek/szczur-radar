// Every E2E test imports `test` and `expect` from here. Each test gets a fresh dev/mock-api.js in its own modes
// (test.use({ modes: { AUTH: 'anonymous' } })) and the clock stopped at FIXED_NOW, in the browser and in the mock,
// before its first goto: Date stands still, timers run.
import { readFileSync } from 'node:fs'
import { test as base, type Page } from '@playwright/test'
import { FIXED_NOW, type MockControl, type MockModes } from '../src/contract/mockModes.ts'

// The mock is one expression, the text playwright-cli run-code and the guard evaluate too.
const MOCK_SOURCE = readFileSync(new URL('../dev/mock-api.js', import.meta.url), 'utf8')

type InstallMock = (page: Page, modes: MockModes) => Promise<MockControl>

export const test = base.extend<{ modes: MockModes; mock: MockControl }>({
  modes: [{}, { option: true }],
  mock: [
    async ({ page, modes }, use) => {
      await page.clock.setFixedTime(FIXED_NOW)
      const install = (0, eval)(MOCK_SOURCE) as InstallMock
      const control = await install(page, { ...modes, now: FIXED_NOW })
      await use(control)
      // A suspended answer must not fulfill a route of the closed page.
      control.dispose()
    },
    { auto: true },
  ],
})

export { expect } from '@playwright/test'
