// The panel of a post over the list (S-51, ui-design.md "Kontrakt dla testów E2E", row `post`): a row opens it as a
// modal dialog with its own address, the focus in it and the row selected; "Zamknij post", Escape and the browser's
// "back" close it, the focus back on the row and the list's address with its filter and page; Tab stays inside;
// "Surowe dane" shows the source's JSON; a post off the page and an unknown id are read by the panel itself.
// The dialog is moved out of the app to the body, so it is found in the page, not in `main`.
import type { Page } from '@playwright/test'
import { expect, test } from './fixtures.ts'

// dev/mock-api.js: feed post n is 00000000-0000-4000-8000-0004 followed by n as 8 digits; post 0 (variant 0, a Wykop
// entry without a title, by uzytkownik0) is the first row of the first page, about CDR; post 70 is on the second page.
const postPath = (n: number) => `/posts/00000000-0000-4000-8000-0004${String(n).padStart(8, '0')}`
const POST_0_NAME = 'Wpis użytkownika uzytkownik0'
// With the filter "wzrostowo" 60 posts match (variants 0, 2 and 5): the second page has 10 rows, the first of them
// post 101, the Orlen link.
const BULLISH_PAGE_1 = '/posts?page=1&direction=bullish'
const ORLEN_TITLE = 'Orlen ogłasza buyback za 4 mld zł'

/** The dialog of the post named `name`. */
function postDialog(page: Page, name: string) {
  return page.getByRole('dialog', { name, exact: true })
}

/** The first row of the list on screen. */
function firstRow(page: Page) {
  // The first row's link: the markers of the filter over the list are list items too, without a link.
  return page.getByRole('main').getByRole('listitem').getByRole('link').first()
}

test('klik wiersza otwiera post w panelu nad listą, z jego adresem, fokusem w panelu i zaznaczonym wierszem', async ({ page }) => {
  await page.goto('/posts')
  const row = firstRow(page)

  await row.click()

  const dialog = postDialog(page, POST_0_NAME)
  await expect(dialog).toBeVisible()
  await expect(dialog).toHaveAttribute('aria-modal', 'true')
  await expect(page).toHaveURL(postPath(0))
  await expect(dialog.getByRole('heading', { level: 2, name: POST_0_NAME, exact: true })).toBeFocused()
  await expect(row).toHaveAttribute('aria-current', 'true')
  await expect(dialog.getByRole('heading', { level: 3, name: 'Ocena', exact: true })).toBeVisible()
  await expect(dialog.getByRole('heading', { level: 4, name: 'CDR', exact: true })).toBeVisible()
  await expect(dialog.getByRole('link', { name: 'Otwórz oryginał na wykop.pl (otwiera się w nowej karcie)', exact: true }))
    .toHaveAttribute('target', '_blank')
  // The list stays under the panel, inert: one main and its heading.
  await expect(page.getByRole('main').getByRole('heading', { level: 1, name: 'Posty', exact: true })).toBeVisible()
  await expect(page.locator('#app')).toHaveAttribute('inert', '')
})

test('„Zamknij post” zamyka panel, oddaje fokus wierszowi i wraca do listy z tym samym filtrem i stroną', async ({ page }) => {
  await page.goto(BULLISH_PAGE_1)
  const row = firstRow(page)
  await row.click()
  const dialog = postDialog(page, ORLEN_TITLE)
  await expect(dialog).toBeVisible()
  await expect(page).toHaveURL(`${postPath(101)}?page=1&direction=bullish`)

  await dialog.getByRole('button', { name: 'Zamknij post', exact: true }).click()

  await expect(dialog).toHaveCount(0)
  await expect(page).toHaveURL(BULLISH_PAGE_1)
  await expect(row).toBeFocused()
  await expect(row).not.toHaveAttribute('aria-current', 'true')
  await expect(page.getByRole('main').getByText('Strona 2 z 2', { exact: true })).toBeVisible()
})

test('Esc zamyka panel, oddaje fokus wierszowi i wraca do listy z tym samym filtrem i stroną', async ({ page }) => {
  await page.goto(BULLISH_PAGE_1)
  const row = firstRow(page)
  await row.click()
  const dialog = postDialog(page, ORLEN_TITLE)
  await expect(dialog).toBeVisible()

  await page.keyboard.press('Escape')

  await expect(dialog).toHaveCount(0)
  await expect(page).toHaveURL(BULLISH_PAGE_1)
  await expect(row).toBeFocused()
})

test('otwarcie i zamknięcie panelu nie przewija listy na górę', async ({ page }) => {
  await page.goto('/posts')
  const row = page.getByRole('main').getByRole('listitem').nth(30).getByRole('link')
  await row.scrollIntoViewIfNeeded()
  const scrolled = await page.evaluate(() => window.scrollY)
  expect(scrolled).toBeGreaterThan(0)

  await row.click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  expect(await page.evaluate(() => window.scrollY)).toBe(scrolled)

  await dialog.getByRole('button', { name: 'Zamknij post', exact: true }).click()

  await expect(dialog).toHaveCount(0)
  await expect(row).toBeFocused()
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(scrolled)
})

test('Tab z ostatniego elementu panelu wraca na pierwszy, a Shift+Tab z pierwszego na ostatni', async ({ page }) => {
  await page.goto('/posts')
  await firstRow(page).click()
  const dialog = postDialog(page, POST_0_NAME)
  const close = dialog.getByRole('button', { name: 'Zamknij post', exact: true })
  const raw = dialog.getByRole('button', { name: 'Surowe dane', exact: true })
  await expect(dialog).toBeVisible()

  // The first control is the bar's close button, the last one "Surowe dane" (its data closed).
  await raw.focus()
  await page.keyboard.press('Tab')
  await expect(close).toBeFocused()

  await page.keyboard.press('Shift+Tab')
  await expect(raw).toBeFocused()
})

test('„Surowe dane” rozwijają JSON źródła w regionie „Surowe dane posta”', async ({ page }) => {
  await page.goto('/posts')
  await firstRow(page).click()
  const dialog = postDialog(page, POST_0_NAME)
  const raw = dialog.getByRole('button', { name: 'Surowe dane', exact: true })
  await expect(raw).toHaveAttribute('aria-expanded', 'false')

  await raw.click()

  await expect(raw).toHaveAttribute('aria-expanded', 'true')
  await expect(dialog.getByRole('region', { name: 'Surowe dane posta', exact: true })).toContainText('"slug": "wpis-0"')
})

test.describe('surowe dane w trakcie wczytywania', () => {
  test.use({ modes: { MODE: 'raw-loading' } })

  test('surowe dane w trakcie wczytywania pokazują szkielet', async ({ page }) => {
    await page.goto('/posts')
    await firstRow(page).click()
    const dialog = postDialog(page, POST_0_NAME)

    await dialog.getByRole('button', { name: 'Surowe dane', exact: true }).click()

    await expect(dialog.getByText('Wczytywanie…', { exact: true })).toBeVisible()
    await expect(dialog.getByRole('region', { name: 'Surowe dane posta', exact: true })).toHaveCount(0)
  })
})

test.describe('błąd surowych danych', () => {
  test.use({ modes: { MODE: 'raw-error' } })

  test('błąd surowych danych pozwala je ponowić, a ocena zostaje', async ({ page }) => {
    await page.goto('/posts')
    await firstRow(page).click()
    const dialog = postDialog(page, POST_0_NAME)

    await dialog.getByRole('button', { name: 'Surowe dane', exact: true }).click()

    const alert = dialog.getByRole('alert')
    await expect(alert).toContainText('Nie udało się wczytać danych źródła')
    await expect(alert.getByRole('button', { name: 'Spróbuj ponownie', exact: true })).toBeVisible()
    await expect(dialog.getByRole('heading', { level: 4, name: 'CDR', exact: true })).toBeVisible()
  })
})

test('link do posta spoza strony listy otwiera panel, który sam wczytuje post', async ({ page }) => {
  const read = page.waitForResponse((response) => new URL(response.url()).pathname === `/api${postPath(70)}`)
  await page.goto(postPath(70))

  await read
  const dialog = postDialog(page, 'Wpis użytkownika uzytkownik70')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('heading', { level: 2, name: 'Wpis użytkownika uzytkownik70', exact: true })).toBeFocused()
  // The list of the first page under it, without the post's row: none is selected.
  const rows = page.getByRole('main').getByRole('listitem').getByRole('link')
  await expect(rows).toHaveCount(50)
  expect(await rows.evaluateAll((links) => links.filter((link) => link.hasAttribute('aria-current')).length)).toBe(0)

  await dialog.getByRole('button', { name: 'Zamknij post', exact: true }).click()

  await expect(page).toHaveURL('/posts')
  await expect(page.getByRole('main').getByRole('heading', { level: 1, name: 'Posty', exact: true })).toBeFocused()
})

test('nieznany id posta mówi w panelu, że nie ma takiego posta', async ({ page }) => {
  await page.goto('/posts/00000000-0000-4000-8000-000499999999')

  const dialog = postDialog(page, 'Nie ma takiego posta')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('Post nie istnieje albo nie został jeszcze oceniony.', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('button', { name: 'Zamknij post', exact: true })).toBeVisible()
})

test.describe('na wąskim ekranie', () => {
  test.use({ viewport: { width: 375, height: 812 } })

  test('przy 375 px panel zajmuje całe okno, a „wstecz” przeglądarki zamyka go i wraca do listy', async ({ page }) => {
    await page.goto('/posts?page=1')
    const row = firstRow(page)
    await row.click()
    const dialog = postDialog(page, 'Post użytkownika uzytkownik50')
    await expect(dialog).toBeVisible()

    // Once it has slid in (it moves only by `transform`, where motion is welcome).
    await expect.poll(() => dialog.boundingBox()).toEqual({ x: 0, y: 0, width: 375, height: 812 })

    await page.goBack()

    await expect(dialog).toHaveCount(0)
    await expect(page).toHaveURL('/posts?page=1')
    await expect(row).toBeFocused()
  })
})
