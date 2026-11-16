// The modes of dev/mock-api.js for TypeScript: the guard (mockApi.test.ts) and the E2E fixture install the mock with
// them. MOCK_MODE_VALUES is the mock's MODE_VALUES (each key's values in its order, the first one the default); the
// guard compares the two both ways, so a mode or value added on one side alone is red. No import: the E2E typecheck
// includes this file alone.

export const MOCK_MODE_VALUES = {
  MODE: ['data', 'loading', 'error', 'empty', 'loading-next', 'error-next', 'raw-loading', 'raw-error'],
  NOTIFICATIONS: ['unread', 'none', 'error'],
  AUTH: ['admin', 'user', 'anonymous'],
  LOGIN: ['ok', 'invalid', 'limited'],
  START: ['accepted', 'busy', 'not_configured'],
  ACTIVATION: ['valid', 'invalid', 'policy', 'checking', 'sending', 'error'],
  FRESHNESS: ['date', 'apart', 'wykop', 'none', 'error'],
  MISSING: ['gaps', 'none', 'error'],
  QUALITY: ['data', 'few', 'nolang', 'none', 'error'],
} as const

/** The second parameter of the mock: a value per mode (absent = its default) and `now`, an ISO instant that stops its clock. */
export type MockModes = { [K in keyof typeof MOCK_MODE_VALUES]?: (typeof MOCK_MODE_VALUES)[K][number] } & { now?: string }

/** What the mock returns once installed. */
export interface MockControl {
  /** Ends the session as if it expired on the server: the next call behind a session is 401 not_authenticated. */
  endSession(): void
  /** Ends every suspended answer without answering it; after it the mock fulfills no route. */
  dispose(): void
  /** The mock's MODE_VALUES. */
  modeValues: Record<string, readonly string[]>
}

/** The moment the guard and the E2E tests stop the clocks at (the mock's `now`, the browser's and Vitest's). */
export const FIXED_NOW = '2026-10-20T10:00:00Z'
