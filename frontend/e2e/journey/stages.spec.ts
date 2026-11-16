// The project by stages of the kit's example course (cursos-ejemplo/bueno, «Mi catálogo»), installed by
// scripts/e2e-journey.mjs with the real importer: real Chromium, Vite, Spring and a disposable database (H11, T6).
// The only interception holds the stage's PUT and then sends it unchanged, to see the UI before the server confirms.
import { randomBytes } from 'node:crypto'
import { expect, test } from '@playwright/test'

const COURSE_ID = 'ejemplo'
const COURSE_TITLE = 'Curso de ejemplo'
const PROJECT_TITLE = 'Mi catálogo'
const STAGE_ID = 'ejemplo.etapa.primer-catalogo'
const STAGE_TITLE = 'El primer catálogo'
const carla = { username: 'Carla', password: `contraseña de Carla ${randomBytes(4).toString('hex')}` }

test('the project by stages: reading, manual confirmation and its own continuation', async ({ page }) => {
  await page.goto('/register')
  await page.getByLabel('Usuario').fill(carla.username)
  await page.getByLabel('Contraseña').fill(carla.password)
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('Usuario').fill(carla.username)
  await page.getByLabel('Contraseña').fill(carla.password)
  await page.getByRole('button', { name: 'Iniciar sesión' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Mis cursos' })).toBeVisible()
  // From here on, signed in: no browser error at all (the anonymous visit's 401 from /api/me is expected).
  const browserErrors: string[] = []
  page.on('pageerror', (error) => browserErrors.push(error.message))
  page.on('console', (message) => { if (message.type() === 'error') browserErrors.push(message.text()) })

  // From the course index to the project page: its title comes from curso.json, never from the code.
  await page.goto(`/courses/${COURSE_ID}`)
  await page.locator('.index-project').getByRole('link', { name: `Proyecto del curso: ${PROJECT_TITLE}` }).click()
  await expect(page).toHaveURL(new RegExp(`/courses/${COURSE_ID}/project$`))
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(`${COURSE_TITLE} · ${PROJECT_TITLE}`)
  await expect(page.getByText(/etapas disponibles completadas/)).toContainText('0 de 1 etapas disponibles completadas')
  await expect(page.getByRole('link', { name: 'Retomar proyecto' })).toHaveCount(0)

  // Reading the stage writes nothing; marking it is a manual confirmation.
  const writes: { path: string; csrf: string | undefined }[] = []
  page.on('request', (request) => {
    if (request.method() === 'PUT') writes.push({ path: new URL(request.url()).pathname, csrf: request.headers()['x-xsrf-token'] })
  })
  await page.getByRole('link', { name: STAGE_TITLE }).click()
  await expect(page).toHaveURL(new RegExp(`/stages/${STAGE_ID.replaceAll('.', '\\.')}$`))
  await expect(page.getByRole('heading', { level: 1 })).toContainText(STAGE_TITLE)
  await expect(page.getByText(`${COURSE_TITLE} · Proyecto del curso · Etapa 1`)).toBeVisible()
  expect(writes).toEqual([])

  let release!: () => void
  const held = new Promise<void>((resolve) => { release = resolve })
  await page.route((url) => url.pathname === `/api/me/stages/${STAGE_ID}/state`, async (route) => {
    await held
    await route.continue()
  })
  await page.getByRole('group', { name: 'Estado de la etapa' }).getByText('Completada', { exact: true }).click()
  await expect(page.getByText('Guardando…')).toBeVisible()
  await expect(page.getByText(/etapas disponibles completadas/)).toContainText('0 de 1')
  release()
  await expect(page.getByText(/Guardado/)).toBeVisible()
  await expect(page.getByText(`${COURSE_TITLE} se añadió a Mis cursos`)).toBeVisible()
  await expect(page.getByText(/etapas disponibles completadas/)).toContainText('1 de 1')
  await page.unroute((url) => url.pathname === `/api/me/stages/${STAGE_ID}/state`)

  // Saved by the backend: after a reload the project page shows the stage completed and offers to resume it.
  await page.goto(`/courses/${COURSE_ID}/project`)
  await expect(page.getByText(/etapas disponibles completadas/)).toContainText('1 de 1 etapas disponibles completadas')
  await expect(page.getByRole('row', { name: new RegExp(STAGE_TITLE) })).toContainText('Completada')
  await expect(page.getByRole('link', { name: 'Retomar proyecto' })).toHaveAttribute('href', `/stages/${STAGE_ID}`)
  // The stage's progress is independent: the course's lesson progress does not change.
  await page.goto(`/courses/${COURSE_ID}`)
  await expect(page.getByText(/lecciones disponibles completadas/)).toContainText('0 de ')

  expect(writes.map(({ path }) => path)).toContain(`/api/me/stages/${STAGE_ID}/state`)
  expect(writes.every(({ csrf }) => typeof csrf === 'string' && csrf.length > 0)).toBe(true)
  expect(browserErrors).toEqual([])
})
