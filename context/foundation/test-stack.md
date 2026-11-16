# Test stack

## E2E

<!-- Written by /10x-e2e-setup. Re-run it to change this section; other skills only read it. -->

- runner: Playwright Test, @playwright/test 1.65.1
- config: szczur-radar-web/playwright.config.ts
- single-spec command: cd szczur-radar-web && npx playwright test e2e/<name>.spec.ts
- full-suite command: cd szczur-radar-web && npm run e2e
- base URL: http://localhost:4173
- port: 4173 (detected default of vite preview, vite.config.ts does not set it; detected default 4173, override with E2E_PORT)
- web server command: npm run build && npm run preview -- --port $E2E_PORT --strictPort; reuseExistingServer outside CI
- auth setup project: none — sesję ustawia tryb AUTH atrapy per test (e2e/fixtures.ts, test.use({ modes }))
- storageState: none
- seed: szczur-radar-web/e2e/session-guard.spec.ts — protects session-guard-roundtrip: a rebuild of the router or the shell (S-47) loses the way back to the requested page after the login
- browser CLI: playwright-cli, command skill at .claude/skills/playwright-cli
- updated: 2026-11-16
