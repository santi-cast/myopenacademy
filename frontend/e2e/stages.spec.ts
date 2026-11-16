import { expect, test } from '@playwright/test'

// Browser UI proof only: every API/health request is synthetic; no backend or DB.
test('project reading, manual confirmation and independent continuation', async ({ page, context }) => {
  const browserErrors: string[] = []
  page.on('pageerror', (error) => browserErrors.push(error.message))
  page.on('console', (message) => { if (message.type() === 'error') browserErrors.push(message.text()) })
  await context.addCookies([{ name: 'XSRF-TOKEN', value: 'synthetic-token', url: 'http://127.0.0.1:5173' }])
  const stages = [
    { id: 'java-stage-one', courseId: 'java', title: 'Primera etapa', position: 1, available: true },
    { id: 'java-stage-two', courseId: 'java', title: 'Segunda etapa', position: 2, available: true },
    { id: 'java-stage-pending', courseId: 'java', title: 'Etapa pendiente', position: 3, available: false },
  ]
  const counts = { completedLessons: 1, availableLessons: 199, plannedLessons: 199, percentage: 100 / 199 }
  const lesson = {
    id: 'java-lesson', courseId: 'java', title: 'Lección conservada', position: 1, phaseId: 'java-phase', available: true,
  }
  let added = false
  let last: string | null = null
  let completed = false
  let release!: () => void
  const confirmation = new Promise<void>((resolve) => { release = resolve })
  const writes: { path: string; body: unknown; csrf: string | undefined }[] = []
  const progress = () => ({
    courseId: 'java', added,
    stageStates: completed ? [{ stageId: stages[0].id, state: 'completed', updatedAt: '2026-10-01T10:00:00Z' }] : [],
    continuation: { lastOpenedStageId: last },
    progress: { completedStages: completed ? 1 : 0, availableStages: 2, plannedStages: 3, percentage: completed ? 50 : 0 },
  })
  await page.route('**/actuator/health', (route) => route.fulfill({ json: { status: 'UP' } }))
  // A broad **/api/** glob also catches Vite's /src/api/client.ts module and
  // prevents React from bootstrapping. Only application API paths are synthetic.
  await page.route((url) => url.pathname.startsWith('/api/'), async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    const json = (body: unknown) => route.fulfill({ json: body })
    if (method === 'PUT') {
      writes.push({ path, body: request.postDataJSON(), csrf: request.headers()['x-xsrf-token'] })
      if (request.headers()['x-xsrf-token'] !== 'synthetic-token') {
        return route.fulfill({ status: 403, json: { code: 'CSRF_REJECTED', message: 'Rejected.' } })
      }
      if (path === '/api/me/stages/java-stage-one/state') {
        await confirmation
        const courseAutoAdded = !added
        added = true
        completed = request.postDataJSON().state === 'completed'
        if (courseAutoAdded) last = stages[0].id
        const saved = progress()
        return json({ courseId: 'java', stageId: stages[0].id, state: completed ? 'completed' : 'not_completed', updatedAt: '2026-10-01T10:00:00Z', courseAutoAdded, continuation: saved.continuation, progress: saved.progress })
      }
      if (path === '/api/me/courses/java/stage-continuation' && added) {
        last = request.postDataJSON().stageId
        return json(progress())
      }
    }
    if (method === 'GET') {
      if (path === '/api/me') return json({ id: 'synthetic-user', username: 'Ana' })
      if (path === '/api/courses/java') return json({ course: { id: 'java', title: 'Java' }, phases: [{ id: 'java-phase', title: 'Sintética', position: 1 }], lessons: [lesson] })
      if (path === '/api/me/courses/java/progress') return json({ courseId: 'java', added, lessonStates: [{ lessonId: lesson.id, state: 'completed', updatedAt: '2026-10-01T09:00:00Z' }], exerciseStates: [], continuation: { lastOpenedLessonId: lesson.id }, progress: counts })
      if (path === '/api/courses/java/stages') return json({ courseId: 'java', stages })
      if (path === '/api/me/courses/java/stage-progress') return json(progress())
      const stage = stages.find((entry) => path === `/api/stages/${entry.id}` && entry.available)
      if (stage) return json({
        stage, bodyHtml: `<h2 id="trabajo">Trabajo de ${stage.title}</h2><details><summary>Orientación</summary><p>Sin corrección automática.</p></details>`,
        resources: [{ resourceId: 'zip', url: '/api/resources/zip', mediaType: 'application/zip', fileName: 'proyecto.zip' }],
        previousStageId: stage.position === 2 ? stages[0].id : null,
        nextStageId: stage.position === 1 ? stages[1].id : null,
      })
    }
    // Unknown requests never reach the dev proxy or a real service.
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: 'Synthetic route not found.' } })
  })

  const clientModule = page.waitForResponse((response) => new URL(response.url()).pathname === '/src/api/client.ts')
  await page.goto('/courses/java')
  expect((await clientModule).status()).toBe(200)
  await expect(page).toHaveTitle('MyOpenAcademy')
  await expect(page.getByText(/lecciones disponibles completadas/)).toContainText('199 previstas')
  await expect(page.getByRole('link', { name: 'Continuar: Lección conservada' })).toHaveAttribute('href', '/lessons/java-lesson')
  await page.getByRole('link', { name: 'Proyecto evolutivo' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Java · Proyecto evolutivo')
  await expect(page.getByRole('link', { name: 'Etapa pendiente' })).toHaveCount(0)
  await page.getByRole('link', { name: 'Primera etapa' }).click()
  await expect(page.getByRole('heading', { name: 'Trabajo de Primera etapa' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'proyecto.zip' })).toHaveAttribute('href', '/api/resources/zip')
  await page.getByText('Orientación', { exact: true }).click()
  expect(writes).toEqual([])
  await page.getByRole('group', { name: 'Estado de la etapa' }).getByText('Completada', { exact: true }).click()
  await expect(page.getByText('Guardando…')).toBeVisible()
  await expect(page.getByText(/0 de 2 etapas disponibles/)).toBeVisible()
  release()
  await expect(page.getByText(/Guardado/)).toBeVisible()
  await expect(page.getByText('Java se añadió a Mis cursos')).toBeVisible()
  await expect(page.getByText(/1 de 2 etapas disponibles/)).toBeVisible()
  await page.getByRole('link', { name: /Segunda etapa/ }).click()
  await expect(page.getByRole('heading', { name: 'Trabajo de Segunda etapa' })).toBeVisible()
  await expect.poll(() => last).toBe(stages[1].id)
  await page.getByRole('link', { name: 'Proyecto evolutivo', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Retomar proyecto' })).toHaveAttribute('href', '/stages/java-stage-two')
  await page.getByRole('link', { name: 'Índice del curso', exact: true }).click()
  await expect(page.getByRole('link', { name: 'Continuar: Lección conservada' })).toHaveAttribute('href', '/lessons/java-lesson')
  await expect(page.getByText(/lecciones disponibles completadas/)).toContainText('199 previstas')
  expect(writes.map(({ path }) => path)).toEqual(['/api/me/stages/java-stage-one/state', '/api/me/courses/java/stage-continuation'])
  expect(writes.every(({ csrf }) => csrf === 'synthetic-token')).toBe(true)
  expect(browserErrors).toEqual([])
})
