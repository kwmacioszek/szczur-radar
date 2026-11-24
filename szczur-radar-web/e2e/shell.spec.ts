// The app shell (S-47): the bar every logged-in page shares — the main navigation with the page's section marked, the
// date of the data per source and the one banner, main and heading 1 of each page. Signing out, the theme, a user
// without "Panel admina" and the bell have their tests in auth.spec.ts, theme.spec.ts and admin.spec.ts.
import { expect, test } from './fixtures.ts'

const POST_0 = '/posts/00000000-0000-4000-8000-000400000000'
const RUN_1 = '/admin/runs/00000000-0000-4000-8000-000100000001'

test('pozycja nawigacji otwiera swój ekran i przenosi zaznaczenie', async ({ page }) => {
  await page.goto('/')
  const nav = page.getByRole('banner').getByRole('navigation', { name: 'Główna', exact: true })
  const main = page.getByRole('main')
  const posts = nav.getByRole('link', { name: 'Posty', exact: true })
  const admin = nav.getByRole('link', { name: 'Panel admina', exact: true })
  await expect(main.getByRole('heading', { level: 1, name: 'Posty' })).toBeVisible()
  await expect(posts).toHaveAttribute('aria-current', 'page')
  await expect(admin).not.toHaveAttribute('aria-current', 'page')

  await admin.click()

  await expect(page).toHaveURL('/admin')
  await expect(main.getByRole('heading', { level: 1, name: 'Panel admina' })).toBeVisible()
  await expect(admin).toHaveAttribute('aria-current', 'page')
  await expect(posts).not.toHaveAttribute('aria-current', 'page')

  // A run's page is still the admin's section, though its address is not the item's.
  await main
    .getByRole('region', { name: 'Ostatnie przebiegi', exact: true })
    .getByRole('link', { name: /^Szczegóły przebiegu z / })
    .first()
    .click()

  await expect(page).toHaveURL(RUN_1)
  await expect(main.getByRole('heading', { level: 1, name: 'Przebieg: zakres dat' })).toBeVisible()
  await expect(admin).toHaveAttribute('aria-current', 'page')
  await expect(posts).not.toHaveAttribute('aria-current', 'page')

  await posts.click()

  await expect(page).toHaveURL('/')
  await expect(main.getByRole('heading', { level: 1, name: 'Posty' })).toBeVisible()
  await expect(posts).toHaveAttribute('aria-current', 'page')
  await expect(admin).not.toHaveAttribute('aria-current', 'page')

  // A post's page is still the posts' section.
  await main.getByRole('article').first().getByRole('link').click()

  await expect(page).toHaveURL(POST_0)
  await expect(main.getByRole('heading', { level: 1, name: 'Wpis użytkownika uzytkownik0' })).toBeVisible()
  await expect(posts).toHaveAttribute('aria-current', 'page')
  await expect(admin).not.toHaveAttribute('aria-current', 'page')
})

test.describe('każdy ekran po zalogowaniu ma jeden pasek, jedną treść i jeden nagłówek 1', () => {
  const SCREENS = [
    { name: 'lista postów', path: '/', title: 'Posty', section: 'Posty' },
    { name: 'post', path: POST_0, title: 'Wpis użytkownika uzytkownik0', section: 'Posty' },
    { name: 'panel admina', path: '/admin', title: 'Panel admina', section: 'Panel admina' },
    { name: 'jakość ocen', path: '/admin/quality', title: 'Panel admina', section: 'Panel admina' },
    { name: 'nieudane posty', path: '/admin/posts', title: 'Panel admina', section: 'Panel admina' },
    { name: 'przebieg', path: RUN_1, title: 'Przebieg: zakres dat', section: 'Panel admina' },
  ]

  for (const screen of SCREENS) {
    test(screen.name, async ({ page }) => {
      await page.goto(screen.path)

      await expect(page.getByRole('heading', { level: 1 })).toHaveText(screen.title)
      await expect(page.getByRole('banner')).toHaveCount(1)
      await expect(page.getByRole('main')).toHaveCount(1)
      await expect(page.getByRole('navigation', { name: 'Główna', exact: true })).toHaveCount(1)
      await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1)
      // Exactly one item of the navigation is the current page: the screen's section.
      const nav = page.getByRole('banner').getByRole('navigation', { name: 'Główna', exact: true })
      for (const item of ['Posty', 'Panel admina']) {
        const link = nav.getByRole('link', { name: item, exact: true })
        if (item === screen.section) await expect(link).toHaveAttribute('aria-current', 'page')
        else await expect(link).not.toHaveAttribute('aria-current', 'page')
      }
    })
  }
})

test.describe('data danych', () => {
  test.describe('z dwóch dni', () => {
    test.use({ modes: { FRESHNESS: 'apart' } })

    test('data danych pokazuje dzień i godzinę każdego źródła', async ({ page }) => {
      await page.goto('/')
      const banner = page.getByRole('banner')

      await expect(banner.getByText('Dane:', { exact: true })).toBeVisible()
      const wykop = banner.getByText('Wykop 13.10.2026, 05:12', { exact: true })
      const reddit = banner.getByText('Reddit 11.10.2026, 05:12', { exact: true })
      await expect(wykop).toBeVisible()
      await expect(reddit).toBeVisible()
      await expect(wykop.getByRole('time')).toHaveAttribute('datetime', '2026-10-13T03:12:00Z')
      await expect(reddit.getByRole('time')).toHaveAttribute('datetime', '2026-10-11T03:12:00Z')
    })
  })

  test.describe('bez aktualizacji', () => {
    test.use({ modes: { FRESHNESS: 'none' } })

    test('bez aktualizacji pasek mówi, że danych jeszcze nie ma', async ({ page }) => {
      await page.goto('/')
      const banner = page.getByRole('banner')

      await expect(banner.getByText('Dane nie były jeszcze aktualizowane.', { exact: true })).toBeVisible()
      await expect(banner.getByText('Dane:', { exact: true })).toHaveCount(0)
    })
  })

  test.describe('z błędem odczytu', () => {
    test.use({ modes: { FRESHNESS: 'error' } })

    test('błąd daty danych nie psuje strony', async ({ page }) => {
      await page.goto('/')
      const main = page.getByRole('main')

      await expect(main.getByRole('status')).toHaveText('Ocenione posty: 120')
      const banner = page.getByRole('banner')
      await expect(banner.getByText('Dane:', { exact: true })).toHaveCount(0)
      await expect(banner.getByText('Dane nie były jeszcze aktualizowane.', { exact: true })).toHaveCount(0)
      await expect(banner.getByRole('button', { name: 'Wyloguj', exact: true })).toBeVisible()
    })
  })

  test.describe('na wąskim ekranie', () => {
    test.use({ viewport: { width: 375, height: 812 } })

    test('na wąskim ekranie data danych zostaje w pasku', async ({ page }) => {
      await page.goto('/')
      const banner = page.getByRole('banner')

      await expect(banner.getByText('Dane:', { exact: true })).toBeVisible()
      await expect(banner.getByText('admin@szczur-radar.pl', { exact: true })).toBeHidden()
      await expect(banner.getByRole('button', { name: 'Wyloguj', exact: true })).toBeVisible()
    })

    test('pasek z datą danych nie poszerza strony na wąskim ekranie', async ({ page }) => {
      await page.goto('/')
      await expect(page.getByRole('banner').getByText('Dane:', { exact: true })).toBeVisible()

      expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375)
    })

    test.describe('bez żadnej aktualizacji', () => {
      test.use({ modes: { FRESHNESS: 'none' } })

      test('pasek z napisem o braku aktualizacji nie poszerza strony na wąskim ekranie', async ({ page }) => {
        await page.goto('/')
        await expect(page.getByRole('banner').getByText('Dane nie były jeszcze aktualizowane.')).toBeVisible()

        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375)
      })
    })
  })
})

test.describe('strony publiczne', () => {
  test.use({ modes: { AUTH: 'anonymous' } })

  test('strony publiczne nie mają paska', async ({ page }) => {
    await page.goto('/login')

    await expect(page.getByRole('main').getByRole('heading', { level: 1, name: 'Logowanie' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Główna', exact: true })).toHaveCount(0)
    await expect(page.getByRole('banner')).toHaveCount(0)
  })
})
