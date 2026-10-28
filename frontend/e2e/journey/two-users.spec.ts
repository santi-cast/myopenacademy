/// <reference lib="dom" />
// H4 closure (PLAN §3, T10): «Dos usuarios conservan avances distintos al reiniciar, sin acceso
// cruzado». Real Chromium, real Vite and real Spring on a disposable database, no stubs: the only
// interception holds one request and then sends it unchanged, to see the UI before the server
// confirms. Run with npm run test:e2e:journey (scripts/e2e-journey.mjs owns the environment).
import { randomBytes } from 'node:crypto'
import { mkdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { expect, test, type BrowserContext, type Locator, type Page } from '@playwright/test'

const controlUrl = process.env.E2E_CONTROL_URL ?? ''
const controlToken = process.env.E2E_CONTROL_TOKEN ?? ''
const runDir = process.env.E2E_RUN_DIR ?? ''
const screenshotDir = process.env.E2E_SCREENSHOT_DIR ?? ''

const LESSON_ID = 'java-02.01'
const LESSON_TITLE = 'Tu primer programa'
const EXERCISES = Array.from({ length: 9 }, (_, index) => `${LESSON_ID}-e0${index + 1}`)
const manifest = JSON.parse(readFileSync(resolve(import.meta.dirname, '../../../content/java/manifest.json'), 'utf8')) as {
  lessons: { id: string; available: boolean }[]
}
const PLANNED = manifest.lessons.length
const AVAILABLE = manifest.lessons.filter((lesson) => lesson.available).length

/** PLAN §3, «Reglas de progreso»: «X de Y lecciones disponibles completadas · P % · Z previstas». */
const progressLine = (completed: number, percentage: number) =>
  `${completed} de ${AVAILABLE} lecciones disponibles completadas · ${percentage}\u00a0% · ${PLANNED} previstas`

type Learner = { username: string; password: string }
// 15+ code points with spaces and a non-ASCII letter; generated per run and never logged.
const ana: Learner = { username: 'Ana', password: `contraseña de Ana ${randomBytes(4).toString('hex')}` }
const bruno: Learner = { username: 'Bruno', password: `contraseña de Bruno ${randomBytes(4).toString('hex')}` }

type ApiResult = { status: number; body: unknown }
type ProgressBody = {
  courseId: string
  added: boolean
  lessonStates: { lessonId: string; state: string; updatedAt: string }[]
  exerciseStates: { exerciseId: string; state: string; updatedAt: string }[]
  continuation: { lastOpenedLessonId: string | null }
  progress: { completedLessons: number; availableLessons: number; plannedLessons: number; percentage: number | null }
}

let shots = 0
async function shot(page: Page, name: string) {
  shots += 1
  await page.screenshot({ path: join(screenshotDir, `${String(shots).padStart(2, '0')}-${name}.png`) })
}

/** Same-origin call from the page, with the session cookie and the CSRF header the app would send. */
async function api(page: Page, method: 'GET' | 'PUT' | 'POST', path: string, body?: unknown): Promise<ApiResult> {
  return page.evaluate(async ({ method, path, body }) => {
    const token = () => document.cookie.split(';').map((part) => part.trim())
      .find((part) => part.startsWith('XSRF-TOKEN='))?.slice('XSRF-TOKEN='.length) ?? ''
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (method !== 'GET') {
      if (token() === '') await fetch('/actuator/health')
      headers['X-XSRF-TOKEN'] = decodeURIComponent(token())
      if (body !== undefined) headers['Content-Type'] = 'application/json'
    }
    const response = await fetch(path, { method, headers, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body) })
    const text = await response.text()
    let parsed: unknown = text
    try {
      parsed = text === '' ? null : JSON.parse(text)
    } catch {
      // Not JSON: keep the text.
    }
    return { status: response.status, body: parsed }
  }, { method, path, body })
}

async function progressOf(page: Page): Promise<ProgressBody> {
  const result = await api(page, 'GET', '/api/me/courses/java/progress')
  expect(result.status).toBe(200)
  return result.body as ProgressBody
}

function statesOf(progress: ProgressBody) {
  return {
    lessons: Object.fromEntries(progress.lessonStates.map((entry) => [entry.lessonId, entry.state])),
    exercises: Object.fromEntries(progress.exerciseStates.map((entry) => [entry.exerciseId, entry.state])),
  }
}

/** Every 403 seen by a page: CSRF_REJECTED or ACCESS_DENIED would both be a defect in this journey. */
function watchForbidden(page: Page, label: string, sink: string[]) {
  page.on('response', (response) => {
    if (response.status() === 403) sink.push(`${label}: ${response.request().method()} ${response.url()}`)
  })
}

async function register(page: Page, learner: Learner) {
  await page.goto('/register')
  await expect(page.getByRole('heading', { level: 1, name: 'Crear una cuenta' })).toBeVisible()
  await page.getByLabel('Usuario').fill(learner.username)
  await page.getByLabel('Contraseña').fill(learner.password)
  const response = page.waitForResponse((r) => r.url().endsWith('/api/auth/register'))
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  expect((await response).status()).toBe(201)
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('status').filter({ hasText: 'Cuenta creada.' })).toBeVisible()
}

async function login(page: Page, learner: Learner) {
  await expect(page.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
  await page.getByLabel('Usuario').fill(learner.username)
  await page.getByLabel('Contraseña').fill(learner.password)
  const response = page.waitForResponse((r) => r.url().endsWith('/api/auth/login'))
  await page.getByRole('button', { name: 'Iniciar sesión' }).click()
  expect((await response).status()).toBe(200)
  await expect(page.getByRole('heading', { level: 1, name: 'Mis cursos' })).toBeVisible()
  await expect(page.getByRole('button', { name: `Menú de usuario: ${learner.username}` })).toBeVisible()
}

async function logout(page: Page) {
  await page.getByRole('button', { name: /^Menú de usuario: / }).click()
  const response = page.waitForResponse((r) => r.url().endsWith('/api/auth/logout'))
  await page.getByRole('button', { name: 'Cerrar sesión' }).click()
  expect((await response).status()).toBe(204)
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
}

const mainNav = (page: Page, name: 'Mis cursos' | 'Catálogo') =>
  page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name, exact: true })
// The lesson's only h1: the delivered HTML's own title («02.01 Tu primer programa») when the body starts with one.
const lessonTitle = (page: Page) => page.locator('main').getByRole('heading', { level: 1 })
const exerciseCard = (page: Page, exerciseId: string) => page.locator(`section.exercise[id="${exerciseId}"]`)
const lessonGroup = (page: Page) => page.getByRole('group', { name: 'Estado de la lección' }).first()

/** Picks a state and waits for the server's confirmation and the «✓ Guardado» that follows it. */
async function markExercise(page: Page, exerciseId: string, label: 'Resuelto' | 'En curso' | 'Pendiente', state: string) {
  const card = exerciseCard(page, exerciseId)
  const group = card.getByRole('group', { name: 'Estado del ejercicio' })
  const response = page.waitForResponse((r) => r.url().endsWith(`/api/me/exercises/${exerciseId}/state`) && r.request().method() === 'PUT')
  await group.getByText(label, { exact: true }).click()
  const confirmed = await response
  expect(confirmed.status()).toBe(200)
  const result = await confirmed.json() as { exerciseId: string; state: string; courseAutoAdded: boolean }
  expect(result).toMatchObject({ exerciseId, state })
  await expect(group.locator('.save-ok')).toHaveText(/^✓ Guardado · (hoy|\d{2}\/\d{2}\/\d{4}), \d{2}:\d{2}$/)
  await expect(card.locator('.ex-head .badge')).toHaveText(label)
  return result
}

async function expectExerciseBadges(page: Page, expected: Record<string, string>) {
  await expect(page.locator('section.exercise')).toHaveCount(EXERCISES.length)
  for (const exerciseId of EXERCISES) {
    await expect(exerciseCard(page, exerciseId).locator('.ex-head .badge')).toHaveText(expected[exerciseId] ?? 'Pendiente')
  }
}

/** No horizontal page scroll; on failure, names the elements that stick out (not those inside a scroller). */
async function expectNoHorizontalScroll(page: Page, label: string) {
  const report = await page.evaluate(() => {
    const root = document.documentElement
    const width = root.clientWidth
    const clipped = (node: Element) => {
      for (let parent = node.parentElement; parent && parent !== document.body; parent = parent.parentElement) {
        if (getComputedStyle(parent).overflowX !== 'visible') return true
      }
      return false
    }
    const offenders = [...document.body.querySelectorAll('*')]
      .filter((node) => node.getBoundingClientRect().right > width + 0.5 && !clipped(node))
      .map((node) => `${node.tagName.toLowerCase()}${node.id ? `#${node.id}` : ''}.${[...node.classList].join('.')} right=${Math.round(node.getBoundingClientRect().right)}`)
    return { overflow: Math.max(root.scrollWidth, document.body.scrollWidth) - width, width, offenders: [...new Set(offenders)].slice(0, 12) }
  })
  // Soft: a layout defect fails the test but the journey still runs to the end.
  expect.soft(report.overflow, `${label}: ${JSON.stringify(report)}`).toBeLessThanOrEqual(0)
}

async function restartBackend() {
  const response = await fetch(`${controlUrl}/backend/restart`, { method: 'POST', headers: { 'x-e2e-token': controlToken } })
  const body = await response.json() as {
    stopped: { pid: number; code: number | null; signal: string | null; forced: boolean }
    started: { pid: number }
  }
  expect(response.status).toBe(200)
  // The JVM handles SIGTERM with a graceful shutdown and exits with 128 + 15.
  expect(body.stopped.forced).toBe(false)
  expect(body.stopped.code === 143 || body.stopped.signal === 'SIGTERM').toBe(true)
  expect(body.started.pid).not.toBe(body.stopped.pid)
  return body
}

/** S9: after the restart the old session is gone; the next screen asks to sign in again. */
async function expectSessionRejected(page: Page, navigation: Locator) {
  const rejected = page.waitForResponse((r) => r.url().includes('/api/') && r.status() === 401)
  await navigation.click()
  const response = await rejected
  expect(await response.json()).toMatchObject({ code: 'AUTHENTICATION_REQUIRED' })
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('status').filter({ hasText: 'Tu sesión terminó. Iniciá sesión de nuevo.' })).toBeVisible()
  await expect(page.locator('.user-btn')).toHaveCount(0)
}

/** After logout, going back shows the login and nothing private. */
async function expectNothingPrivateAfterBack(page: Page, learner: Learner) {
  await page.goBack()
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
  await expect(page.locator('.user-btn, .lesson-layout, .course-card, .exercise')).toHaveCount(0)
  await expect(page.locator('body')).not.toContainText(LESSON_TITLE)
  await expect(page.locator('body')).not.toContainText(`Sesión iniciada como ${learner.username}`)
  await expect(page.getByLabel('Usuario')).toHaveValue('')
  expect((await api(page, 'GET', '/api/me')).status).toBe(401)
}

test('two learners keep separate progress across a backend restart, without cross access', async ({ browser }) => {
  expect(controlUrl && controlToken && runDir && screenshotDir, 'run with npm run test:e2e:journey').toBeTruthy()
  const forbidden: string[] = []
  const contextOptions = { viewport: { width: 1440, height: 900 }, locale: 'es-ES' }
  const contextA: BrowserContext = await browser.newContext(contextOptions)
  const contextB: BrowserContext = await browser.newContext(contextOptions)
  const pageA = await contextA.newPage()
  const pageB = await contextB.newPage()
  watchForbidden(pageA, 'browser A', forbidden)
  watchForbidden(pageB, 'browser B', forbidden)

  await test.step('1. register Ana and Bruno through the UI, then sign Ana in', async () => {
    await register(pageA, ana)
    await shot(pageA, 'registered-login')
    await pageA.getByRole('link', { name: 'Crear una cuenta' }).click()
    await register(pageA, bruno)
    await login(pageA, ana)
    await expect(pageA.getByText('Todavía no añadiste ningún curso.')).toBeVisible()
  })

  await test.step('2a. CSRF: Ana writes right after login («Añadir Java a Mis cursos»)', async () => {
    await mainNav(pageA, 'Catálogo').click()
    const added = pageA.waitForResponse((r) => r.url().endsWith('/api/me/courses/java') && r.request().method() === 'PUT')
    await pageA.getByRole('button', { name: 'Añadir Java a Mis cursos' }).click()
    const response = await added
    expect(response.status()).toBe(200)
    expect(await response.json()).toMatchObject({ courseId: 'java', added: true, continuation: { lastOpenedLessonId: null } })
    await expect(pageA.getByText('Java está en Mis cursos.')).toBeVisible()
    await shot(pageA, 'ana-catalog-added')
    expect(forbidden).toEqual([])
  })

  await test.step('2b. CSRF: logout, Bruno signs in in the same browser and writes at once (e03 Resuelto)', async () => {
    await logout(pageA)
    await login(pageA, bruno)
    // Nothing of Ana's enrollment: Bruno starts with no courses.
    await expect(pageA.getByText('Todavía no añadiste ningún curso.')).toBeVisible()
    const continuationCalls: string[] = []
    pageA.on('request', (request) => {
      if (request.url().endsWith('/continuation')) continuationCalls.push(request.url())
    })
    await mainNav(pageA, 'Catálogo').click()
    await expect(pageA.getByRole('button', { name: 'Añadir Java a Mis cursos' })).toBeVisible()
    await pageA.getByRole('link', { name: 'Java', exact: true }).click()
    await expect(pageA.getByRole('heading', { level: 1, name: 'Java · Índice del curso' })).toBeVisible()
    await expect(pageA.locator('.index-head .summary')).toHaveText(progressLine(0, 0))
    await pageA.locator('.phase-grid').getByRole('link', { name: LESSON_TITLE }).click()
    await expect(lessonTitle(pageA)).toContainText(LESSON_TITLE)
    await expectExerciseBadges(pageA, {})
    const result = await markExercise(pageA, `${LESSON_ID}-e03`, 'Resuelto', 'solved')
    // The first mark added the course: the server says so and the UI tells the learner.
    expect(result.courseAutoAdded).toBe(true)
    await expect(pageA.locator('.toast')).toContainText('Java se añadió a Mis cursos')
    await expect(pageA.locator('.lbar-ex')).toContainText('Ejercicios 1/9')
    // Course not added when the lesson opened: no continuation call (docs/api.md).
    expect(continuationCalls).toEqual([])
    await shot(pageA, 'bruno-first-mark-toast')
    await logout(pageA)
    expect(forbidden).toEqual([])
  })

  let anaSnapshot: ProgressBody
  await test.step('3. Ana opens java-02.01 from Mis cursos and the index, marks lesson, e01 and e02', async () => {
    await login(pageA, ana)
    await pageA.getByRole('link', { name: 'Ver el índice del curso' }).click()
    await expect(pageA.getByRole('heading', { level: 1, name: 'Java · Índice del curso' })).toBeVisible()
    await shot(pageA, 'ana-course-index')
    const continuation = pageA.waitForResponse((r) => r.url().endsWith('/api/me/courses/java/continuation') && r.request().method() === 'PUT')
    await pageA.locator('.phase-grid').getByRole('link', { name: LESSON_TITLE }).click()
    await expect(pageA).toHaveURL(new RegExp(`/lessons/${LESSON_ID.replace('.', '\\.')}$`))
    const continued = await continuation
    expect(continued.status()).toBe(200)
    expect(await continued.json()).toMatchObject({ courseId: 'java', added: true, continuation: { lastOpenedLessonId: LESSON_ID } })

    // The lesson as delivered, enhanced in a real browser.
    await expect(lessonTitle(pageA)).toContainText(LESSON_TITLE)
    await expect(pageA.locator('.lesson-body h2').first()).toBeVisible()
    await expect(pageA.locator('section.exercise')).toHaveCount(9)
    await expect(pageA.locator('.lesson-body figure.diagram svg')).toHaveCount(2)
    // Entities written for GitHub (&quot;) are shown as the characters they stand for, never literally.
    // Mermaid draws each word as its own <tspan>, so compare the label text without spaces.
    const firstDiagram = await pageA.locator('.lesson-body figure.diagram svg').first().evaluate((svg) =>
      Array.from(svg.querySelectorAll('text')).map((text) => text.textContent ?? '').join('|').replace(/\s/g, ''))
    expect(firstDiagram).toContain('IO.println("Pedidorecibido")')
    expect(firstDiagram).toContain('IO.println("Enpreparación")')
    expect(firstDiagram).not.toContain('&quot;')
    await expect(pageA.locator('main').getByRole('heading', { level: 1 })).toHaveCount(1)
    await expect(pageA.locator('.diagram-error')).toHaveCount(0)
    await expect(pageA.locator('.lesson-body code.language-java.hljs').first()).toBeVisible()
    await expect(pageA.locator('.lesson-body code.language-java .hljs-keyword').first()).toBeVisible()
    await expectExerciseBadges(pageA, {})
    await expect(pageA.locator('.lbar-lesson .badge')).toHaveText('No completada')
    await shot(pageA, 'ana-lesson-loaded')
    await pageA.locator('figure.diagram').first().scrollIntoViewIfNeeded()
    await shot(pageA, 'ana-lesson-mermaid')

    // Resource link: downloads with the original file name (S14).
    const helloLink = pageA.locator('.lesson-files a.resource-link').filter({ hasText: /^Hello\.java$/ }).first()
    await expect(helloLink).toBeVisible()
    const [download] = await Promise.all([pageA.waitForEvent('download'), helloLink.click()])
    expect(download.suggestedFilename()).toBe('Hello.java')
    const downloadDir = join(runDir, 'downloads')
    mkdirSync(downloadDir, { recursive: true })
    await download.saveAs(join(downloadDir, 'Hello.java'))
    expect(readFileSync(join(downloadDir, 'Hello.java'), 'utf8')).toMatch(/Hello|mundo/)

    // «✓ Guardado» only after the server confirms: the request is held, then sent unchanged.
    let releaseSave = () => {}
    const held = new Promise<void>((done) => {
      releaseSave = done
    })
    let heldRequests = 0
    await pageA.route('**/api/me/lessons/*/state', async (route) => {
      heldRequests += 1
      await held
      await route.continue()
    })
    const lessonSaved = pageA.waitForResponse((r) => r.url().endsWith(`/api/me/lessons/${LESSON_ID}/state`) && r.request().method() === 'PUT')
    await lessonGroup(pageA).getByText('Completada', { exact: true }).click()
    await expect(lessonGroup(pageA).locator('.save-progress')).toHaveText('Guardando…')
    await expect.poll(() => heldRequests).toBe(1)
    await pageA.waitForTimeout(500)
    await expect(pageA.locator('.save-ok')).toHaveCount(0)
    await expect(pageA.locator('.lbar-lesson .badge')).toHaveText('No completada')
    await shot(pageA, 'ana-lesson-saving')
    releaseSave()
    const lessonResponse = await lessonSaved
    expect(lessonResponse.status()).toBe(200)
    expect(await lessonResponse.json()).toMatchObject({ lessonId: LESSON_ID, state: 'completed', courseAutoAdded: false })
    await expect(lessonGroup(pageA).locator('.save-ok')).toHaveText(/^✓ Guardado · /)
    await expect(pageA.locator('.lbar-lesson .badge')).toHaveText('Completada')
    await pageA.unroute('**/api/me/lessons/*/state')

    expect((await markExercise(pageA, `${LESSON_ID}-e01`, 'Resuelto', 'solved')).courseAutoAdded).toBe(false)
    expect((await markExercise(pageA, `${LESSON_ID}-e02`, 'En curso', 'in_progress')).courseAutoAdded).toBe(false)
    // The course was already added from the catalog: no toast for Ana.
    await expect(pageA.locator('.toast')).toHaveCount(0)
    await expect(pageA.locator('.lbar-ex')).toContainText('Ejercicios 1/9')
    await expect(pageA.locator('.ex-summary')).toHaveText('1 resueltos, 1 en curso, 7 pendientes')
    await shot(pageA, 'ana-lesson-marked')

    anaSnapshot = await progressOf(pageA)
    expect(statesOf(anaSnapshot)).toEqual({
      lessons: { [LESSON_ID]: 'completed' },
      exercises: { [`${LESSON_ID}-e01`]: 'solved', [`${LESSON_ID}-e02`]: 'in_progress' },
    })
    expect(anaSnapshot.continuation.lastOpenedLessonId).toBe(LESSON_ID)
    expect(anaSnapshot.progress).toEqual({ completedLessons: 1, availableLessons: AVAILABLE, plannedLessons: PLANNED, percentage: 100 })

    await mainNav(pageA, 'Mis cursos').click()
    await expect(pageA.locator('.course-card .summary')).toHaveText(progressLine(1, 100))
    await expect(pageA.getByRole('link', { name: 'Continuar', exact: true })).toHaveAttribute('href', `/lessons/${LESSON_ID}`)
    expect(forbidden).toEqual([])
  })

  await test.step('4. Bruno, in his own browser, sees only his e03', async () => {
    await pageB.goto('/login')
    await login(pageB, bruno)
    await expect(pageB.locator('.course-card .summary')).toHaveText(progressLine(0, 0))
    await pageB.getByRole('link', { name: 'Continuar', exact: true }).click()
    await expect(lessonTitle(pageB)).toContainText(LESSON_TITLE)
    await expect(pageB.locator('.lbar-lesson .badge')).toHaveText('No completada')
    await expectExerciseBadges(pageB, { [`${LESSON_ID}-e03`]: 'Resuelto' })
    await expect(pageB.locator('.lbar-ex')).toContainText('Ejercicios 1/9')
    await expect(pageB.locator('.ex-summary')).toHaveText('1 resueltos, 0 en curso, 8 pendientes')
    await shot(pageB, 'bruno-lesson-own-states')
  })

  await test.step('5. Bruno\'s session cannot read or change Ana\'s data', async () => {
    const me = await api(pageA, 'GET', '/api/me')
    expect(me.body).toMatchObject({ username: 'Ana' })
    const anaId = (me.body as { id: string | number }).id
    const brunoMe = await api(pageB, 'GET', '/api/me')
    expect(brunoMe.body).toMatchObject({ username: 'Bruno' })
    expect((brunoMe.body as { id: unknown }).id).not.toEqual(anaId)

    const own = await progressOf(pageB)
    expect(statesOf(own)).toEqual({ lessons: {}, exercises: { [`${LESSON_ID}-e03`]: 'solved' } })
    // There is no user ID in the API: an ID in the query is ignored, never a way to pick the owner.
    const query = `userId=${encodeURIComponent(String(anaId))}&usuario_id=${encodeURIComponent(String(anaId))}&username=Ana`
    const withAnaQuery = await api(pageB, 'GET', `/api/me/courses/java/progress?${query}`)
    expect(withAnaQuery).toEqual({ status: 200, body: own })
    const myCourses = await api(pageB, 'GET', '/api/me/courses')
    expect(await api(pageB, 'GET', `/api/me/courses?${query}`)).toEqual(myCourses)
    expect(await api(pageB, 'GET', `/api/me?${query}`)).toEqual(brunoMe)
    // An ID in the body is an unknown field: rejected, nothing written.
    for (const [path, body] of [
      [`/api/me/exercises/${LESSON_ID}-e01/state`, { state: 'pending', userId: anaId }],
      [`/api/me/lessons/${LESSON_ID}/state`, { state: 'not_completed', userId: anaId }],
      ['/api/me/courses/java/continuation', { lessonId: LESSON_ID, userId: anaId }],
    ] as const) {
      const rejected = await api(pageB, 'PUT', path, body)
      expect(rejected.status).toBe(400)
      expect(rejected.body).toMatchObject({ code: 'INVALID_REQUEST' })
    }
    expect(await progressOf(pageB)).toEqual(own)
    // With Ana's ID in the query a write lands on Bruno's own records, never on Ana's.
    const ownLesson = await api(pageB, 'PUT', `/api/me/lessons/${LESSON_ID}/state?${query}`, { state: 'not_completed' })
    expect(ownLesson.status).toBe(200)
    const ownExercise = await api(pageB, 'PUT', `/api/me/exercises/${LESSON_ID}-e01/state?${query}`, { state: 'pending' })
    expect(ownExercise.status).toBe(200)
    expect(statesOf(await progressOf(pageB))).toEqual({
      lessons: { [LESSON_ID]: 'not_completed' },
      exercises: { [`${LESSON_ID}-e01`]: 'pending', [`${LESSON_ID}-e03`]: 'solved' },
    })
    // Ana's data, read from her own session, is exactly as she left it (updatedAt included).
    expect(await progressOf(pageA)).toEqual(anaSnapshot)
    await pageB.reload()
    await expect(lessonTitle(pageB)).toContainText(LESSON_TITLE)
    await expect(pageB.locator('.lbar-lesson .badge')).toHaveText('No completada')
    await expectExerciseBadges(pageB, { [`${LESSON_ID}-e03`]: 'Resuelto' })
    expect(forbidden).toEqual([])
  })

  await test.step('6. restart the backend: sessions are gone, progress stays (S9)', async () => {
    await pageA.goto(`/lessons/${LESSON_ID}`)
    await expect(lessonTitle(pageA)).toContainText(LESSON_TITLE)
    await restartBackend()
    // Both browsers still hold their old session cookies; the new backend does not know them.
    expect((await contextA.cookies()).some((cookie) => cookie.name === 'JSESSIONID')).toBe(true)
    expect((await contextB.cookies()).some((cookie) => cookie.name === 'JSESSIONID')).toBe(true)
    await expectSessionRejected(pageA, mainNav(pageA, 'Mis cursos'))
    await shot(pageA, 'ana-session-ended-after-restart')
    await expectSessionRejected(pageB, mainNav(pageB, 'Catálogo'))

    await login(pageA, ana)
    await expect(pageA.locator('.course-card .summary')).toHaveText(progressLine(1, 100))
    await shot(pageA, 'ana-my-courses-after-restart')
    await pageA.getByRole('link', { name: 'Continuar', exact: true }).click()
    await expect(pageA).toHaveURL(new RegExp(`/lessons/${LESSON_ID.replace('.', '\\.')}$`))
    await expect(pageA.locator('.lbar-lesson .badge')).toHaveText('Completada')
    await expectExerciseBadges(pageA, { [`${LESSON_ID}-e01`]: 'Resuelto', [`${LESSON_ID}-e02`]: 'En curso' })
    await expect(pageA.locator('.lbar-ex')).toContainText('Ejercicios 1/9')
    await expect(pageA.locator('.ex-summary')).toHaveText('1 resueltos, 1 en curso, 7 pendientes')
    await shot(pageA, 'ana-lesson-after-restart')
    const anaAfter = await progressOf(pageA)
    expect(statesOf(anaAfter)).toEqual(statesOf(anaSnapshot))
    expect(anaAfter.progress).toEqual(anaSnapshot.progress)
    expect(anaAfter.continuation).toEqual({ lastOpenedLessonId: LESSON_ID })

    await login(pageB, bruno)
    await expect(pageB.locator('.course-card .summary')).toHaveText(progressLine(0, 0))
    await pageB.getByRole('link', { name: 'Continuar', exact: true }).click()
    await expect(pageB.locator('.lbar-lesson .badge')).toHaveText('No completada')
    await expectExerciseBadges(pageB, { [`${LESSON_ID}-e03`]: 'Resuelto' })
    await expect(pageB.locator('.ex-summary')).toHaveText('1 resueltos, 0 en curso, 8 pendientes')
    await shot(pageB, 'bruno-lesson-after-restart')
    const brunoAfter = await progressOf(pageB)
    expect(brunoAfter.progress).toEqual({ completedLessons: 0, availableLessons: AVAILABLE, plannedLessons: PLANNED, percentage: 0 })
    expect(brunoAfter.exerciseStates.filter((entry) => entry.state !== 'pending').map((entry) => [entry.exerciseId, entry.state]))
      .toEqual([[`${LESSON_ID}-e03`, 'solved']])
    expect(forbidden).toEqual([])
  })

  await test.step('8. real-browser checks on the lesson (L62)', async () => {
    await contextA.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: new URL(pageA.url()).origin })

    // Theme toggle: the label names the action and flips with the applied theme.
    const toggle = pageA.getByRole('button', { name: /^Modo (oscuro|claro)$/ })
    const before = (await toggle.textContent())?.trim()
    const themeBefore = await pageA.evaluate(() => document.documentElement.dataset.theme)
    await toggle.click()
    const expectedLabel = before === 'Modo oscuro' ? 'Modo claro' : 'Modo oscuro'
    await expect(pageA.getByRole('button', { name: expectedLabel, exact: true })).toBeVisible()
    expect(await pageA.evaluate(() => document.documentElement.dataset.theme)).toBe(before === 'Modo oscuro' ? 'dark' : 'light')
    expect(await pageA.evaluate(() => document.documentElement.dataset.theme)).not.toBe(themeBefore)
    await expect(pageA.locator('.lesson-body figure.diagram svg')).toHaveCount(2)
    await shot(pageA, 'ana-lesson-theme-toggled')
    await pageA.getByRole('button', { name: expectedLabel, exact: true }).click()
    await expect(pageA.getByRole('button', { name: before ?? '', exact: true })).toBeVisible()

    // Focus mode survives a reload.
    await expect(pageA.locator('#lesson-syllabus')).toBeVisible()
    await pageA.getByRole('button', { name: 'Ocultar temario' }).click()
    await expect(pageA.locator('#lesson-syllabus')).toBeHidden()
    await pageA.reload()
    await expect(lessonTitle(pageA)).toContainText(LESSON_TITLE)
    await expect(pageA.getByRole('button', { name: 'Mostrar temario' })).toBeVisible()
    await expect(pageA.locator('#lesson-syllabus')).toBeHidden()
    await shot(pageA, 'ana-focus-mode-after-reload')
    await pageA.getByRole('button', { name: 'Mostrar temario' }).click()
    await expect(pageA.locator('#lesson-syllabus')).toBeVisible()

    // Internal link from the lesson HTML: router navigation, no document reload.
    await pageA.evaluate(() => {
      (window as unknown as { __journeyMarker: string }).__journeyMarker = 'same-document'
    })
    const internal = pageA.locator('.lesson-body a[href^="/courses/"]').first()
    await expect(internal).toBeAttached()
    const internalHref = await internal.getAttribute('href')
    await internal.click()
    await expect(pageA).toHaveURL(new RegExp(`${internalHref}$`))
    await expect(pageA.getByRole('heading', { level: 1, name: 'Java · Índice del curso' })).toBeVisible()
    expect(await pageA.evaluate(() => (window as unknown as { __journeyMarker?: string }).__journeyMarker)).toBe('same-document')
    await pageA.goBack()
    await expect(lessonTitle(pageA)).toContainText(LESSON_TITLE)
    expect(await pageA.evaluate(() => (window as unknown as { __journeyMarker?: string }).__journeyMarker)).toBe('same-document')

    // Fragment link: scrolls to its heading, found by ID (IDs may start with a digit).
    const fragment = pageA.locator('.lesson-body a[href^="#3-errores"]').first()
    const fragmentHref = await fragment.getAttribute('href') ?? ''
    const targetId = decodeURIComponent(fragmentHref.slice(1))
    await fragment.click()
    await expect.poll(() => pageA.evaluate(() => decodeURIComponent(window.location.hash.slice(1)))).toBe(targetId)
    await expect.poll(() => pageA.evaluate((id) => {
      const target = document.getElementById(id)
      if (!target || window.scrollY < 200) return false
      const top = target.getBoundingClientRect().top
      return top >= -2 && top < window.innerHeight / 2
    }, targetId)).toBe(true)
    await shot(pageA, 'ana-fragment-scrolled')

    // Clipboard: «Copiar» puts the code block's text in the clipboard.
    const copyButton = pageA.locator('.lesson-body [data-copy-code]').first()
    const expectedCode = await copyButton.evaluate((button) =>
      button.closest('.code-head')?.nextElementSibling?.querySelector('code')?.textContent ?? '')
    expect(expectedCode.length).toBeGreaterThan(0)
    await copyButton.click()
    await expect(copyButton.locator('.copy-label')).toHaveText('Copiado')
    expect(await pageA.evaluate(() => navigator.clipboard.readText())).toBe(expectedCode)

    // No horizontal page scroll at 1440×900 and 720×900, on the lesson and on the index.
    for (const width of [1440, 720]) {
      await pageA.setViewportSize({ width, height: 900 })
      await pageA.goto(`/lessons/${LESSON_ID}`)
      await expect(pageA.locator('.lesson-body figure.diagram svg')).toHaveCount(2)
      await expectNoHorizontalScroll(pageA, `lesson at ${width}x900`)
      await shot(pageA, `ana-lesson-${width}x900`)
      await pageA.goto('/courses/java')
      await expect(pageA.getByRole('heading', { level: 1, name: 'Java · Índice del curso' })).toBeVisible()
      await expectNoHorizontalScroll(pageA, `index at ${width}x900`)
      await shot(pageA, `ana-index-${width}x900`)
    }
    await pageA.setViewportSize({ width: 1440, height: 900 })

    // Print: only the lesson; header, syllabus and lesson bar hidden.
    await pageA.goto(`/lessons/${LESSON_ID}`)
    await expect(lessonTitle(pageA)).toContainText(LESSON_TITLE)
    await expect(pageA.locator('header.topbar')).toBeVisible()
    await pageA.emulateMedia({ media: 'print' })
    await expect(pageA.locator('header.topbar')).toBeHidden()
    await expect(pageA.locator('#lesson-syllabus')).toBeHidden()
    await expect(pageA.locator('.lbar')).toBeHidden()
    await expect(pageA.locator('.lesson-body')).toBeVisible()
    await shot(pageA, 'ana-lesson-print')
    await pageA.emulateMedia({ media: 'screen' })
    await expect(pageA.locator('header.topbar')).toBeVisible()
    expect(forbidden).toEqual([])
  })

  await test.step('7. logout clears private data; going back shows nothing private', async () => {
    await logout(pageA)
    await expectNothingPrivateAfterBack(pageA, ana)
    await shot(pageA, 'ana-back-after-logout')
    await logout(pageB)
    await expectNothingPrivateAfterBack(pageB, bruno)
    expect(forbidden).toEqual([])
  })

  await contextA.close()
  await contextB.close()
})
