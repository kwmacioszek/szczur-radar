// Smoke of the list of posts (F-05): the list under /posts in the frames of its days, its filter in the address, the
// pager and the list's states (the shared loading, empty and error states of S-48).
import { expect, test } from './fixtures.ts'

const ORLEN_TITLE = 'Orlen ogłasza buyback za 4 mld zł'
// dev/mock-api.js: of the 120 feed posts, post n is variant n % 6, and only variant 5 (the Orlen link) has a target
// whose quote holds "orlen" — "Orlen", bullish. 120 / 6 = 20 posts, all on the first page of 50.
const ORLEN_BULLISH_POSTS = 20

test('filtr po celu i stanowisku trafia do adresu i zawęża listę', async ({ page }) => {
  await page.goto('/posts')
  const main = page.getByRole('main')
  await expect(main.getByRole('status')).toHaveText('Ocenione posty: 120')

  await main.getByLabel('Spółka lub temat', { exact: true }).fill('orlen')
  await main.getByRole('combobox', { name: 'Stanowisko', exact: true }).selectOption({ label: 'wzrostowo' })
  await main.getByRole('button', { name: 'Filtruj' }).click()

  await expect(page).toHaveURL('/posts?q=orlen&direction=bullish')
  await expect(main.getByRole('status')).toHaveText(`Pasujące do filtra: ${ORLEN_BULLISH_POSTS}`)
  // Each row is one link in an item of its day's list.
  const rows = main.getByRole('listitem').getByRole('link')
  await expect(rows).toHaveCount(ORLEN_BULLISH_POSTS)
  await expect(rows).toContainText(Array(ORLEN_BULLISH_POSTS).fill(ORLEN_TITLE))
})

test('stary adres listy z filtrem prowadzi na /posts z tym samym filtrem', async ({ page }) => {
  await page.goto('/?q=orlen&direction=bullish')
  const main = page.getByRole('main')

  await expect(page).toHaveURL('/posts?q=orlen&direction=bullish')
  await expect(main.getByRole('status')).toHaveText(`Pasujące do filtra: ${ORLEN_BULLISH_POSTS}`)
  await expect(main.getByLabel('Spółka lub temat', { exact: true })).toHaveValue('orlen')
  await expect(main.getByRole('listitem').getByRole('link')).toHaveCount(ORLEN_BULLISH_POSTS)
})

// dev/mock-api.js: post n is published 17 min before the previous one, from 2026-10-12, 22:00 in Warsaw. The second
// page (posts 50–99) runs from 07:50 on 12 October to 17:57 on the 11th: posts 50–77 are Monday's, the last one at
// 00:11, posts 78–99 Sunday's, the first one at 23:54.
test('wiersze stoją pod nagłówkiem swojego dnia', async ({ page }) => {
  await page.goto('/posts?page=1')
  const main = page.getByRole('main')
  await expect(main.getByText('Strona 2 z 3', { exact: true })).toBeVisible()

  const monday = main.getByRole('region', { name: 'Poniedziałek, 12 października', exact: true })
  const sunday = main.getByRole('region', { name: 'Niedziela, 11 października', exact: true })
  await expect(monday.getByRole('heading', { level: 2 })).toHaveText('Poniedziałek, 12 października')
  await expect(monday.getByRole('listitem')).toHaveCount(28)
  await expect(sunday.getByRole('listitem')).toHaveCount(22)
  // Midnight in Warsaw is the border: the last row of Monday and the first of Sunday.
  await expect(monday.getByRole('listitem').last().getByRole('time')).toHaveText('00:11')
  await expect(sunday.getByRole('listitem').first().getByRole('time')).toHaveText('23:54')
  // Every row of the page is in a day: no row outside the two.
  await expect(main.getByRole('listitem')).toHaveCount(50)
})

test.describe('na najwęższym ekranie', () => {
  test.use({ viewport: { width: 320, height: 640 } })

  test('lista przy 320 px nie przewija się w poziomie', async ({ page }) => {
    await page.goto('/posts')
    const main = page.getByRole('main')
    await expect(main.getByRole('listitem').getByRole('link')).toHaveCount(50)
    await expect(page.getByRole('banner').getByText('Dane:', { exact: true })).toBeVisible()
    await page.evaluate(() => document.fonts.ready)

    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320)
  })
})

test('filtr bez wyników pozwala wrócić do pełnej listy', async ({ page }) => {
  await page.goto('/posts?q=zzz')
  const main = page.getByRole('main')
  await expect(main.getByRole('status')).toHaveText('Żaden post nie pasuje do filtra.')
  await expect(main.getByRole('heading', { level: 2, name: 'Brak postów dla tych filtrów', exact: true })).toBeVisible()

  // Both "Wyczyść filtry" are in main: the form's (first in the document) and the empty result's.
  await main.getByRole('button', { name: 'Wyczyść filtry' }).last().click()

  await expect(main.getByRole('status')).toHaveText('Ocenione posty: 120')
  await expect(page).toHaveURL('/posts')
})

test('przejście na starszą stronę zmienia adres i stronę', async ({ page }) => {
  await page.goto('/posts')
  const main = page.getByRole('main')
  await expect(main.getByText('Strona 1 z 3')).toBeVisible()

  await main.getByRole('button', { name: 'Starsze' }).click()

  await expect(page).toHaveURL('/posts?page=1')
  await expect(main.getByText('Strona 2 z 3')).toBeVisible()
})

test.describe('ładowanie', () => {
  test.use({ modes: { MODE: 'loading' } })

  test('ładowanie listy jest widoczne', async ({ page }) => {
    await page.goto('/posts')

    // The skeleton's text for assistive technology; the list's aria-busy has no role to find it by (FeedView.test.ts).
    await expect(page.getByRole('main').getByText('Wczytywanie…', { exact: true })).toBeVisible()
  })
})

test.describe('błąd', () => {
  test.use({ modes: { MODE: 'error' } })

  test('błąd listy pozwala ponowić wczytanie', async ({ page }) => {
    await page.goto('/posts')
    const main = page.getByRole('main')
    const title = main.getByRole('alert').getByRole('heading', { level: 2, name: 'Nie udało się wczytać postów', exact: true })
    await expect(title).toBeVisible()

    // The retry's own answer, not just its request: the alert below is the one the retry rendered.
    const retried = page.waitForResponse((response) =>
      response.request().method() === 'GET' && new URL(response.url()).pathname === '/api/posts'
      && response.status() === 500)
    await main.getByRole('button', { name: 'Spróbuj ponownie' }).click()
    await retried

    await expect(title).toBeVisible()
  })
})

test.describe('pusto', () => {
  test.use({ modes: { MODE: 'empty' } })

  test('pusta lista mówi, kiedy pojawią się posty', async ({ page }) => {
    await page.goto('/posts')
    const main = page.getByRole('main')

    await expect(main.getByRole('heading', { level: 2, name: 'Brak ocenionych postów', exact: true })).toBeVisible()
    await expect(main.getByText('Pojawią się po najbliższej aktualizacji danych.', { exact: true })).toBeVisible()
  })
})
