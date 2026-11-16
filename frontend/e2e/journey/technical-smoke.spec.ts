import { expect, test } from '@playwright/test'

// Minimal smoke against the real backend through the Vite proxy, inside the journey's environment (H11, T6).
test('an anonymous visit reaches the real backend and lands on the login screen', async ({ page }) => {
  const meResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === '/api/me'
    && response.request().method() === 'GET',
  )

  await page.goto('/')

  const response = await meResponse
  expect(response.status()).toBe(401)
  expect(await response.json()).toMatchObject({ code: 'AUTHENTICATION_REQUIRED' })
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
  await expect(page.getByRole('button', { name: /^Modo (oscuro|claro)$/ })).toBeVisible()
})
