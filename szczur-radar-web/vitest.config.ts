import { configDefaults, defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

// Kept separate from vite.config.ts: `defineConfig` from 'vite' does not type
// the `test` key, and vue-tsc -b typechecks vite.config.ts in the build.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      // e2e/ is Playwright's (npm run e2e), not Vitest's.
      exclude: [...configDefaults.exclude, 'e2e/**'],
      // Vitest empties every CSS import by default, `?raw` ones too; keep the text of `?raw`
      // imports so src/styleContract.test.ts can read the tokens of style.css.
      css: { include: /\.css\?raw$/ },
    },
  }),
)
