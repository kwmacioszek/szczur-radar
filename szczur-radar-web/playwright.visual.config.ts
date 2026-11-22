import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'
import { DEV_PORT, DEV_SERVER_URL, PROJECTS } from './visual/targets.ts'

// Visual tests (F-06): screenshots compared with the baselines in visual/__screenshots__/<project>/ and axe-core, on
// the production build (vite preview) for the views and on vite dev for /kitchen-sink; /api/* is dev/mock-api.js,
// installed per test by e2e/fixtures.ts with the clock stopped. Only Playwright's pinned image on linux/amd64 renders
// the baselines (the same image as CI's web-e2e job), with the test-only font alias visual/fonts.conf.
if (process.platform !== 'linux' || process.arch !== 'x64' || process.env.PLAYWRIGHT_BROWSERS_PATH !== '/ms-playwright') {
  throw new Error(
    'Testy wyglądu renderuje tylko przypięty obraz Playwright na linux/amd64 (tam powstają wzorce). '
    + 'Uruchom je przez `npm run visual:docker`, a wzorce zaktualizuj przez `npm run visual:update`.',
  )
}

const PORT = Number(process.env.E2E_PORT ?? 4173)
const baseURL = `http://localhost:${PORT}`
const FONTCONFIG_FILE = fileURLToPath(new URL('./visual/fonts.conf', import.meta.url))

// launchOptions.env replaces the browser's environment: keep the process's and add the font alias.
const browserEnv: Record<string, string> = { FONTCONFIG_FILE }
for (const [key, value] of Object.entries(process.env)) {
  if (value !== undefined && !(key in browserEnv)) browserEnv[key] = value
}

export default defineConfig({
  testDir: './visual',
  outputDir: 'test-results-visual',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  // As playwright.config.ts: the retry in CI only collects a trace, a test that passes on its retry fails the run.
  retries: process.env.CI ? 1 : 0,
  failOnFlakyTests: !!process.env.CI,
  // CI never writes a baseline (a missing one is red); locally a missing one is written. The command line's
  // --update-snapshots (npm run visual:update) wins over this.
  updateSnapshots: process.env.CI ? 'none' : 'missing',
  reporter: process.env.CI
    ? [['list'], ['html', { outputFolder: 'playwright-report-visual', open: 'never' }]]
    : 'list',
  // A full-page screenshot of a tall view takes several seconds under amd64 emulation on an arm64 host (the local
  // loop); the defaults (30 s a test, 5 s an assertion) are too short there. CI runs natively on amd64 and keeps the
  // defaults, so a hang there is red early.
  timeout: process.env.CI ? 30_000 : 90_000,
  expect: {
    timeout: process.env.CI ? 5_000 : 20_000,
    toHaveScreenshot: {
      // One baseline per project, no platform suffix: only the image renders them.
      pathTemplate: '{testDir}/__screenshots__/{projectName}/{arg}{ext}',
      animations: 'disabled',
      caret: 'hide',
      // Two runs give byte-identical screenshots: no pixel may differ, by no amount. The default threshold (0.2)
      // lets a subtle colour change through (a button's hover darkened by a 15 % mix went unseen with it).
      maxDiffPixels: 0,
      threshold: 0,
    },
  },
  use: {
    baseURL,
    trace: 'on-first-retry',
    locale: 'pl-PL',
    timezoneId: 'Europe/Warsaw',
    // Skia picks its SIMD code by the CPU it runs on (AVX2, AVX-512…), and GitHub's runners differ by region: rounded
    // corners came out ±1 per channel apart. The baseline code path draws the same pixels on every CPU, also under
    // amd64 emulation on an arm64 host (Chromium's own pixel tests run with this switch). Partial raster redraws only
    // the changed part of a tile, so a corner's pixels depended on the order of the page's late changes (the activation
    // form replacing its "checking" line, the focus, the fonts): every tile is drawn whole.
    launchOptions: { env: browserEnv, args: ['--disable-skia-runtime-opts', '--disable-partial-raster'] },
  },
  projects: PROJECTS.map((project) => ({
    name: project.name,
    use: {
      ...devices['Desktop Chrome'],
      viewport: { width: project.width, height: project.height },
      colorScheme: project.colorScheme,
    },
  })),
  webServer: [
    {
      command: `npm run build && npm run preview -- --port ${PORT} --strictPort`,
      url: baseURL,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
    {
      // /kitchen-sink exists only under vite dev (src/router/index.ts).
      command: `npx vite --port ${DEV_PORT} --strictPort`,
      url: DEV_SERVER_URL,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
  ],
})
