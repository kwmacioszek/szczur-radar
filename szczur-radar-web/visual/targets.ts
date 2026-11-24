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

/** The bar of the admin panel's parts (S-50). */
function adminParts(page: Page): Locator {
  return page.getByRole('main').getByRole('navigation', { name: 'Panel admina', exact: true })
}

/**
 * The admin panel's pages are ready only with the bar's count of the mock's 4 failed posts, and the focus on the
 * heading, which it takes once the part's first section is read (both are on the screenshot).
 */
async function adminPartsLoaded(page: Page): Promise<void> {
  await expect(adminParts(page).getByRole('link', { name: 'Nieudane posty 4', exact: true })).toBeVisible()
  await expect(page.getByRole('main').getByRole('heading', { level: 1, name: 'Panel admina' })).toBeFocused()
}

/** Every logged-in page is ready only with the date of the data in the bar (it sets the bar's height and its row). */
async function freshnessLoaded(page: Page): Promise<void> {
  await expect(page.getByRole('banner').getByText('Dane:', { exact: true })).toBeVisible()
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
    name: 'view-login-error',
    risk: 'komunikat błędu logowania nie zmienia wyglądu i nie łamie dostępności',
    path: '/login',
    modes: { AUTH: 'anonymous', LOGIN: 'invalid' },
    // Submitted with Enter in the password field, never by a click: the pointer over the button would put its hover
    // into the baseline.
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByRole('heading', { level: 1, name: 'Logowanie', exact: true })).toBeVisible()
      await main.getByLabel('E-mail', { exact: true }).fill('jan.kowalski@example.com')
      const password = main.getByLabel('Hasło', { exact: true })
      await password.fill('zle-haslo')
      await password.press('Enter')
      await expect(main.getByRole('alert')).toHaveText('Nieprawidłowy e-mail lub hasło.')
    },
  },
  {
    name: 'view-activate-error',
    risk: 'błąd przy polu hasła aktywacji nie zmienia wyglądu i nie łamie dostępności',
    path: '/activate#token',
    modes: { AUTH: 'anonymous', ACTIVATION: 'valid' },
    // A too short password in both fields, submitted with Enter in the second one (not by a click, see above): the
    // problem is shown at the first field, which takes the focus back.
    ready: async (page) => {
      const main = page.getByRole('main')
      const password = main.getByLabel('Nowe hasło', { exact: true })
      await expect(password).toBeFocused()
      await password.fill('krotkie')
      const repeat = main.getByLabel('Powtórz hasło', { exact: true })
      await repeat.fill('krotkie')
      await repeat.press('Enter')
      await expect(main.getByText('Hasło musi mieć co najmniej 12 znaków.')).toBeVisible()
      await expect(password).toBeFocused()
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
      await expect(main.getByRole('status')).toHaveText('Ocenione posty: 120')
      await freshnessLoaded(page)
    },
    clipToArticle: FEED_SHOWN_ARTICLES,
  },
  {
    name: 'view-feed-loading',
    risk: 'szkielet ładowania listy nie zmienia wyglądu i nie łamie dostępności',
    path: '/',
    modes: { AUTH: 'user', MODE: 'loading' },
    // The skeleton's text for assistive technology: the list's aria-busy has no role to find it by (as in E2E).
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByText('Wczytywanie…', { exact: true })).toBeVisible()
      await freshnessLoaded(page)
    },
  },
  {
    name: 'view-feed-empty',
    risk: 'pusty wynik filtra listy nie zmienia wyglądu i nie łamie dostępności',
    path: '/?q=zzz',
    modes: { AUTH: 'user' },
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByRole('heading', { level: 2, name: 'Brak postów dla tych filtrów', exact: true })).toBeVisible()
      await expect(main.getByRole('status')).toHaveText('Żaden post nie pasuje do filtra.')
      await freshnessLoaded(page)
    },
  },
  {
    name: 'view-feed-error',
    risk: 'błąd listy z ponowieniem nie zmienia wyglądu i nie łamie dostępności',
    path: '/',
    modes: { AUTH: 'user', MODE: 'error' },
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(
        main.getByRole('alert').getByRole('heading', { level: 2, name: 'Nie udało się wczytać postów', exact: true }),
      ).toBeVisible()
      await freshnessLoaded(page)
    },
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
      await freshnessLoaded(page)
    },
  },
  {
    name: 'view-admin',
    risk: 'część „Przebiegi” panelu admina nie zmienia wyglądu i nie łamie dostępności',
    path: '/admin',
    modes: { AUTH: 'admin' },
    // As e2e/admin.spec.ts: every section of the part with its data, none still loading, and the bar's count.
    ready: async (page) => {
      const main = page.getByRole('main')
      const region = (name: string) => main.getByRole('region', { name, exact: true })
      for (const name of ['Nowy przebieg', 'Dni bez danych', 'Ostatnie przebiegi']) {
        await expect(main.getByRole('heading', { level: 2, name, exact: true })).toBeVisible()
      }
      await expect(region('Nowy przebieg').getByRole('checkbox')).toHaveCount(2)
      await expect(region('Dni bez danych').getByRole('button', { name: /^Wpisz do formularza: / }).first()).toBeVisible()
      await expect(region('Ostatnie przebiegi').getByRole('table')).toBeVisible()
      await expect(region('Ostatnie przebiegi').getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
      await adminPartsLoaded(page)
      await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)
      await bellLoaded(page)
      await freshnessLoaded(page)
    },
  },
  {
    name: 'view-admin-loading',
    risk: 'szkielety sekcji panelu admina nie zmieniają wyglądu i nie łamią dostępności',
    path: '/admin',
    modes: { AUTH: 'admin', MODE: 'loading' },
    // Every admin call hangs in this mode, the bell's and the bar's count too: only the bell's button is there (no
    // count), not `bellLoaded`, and the bar has no count.
    ready: async (page) => {
      const main = page.getByRole('main')
      for (const name of ['Nowy przebieg', 'Dni bez danych', 'Ostatnie przebiegi']) {
        await expect(main.getByRole('region', { name, exact: true })).toHaveAttribute('aria-busy', 'true')
      }
      await expect(main.getByRole('button', { name: 'Odśwież', exact: true })).toBeDisabled()
      await expect(adminParts(page).getByRole('link', { name: 'Nieudane posty', exact: true })).toBeVisible()
      await expect(page.getByRole('banner').getByRole('button', { name: /^Powiadomienia/ })).toBeVisible()
      await freshnessLoaded(page)
    },
  },
  {
    name: 'view-admin-empty',
    risk: 'pusta część „Nieudane posty” panelu admina nie zmienia wyglądu i nie łamie dostępności',
    path: '/admin/posts',
    modes: { AUTH: 'admin', MODE: 'empty' },
    // No failed post: the bar has no count. The heading takes the focus once the list is read: it is on the shot.
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByText('Brak nieudanych postów', { exact: true })).toBeVisible()
      await expect(adminParts(page).getByRole('link', { name: 'Nieudane posty', exact: true })).toBeVisible()
      await expect(main.getByRole('heading', { level: 1, name: 'Panel admina' })).toBeFocused()
      await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)
      await bellLoaded(page)
      await freshnessLoaded(page)
    },
  },
  {
    name: 'view-admin-error',
    risk: 'błąd części „Jakość ocen” panelu admina nie zmienia wyglądu i nie łamie dostępności',
    path: '/admin/quality',
    modes: { AUTH: 'admin', QUALITY: 'error' },
    ready: async (page) => {
      const main = page.getByRole('main')
      await expect(main.getByRole('region', { name: 'Jakość ocen (7 dni)', exact: true }).getByRole('alert'))
        .toContainText('Nie udało się wczytać jakości ocen')
      await adminPartsLoaded(page)
      await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)
      await bellLoaded(page)
      await freshnessLoaded(page)
    },
  },
  {
    name: 'view-admin-quality',
    risk: 'część „Jakość ocen” panelu admina nie zmienia wyglądu i nie łamie dostępności',
    path: '/admin/quality',
    modes: { AUTH: 'admin' },
    ready: async (page) => {
      const main = page.getByRole('main')
      const quality = main.getByRole('region', { name: 'Jakość ocen (7 dni)', exact: true })
      await expect(quality.getByRole('heading', { level: 3 })).toHaveText(['Wykop', 'Reddit'])
      await expect(quality.getByRole('img', { name: /^Nieudane próby oceny: / })).toHaveCount(2)
      await expect(quality.getByRole('img', { name: /^Tezy w innym języku niż post: / })).toHaveCount(2)
      await adminPartsLoaded(page)
      await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)
      await bellLoaded(page)
      await freshnessLoaded(page)
    },
  },
  {
    name: 'view-admin-posts',
    risk: 'część „Nieudane posty” panelu admina nie zmienia wyglądu i nie łamie dostępności',
    path: '/admin/posts',
    modes: { AUTH: 'admin' },
    ready: async (page) => {
      const main = page.getByRole('main')
      const failed = main.getByRole('region', { name: 'Nieudane posty', exact: true })
      await expect(failed.getByRole('table')).toBeVisible()
      await expect(failed.getByRole('checkbox')).toHaveCount(4)
      await adminPartsLoaded(page)
      await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)
      await bellLoaded(page)
      await freshnessLoaded(page)
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
      await freshnessLoaded(page)
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

/**
 * A part of the admin panel that is not the current one, in the bar whose sample has the count (the first variant of
 * „Części panelu admina”): both bars of the section share their link names, so the bar is found by its title.
 */
function adminPartLink(name: string): (region: Locator) => Locator {
  return (region) =>
    region
      .getByRole('listitem')
      .filter({ hasText: 'Bieżące Przebiegi, licznik nieudanych postów' })
      .getByRole('navigation', { name: 'Panel admina', exact: true })
      .getByRole('link', { name, exact: true })
}

/**
 * A cell of a run's row in the runs table of „Tabela”, in its second column, which is in the frame at 375 px: the
 * pointer over it is over the row, and the frame does not scroll sideways to bring it into view.
 */
function runRowCell(region: Locator): Locator {
  return region
    .getByRole('table', { name: 'Ostatnie przebiegi, od najnowszego', exact: true })
    .getByRole('cell', { name: 'ponowienie resztek', exact: true })
}

// The catalogue gives each control of a state its own accessible name within its region (no `nth`): the primary and the
// plain button, the icon-only one, the link, the e-mail and search fields, the theme toggle and (below) the bar's link,
// the admin panel's parts and a cell of a table's row.
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
  {
    name: 'state-czesci-panelu-admina-hover',
    risk: 'hover części panelu admina nie zmienia wyglądu',
    region: 'Części panelu admina',
    control: adminPartLink('Jakość ocen'),
    state: 'hover',
  },
  {
    name: 'state-czesci-panelu-admina-focus',
    risk: 'pierścień fokusu części panelu admina z licznikiem nie zmienia wyglądu',
    region: 'Części panelu admina',
    control: adminPartLink('Nieudane posty 7'),
    state: 'focus',
  },
  {
    name: 'state-tabela-wiersz-hover',
    risk: 'hover wiersza tabeli nie zmienia wyglądu',
    region: 'Tabela',
    control: runRowCell,
    state: 'hover',
  },
]
