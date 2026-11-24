// Smoke of the admin's pages (F-05): the panel's three parts (/admin, /admin/quality, /admin/posts) with their
// sections, starting a run, a run's page and the notification bell — what S-50 rebuilds — and the flows of its parts
// (ui-design.md "Kontrakt dla testów E2E": admin-runs, admin-quality, admin-posts, admin-empty, run). The session is
// the mock's default, AUTH 'admin'.
import type { Page } from '@playwright/test'
import { expect, test } from './fixtures.ts'

const RUN_1 = '/admin/runs/00000000-0000-4000-8000-000100000001'
// Run 1 is created 12 minutes before the stopped clock (FIXED_NOW, 10:00 UTC): 11:48 in Warsaw, the time its page
// is named by.
const RUN_1_TITLE = 'Przebieg z 20.10.2026, 11:48'
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
  await expect(main.getByRole('heading', { level: 1, name: RUN_1_TITLE })).toBeVisible()
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

test.describe('części panelu', () => {
  const region = (page: Page, name: string) => page.getByRole('main').getByRole('region', { name, exact: true })
  const parts = (page: Page) => page.getByRole('main').getByRole('navigation', { name: 'Panel admina', exact: true })

  test('dzień bez danych wpisuje swój zakres do formularza i stawia fokus na „Od”', async ({ page }) => {
    await page.goto('/admin')
    const form = region(page, 'Nowy przebieg')
    const fromField = form.getByLabel('Od', { exact: true })
    // The form starts with yesterday of the fixed clock (2026-10-20 in Warsaw); the earliest day without data is
    // D-9, 2026-10-11 (dev/mock-api.js, MISSING 'gaps').
    await expect(fromField).toHaveValue('2026-10-19')

    await region(page, 'Dni bez danych').getByRole('button', { name: /^Wpisz do formularza: / }).first().click()

    await expect(fromField).toHaveValue('2026-10-11')
    await expect(form.getByLabel('Do', { exact: true })).toHaveValue('2026-10-11')
    await expect(fromField).toBeFocused()
  })

  test('„Pokaż starsze” dokłada starsze przebiegi do tabeli', async ({ page }) => {
    await page.goto('/admin')
    const runs = region(page, 'Ostatnie przebiegi')
    const details = runs.getByRole('link', { name: /^Szczegóły przebiegu z / })
    // The mock has 32 runs: a page of 20, then the 12 older ones.
    await expect(details).toHaveCount(20)

    await runs.getByRole('button', { name: 'Pokaż starsze' }).click()

    await expect(details).toHaveCount(32)
    await expect(runs.getByRole('button', { name: 'Pokaż starsze' })).toHaveCount(0)
  })

  // dev/mock-api.js QUALITY 'data' and 'few': each measure's value, its counts with the threshold and its state.
  const QUALITY_CASES = [
    {
      mode: 'data' as const,
      sources: [
        { name: 'Wykop', state: 'w normie', measures: [
          { value: '2,6%', counts: '82 z 3134 prób · próg 5%', state: 'w normie' },
          { value: '0,3%', counts: '11 z 3476 tez · próg 2%', state: 'w normie' },
        ] },
        { name: 'Reddit', state: 'ponad progiem', measures: [
          { value: '27,4%', counts: '164 z 598 prób · próg 25%', state: 'ponad progiem' },
          { value: '31,0%', counts: '886 z 2857 tez · próg 30%', state: 'ponad progiem' },
        ] },
      ],
    },
    {
      mode: 'few' as const,
      sources: [
        { name: 'Reddit', state: 'za mało danych', measures: [
          { value: '15,2%', counts: '7 z 46 prób · próg 25%', state: 'za mało danych' },
          { value: '17,5%', counts: '21 z 120 tez · próg 30%', state: 'za mało danych' },
        ] },
      ],
    },
  ]
  const MEASURES = ['Nieudane próby oceny', 'Tezy w innym języku niż post']
  const STATES = /^(w normie|ponad progiem|za mało danych)$/

  for (const { mode, sources } of QUALITY_CASES) {
    test.describe(`jakość ${mode}`, () => {
      test.use({ modes: { QUALITY: mode } })

      test(`każda miara jakości ma wartość, próg i stan słowami (${mode})`, async ({ page }) => {
        await page.goto('/admin/quality')
        const quality = region(page, 'Jakość ocen (7 dni)')

        for (const source of sources) {
          const card = quality.getByRole('region', { name: source.name, exact: true })
          for (const [index, shown] of source.measures.entries()) {
            await expect(card.getByText(shown.value, { exact: true })).toBeVisible()
            await expect(card.getByText(shown.counts, { exact: true })).toBeVisible()
            // The bar says the same in words.
            await expect(card.getByRole('img', { name: `${MEASURES[index]}: ${shown.value}, ${shown.counts}`, exact: true }))
              .toBeVisible()
          }
          // The source's state, then each measure's, in their order.
          await expect(card.getByText(STATES)).toHaveText([source.state, ...source.measures.map((shown) => shown.state)])
        }
      })
    })
  }

  // dev/mock-api.js FAILED 'data': posts 1 and 2 to retry, 3 dead, 4 on hold (retried too).
  const POST_2 = 'KNF ostrzega przed nowym schematem piramidy finansowej'
  const POST_3 = 'Kupiłem PKO, zobaczymy'
  const POST_4 = 'Ten wykres XTB to klasyczna głowa z ramionami, ja bym uważał.'
  const select = (page: Page, post: string) =>
    region(page, 'Nieudane posty').getByRole('checkbox', { name: `Zaznacz post: ${post}`, exact: true })

  test('zaznaczenie postów liczy osobno posty do ponowienia i martwe', async ({ page }) => {
    await page.goto('/admin/posts')
    const failed = region(page, 'Nieudane posty')
    const counts = async (retry: number, restore: number) => {
      await expect(failed.getByRole('button', { name: `Ponów zaznaczone (${retry})`, exact: true })).toBeVisible()
      await expect(failed.getByRole('button', { name: `Przywróć martwe (${restore})`, exact: true })).toBeVisible()
    }
    await counts(0, 0)

    await select(page, POST_2).check()
    await counts(1, 0)

    await select(page, POST_3).check()
    await counts(1, 1)

    await select(page, POST_4).check()
    await counts(2, 1)
  })

  test('ponowienie zaznaczonych daje odnośnik do strony nowego przebiegu', async ({ page }) => {
    await page.goto('/admin/posts')
    const failed = region(page, 'Nieudane posty')
    await select(page, POST_2).check()

    await failed.getByRole('button', { name: 'Ponów zaznaczone (1)', exact: true }).click()

    // The mock's first started run is run 1000, created at the stopped clock: 12:00 in Warsaw.
    const message = failed.getByRole('status')
    await expect(message).toContainText('Uruchomiono ponowienie: 1 post.')
    await message.getByRole('link', { name: 'Szczegóły przebiegu z 20.10.2026, 12:00', exact: true }).click()

    await expect(page).toHaveURL('/admin/runs/00000000-0000-4000-8000-000100001000')
    await expect(page.getByRole('main').getByRole('heading', { level: 1, name: 'Przebieg z 20.10.2026, 12:00' }))
      .toBeVisible()
  })

  test('przywrócenie martwego posta usuwa go z tabeli i zmniejsza licznik w pasku', async ({ page }) => {
    await page.goto('/admin/posts')
    const failed = region(page, 'Nieudane posty')
    await expect(parts(page).getByRole('link', { name: 'Nieudane posty 4', exact: true })).toBeVisible()
    await select(page, POST_3).check()

    await failed.getByRole('button', { name: 'Przywróć martwe (1)', exact: true }).click()

    await expect(failed.getByRole('status')).toHaveText('Przywrócono 1 post(ów) do oceny. Oceni je kolejny przebieg.')
    await expect(select(page, POST_3)).toHaveCount(0)
    await expect(failed.getByRole('checkbox')).toHaveCount(3)
    await expect(parts(page).getByRole('link', { name: 'Nieudane posty 3', exact: true })).toBeVisible()
  })

  test('tytuł nieudanego posta prowadzi do oryginału w nowej karcie', async ({ page }) => {
    await page.goto('/admin/posts')
    const title = region(page, 'Nieudane posty')
      .getByRole('link', { name: `${POST_2} (otwiera się w nowej karcie)`, exact: true })

    await expect(title).toHaveAttribute('href', 'https://www.reddit.com/r/stocks/comments/2/')
    await expect(title).toHaveAttribute('target', '_blank')
  })

  test.describe('pusto', () => {
    test.use({ modes: { MODE: 'empty' } })

    test('pusty stan nieudanych postów nie ma tabeli ani licznika, a prowadzi do przebiegów', async ({ page }) => {
      // The bar's count is the total of a first page of one, read when the panel opens.
      const counted = page.waitForResponse((response) => {
        const url = new URL(response.url())
        return url.pathname === '/api/admin/posts/failed' && url.searchParams.get('size') === '1'
      })
      await page.goto('/admin/posts')
      await counted
      const failed = region(page, 'Nieudane posty')

      await expect(failed.getByText('Brak nieudanych postów', { exact: true })).toBeVisible()
      await expect(failed.getByRole('table')).toHaveCount(0)
      await expect(parts(page).getByRole('link', { name: /^Nieudane posty/ })).toHaveAccessibleName('Nieudane posty')

      await failed.getByRole('link', { name: 'Pokaż przebiegi', exact: true }).click()

      await expect(page).toHaveURL('/admin')
      await expect(parts(page).getByRole('link', { name: 'Przebiegi', exact: true })).toHaveAttribute('aria-current', 'page')
      await expect(region(page, 'Ostatnie przebiegi').getByText('Jeszcze nie było przebiegu', { exact: true })).toBeVisible()
    })
  })

  test.describe('wiele stron', () => {
    test.use({ modes: { FAILED: 'many' } })

    test('druga strona nieudanych postów jest w adresie i zostaje po odświeżeniu', async ({ page }) => {
      await page.goto('/admin/posts')
      const failed = region(page, 'Nieudane posty')
      const pager = failed.getByRole('navigation', { name: 'Strony', exact: true })
      // 57 posts in pages of 50: the second page has posts 51–57, the oldest.
      await expect(pager.getByText('Strona 1 z 2', { exact: true })).toBeVisible()
      await expect(failed.getByRole('checkbox')).toHaveCount(50)

      await pager.getByRole('button', { name: 'Następne' }).click()

      const secondPage = async () => {
        await expect(page).toHaveURL('/admin/posts?page=1')
        await expect(pager.getByText('Strona 2 z 2', { exact: true })).toBeVisible()
        await expect(failed.getByRole('checkbox')).toHaveCount(7)
        await expect(select(page, 'Wpis 57: spółka po wynikach, ocena nie przeszła walidacji.')).toBeVisible()
      }
      await secondPage()

      await page.reload()

      await secondPage()
    })
  })

  test('„Panel admina” na stronie przebiegu wraca do części „Przebiegi”', async ({ page }) => {
    await page.goto(RUN_1)
    const main = page.getByRole('main')
    await expect(main.getByRole('heading', { level: 1, name: RUN_1_TITLE })).toBeVisible()

    await main.getByRole('link', { name: 'Panel admina', exact: true }).click()

    await expect(page).toHaveURL('/admin')
    await expect(parts(page).getByRole('link', { name: 'Przebiegi', exact: true })).toHaveAttribute('aria-current', 'page')
    await expect(region(page, 'Ostatnie przebiegi').getByRole('link', { name: /^Szczegóły przebiegu z / })).toHaveCount(20)
  })

  test.describe('zwykły użytkownik', () => {
    test.use({ modes: { AUTH: 'user' } })

    test('część panelu bez roli admina odbija na listę postów', async ({ page }) => {
      await page.goto('/admin/posts')

      await expect(page).toHaveURL('/')
      await expect(page.getByRole('main').getByRole('heading', { level: 1, name: 'Posty' })).toBeVisible()
      await expect(region(page, 'Nieudane posty')).toHaveCount(0)
    })
  })
})
