// What the visual tests screenshot (F-06), as data: the projects (theme × width), the views with their mock modes and
// the moment each one is ready. views.spec.ts screenshots them, and the baselines guard (phase 2) derives the
// expected files under visual/__screenshots__/<project>/ from the same tables. playwright.visual.config.ts builds its
// projects and its dev server from here.
import { expect, type Page } from '@playwright/test'
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

/** A hover or focus state of a shared control on /kitchen-sink (phase 2): its baseline is its region's screenshot. */
export interface State {
  name: string
}

export const STATES: readonly State[] = []
