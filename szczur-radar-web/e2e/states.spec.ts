// The flows of the shared view states (S-48, ui-design.md "Kontrakt dla testów E2E": state-loading, state-empty,
// state-error, admin-loading, admin-empty, admin-error and the pager of posts) that feed.spec.ts does not cover: the
// list stays while the next page loads, an error of a page or a filter is retried with the same request, one page has
// no pager, and each section of /admin has its own skeleton, error with its own retry, and empty state.
import { FIXED_NOW } from '../src/contract/mockModes.ts'
import { expect, test } from './fixtures.ts'

// dev/mock-api.js: 120 feed posts in pages of 50; 20 of them match "orlen", all on one page (feed.spec.ts).
const PAGE_SIZE = 50
const ORLEN_POSTS = 20
const SECTIONS = ['Nowy przebieg', 'Dni bez danych', 'Jakość ocen (7 dni)', 'Ostatnie przebiegi', 'Nieudane posty']

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

    test('każda sekcja panelu pokazuje szkielet do czasu swoich danych', async ({ page }) => {
      await page.goto('/admin')
      const main = page.getByRole('main')

      for (const name of SECTIONS) {
        const section = main.getByRole('region', { name, exact: true })
        await expect(section).toHaveAttribute('aria-busy', 'true')
        await expect(section.getByText('Wczytywanie…', { exact: true })).toBeVisible()
      }
      // Nothing to refresh before the first answer.
      await expect(main.getByRole('region', { name: 'Ostatnie przebiegi', exact: true })
        .getByRole('button', { name: 'Odśwież', exact: true })).toBeDisabled()
    })
  })

  test.describe('błąd jednej sekcji', () => {
    test.use({ modes: { QUALITY: 'error' } })

    test('błąd jednej sekcji nie psuje pozostałych, a jej ponowienie wczytuje tylko ją', async ({ page }) => {
      // Timers stand still, Date stays at FIXED_NOW: the runs' poll every 10 s (run 1 is running) never falls into
      // the counted window.
      await page.clock.install({ time: FIXED_NOW })
      await page.clock.pauseAt(FIXED_NOW)
      await page.goto('/admin')
      const main = page.getByRole('main')
      const region = (name: string) => main.getByRole('region', { name, exact: true })
      const quality = region('Jakość ocen (7 dni)')

      await expect(quality.getByRole('alert')).toContainText('Nie udało się wczytać jakości ocen')
      await expect(main.getByRole('alert')).toHaveCount(1)
      // The other sections have their data.
      await expect(region('Nowy przebieg').getByRole('checkbox')).toHaveCount(2)
      await expect(region('Dni bez danych').getByRole('button', { name: /^Wpisz do formularza: / }).first()).toBeVisible()
      await expect(region('Ostatnie przebiegi').getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
      await expect(region('Nieudane posty').getByRole('checkbox')).toHaveCount(4)

      // The bell reads /api/admin/notifications on its own, so it is not counted.
      const requests: string[] = []
      page.on('request', (request) => {
        const path = new URL(request.url()).pathname
        if (path.startsWith('/api/admin/') && !path.startsWith('/api/admin/notifications')) {
          requests.push(`${request.method()} ${path}`)
        }
      })
      const retried = page.waitForResponse((response) =>
        new URL(response.url()).pathname === '/api/admin/assessment-quality' && response.status() === 500)
      await quality.getByRole('button', { name: 'Spróbuj ponownie' }).click()
      await retried

      await expect(quality.getByRole('alert')).toContainText('Nie udało się wczytać jakości ocen')
      await expect(region('Ostatnie przebiegi').getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
      expect(requests).toEqual(['GET /api/admin/assessment-quality'])
    })
  })

  test.describe('błąd całego panelu', () => {
    test.use({ modes: { MODE: 'error' } })

    test('błąd całego panelu daje ponowienie w każdej sekcji', async ({ page }) => {
      await page.goto('/admin')
      const main = page.getByRole('main')

      for (const name of SECTIONS) {
        const section = main.getByRole('region', { name, exact: true })
        await expect(section.getByRole('alert')).toHaveCount(1)
        await expect(section.getByRole('alert').getByRole('button', { name: 'Spróbuj ponownie' })).toBeVisible()
      }
    })
  })

  test.describe('pusto', () => {
    test.use({ modes: { MODE: 'empty' } })

    test('brak nieudanych postów to pusty stan bez listy', async ({ page }) => {
      await page.goto('/admin')
      const main = page.getByRole('main')
      const failed = main.getByRole('region', { name: 'Nieudane posty', exact: true })

      await expect(failed.getByText('Brak nieudanych postów', { exact: true })).toBeVisible()
      await expect(failed.getByRole('checkbox')).toHaveCount(0)
      await expect(main.getByRole('region', { name: 'Ostatnie przebiegi', exact: true })
        .getByText('Jeszcze nie było przebiegu', { exact: true })).toBeVisible()
    })
  })
})
