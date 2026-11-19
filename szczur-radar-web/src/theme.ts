// The theme of the page — one reactive value, like auth/session.ts. The attribute data-theme of <html> is the
// single source for style.css; the classic script in index.html sets it before the first paint (a valid stored
// choice, else the system's theme), and this module takes over from there: it reads the attribute, stores a
// choice made by the button and, while there is no choice, follows the system live.
import { readonly, ref } from 'vue'

export type Theme = 'light' | 'dark'

/** localStorage key of the choice; the script in index.html spells the same literal (src/theme.test.ts). */
export const THEME_STORAGE_KEY = 'szczur-radar-theme'

const DARK_QUERY = '(prefers-color-scheme: dark)'

/** The rule shared with index.html: a stored `light`/`dark` wins, anything else is the system's theme. */
export function resolveTheme(stored: string | null, systemDark: boolean): Theme {
  if (stored === 'light' || stored === 'dark') return stored
  return systemDark ? 'dark' : 'light'
}

function attributeTheme(): Theme {
  return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light'
}

const theme = ref<Theme>(attributeTheme())

export const currentTheme = readonly(theme)

let stopFollowing: (() => void) | null = null

function apply(next: Theme): void {
  theme.value = next
  document.documentElement.dataset.theme = next
}

function storedChoice(): string | null {
  try {
    return localStorage.getItem(THEME_STORAGE_KEY)
  } catch {
    return null
  }
}

function stopFollowingSystem(): void {
  stopFollowing?.()
  stopFollowing = null
}

let stopSyncing: (() => void) | null = null

/** A choice made in another tab of the page: applied here too, and the system no longer followed. */
function syncAcrossTabs(): void {
  stopSyncing?.()
  const onStorage = (event: StorageEvent): void => {
    if (event.key !== THEME_STORAGE_KEY) return
    if (event.newValue !== 'light' && event.newValue !== 'dark') return
    stopFollowingSystem()
    apply(event.newValue)
  }
  window.addEventListener('storage', onStorage)
  stopSyncing = () => window.removeEventListener('storage', onStorage)
}

/** Choose a theme: applied now, kept in the browser and no longer following the system. */
export function setTheme(next: Theme): void {
  stopFollowingSystem()
  apply(next)
  try {
    localStorage.setItem(THEME_STORAGE_KEY, next)
  } catch {
    // Storage is blocked or full: the theme holds until the page is closed.
  }
}

export function toggleTheme(): void {
  setTheme(theme.value === 'dark' ? 'light' : 'dark')
}

/** Forget the theme: no listener, light, no attribute (tests). */
export function resetTheme(): void {
  stopFollowingSystem()
  stopSyncing?.()
  stopSyncing = null
  theme.value = 'light'
  delete document.documentElement.dataset.theme
}

/** Take over from the script in index.html. Without a stored choice the page follows the system's theme. */
export function initTheme(): void {
  stopFollowingSystem()
  syncAcrossTabs()
  theme.value = attributeTheme()
  const stored = storedChoice()
  if (stored === 'light' || stored === 'dark') return
  try {
    const query = window.matchMedia(DARK_QUERY)
    const onChange = (event: MediaQueryListEvent): void => apply(resolveTheme(null, event.matches))
    query.addEventListener('change', onChange)
    stopFollowing = () => query.removeEventListener('change', onChange)
  } catch {
    // No usable matchMedia (missing, throwing, or without addEventListener): the theme the script set stays.
  }
}
