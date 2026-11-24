/// <reference lib="dom" />
// H12, T5: the parts of a course in a real browser, against the real backend (S1, S5 to S7), and the keys of a lesson
// (S12). «partes» is the academy's own fixture (e2e/fixtures/curso-partes: two parts, three phases, one pending
// lesson); «ejemplo-destino» and «ejemplo» are the kit's examples. All three are installed by scripts/e2e-journey.mjs.
import { randomBytes } from 'node:crypto'
import { join } from 'node:path'
import { expect, test, type Locator, type Page } from '@playwright/test'

const screenshotDir = process.env.E2E_SCREENSHOT_DIR ?? ''

const PARTS_COURSE = { id: 'partes', title: 'Curso con partes' }
const PART_1 = 'Parte 1 · Primeros pasos'
const PART_2 = 'Parte 2 · Más allá'
const EDITOR = { id: 'partes.el-editor', title: 'El editor' }
const SCRIPTS = { id: 'partes.scripts', title: 'Scripts' }
const PLAIN_COURSE = { id: 'ejemplo-destino', title: 'Curso de destino de ejemplo' }
const PLAIN_LESSON = { id: 'ejemplo-destino.mi-primer-repositorio', title: 'Mi primer repositorio' }

test.use({ viewport: { width: 1440, height: 900 }, locale: 'es-ES' })

async function shot(page: Page, name: string) {
  await page.screenshot({ path: join(screenshotDir, `parts-${name}.png`), fullPage: true })
}

/** Registers a learner of this run and signs in; from then on, no browser error is allowed. */
async function signIn(page: Page, name: string): Promise<string[]> {
  const learner = { username: name, password: `contraseña de ${name} ${randomBytes(4).toString('hex')}` }
  await page.goto('/register')
  await page.getByLabel('Usuario').fill(learner.username)
  await page.getByLabel('Contraseña').fill(learner.password)
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.getByLabel('Usuario').fill(learner.username)
  await page.getByLabel('Contraseña').fill(learner.password)
  await page.getByRole('button', { name: 'Iniciar sesión' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Mis cursos' })).toBeVisible()
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()) })
  return errors
}

/** Marks the open lesson «Completada» and waits for the server's confirmation. */
async function completeLesson(page: Page, lessonId: string) {
  const saved = page.waitForResponse((r) => r.url().endsWith(`/api/me/lessons/${lessonId}/state`) && r.request().method() === 'PUT')
  await page.getByRole('group', { name: 'Estado de la lección' }).first().getByText('Completada', { exact: true }).click()
  expect((await saved).status()).toBe(200)
  await expect(page.locator('.lbar-lesson .badge')).toHaveText('Completada')
}

const indexPart = (page: Page, label: string) => page.getByRole('region', { name: label, exact: true })
const phaseCaption = (scope: Page | Locator, label: string) => scope.locator('caption').filter({ hasText: label })
const sidebar = (page: Page) => page.locator('.phase-nav')
const partToggle = (page: Page, label: string) => sidebar(page).locator('.part-toggle').filter({ hasText: label })
const partBody = (page: Page, label: string) =>
  sidebar(page).locator('.part-nav').filter({ has: page.locator('.part-toggle').filter({ hasText: label }) }).locator('.part-body')
const phaseSummary = (page: Page, label: string) => sidebar(page).locator('summary').filter({ hasText: label })
const phaseDetails = (page: Page, label: string) => sidebar(page).locator('details').filter({ has: page.locator('summary').filter({ hasText: label }) })

test('a course with parts: index, sidebar and «Mis cursos» grouped by part, with their progress (S5, S6, S7)', async ({ page }) => {
  const errors = await signIn(page, 'Dana')

  await test.step('S5: the index shows one block per part, with its progress, and its phases with theirs', async () => {
    await page.goto(`/courses/${PARTS_COURSE.id}`)
    await expect(page.getByRole('heading', { level: 1, name: `${PARTS_COURSE.title} · Índice del curso` })).toBeVisible()
    await expect(page.locator('.part-block')).toHaveCount(2)
    await expect(page.locator('.part-block h2')).toHaveText([PART_1, PART_2])
    await expect(indexPart(page, PART_1).locator('.part-progress')).toHaveText('0 de 4 lecciones')
    // The pending lesson of phase 03 does not count (S8).
    await expect(indexPart(page, PART_2).locator('.part-progress')).toHaveText('0 de 1 lecciones')
    await expect(indexPart(page, PART_1).locator('caption')).toHaveText([
      'Fase 01 · Empezar 2 de 2 disponibles · 0 de 2 completadas',
      'Fase 02 · Seguir 2 de 2 disponibles · 0 de 2 completadas',
    ])
    await expect(indexPart(page, PART_2).locator('caption')).toHaveText(['Fase 03 · Profundizar 1 de 2 disponibles · 0 de 1 completadas'])
    // Every phase is inside its part: no phase table is left outside.
    await expect(page.locator('.index > .phase-grid')).toHaveCount(0)
    await shot(page, 'index-before')
  })

  await test.step('S6: in a lesson, only its part is open; each part and phase with its «N de M»', async () => {
    await indexPart(page, PART_1).getByRole('link', { name: EDITOR.title, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/lessons/${EDITOR.id.replaceAll('.', '\\.')}$`))
    await expect(page.locator('main').getByRole('heading', { level: 1 })).toContainText(EDITOR.title)
    await expect(sidebar(page).locator('.part-toggle')).toHaveCount(2)
    await expect(partToggle(page, PART_1)).toHaveAttribute('aria-expanded', 'true')
    await expect(partToggle(page, PART_2)).toHaveAttribute('aria-expanded', 'false')
    await expect(partBody(page, PART_1)).toBeVisible()
    await expect(partBody(page, PART_2)).toBeHidden()
    await expect(partToggle(page, PART_1).locator('.ph-count')).toHaveText('0 de 4')
    await expect(partToggle(page, PART_2).locator('.ph-count')).toHaveText('0 de 1')
    // Inside the open part, the phases fold as before: only the current lesson's phase is open.
    await expect(phaseSummary(page, 'Fase 01 · Empezar').locator('.ph-count')).toHaveText(['0 de 2', '2/2'])
    await expect(phaseSummary(page, 'Fase 02 · Seguir').locator('.ph-count')).toHaveText(['0 de 2', '2/2'])
    await expect(phaseDetails(page, 'Fase 01 · Empezar')).not.toHaveAttribute('open')
    await expect(phaseDetails(page, 'Fase 02 · Seguir')).toHaveAttribute('open', '')
  })

  await test.step('S6: completing the lesson raises its part and phase counts', async () => {
    await completeLesson(page, EDITOR.id)
    await expect(partToggle(page, PART_1).locator('.ph-count')).toHaveText('1 de 4')
    await expect(phaseSummary(page, 'Fase 02 · Seguir').locator('.ph-count')).toHaveText(['1 de 2', '2/2'])
    await expect(partToggle(page, PART_2).locator('.ph-count')).toHaveText('0 de 1')
  })

  await test.step('S6: the parts open and close with their button (aria-expanded)', async () => {
    await partToggle(page, PART_2).click()
    await expect(partToggle(page, PART_2)).toHaveAttribute('aria-expanded', 'true')
    await expect(partBody(page, PART_2)).toBeVisible()
    await expect(phaseSummary(page, 'Fase 03 · Profundizar').locator('.ph-count')).toHaveText(['0 de 1', '1/2'])
    await shot(page, 'sidebar-both-open')
    await partToggle(page, PART_2).click()
    await expect(partToggle(page, PART_2)).toHaveAttribute('aria-expanded', 'false')
    await expect(partBody(page, PART_2)).toBeHidden()
    await partToggle(page, PART_1).click()
    await expect(partToggle(page, PART_1)).toHaveAttribute('aria-expanded', 'false')
    await expect(partBody(page, PART_1)).toBeHidden()
    await partToggle(page, PART_1).click()
    await expect(partToggle(page, PART_1)).toHaveAttribute('aria-expanded', 'true')
  })

  await test.step('the counts are saved: they survive a reload, in the sidebar and in the index', async () => {
    await page.reload()
    await expect(partToggle(page, PART_1)).toHaveAttribute('aria-expanded', 'true')
    await expect(partToggle(page, PART_2)).toHaveAttribute('aria-expanded', 'false')
    await expect(partToggle(page, PART_1).locator('.ph-count')).toHaveText('1 de 4')
    await expect(phaseSummary(page, 'Fase 02 · Seguir').locator('.ph-count')).toHaveText(['1 de 2', '2/2'])
    await page.goto(`/courses/${PARTS_COURSE.id}`)
    await expect(indexPart(page, PART_1).locator('.part-progress')).toHaveText('1 de 4 lecciones')
    await expect(phaseCaption(indexPart(page, PART_1), 'Fase 02 · Seguir')).toHaveText('Fase 02 · Seguir 2 de 2 disponibles · 1 de 2 completadas')
    await expect(phaseCaption(indexPart(page, PART_1), 'Fase 01 · Empezar')).toHaveText('Fase 01 · Empezar 2 de 2 disponibles · 0 de 2 completadas')
    await expect(indexPart(page, PART_2).locator('.part-progress')).toHaveText('0 de 1 lecciones')
    await expect(indexPart(page, PART_1).getByRole('row', { name: new RegExp(EDITOR.title) })).toContainText('Completada')
    await shot(page, 'index-after')
  })

  const card = page.getByRole('article', { name: PARTS_COURSE.title, exact: true })
  await test.step('S7: «Mis cursos» says which part «Continuar» opens, with its progress', async () => {
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Mis cursos', exact: true }).click()
    await expect(card.getByRole('link', { name: 'Continuar' })).toHaveAttribute('href', `/lessons/${EDITOR.id}`)
    await expect(card.locator('.current-part')).toHaveText('Vas por: 1. Primeros pasos (1 de 4)')
    await shot(page, 'my-courses-part-1')
  })

  await test.step('S6 and S7: a lesson of the other part opens that part only, and «Vas por» follows it', async () => {
    await card.getByRole('link', { name: 'Continuar' }).click()
    await partToggle(page, PART_2).click()
    // The part opens with its phases folded, as the phases of the open part not holding the current lesson.
    await expect(phaseDetails(page, 'Fase 03 · Profundizar')).not.toHaveAttribute('open')
    await phaseSummary(page, 'Fase 03 · Profundizar').click()
    await partBody(page, PART_2).getByRole('link', { name: SCRIPTS.title, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/lessons/${SCRIPTS.id.replaceAll('.', '\\.')}$`))
    await expect(page.locator('main').getByRole('heading', { level: 1 })).toContainText(SCRIPTS.title)
    await expect(partToggle(page, PART_2)).toHaveAttribute('aria-expanded', 'true')
    await expect(partToggle(page, PART_1)).toHaveAttribute('aria-expanded', 'false')
    await expect(partBody(page, PART_1)).toBeHidden()
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Mis cursos', exact: true }).click()
    await expect(card.getByRole('link', { name: 'Continuar' })).toHaveAttribute('href', `/lessons/${SCRIPTS.id}`)
    await expect(card.locator('.current-part')).toHaveText('Vas por: 2. Más allá (0 de 1)')
  })

  expect(errors).toEqual([])
})

test('a course without parts looks as before, with the progress of each phase (S1)', async ({ page }) => {
  const errors = await signIn(page, 'Elena')

  await test.step('the index: no part blocks, its phase with «N de M completadas»', async () => {
    await page.goto(`/courses/${PLAIN_COURSE.id}`)
    await expect(page.getByRole('heading', { level: 1, name: `${PLAIN_COURSE.title} · Índice del curso` })).toBeVisible()
    await expect(page.locator('.part-block')).toHaveCount(0)
    await expect(page.locator('.index > .phase-grid caption')).toHaveText(['Fase 01 · Tu primer proyecto 1 de 1 disponible · 0 de 1 completadas'])
  })

  await test.step('the sidebar: no part buttons, its phase with «N de M»', async () => {
    await page.locator('.phase-grid').getByRole('link', { name: PLAIN_LESSON.title, exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/lessons/${PLAIN_LESSON.id.replaceAll('.', '\\.')}$`))
    await expect(page.locator('main').getByRole('heading', { level: 1 })).toContainText(PLAIN_LESSON.title)
    await expect(sidebar(page).locator('.part-toggle, .part-nav')).toHaveCount(0)
    await expect(phaseSummary(page, 'Fase 01 · Tu primer proyecto').locator('.ph-count')).toHaveText(['0 de 1', '1/1'])
    await completeLesson(page, PLAIN_LESSON.id)
    await expect(phaseSummary(page, 'Fase 01 · Tu primer proyecto').locator('.ph-count')).toHaveText(['1 de 1', '1/1'])
    await page.reload()
    await expect(phaseSummary(page, 'Fase 01 · Tu primer proyecto').locator('.ph-count')).toHaveText(['1 de 1', '1/1'])
    await page.goto(`/courses/${PLAIN_COURSE.id}`)
    await expect(page.locator('.index > .phase-grid caption')).toHaveText(['Fase 01 · Tu primer proyecto 1 de 1 disponible · 1 de 1 completadas'])
  })

  await test.step('«Mis cursos»: no «Vas por» line', async () => {
    // The card asks for the syllabus to know whether the course has parts: wait for that answer before checking.
    const syllabus = page.waitForResponse((r) => new URL(r.url()).pathname === `/api/courses/${PLAIN_COURSE.id}` && r.request().method() === 'GET')
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Mis cursos', exact: true }).click()
    expect((await syllabus).status()).toBe(200)
    const card = page.getByRole('article', { name: PLAIN_COURSE.title, exact: true })
    await expect(card.getByRole('link', { name: 'Continuar' })).toHaveAttribute('href', `/lessons/${PLAIN_LESSON.id}`)
    await expect(card.locator('.current-part')).toHaveCount(0)
    await expect(page.locator('main')).not.toContainText('Vas por')
    await shot(page, 'my-courses-no-parts')
  })

  expect(errors).toEqual([])
})

test('a key in a lesson is drawn as a keycap (<kbd>, S12)', async ({ page }) => {
  const errors = await signIn(page, 'Fermin')
  await page.goto('/lessons/ejemplo.ficheros-json')
  const keys = page.locator('.reading kbd')
  await expect(keys).toHaveText(['Ctrl', 'S'])
  for (const key of await keys.all()) {
    const style = await key.evaluate((element) => {
      const computed = getComputedStyle(element)
      return { width: parseFloat(computed.borderBottomWidth), style: computed.borderBottomStyle }
    })
    expect(style.width).toBeGreaterThan(0)
    expect(style.style).not.toBe('none')
  }
  await keys.first().scrollIntoViewIfNeeded()
  await shot(page, 'kbd')
  expect(errors).toEqual([])
})
