import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

// Kept separate from vite.config.ts: `defineConfig` from 'vite' does not type
// the `test` key, and vue-tsc -b typechecks vite.config.ts in the build.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
    },
  }),
)
