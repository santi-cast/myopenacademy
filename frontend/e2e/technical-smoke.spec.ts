import { expect, test } from '@playwright/test'

// Minimal smoke against the real backend through the Vite proxy; T10 owns the full journey.
test('an anonymous visit reaches the real backend and lands on the login screen', async ({ page }) => {
  const meResponse = page.waitForResponse((response) =>
    response.url() === 'http://127.0.0.1:5173/api/me'
    && response.request().method() === 'GET',
  )

  await page.goto('/')

  const response = await meResponse
  expect(response.status()).toBe(401)
  expect(await response.json()).toMatchObject({ code: 'AUTHENTICATION_REQUIRED' })
  await expect(page).toHaveURL('http://127.0.0.1:5173/login')
  await expect(page.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
  await expect(page.getByRole('button', { name: /^Modo (oscuro|claro)$/ })).toBeVisible()
})
