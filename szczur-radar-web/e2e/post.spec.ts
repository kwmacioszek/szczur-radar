// Smoke of one post's page (F-05): the post with its assessment and its source's raw data, and the way back to the
// list — the view S-51 replaces with the inspector.
import { expect, test } from './fixtures.ts'

const POST_0 = '/posts/00000000-0000-4000-8000-000400000000'

test('post otwarty z listy pokazuje ocenę i surowe dane, a powrót wraca do listy', async ({ page }) => {
  await page.goto('/posts')
  const main = page.getByRole('main')

  await main.getByRole('listitem').first().getByRole('link').click()

  await expect(page).toHaveURL(POST_0)
  // Post 0 has no title: the view names it "{Type} użytkownika {author}".
  await expect(main.getByRole('heading', { level: 1, name: 'Wpis użytkownika uzytkownik0' })).toBeVisible()
  await expect(main.getByRole('heading', { level: 2, name: 'Ocena' })).toBeVisible()
  await expect(main.getByRole('heading', { level: 3, name: 'CDR', exact: true })).toBeVisible()

  await main.getByText('Dane dla nerdów: surowy JSON źródła').click()
  await expect(main.getByText('"slug": "wpis-0"')).toBeVisible()

  // The header has a "Posty" link too: the back link is the one in main.
  await main.getByRole('link', { name: 'Posty', exact: true }).click()
  await expect(page).toHaveURL('/posts')
  await expect(main.getByRole('heading', { level: 1, name: 'Posty' })).toBeVisible()
})
