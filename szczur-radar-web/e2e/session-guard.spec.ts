// Seed of the E2E suite (F-05), risk session-guard-roundtrip: a rebuild of the router or the shell (S-47) loses the
// way back to the requested page after the login. The pattern for the other tests: import from ./fixtures, the mock's
// modes through test.use, locators by role and label only.
import { expect, test } from './fixtures.ts'

const POST_5 = '/posts/00000000-0000-4000-8000-000400000005'

test.use({ modes: { AUTH: 'anonymous' } })

test('anonimowy link do posta prowadzi przez logowanie z powrotem do tego posta', async ({ page }) => {
  await page.goto(POST_5)

  // vue-router does not encode `/` in a query value.
  await expect(page).toHaveURL(`/login?redirect=${POST_5}`)
  await expect(page.getByRole('heading', { level: 1, name: 'Logowanie' })).toBeVisible()

  await page.getByLabel('E-mail').fill('admin@szczur-radar.pl')
  await page.getByLabel('Hasło', { exact: true }).fill('dowolne-haslo')
  await page.getByRole('button', { name: 'Zaloguj się' }).click()

  await expect(page).toHaveURL(POST_5)
  await expect(page.getByRole('heading', { level: 1, name: 'Orlen ogłasza buyback za 4 mld zł' })).toBeVisible()
  await expect(page.getByRole('banner').getByRole('link', { name: 'Panel admina' })).toBeVisible()
})
