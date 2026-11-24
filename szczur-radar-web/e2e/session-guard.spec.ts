// Seed of the E2E suite (F-05), risk session-guard-roundtrip: a rebuild of the router or the shell (S-47) loses the
// way back to the requested page after the login. The pattern for the other tests: import from ./fixtures, the mock's
// modes through test.use, locators by role, label and text only, scoped to banner / main, labels exact.
import { expect, test } from './fixtures.ts'

const POST_5 = '/posts/00000000-0000-4000-8000-000400000005'

test.use({ modes: { AUTH: 'anonymous' } })

test('anonimowy link do posta prowadzi przez logowanie z powrotem do tego posta', async ({ page }) => {
  await page.goto(POST_5)

  // vue-router does not encode `/` in a query value.
  await expect(page).toHaveURL(`/login?redirect=${POST_5}`)
  const main = page.getByRole('main')
  await expect(main.getByRole('heading', { level: 1, name: 'Logowanie' })).toBeVisible()

  await main.getByLabel('E-mail', { exact: true }).fill('admin@szczur-radar.pl')
  await main.getByLabel('Hasło', { exact: true }).fill('dowolne-haslo')
  await main.getByRole('button', { name: 'Zaloguj się' }).click()

  await expect(page).toHaveURL(POST_5)
  // The post opens in its panel over the list: a dialog named by its title, out of `main`.
  await expect(page.getByRole('dialog', { name: 'Orlen ogłasza buyback za 4 mld zł', exact: true })).toBeVisible()
  await expect(main.getByRole('heading', { level: 1, name: 'Posty', exact: true })).toBeVisible()
  await expect(page.getByRole('banner').getByRole('link', { name: 'Panel admina' })).toBeVisible()
})
