// Smoke of the admin's pages (F-05): the panel's three parts (/admin, /admin/quality, /admin/posts) with their
// sections, starting a run, a run's page and the notification bell — what S-50 rebuilds. The session is the mock's
// default, AUTH 'admin'.
import { expect, test } from './fixtures.ts'

const RUN_1 = '/admin/runs/00000000-0000-4000-8000-000100000001'
// Notification 1, the newest, is about run 3 (dev/mock-api.js).
const RUN_3 = '/admin/runs/00000000-0000-4000-8000-000100000003'

test('panel admina wczytuje każdą część z paska części', async ({ page }) => {
  await page.goto('/admin')
  const main = page.getByRole('main')
  const parts = main.getByRole('navigation', { name: 'Panel admina', exact: true })
  const part = (name: string | RegExp) => parts.getByRole('link', { name, exact: typeof name === 'string' })
  const region = (name: string) => main.getByRole('region', { name, exact: true })
  const sections = async (names: string[]) => {
    for (const name of names) await expect(main.getByRole('heading', { level: 2, name, exact: true })).toBeVisible()
    await expect(main.getByRole('heading', { level: 2 })).toHaveCount(names.length)
  }
  // With its count the name of the failed posts' link is „Nieudane posty 4”.
  const PARTS = ['Przebiegi', 'Jakość ocen', /^Nieudane posty/]
  /** Only the part at `at` of PARTS is the current one. */
  const current = async (at: number) => {
    for (const [index, name] of PARTS.entries()) {
      if (index === at) await expect(part(name)).toHaveAttribute('aria-current', 'page')
      else await expect(part(name)).not.toHaveAttribute('aria-current', 'page')
    }
  }
  await expect(main.getByRole('heading', { level: 1, name: 'Panel admina' })).toBeVisible()
  // The bar counts the mock's 4 failed posts.
  await expect(part('Nieudane posty 4')).toBeVisible()

  await sections(['Nowy przebieg', 'Dni bez danych', 'Ostatnie przebiegi'])
  await current(0)
  await expect(region('Nowy przebieg').getByRole('checkbox')).toHaveCount(2)
  await expect(region('Dni bez danych').getByRole('button', { name: /^Wpisz do formularza: / }).first()).toBeVisible()
  const runs = region('Ostatnie przebiegi')
  await expect(runs.getByRole('table')).toBeVisible()
  await expect(runs.getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
  await expect(runs.getByRole('button', { name: 'Pokaż starsze' })).toBeVisible()
  await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)

  await part('Jakość ocen').click()

  await expect(page).toHaveURL('/admin/quality')
  await sections(['Jakość ocen (7 dni)'])
  await current(1)
  const quality = region('Jakość ocen (7 dni)')
  await expect(quality.getByRole('heading', { level: 3 })).toHaveText(['Wykop', 'Reddit'])
  await expect(quality.getByText('82 z 3134 prób · próg 5%', { exact: true })).toBeVisible()
  await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)
  // The layout stays: the focus stays on the clicked link of the bar.
  await expect(part('Jakość ocen')).toBeFocused()

  await part(/^Nieudane posty/).click()

  await expect(page).toHaveURL('/admin/posts')
  await sections(['Nieudane posty'])
  await current(2)
  await expect(region('Nieudane posty').getByRole('table')).toBeVisible()
  await expect(region('Nieudane posty').getByRole('checkbox')).toHaveCount(4)
  await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)

  await part('Przebiegi').click()

  await expect(page).toHaveURL('/admin')
  await sections(['Nowy przebieg', 'Dni bez danych', 'Ostatnie przebiegi'])
  await current(0)
})

test('wejście do panelu stawia fokus na jego nagłówku', async ({ page }) => {
  await page.goto('/admin/posts')
  const main = page.getByRole('main')
  await expect(main.getByRole('region', { name: 'Nieudane posty', exact: true }).getByRole('checkbox')).toHaveCount(4)

  await expect(main.getByRole('heading', { level: 1, name: 'Panel admina' })).toBeFocused()
})

test('uruchomienie przebiegu dodaje go na górę listy', async ({ page }) => {
  await page.goto('/admin')
  const form = page.getByRole('main').getByRole('region', { name: 'Nowy przebieg', exact: true })
  // Yesterday of the fixed clock (2026-10-20 in Warsaw).
  await expect(form.getByLabel('Od', { exact: true })).toHaveValue('2026-10-19')
  await expect(form.getByLabel('Do', { exact: true })).toHaveValue('2026-10-19')
  const runs = page.getByRole('main').getByRole('region', { name: 'Ostatnie przebiegi', exact: true })
  // The first row of the table is its header.
  await expect(runs.getByRole('row').nth(1).getByText('trwa', { exact: true })).toBeVisible()

  await form.getByRole('button', { name: 'Uruchom' }).click()

  await expect(form.getByRole('status')).toHaveText(/^Uruchomiono:/)
  await expect(runs.getByRole('row').nth(1).getByText('w kolejce', { exact: true })).toBeVisible()
})

test.describe('zajęty pipeline', () => {
  test.use({ modes: { START: 'busy' } })

  test('zajęty pipeline odrzuca start z komunikatem', async ({ page }) => {
    await page.goto('/admin')
    const form = page.getByRole('main').getByRole('region', { name: 'Nowy przebieg', exact: true })

    await form.getByRole('button', { name: 'Uruchom' }).click()

    await expect(form.getByRole('alert')).toHaveText('Przebieg już trwa. Poczekaj, aż się zakończy, i spróbuj ponownie.')
  })
})

test('szczegóły przebiegu otwierają jego stronę', async ({ page }) => {
  await page.goto('/admin')
  const runs = page.getByRole('main').getByRole('region', { name: 'Ostatnie przebiegi', exact: true })

  await runs.getByRole('link', { name: /^Szczegóły przebiegu z / }).first().click()

  await expect(page).toHaveURL(RUN_1)
  const main = page.getByRole('main')
  await expect(main.getByRole('heading', { level: 1, name: 'Przebieg: zakres dat' })).toBeVisible()
  await expect(main.getByRole('heading', { level: 2, name: 'Postęp' })).toBeVisible()
})

test('powiadomienie z dzwonka otwiera przebieg i zmniejsza licznik', async ({ page }) => {
  await page.goto('/admin')
  const banner = page.getByRole('banner')

  await banner.getByRole('button', { name: 'Powiadomienia, 5 nieprzeczytanych', exact: true }).click()
  await banner.getByRole('region', { name: 'Powiadomienia', exact: true }).getByRole('link').first().click()

  await expect(page).toHaveURL(RUN_3)
  await expect(banner.getByRole('button', { name: 'Powiadomienia, 4 nieprzeczytane', exact: true })).toBeVisible()
})
