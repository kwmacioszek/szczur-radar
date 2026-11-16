// Smoke of the feed (F-05): the list with its filter in the address, the pager and the list's states — what S-48
// and S-51 rebuild.
import { expect, test } from './fixtures.ts'

const ORLEN_TITLE = 'Orlen ogłasza buyback za 4 mld zł'
// dev/mock-api.js: of the 120 feed posts, post n is variant n % 6, and only variant 5 (the Orlen link) has a target
// whose quote holds "orlen" — "Orlen", bullish. 120 / 6 = 20 posts, all on the first page of 50.
const ORLEN_BULLISH_POSTS = 20

test('filtr po celu i stanowisku trafia do adresu i zawęża listę', async ({ page }) => {
  await page.goto('/')
  const main = page.getByRole('main')
  await expect(main.getByRole('status')).toHaveText('Ocenione posty: 120')

  await main.getByLabel('Spółka lub temat', { exact: true }).fill('orlen')
  await main.getByRole('combobox', { name: 'Stanowisko', exact: true }).selectOption({ label: 'wzrostowo' })
  await main.getByRole('button', { name: 'Filtruj' }).click()

  await expect(page).toHaveURL('/?q=orlen&direction=bullish')
  await expect(main.getByRole('status')).toHaveText(`Pasujące do filtra: ${ORLEN_BULLISH_POSTS}`)
  const cards = main.getByRole('article')
  await expect(cards).toHaveCount(ORLEN_BULLISH_POSTS)
  await expect(cards.getByRole('heading', { level: 2 })).toHaveText(Array(ORLEN_BULLISH_POSTS).fill(ORLEN_TITLE))
})

test('filtr bez wyników pozwala wrócić do pełnej listy', async ({ page }) => {
  await page.goto('/?q=zzz')
  const main = page.getByRole('main')
  await expect(main.getByRole('status')).toHaveText('Żaden post nie pasuje do filtra.')

  // Both "Wyczyść filtry" are in main: the form's (first in the document) and the empty result's.
  await main.getByRole('button', { name: 'Wyczyść filtry' }).last().click()

  await expect(main.getByRole('status')).toHaveText('Ocenione posty: 120')
  await expect(page).toHaveURL('/')
})

test('przejście na starszą stronę zmienia adres i stronę', async ({ page }) => {
  await page.goto('/')
  const main = page.getByRole('main')
  await expect(main.getByText('Strona 1 z 3')).toBeVisible()

  await main.getByRole('button', { name: 'Starsze' }).click()

  await expect(page).toHaveURL('/?page=1')
  await expect(main.getByText('Strona 2 z 3')).toBeVisible()
})

test.describe('ładowanie', () => {
  test.use({ modes: { MODE: 'loading' } })

  test('ładowanie listy jest widoczne', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('main').getByText('Wczytywanie…', { exact: true })).toBeVisible()
  })
})

test.describe('błąd', () => {
  test.use({ modes: { MODE: 'error' } })

  test('błąd listy pozwala ponowić wczytanie', async ({ page }) => {
    await page.goto('/')
    const main = page.getByRole('main')
    await expect(main.getByRole('alert')).toHaveText('Nie udało się wczytać postów.')

    // The retry's own answer, not just its request: the alert below is the one the retry rendered.
    const retried = page.waitForResponse((response) =>
      response.request().method() === 'GET' && new URL(response.url()).pathname === '/api/posts'
      && response.status() === 500)
    await main.getByRole('button', { name: 'Spróbuj ponownie' }).click()
    await retried

    await expect(main.getByRole('alert')).toHaveText('Nie udało się wczytać postów.')
  })
})

test.describe('pusto', () => {
  test.use({ modes: { MODE: 'empty' } })

  test('pusta lista mówi, kiedy pojawią się posty', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByRole('main')
      .getByText('Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.')).toBeVisible()
  })
})
