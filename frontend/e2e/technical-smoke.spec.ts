import { expect, test } from '@playwright/test'

test('connects to the real backend health endpoint through the Vite proxy', async ({ page }) => {
  const healthResponse = page.waitForResponse((response) =>
    response.url() === 'http://127.0.0.1:5173/actuator/health'
    && response.request().method() === 'GET',
  )

  await page.goto('/')

  await expect(page.getByRole('heading', { level: 1, name: 'Code Academy' })).toBeVisible()
  await expect(page.getByText('Base técnica. Todavía no hay cursos disponibles.')).toBeVisible()
  const response = await healthResponse
  expect(response.status()).toBe(200)
  expect(await response.json()).toEqual({ status: 'UP' })
  await expect(page.getByRole('status')).toHaveText('Backend disponible.')
})
