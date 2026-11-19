// The theme switch (S-46): a choice made with the header button, kept in the browser and winning over the system's
// theme, the system's theme followed while there is no choice, and the login page applying the stored choice. The
// theme is read from <html>: the data-theme attribute and the computed color-scheme, which follows it.
import { expect, test } from './fixtures.ts'
import type { Page } from '@playwright/test'

type Seen = { theme: string | undefined; scheme: string }

async function seen(page: Page): Promise<Seen> {
  return page.evaluate(() => ({
    theme: document.documentElement.dataset.theme,
    scheme: getComputedStyle(document.documentElement).colorScheme,
  }))
}

const LIGHT: Seen = { theme: 'light', scheme: 'light' }
const DARK: Seen = { theme: 'dark', scheme: 'dark' }

test.describe('przełącznik motywu zalogowanego', () => {
  test.use({ modes: { AUTH: 'user' } })

  test('wybór motywu przetrwa przeładowanie strony', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' })
    await page.goto('/')
    const toggle = page.getByRole('banner').getByRole('button', { name: 'Ciemny motyw', exact: true })
    await expect(toggle).toHaveAttribute('aria-pressed', 'false')

    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-pressed', 'true')
    await expect.poll(() => seen(page)).toEqual(DARK)

    await page.reload()
    await expect(toggle).toHaveAttribute('aria-pressed', 'true')
    await expect.poll(() => seen(page)).toEqual(DARK)
  })

  test('zapamiętany wybór wygrywa z motywem systemu, także po jego zmianie', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.goto('/')
    const toggle = page.getByRole('banner').getByRole('button', { name: 'Ciemny motyw', exact: true })
    await expect(toggle).toHaveAttribute('aria-pressed', 'true')

    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-pressed', 'false')
    await expect.poll(() => seen(page)).toEqual(LIGHT)

    // The system goes light and dark again: the choice stays, on the page and after a reload. A probe listener,
    // added after the app's own one would have been, records each change, so the theme is read only once the browser
    // has delivered it (a listener still following the system would have run first). Each change is awaited on its
    // own: two quick ones back to the starting scheme may reach the page as none.
    await page.evaluate(() => {
      const probe = globalThis as unknown as { schemeChanges: boolean[] }
      probe.schemeChanges = []
      matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (event) => {
        probe.schemeChanges.push(event.matches)
      })
    })
    const changes = () => page.evaluate(() => (globalThis as unknown as { schemeChanges: boolean[] }).schemeChanges)
    await page.emulateMedia({ colorScheme: 'light' })
    await expect.poll(changes).toEqual([false])
    expect(await seen(page)).toEqual(LIGHT)
    await page.emulateMedia({ colorScheme: 'dark' })
    await expect.poll(changes).toEqual([false, true])
    expect(await seen(page)).toEqual(LIGHT)
    await page.reload()
    await expect(toggle).toHaveAttribute('aria-pressed', 'false')
    await expect.poll(() => seen(page)).toEqual(LIGHT)
  })

  test('bez wyboru motyw idzie za systemem, także na żywo', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' })
    await page.goto('/')
    const toggle = page.getByRole('banner').getByRole('button', { name: 'Ciemny motyw', exact: true })
    await expect(toggle).toHaveAttribute('aria-pressed', 'false')
    await expect.poll(() => seen(page)).toEqual(LIGHT)

    await page.emulateMedia({ colorScheme: 'dark' })
    await expect(toggle).toHaveAttribute('aria-pressed', 'true')
    await expect.poll(() => seen(page)).toEqual(DARK)

    await page.emulateMedia({ colorScheme: 'light' })
    await expect(toggle).toHaveAttribute('aria-pressed', 'false')
    await expect.poll(() => seen(page)).toEqual(LIGHT)
  })
})

test.describe('logowanie', () => {
  test.use({ modes: { AUTH: 'anonymous' } })

  test('logowanie stosuje zapamiętany motyw', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' })
    await page.addInitScript(() => localStorage.setItem('szczur-radar-theme', 'dark'))
    await page.goto('/login')
    await expect(page.getByRole('main').getByRole('heading', { level: 1, name: 'Logowanie', exact: true })).toBeVisible()

    await expect.poll(() => seen(page)).toEqual(DARK)
  })
})
