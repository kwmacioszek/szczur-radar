// Smoke of the admin's pages (F-05): /admin with its sections, starting a run, a run's page and the notification
// bell — what S-50 rebuilds. The session is the mock's default, AUTH 'admin'.
import { expect, test } from './fixtures.ts'

const RUN_1 = '/admin/runs/00000000-0000-4000-8000-000100000001'
// Notification 1, the newest, is about run 3 (dev/mock-api.js).
const RUN_3 = '/admin/runs/00000000-0000-4000-8000-000100000003'

test('panel admina wczytuje wszystkie sekcje', async ({ page }) => {
  await page.goto('/admin')
  const main = page.getByRole('main')
  for (const name of ['Nowy przebieg', 'Dni bez danych', 'Jakość ocen (7 dni)', 'Ostatnie przebiegi', 'Nieudane posty']) {
    await expect(main.getByRole('heading', { level: 2, name, exact: true })).toBeVisible()
  }
  const region = (name: string) => main.getByRole('region', { name, exact: true })

  await expect(region('Nowy przebieg').getByRole('checkbox')).toHaveCount(2)
  await expect(region('Dni bez danych').getByRole('button', { name: /^Wpisz do formularza: / }).first()).toBeVisible()
  await expect(region('Jakość ocen (7 dni)').getByText(/^nieudane .* z 3134 prób/)).toBeVisible()
  const runs = region('Ostatnie przebiegi')
  await expect(runs.getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
  await expect(runs.getByRole('button', { name: 'Pokaż starsze' })).toBeVisible()
  await expect(region('Nieudane posty').getByRole('checkbox')).toHaveCount(4)
  // Every section shows its data: none is still loading.
  await expect(main.getByText('Wczytywanie…', { exact: true })).toHaveCount(0)
})

test('uruchomienie przebiegu dodaje go na górę listy', async ({ page }) => {
  await page.goto('/admin')
  const form = page.getByRole('main').getByRole('region', { name: 'Nowy przebieg', exact: true })
  // Yesterday of the fixed clock (2026-10-20 in Warsaw).
  await expect(form.getByLabel('Od', { exact: true })).toHaveValue('2026-10-19')
  await expect(form.getByLabel('Do', { exact: true })).toHaveValue('2026-10-19')
  const runs = page.getByRole('main').getByRole('region', { name: 'Ostatnie przebiegi', exact: true })
  await expect(runs.getByRole('listitem').first().getByText('trwa', { exact: true })).toBeVisible()

  await form.getByRole('button', { name: 'Uruchom' }).click()

  await expect(form.getByRole('status')).toHaveText(/^Uruchomiono:/)
  await expect(runs.getByRole('listitem').first().getByText('w kolejce', { exact: true })).toBeVisible()
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
