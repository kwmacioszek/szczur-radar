// Smoke of the activation link (F-05): the only way an account gets its first password (FR-026–028), on the screen
// S-49 rebuilds.
import { expect, test } from './fixtures.ts'

test.use({ modes: { AUTH: 'anonymous' } })

test('link aktywacyjny ustawia hasło i prowadzi do logowania', async ({ page }) => {
  await page.goto('/activate#token')
  const main = page.getByRole('main')
  await expect(main.getByText('Ustaw hasło dla jan.kowalski@example.com.')).toBeVisible()

  await main.getByLabel('Nowe hasło', { exact: true }).fill('dlugie-haslo-123')
  await main.getByLabel('Powtórz hasło', { exact: true }).fill('dlugie-haslo-123')
  await main.getByRole('button', { name: 'Ustaw hasło' }).click()

  await expect(page).toHaveURL('/login')
  await expect(main.getByRole('status')).toHaveText('Hasło ustawione. Zaloguj się.')
  await expect(main.getByLabel('E-mail', { exact: true })).toHaveValue('jan.kowalski@example.com')
})
