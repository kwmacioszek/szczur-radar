// The flows of the shared view states (S-48, ui-design.md "Kontrakt dla testów E2E": state-loading, state-empty,
// state-error, admin-loading, admin-empty, admin-error and the pager of posts) that feed.spec.ts does not cover: the
// list stays while the next page loads, an error of a page or a filter is retried with the same request, one page has
// no pager, and each section of the admin panel's parts has its own skeleton, error with its own retry, and empty
// state.
import { FIXED_NOW } from '../src/contract/mockModes.ts'
import type { Page } from '@playwright/test'
import { expect, test } from './fixtures.ts'

// dev/mock-api.js: 120 feed posts in pages of 50; 20 of them match "orlen", all on one page (feed.spec.ts).
const PAGE_SIZE = 50
const ORLEN_POSTS = 20
// The admin panel's parts (S-50) with their sections.
const PARTS = [
  { path: '/admin', sections: ['Nowy przebieg', 'Dni bez danych', 'Ostatnie przebiegi'] },
  { path: '/admin/quality', sections: ['Jakość ocen (7 dni)'] },
  { path: '/admin/posts', sections: ['Nieudane posty'] },
]
const RUNS_SECTIONS = PARTS[0]!.sections

const isPostsRequest = (url: string) => new URL(url).pathname === '/api/posts'

test.describe('lista postów', () => {
  test.describe('wczytywanie kolejnej strony', () => {
    test.use({ modes: { MODE: 'loading-next' } })

    test('kolejna strona wczytuje się z listą na ekranie', async ({ page }) => {
      await page.goto('/')
      const main = page.getByRole('main')
      const pager = main.getByRole('navigation', { name: 'Strony', exact: true })
      await expect(pager.getByText('Strona 1 z 3', { exact: true })).toBeVisible()

      await pager.getByRole('button', { name: 'Starsze' }).click()

      await expect(page).toHaveURL('/?page=1')
      await expect(pager.getByRole('button', { name: 'Wczytywanie…', exact: true })).toHaveAttribute('aria-busy', 'true')
      // The old page stays on the screen, not a skeleton, until the next one comes (it hangs in this mode).
      await expect(main.getByRole('article')).toHaveCount(PAGE_SIZE)
      await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(1)
    })
  })

  test.describe('błąd kolejnej strony', () => {
    test.use({ modes: { MODE: 'error-next' } })

    test('błąd kolejnej strony pozwala ją ponowić', async ({ page }) => {
      await page.goto('/')
      const main = page.getByRole('main')
      await main.getByRole('navigation', { name: 'Strony', exact: true }).getByRole('button', { name: 'Starsze' }).click()

      const title = main.getByRole('alert').getByRole('heading', { level: 2, name: 'Nie udało się wczytać postów', exact: true })
      await expect(title).toBeVisible()
      await expect(main.getByRole('heading', { level: 1 })).toBeFocused()

      // The retry asks for the same page, and the alert below is the one its answer rendered.
      const retried = page.waitForResponse((response) =>
        response.request().method() === 'GET' && isPostsRequest(response.url())
        && new URL(response.url()).searchParams.get('page') === '1' && response.status() === 500)
      await main.getByRole('button', { name: 'Spróbuj ponownie' }).click()
      await retried

      await expect(title).toBeVisible()
      await expect(page).toHaveURL('/?page=1')
    })
  })

  test.describe('błąd listy', () => {
    test.use({ modes: { MODE: 'error' } })

    test('błąd listy ponawia ten sam filtr', async ({ page }) => {
      await page.goto('/?q=orlen')
      const main = page.getByRole('main')
      const alert = main.getByRole('alert')
      await expect(alert.getByRole('heading', { level: 2, name: 'Nie udało się wczytać postów', exact: true })).toBeVisible()
      await expect(alert.getByText('Serwer nie odpowiedział. Twoje filtry zostały.', { exact: true })).toBeVisible()

      const retried = page.waitForRequest((request) =>
        request.method() === 'GET' && isPostsRequest(request.url())
        && new URL(request.url()).searchParams.get('q') === 'orlen')
      await main.getByRole('button', { name: 'Spróbuj ponownie' }).click()
      await retried

      await expect(alert.getByRole('heading', { level: 2, name: 'Nie udało się wczytać postów', exact: true })).toBeVisible()
      await expect(page).toHaveURL('/?q=orlen')
      await expect(main.getByLabel('Spółka lub temat', { exact: true })).toHaveValue('orlen')
    })
  })

  test('lista mieszcząca się na jednej stronie nie ma stronicowania', async ({ page }) => {
    await page.goto('/?q=orlen')
    const main = page.getByRole('main')
    await expect(main.getByRole('status')).toHaveText(`Pasujące do filtra: ${ORLEN_POSTS}`)
    await expect(main.getByRole('article')).toHaveCount(ORLEN_POSTS)

    await expect(main.getByRole('navigation', { name: 'Strony', exact: true })).toHaveCount(0)
  })
})

test.describe('panel admina', () => {
  test.describe('ładowanie', () => {
    test.use({ modes: { MODE: 'loading' } })

    test('każda sekcja przebiegów pokazuje szkielet do czasu swoich danych', async ({ page }) => {
      await page.goto('/admin')
      const main = page.getByRole('main')

      for (const name of RUNS_SECTIONS) {
        const section = main.getByRole('region', { name, exact: true })
        await expect(section).toHaveAttribute('aria-busy', 'true')
        await expect(section.getByText('Wczytywanie…', { exact: true })).toBeVisible()
      }
      // Nothing to refresh before the first answer.
      await expect(main.getByRole('region', { name: 'Ostatnie przebiegi', exact: true })
        .getByRole('button', { name: 'Odśwież', exact: true })).toBeDisabled()
    })
  })

  // The bell reads /api/admin/notifications on its own, so it is not counted.
  function adminRequests(page: Page): string[] {
    const requests: string[] = []
    page.on('request', (request) => {
      const path = new URL(request.url()).pathname
      if (path.startsWith('/api/admin/') && !path.startsWith('/api/admin/notifications')) {
        requests.push(`${request.method()} ${path}`)
      }
    })
    return requests
  }

  test.describe('błąd jednej części', () => {
    test.use({ modes: { QUALITY: 'error' } })

    test('błąd jednej części nie psuje pozostałych, a jej ponowienie wczytuje tylko ją', async ({ page }) => {
      await page.goto('/admin/quality')
      const main = page.getByRole('main')
      const region = (name: string) => main.getByRole('region', { name, exact: true })
      const quality = region('Jakość ocen (7 dni)')

      await expect(quality.getByRole('alert')).toContainText('Nie udało się wczytać jakości ocen')
      await expect(main.getByRole('alert')).toHaveCount(1)
      // The bar's count of failed posts is there before the retry, so its read is not counted.
      const parts = main.getByRole('navigation', { name: 'Panel admina', exact: true })
      await expect(parts.getByRole('link', { name: 'Nieudane posty 4', exact: true })).toBeVisible()

      const requests = adminRequests(page)
      const retried = page.waitForResponse((response) =>
        new URL(response.url()).pathname === '/api/admin/assessment-quality' && response.status() === 500)
      await quality.getByRole('button', { name: 'Spróbuj ponownie' }).click()
      await retried

      await expect(quality.getByRole('alert')).toContainText('Nie udało się wczytać jakości ocen')
      expect(requests).toEqual(['GET /api/admin/assessment-quality'])

      // The other parts work: "Przebiegi" has its data.
      await parts.getByRole('link', { name: 'Przebiegi', exact: true }).click()

      await expect(page).toHaveURL('/admin')
      await expect(region('Nowy przebieg').getByRole('checkbox')).toHaveCount(2)
      await expect(region('Dni bez danych').getByRole('button', { name: /^Wpisz do formularza: / }).first()).toBeVisible()
      await expect(region('Ostatnie przebiegi').getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
      await expect(main.getByRole('alert')).toHaveCount(0)
    })
  })

  test.describe('błąd jednej sekcji', () => {
    test.use({ modes: { MISSING: 'error' } })

    test('błąd jednej sekcji nie psuje pozostałych, a jej ponowienie wczytuje tylko ją', async ({ page }) => {
      // Timers stand still, Date stays at FIXED_NOW: the runs' poll every 10 s (run 1 is running) never falls into
      // the counted window.
      await page.clock.install({ time: FIXED_NOW })
      await page.clock.pauseAt(FIXED_NOW)
      await page.goto('/admin')
      const main = page.getByRole('main')
      const region = (name: string) => main.getByRole('region', { name, exact: true })
      const missing = region('Dni bez danych')

      await expect(missing.getByRole('alert')).toContainText('Nie udało się wczytać dni bez danych')
      await expect(main.getByRole('alert')).toHaveCount(1)
      // The other sections have their data.
      await expect(region('Nowy przebieg').getByRole('checkbox')).toHaveCount(2)
      await expect(region('Ostatnie przebiegi').getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
      await expect(main.getByRole('navigation', { name: 'Panel admina', exact: true })
        .getByRole('link', { name: 'Nieudane posty 4', exact: true })).toBeVisible()

      const requests = adminRequests(page)
      const retried = page.waitForResponse((response) =>
        new URL(response.url()).pathname === '/api/admin/pipeline-runs/missing-days' && response.status() === 500)
      await missing.getByRole('button', { name: 'Spróbuj ponownie' }).click()
      await retried

      await expect(missing.getByRole('alert')).toContainText('Nie udało się wczytać dni bez danych')
      await expect(region('Ostatnie przebiegi').getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
      expect(requests).toEqual(['GET /api/admin/pipeline-runs/missing-days'])
    })
  })

  test.describe('błąd całego panelu', () => {
    test.use({ modes: { MODE: 'error' } })

    test('błąd całego panelu daje ponowienie w każdej sekcji każdej części', async ({ page }) => {
      for (const part of PARTS) {
        await page.goto(part.path)
        const main = page.getByRole('main')

        for (const name of part.sections) {
          const section = main.getByRole('region', { name, exact: true })
          await expect(section.getByRole('alert')).toHaveCount(1)
          await expect(section.getByRole('alert').getByRole('button', { name: 'Spróbuj ponownie' })).toBeVisible()
        }
      }
    })
  })

  test.describe('pusto', () => {
    test.use({ modes: { MODE: 'empty' } })

    test('brak nieudanych postów to pusty stan bez listy', async ({ page }) => {
      await page.goto('/admin/posts')
      const main = page.getByRole('main')
      const failed = main.getByRole('region', { name: 'Nieudane posty', exact: true })

      await expect(failed.getByText('Brak nieudanych postów', { exact: true })).toBeVisible()
      await expect(failed.getByRole('checkbox')).toHaveCount(0)
    })

    test('brak przebiegów to pusty stan listy przebiegów', async ({ page }) => {
      await page.goto('/admin')
      await expect(page.getByRole('main').getByRole('region', { name: 'Ostatnie przebiegi', exact: true })
        .getByText('Jeszcze nie było przebiegu', { exact: true })).toBeVisible()
    })
  })
})
