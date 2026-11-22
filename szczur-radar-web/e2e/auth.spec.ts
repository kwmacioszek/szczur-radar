// Smoke of the session and the role (F-05): logging in, logging out, a session that expires while the app is open
// and a user without the admin role — the behaviour S-47 (the shell) and S-49 (the login) rebuild.
import { expect, test } from './fixtures.ts'

test.describe('logowanie anonimowego', () => {
  test.use({ modes: { AUTH: 'anonymous', LOGIN: 'invalid' } })

  test('złe hasło zostaje na logowaniu z komunikatem', async ({ page }) => {
    await page.goto('/login')
    const main = page.getByRole('main')

    await main.getByLabel('E-mail', { exact: true }).fill('admin@szczur-radar.pl')
    await main.getByLabel('Hasło', { exact: true }).fill('zle-haslo')
    await main.getByRole('button', { name: 'Zaloguj się' }).click()

    await expect(main.getByRole('alert')).toHaveText('Nieprawidłowy e-mail lub hasło.')
    await expect(page).toHaveURL('/login')
  })

  test('po kliknięciu i odmowie fokus wraca na przycisk, nie spada na początek strony', async ({ page }) => {
    await page.goto('/login')
    const main = page.getByRole('main')
    await main.getByLabel('E-mail', { exact: true }).fill('admin@szczur-radar.pl')
    await main.getByLabel('Hasło', { exact: true }).fill('zle-haslo')
    const button = main.getByRole('button', { name: 'Zaloguj się' })
    await button.click()

    await expect(main.getByRole('alert')).toHaveText('Nieprawidłowy e-mail lub hasło.')
    await expect(button).toBeFocused()
  })
})

test.describe('logowanie z wyczerpanym limitem prób', () => {
  test.use({ modes: { AUTH: 'anonymous', LOGIN: 'limited' } })

  test('limit prób zostaje na logowaniu z komunikatem o ponowieniu', async ({ page }) => {
    await page.goto('/login')
    const main = page.getByRole('main')

    await main.getByLabel('E-mail', { exact: true }).fill('admin@szczur-radar.pl')
    await main.getByLabel('Hasło', { exact: true }).fill('dowolne-haslo')
    await main.getByRole('button', { name: 'Zaloguj się' }).click()

    await expect(main.getByRole('alert')).toContainText(/^Zbyt wiele prób\./)
    await expect(page).toHaveURL('/login')
  })
})

test('wylogowanie kończy sesję, a chroniona strona wraca do logowania', async ({ page }) => {
  await page.goto('/')
  const main = page.getByRole('main')
  await expect(main.getByRole('heading', { level: 1, name: 'Posty' })).toBeVisible()

  await page.getByRole('banner').getByRole('button', { name: 'Wyloguj' }).click()
  await expect(page).toHaveURL('/login')
  await expect(main.getByRole('heading', { level: 1, name: 'Logowanie' })).toBeVisible()

  // A new page load asks the server again: the session is gone. vue-router does not encode `/` in a query value.
  await page.goto('/')
  await expect(page).toHaveURL('/login?redirect=/')
})

test('wygaśnięcie sesji w trakcie pracy prowadzi do logowania i z powrotem', async ({ page, mock }) => {
  await page.goto('/')
  const main = page.getByRole('main')
  await expect(main.getByText('Strona 1 z 3')).toBeVisible()

  mock.endSession()
  await main.getByRole('button', { name: 'Starsze' }).click()

  await expect(page).toHaveURL('/login?redirect=/?page=1')
  await main.getByLabel('E-mail', { exact: true }).fill('admin@szczur-radar.pl')
  await main.getByLabel('Hasło', { exact: true }).fill('dowolne-haslo')
  await main.getByRole('button', { name: 'Zaloguj się' }).click()

  await expect(page).toHaveURL('/?page=1')
  await expect(main.getByText('Strona 2 z 3')).toBeVisible()
})

test.describe('zwykły użytkownik', () => {
  test.use({ modes: { AUTH: 'user' } })

  test('zwykły użytkownik nie widzi panelu admina', async ({ page }) => {
    await page.goto('/')
    const banner = page.getByRole('banner')
    // The header is there (the user's own e-mail), so the missing admin controls are not just a page still loading.
    await expect(banner.getByText('user@szczur-radar.pl')).toBeVisible()
    await expect(banner.getByRole('link', { name: 'Panel admina' })).toHaveCount(0)
    await expect(banner.getByRole('button', { name: /^Powiadomienia/ })).toHaveCount(0)

    await page.goto('/admin')
    await expect(page).toHaveURL('/')
    await expect(page.getByRole('main').getByRole('heading', { level: 1, name: 'Posty' })).toBeVisible()
  })
})
