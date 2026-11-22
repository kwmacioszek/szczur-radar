// What the visual tests screenshot (F-06), as data: the projects (theme × width), the views with their mock modes and
// the moment each one is ready, the catalogue's sections and the hover and focus states. views.spec.ts and
// kitchen-sink.spec.ts screenshot them, and the baselines guard (baselines.spec.ts) derives the expected files under
// visual/__screenshots__/<project>/ from the same tables and the same enumeration of the catalogue.
// playwright.visual.config.ts builds its projects and its dev server from here.
import { expect, type Locator, type Page } from '@playwright/test'
import type { MockModes } from '../src/contract/mockModes.ts'

/** The four projects: a baseline per view lives in visual/__screenshots__/<name>/. */
export const PROJECTS = [
  { name: 'light-375', colorScheme: 'light', width: 375, height: 812 },
  { name: 'dark-375', colorScheme: 'dark', width: 375, height: 812 },
  { name: 'light-1280', colorScheme: 'light', width: 1280, height: 720 },
  { name: 'dark-1280', colorScheme: 'dark', width: 1280, height: 720 },
] as const

export type ProjectName = (typeof PROJECTS)[number]['name']

/** The 375 px projects: the component catalogue and the states are screenshotted only there (PRD :330). */
export const NARROW_PROJECTS: readonly ProjectName[] = PROJECTS.filter((project) => project.width === 375)
  .map((project) => project.name)

/** The project of the single-project checks (the font guard, the baselines guard). */
export const GUARD_PROJECT: ProjectName = 'light-375'

/** vite dev, for /kitchen-sink (only under import.meta.env.DEV, so not in the production build). */
export const DEV_PORT = Number(process.env.E2E_DEV_PORT ?? 5173)
export const DEV_SERVER_URL = `http://localhost:${DEV_PORT}`

// Ids of dev/mock-api.js (its header): run 3 is the cron's finished night with the failed Reddit source, post 0 the
// first post of the feed.
const RUN_3 = '00000000-0000-4000-8000-000100000003'
const POST_0 = '00000000-0000-4000-8000-000400000000'

// The feed's first page: post n is variant n % 6 (e2e/feed.spec.ts), so the first 6 articles show every variant and
// the 7th is where the feed's screenshot ends.
const FEED_PAGE = 50
export const FEED_SHOWN_ARTICLES = 6

/** The admin's pages are ready only with the bell's notifications in the header (its count is on the screenshot). */
async function bellLoaded(page: Page): Promise<void> {
  await expect(page.getByRole('banner').getByRole('button', { name: /^Powiadomienia, / })).toBeVisible()
}

export interface View {
  /** The baseline's file name, without .png. */
  name: string
  /** What breaks when this test is red: the test's name. */
  risk: string
  path: string
  modes: MockModes
  /** Resolves once the view shows all its data: nothing on it changes afterwards. */
  ready: (page: Page) => Promise<void>
  /** Screenshot the page from its top to the top of this article (0-based), not the whole page. */
  clipToArticle?: number
}

export const VIEWS: readonly View[] = [
  {
    name: 'view-login',
    risk: 'widok logowania nie zmienia wyglądu i nie łamie dostępności',
    path: '/login',
    modes: { AUTH: 'anonymous' },
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByRole('heading', { level: 1, name: 'Logowanie', exact: true })).toBeVisible()
      await expect(main.getByLabel('E-mail', { exact: true })).toBeVisible()
      await expect(main.getByLabel('Hasło', { exact: true })).toBeVisible()
    },
  },
  {
    name: 'view-activate',
    risk: 'widok aktywacji konta nie zmienia wyglądu i nie łamie dostępności',
    path: '/activate#token',
    modes: { AUTH: 'anonymous', ACTIVATION: 'valid' },
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByText('Ustaw hasło dla jan.kowalski@example.com.')).toBeVisible()
      // The form takes the focus once the link is checked (ActivateView.vue).
      await expect(main.getByLabel('Nowe hasło', { exact: true })).toBeFocused()
      await expect(main.getByLabel('Powtórz hasło', { exact: true })).toBeVisible()
    },
  },
  {
    name: 'view-feed',
    risk: 'widok feedu nie zmienia wyglądu i nie łamie dostępności',
    path: '/',
    modes: { AUTH: 'user' },
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByRole('article')).toHaveCount(FEED_PAGE)
      await expect(main.getByText('Dane zaktualizowane:')).toBeVisible()
      await expect(main.getByRole('status')).toHaveText('Ocenione posty: 120')
    },
    clipToArticle: FEED_SHOWN_ARTICLES,
  },
  {
    name: 'view-post',
    risk: 'widok posta nie zmienia wyglądu i nie łamie dostępności',
    path: `/posts/${POST_0}`,
    modes: { AUTH: 'user' },
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByRole('heading', { level: 1, name: 'Wpis użytkownika uzytkownik0' })).toBeVisible()
      await expect(main.getByRole('heading', { level: 2, name: 'Ocena', exact: true })).toBeVisible()
      await expect(main.getByRole('heading', { level: 3, name: 'CDR', exact: true })).toBeVisible()
    },
  },
  {
    name: 'view-admin',
    risk: 'panel admina nie zmienia wyglądu i nie łamie dostępności',
    path: '/admin',
    modes: { AUTH: 'admin' },
    // As e2e/admin.spec.ts: every section with its data, none still loading.
    ready: async (page) => {
      const main = page.getByRole('main')
      const region = (name: string) => main.getByRole('region', { name, exact: true })
      for (const name of ['Nowy przebieg', 'Dni bez danych', 'Jakość ocen (7 dni)', 'Ostatnie przebiegi', 'Nieudane posty']) {
        await expect(main.getByRole('heading', { level: 2, name, exact: true })).toBeVisible()
      }
      await expect(region('Nowy przebieg').getByRole('checkbox')).toHaveCount(2)
      await expect(region('Dni bez danych').getByRole('button', { name: /^Wpisz do formularza: / }).first()).toBeVisible()
      await expect(region('Jakość ocen (7 dni)').getByText(/^nieudane .* z 3134 prób/)).toBeVisible()
      await expect(region('Ostatnie przebiegi').getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
      await expect(region('Nieudane posty').getByRole('checkbox')).toHaveCount(4)
      await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)
      await bellLoaded(page)
    },
  },
  {
    name: 'view-run',
    risk: 'strona przebiegu nie zmienia wyglądu i nie łamie dostępności',
    path: `/admin/runs/${RUN_3}`,
    modes: { AUTH: 'admin' },
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByRole('heading', { level: 2, name: 'Wynik per źródło', exact: true })).toBeVisible()
      await expect(main.getByRole('heading', { level: 2, name: 'Kroki', exact: true })).toBeVisible()
      await bellLoaded(page)
    },
  },
]

/** The component catalogue, served by vite dev only. */
export const CATALOG_URL = `${DEV_SERVER_URL}/kitchen-sink`

/** Opens the catalogue (the test's mock answers the router's /api/auth/me) and waits for its heading. */
export async function openCatalog(page: Page): Promise<Locator> {
  await page.goto(CATALOG_URL)
  const main = page.getByRole('main')
  await expect(main.getByRole('heading', { level: 1, name: 'Kitchen sink', exact: true })).toBeVisible()
  return main
}

/**
 * Waits until the fonts the page asked for are loaded (font-display: swap draws a fallback first, whose metrics move
 * content and controls). Call it after the text is visible, before a screenshot, a measurement or a hover or focus.
 */
export async function waitForFonts(page: Page): Promise<void> {
  await page.evaluate(() => document.fonts.ready)
}

/** A section's file name part: lower-case ASCII, Polish letters without their marks, spaces as `-`. */
export function slug(name: string): string {
  const text = name
    .toLowerCase()
    .replaceAll('ł', 'l')
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/\s+/g, '-')
  if (!/^[a-z0-9-]+$/.test(text)) throw new Error(`nazwa sekcji „${name}” nie daje sluga z małych liter ASCII: „${text}”`)
  return text
}

export interface CatalogSection {
  name: string
  slug: string
  region: Locator
}

/**
 * The catalogue's sections, each a region named by its level-2 heading, in page order. Enumerated by the headings, not
 * by every region: the bell's open panels are regions too („Powiadomienia”), and a post card's heading is a level-2
 * heading inside its article, not a section's. A new section gets its baseline by itself; two sections with one slug
 * are an error (they would share a file).
 */
export async function catalogSections(main: Locator): Promise<CatalogSection[]> {
  const headings = await main.getByRole('heading', { level: 2 }).allTextContents()
  const cardHeadings = await main.getByRole('article').getByRole('heading', { level: 2 }).allTextContents()
  const names = headings.map((heading) => heading.trim())
  for (const cardHeading of cardHeadings) {
    const index = names.indexOf(cardHeading.trim())
    if (index < 0) throw new Error(`nagłówek karty „${cardHeading}” nie jest nagłówkiem poziomu 2 w main`)
    names.splice(index, 1)
  }

  const sections = names.map((name) => ({ name, slug: slug(name), region: main.getByRole('region', { name, exact: true }) }))
  const slugs = sections.map((section) => section.slug)
  const repeated = slugs.filter((value, index) => slugs.indexOf(value) !== index)
  if (repeated.length > 0) throw new Error(`dwie sekcje katalogu mają ten sam slug: ${[...new Set(repeated)].join(', ')}`)
  for (const section of sections) await expect(section.region, `region sekcji „${section.name}”`).toHaveCount(1)
  return sections
}

/** The states a control's style has beside its default one: the pointer over it, the keyboard's focus ring. */
export type ControlState = 'hover' | 'focus'

/** A hover or focus state of a shared control on /kitchen-sink: its baseline is its region's screenshot. */
export interface State {
  /** The baseline's file name, without .png. */
  name: string
  /** What breaks when this test is red: the test's name. */
  risk: string
  /** The catalogue section (its level-2 heading) the control is in. */
  region: string
  /** The control, by role and name, inside its region. */
  control: (region: Locator) => Locator
  state: ControlState
  /** The control is a toggle of the theme: before the screenshot it is pressed exactly in the dark projects. */
  pressedByTheme?: true
}

/**
 * The navigation's link that is not the current one, in the bar of the administrator (the first variant of „Pasek
 * aplikacji”): the four bars of the section share their link names, so the bar is found by its title, not by `nth`.
 */
function navigationLink(region: Locator): Locator {
  return region
    .getByRole('listitem')
    .filter({ hasText: 'Administrator z dzwonkiem' })
    .getByRole('navigation', { name: 'Główna', exact: true })
    .getByRole('link', { name: 'Panel admina', exact: true })
}

// The catalogue gives each control of a state its own accessible name within its region (no `nth`): the primary and the
// plain button, the icon-only one, the link, the e-mail and search fields, the theme toggle and (below) the bar's link.
export const STATES: readonly State[] = [
  {
    name: 'state-przycisk-primary-hover',
    risk: 'hover przycisku głównego nie zmienia wyglądu',
    region: 'Przycisk',
    control: (region) => region.getByRole('button', { name: 'Uruchom', exact: true }),
    state: 'hover',
  },
  {
    name: 'state-przycisk-primary-focus',
    risk: 'pierścień fokusu przycisku głównego nie zmienia wyglądu',
    region: 'Przycisk',
    control: (region) => region.getByRole('button', { name: 'Uruchom', exact: true }),
    state: 'focus',
  },
  {
    name: 'state-przycisk-secondary-hover',
    risk: 'hover przycisku zwykłego nie zmienia wyglądu',
    region: 'Przycisk',
    control: (region) => region.getByRole('button', { name: 'Wyczyść filtry', exact: true }),
    state: 'hover',
  },
  {
    name: 'state-przycisk-secondary-focus',
    risk: 'pierścień fokusu przycisku zwykłego nie zmienia wyglądu',
    region: 'Przycisk',
    control: (region) => region.getByRole('button', { name: 'Wyczyść filtry', exact: true }),
    state: 'focus',
  },
  {
    name: 'state-przycisk-icon-hover',
    risk: 'hover przycisku z samą ikoną nie zmienia wyglądu',
    region: 'Przycisk',
    control: (region) => region.getByRole('button', { name: 'Następny post', exact: true }),
    state: 'hover',
  },
  {
    name: 'state-przycisk-icon-focus',
    risk: 'pierścień fokusu przycisku z samą ikoną nie zmienia wyglądu',
    region: 'Przycisk',
    control: (region) => region.getByRole('button', { name: 'Następny post', exact: true }),
    state: 'focus',
  },
  {
    name: 'state-odnosnik-hover',
    risk: 'hover odnośnika (grubsze podkreślenie) nie zmienia wyglądu',
    region: 'Odnośnik',
    control: (region) => region.getByRole('link', { name: 'Wszystkie posty o KGHM', exact: true }),
    state: 'hover',
  },
  {
    name: 'state-odnosnik-focus',
    risk: 'pierścień fokusu odnośnika nie zmienia wyglądu',
    region: 'Odnośnik',
    control: (region) => region.getByRole('link', { name: 'Wszystkie posty o KGHM', exact: true }),
    state: 'focus',
  },
  {
    name: 'state-pola-email-hover',
    risk: 'hover pola tekstowego (ciemniejsza krawędź) nie zmienia wyglądu',
    region: 'Pola',
    control: (region) => region.getByRole('textbox', { name: 'E-mail', exact: true }),
    state: 'hover',
  },
  {
    name: 'state-pola-email-focus',
    risk: 'pierścień fokusu pola tekstowego nie zmienia wyglądu',
    region: 'Pola',
    control: (region) => region.getByRole('textbox', { name: 'E-mail', exact: true }),
    state: 'focus',
  },
  {
    name: 'state-pola-search-focus',
    risk: 'pierścień fokusu pola wyszukiwania nie zmienia wyglądu',
    region: 'Pola',
    control: (region) => region.getByRole('searchbox', { name: 'Puste', exact: true }),
    state: 'focus',
  },
  {
    name: 'state-motyw-hover',
    risk: 'hover przycisku motywu nie zmienia wyglądu',
    region: 'Motyw',
    control: (region) => region.getByRole('button', { name: 'Ciemny motyw', exact: true }),
    state: 'hover',
    pressedByTheme: true,
  },
  {
    name: 'state-motyw-focus',
    risk: 'pierścień fokusu przycisku motywu nie zmienia wyglądu',
    region: 'Motyw',
    control: (region) => region.getByRole('button', { name: 'Ciemny motyw', exact: true }),
    state: 'focus',
    pressedByTheme: true,
  },
  {
    name: 'state-nawigacja-hover',
    risk: 'hover pozycji nawigacji w pasku nie zmienia wyglądu',
    region: 'Pasek aplikacji',
    control: navigationLink,
    state: 'hover',
  },
  {
    name: 'state-nawigacja-focus',
    risk: 'pierścień fokusu pozycji nawigacji w pasku nie zmienia wyglądu',
    region: 'Pasek aplikacji',
    control: navigationLink,
    state: 'focus',
  },
]
