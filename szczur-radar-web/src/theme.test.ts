import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import indexHtml from '../index.html?raw'
import {
  currentTheme,
  initTheme,
  resetTheme,
  resolveTheme,
  setTheme,
  THEME_STORAGE_KEY,
  toggleTheme,
  type Theme,
} from './theme'

/** Stored value × system dark → the theme; the rule of resolveTheme and of the script in index.html. */
const TABLE: { stored: string | null; systemDark: boolean; theme: Theme }[] = [
  { stored: null, systemDark: false, theme: 'light' },
  { stored: null, systemDark: true, theme: 'dark' },
  { stored: 'light', systemDark: false, theme: 'light' },
  { stored: 'light', systemDark: true, theme: 'light' },
  { stored: 'dark', systemDark: false, theme: 'dark' },
  { stored: 'dark', systemDark: true, theme: 'dark' },
  { stored: 'sepia', systemDark: false, theme: 'light' },
  { stored: 'sepia', systemDark: true, theme: 'dark' },
  { stored: '', systemDark: true, theme: 'dark' },
]

/** A controllable stand-in for matchMedia('(prefers-color-scheme: dark)'). */
function stubSystem(initialDark: boolean) {
  let dark = initialDark
  const listeners = new Set<(event: MediaQueryListEvent) => void>()
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({
      get matches() {
        return dark
      },
      addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => listeners.add(listener),
      removeEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => listeners.delete(listener),
    })),
  )
  return {
    change(next: boolean) {
      dark = next
      for (const listener of [...listeners]) listener({ matches: next } as MediaQueryListEvent)
    },
    listeners: () => listeners.size,
  }
}

/** The classic script of index.html's head, as the text between its <script> tags. */
function themeScript(): string {
  const head = indexHtml.slice(indexHtml.indexOf('<head>'), indexHtml.indexOf('</head>'))
  const match = /<script>([\s\S]*?)<\/script>/.exec(head)
  if (!match) throw new Error('index.html has no classic <script> in <head>')
  return match[1]
}

function runThemeScript(): string | undefined {
  delete document.documentElement.dataset.theme
  new Function(themeScript())()
  return document.documentElement.dataset.theme
}

beforeEach(() => {
  localStorage.clear()
  delete document.documentElement.dataset.theme
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  resetTheme()
})

describe('resolveTheme', () => {
  it.each(TABLE)('stored $stored, system dark $systemDark → $theme', ({ stored, systemDark, theme }) => {
    expect(resolveTheme(stored, systemDark)).toBe(theme)
  })
})

describe('the script in index.html', () => {
  it('is a classic script in <head>, before the module of the app', () => {
    const scriptAt = indexHtml.indexOf('<script>')
    expect(scriptAt).toBeGreaterThan(indexHtml.indexOf('<head>'))
    expect(scriptAt).toBeLessThan(indexHtml.indexOf('</head>'))
    expect(scriptAt).toBeLessThan(indexHtml.indexOf('<script type="module"'))
    expect(themeScript()).not.toMatch(/\b(import|export)\b/)
  })

  it('reads the same storage key as the module', () => {
    expect(themeScript()).toContain(`'${THEME_STORAGE_KEY}'`)
  })

  it.each(TABLE)('stored $stored, system dark $systemDark → $theme, as resolveTheme', ({ stored, systemDark, theme }) => {
    stubSystem(systemDark)
    if (stored !== null) localStorage.setItem(THEME_STORAGE_KEY, stored)
    expect(runThemeScript()).toBe(theme)
  })

  it('takes the system theme when reading the storage throws', () => {
    stubSystem(true)
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(runThemeScript()).toBe('dark')
  })

  it('is light when there is neither a choice nor matchMedia', () => {
    vi.stubGlobal('matchMedia', undefined)
    expect(runThemeScript()).toBe('light')
  })
})

describe('theme state', () => {
  it('setTheme sets the attribute, the state and the stored choice', () => {
    setTheme('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(currentTheme.value).toBe('dark')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
  })

  it('toggleTheme flips the theme and stores it', () => {
    setTheme('light')
    toggleTheme()
    expect(currentTheme.value).toBe('dark')
    toggleTheme()
    expect(currentTheme.value).toBe('light')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
  })

  it('initTheme takes the attribute the script set', () => {
    stubSystem(false)
    document.documentElement.dataset.theme = 'dark'
    initTheme()
    expect(currentTheme.value).toBe('dark')
  })

  it('without a choice follows a change of the system, live', () => {
    const system = stubSystem(false)
    document.documentElement.dataset.theme = 'light'
    initTheme()

    system.change(true)
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(currentTheme.value).toBe('dark')
    system.change(false)
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull()
  })

  it('with a stored choice ignores the system', () => {
    const system = stubSystem(false)
    localStorage.setItem(THEME_STORAGE_KEY, 'light')
    document.documentElement.dataset.theme = 'light'
    initTheme()

    expect(system.listeners()).toBe(0)
    system.change(true)
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('a choice made after init stops following the system', () => {
    const system = stubSystem(false)
    document.documentElement.dataset.theme = 'light'
    initTheme()
    expect(system.listeners()).toBe(1)

    setTheme('light')
    expect(system.listeners()).toBe(0)
    system.change(true)
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('initTheme called twice keeps one listener', () => {
    const system = stubSystem(false)
    initTheme()
    initTheme()
    expect(system.listeners()).toBe(1)
  })

  it.each([
    ['is missing', undefined],
    [
      'throws',
      () => {
        throw new Error('blocked')
      },
    ],
    ['has no addEventListener', () => ({ matches: true })],
  ])('initTheme keeps the attribute and the toggle works when matchMedia %s', (_case, matchMedia) => {
    vi.stubGlobal('matchMedia', matchMedia)
    document.documentElement.dataset.theme = 'dark'
    expect(() => initTheme()).not.toThrow()
    expect(currentTheme.value).toBe('dark')

    toggleTheme()
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('a choice made in another tab is applied and stops following the system', () => {
    const system = stubSystem(false)
    document.documentElement.dataset.theme = 'light'
    initTheme()

    window.dispatchEvent(new StorageEvent('storage', { key: THEME_STORAGE_KEY, newValue: 'dark' }))
    expect(currentTheme.value).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(system.listeners()).toBe(0)
    system.change(false)
    expect(currentTheme.value).toBe('dark')
  })

  it('ignores another key and an unknown value from another tab', () => {
    stubSystem(false)
    document.documentElement.dataset.theme = 'light'
    initTheme()

    window.dispatchEvent(new StorageEvent('storage', { key: 'other', newValue: 'dark' }))
    window.dispatchEvent(new StorageEvent('storage', { key: THEME_STORAGE_KEY, newValue: 'sepia' }))
    window.dispatchEvent(new StorageEvent('storage', { key: THEME_STORAGE_KEY, newValue: null }))
    expect(currentTheme.value).toBe('light')
  })

  it('initTheme called twice listens to other tabs once', () => {
    stubSystem(false)
    const add = vi.spyOn(window, 'addEventListener')
    const remove = vi.spyOn(window, 'removeEventListener')
    initTheme()
    initTheme()
    const storageCalls = (spy: typeof add) => spy.mock.calls.filter(([type]) => type === 'storage').length
    expect(storageCalls(add) - storageCalls(remove)).toBe(1)
  })

  it('a storage that throws on read follows the system, and one that throws on write still switches', () => {
    const system = stubSystem(false)
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    document.documentElement.dataset.theme = 'light'
    initTheme()
    system.change(true)
    expect(currentTheme.value).toBe('dark')

    expect(() => toggleTheme()).not.toThrow()
    expect(currentTheme.value).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')
  })
})
