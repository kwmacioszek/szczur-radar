// Smoke of the activation link (F-05): the only way an account gets its first password (FR-026–028), on the screen
// S-49 rebuilt on the shared components.
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

test('za krótkie hasło daje błąd przy polu z fokusem na nim, nie alert formularza', async ({ page }) => {
  await page.goto('/activate#token')
  const main = page.getByRole('main')
  const password = main.getByLabel('Nowe hasło', { exact: true })
  await password.fill('krotkie')
  await main.getByLabel('Powtórz hasło', { exact: true }).fill('krotkie')
  await main.getByRole('button', { name: 'Ustaw hasło' }).click()

  await expect(main.getByText('Hasło musi mieć co najmniej 12 znaków.')).toBeVisible()
  await expect(password).toBeFocused()
  await expect(password).toHaveAttribute('aria-invalid', 'true')
  await expect(main.getByRole('alert')).toHaveCount(0)
})

test.describe('link nieważny', () => {
  test.use({ modes: { AUTH: 'anonymous', ACTIVATION: 'invalid' } })

  test('nieważny link pokazuje komunikat zamiast formularza', async ({ page }) => {
    await page.goto('/activate#token')
    const main = page.getByRole('main')
    await expect(main.getByRole('alert')).toHaveText('Link jest nieważny lub wygasł. Poproś administratora o nowy.')
    await expect(main.getByLabel('Nowe hasło', { exact: true })).toHaveCount(0)
  })
})

test.describe('polityka haseł serwera', () => {
  test.use({ modes: { AUTH: 'anonymous', ACTIVATION: 'policy' } })

  test('odrzucenie hasła przez serwer pojawia się przy polu hasła', async ({ page }) => {
    await page.goto('/activate#token')
    const main = page.getByRole('main')
    const password = main.getByLabel('Nowe hasło', { exact: true })
    await password.fill('dlugie-haslo-123')
    await main.getByLabel('Powtórz hasło', { exact: true }).fill('dlugie-haslo-123')
    await main.getByRole('button', { name: 'Ustaw hasło' }).click()

    await expect(main.getByText('Hasło musi mieć od 12 do 128 znaków.')).toBeVisible()
    await expect(password).toBeFocused()
    await expect(password).toHaveAttribute('aria-invalid', 'true')
    await expect(main.getByRole('alert')).toHaveCount(0)
  })
})

test.describe('błąd serwera przy ustawianiu hasła', () => {
  test.use({ modes: { AUTH: 'anonymous', ACTIVATION: 'unavailable' } })

  test('po kliknięciu i błędzie serwera fokus wraca na przycisk, nie na początek strony', async ({ page }) => {
    await page.goto('/activate#token')
    const main = page.getByRole('main')
    await main.getByLabel('Nowe hasło', { exact: true }).fill('dlugie-haslo-123')
    await main.getByLabel('Powtórz hasło', { exact: true }).fill('dlugie-haslo-123')
    const button = main.getByRole('button', { name: 'Ustaw hasło' })
    await button.click()

    await expect(main.getByRole('alert')).toHaveText('Nie udało się ustawić hasła. Spróbuj ponownie.')
    await expect(button).toBeFocused()
    await expect(page).toHaveURL('/activate')
  })
})
