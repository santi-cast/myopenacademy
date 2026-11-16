import { configure, fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { apiGet } from './api/client'
import type { Progress, StageProgress } from './api/types'
import { ana, apiCalls, error, installFakeBackend, type Reply } from './test/fakeBackend'
import { renderApp } from './test/renderApp'
import { stageFixture } from './test/stageFixtures'

// Keep intentional RED failures below the runner's per-test deadline.
configure({ asyncUtilTimeout: 1000 })

const snapshot = () => Promise.all([
  apiGet<StageProgress>('/api/me/courses/java/stage-progress'),
  apiGet<Progress>('/api/me/courses/java/progress'),
])

const writes = (backend: ReturnType<typeof installFakeBackend>) => backend.calls.filter((call) => call.method === 'PUT')

describe('Proyecto del curso', () => {
  it('separates the project index and body from the unchanged lesson index', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend)
    renderApp('/courses/java')
    fireEvent.click(await screen.findByRole('link', { name: 'Proyecto del curso: Mi Biblioteca' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Java · Mi Biblioteca' })).toBeVisible()
    expect(screen.getByText(/0 de 2 etapas disponibles completadas/)).toHaveTextContent('3 previstas')
    expect(screen.queryByRole('link', { name: 'Etapa pendiente' })).not.toBeInTheDocument()
    expect(screen.getByText('Etapa pendiente')).toHaveAccessibleDescription('Etapa pendiente: todavía no disponible, no se puede abrir.')
    fireEvent.click(screen.getByRole('link', { name: 'Primera etapa' }))
    expect(await screen.findByRole('heading', { name: 'Trabajo de Primera etapa' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'proyecto.zip' })).toHaveAttribute('href', '/api/resources/stage-zip')
    expect(screen.getByRole('link', { name: /Segunda etapa/ })).toHaveAttribute('href', '/stages/java-stage-2')
    fireEvent.click(screen.getByText('Orientación'))
    expect(writes(backend)).toEqual([])
  })

  it('saves binary manual state only after confirmation, auto-adds and can undo', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend)
    let confirm!: (reply: Reply) => void
    backend.route('PUT /api/me/stages/java-stage-1/state', () => new Promise<Reply>((resolve) => { confirm = resolve }))
    renderApp('/stages/java-stage-1')
    expect(await screen.findByRole('radio', { name: 'No completada' })).toBeChecked()
    expect(screen.getAllByRole('radio')).toHaveLength(2)
    fireEvent.click(screen.getByRole('radio', { name: 'Completada' }))
    await waitFor(() => expect(confirm).toBeDefined())
    expect(screen.getByText('Guardando…')).toBeVisible()
    expect(screen.getByText(/0 de 2 etapas disponibles/)).toBeVisible()
    expect(screen.getByRole('radio', { name: 'Completada' })).toBeDisabled()
    confirm({ status: 200, body: { courseId: 'java', stageId: 'java-stage-1', state: 'completed', updatedAt: '2026-10-01T10:00:00Z', courseAutoAdded: true, continuation: { lastOpenedStageId: 'java-stage-1' }, progress: { completedStages: 1, availableStages: 2, plannedStages: 3, percentage: 50 } } })
    expect(await screen.findByText(/Guardado/)).toBeVisible()
    expect(screen.getByText('Java se añadió a Mis cursos')).toBeVisible()
    expect(screen.getByText(/1 de 2 etapas disponibles/)).toBeVisible()
    backend.route('PUT /api/me/stages/java-stage-1/state', { status: 200, body: { courseId: 'java', stageId: 'java-stage-1', state: 'not_completed', updatedAt: '2026-10-01T11:00:00Z', courseAutoAdded: false, continuation: { lastOpenedStageId: 'java-stage-1' }, progress: { completedStages: 0, availableStages: 2, plannedStages: 3, percentage: 0 } } })
    fireEvent.click(screen.getByRole('radio', { name: 'No completada' }))
    await waitFor(() => expect(screen.getByRole('radio', { name: 'No completada' })).toBeEnabled())
    expect(screen.getByText(/0 de 2 etapas disponibles/)).toBeVisible()
    expect(writes(backend).map((call) => call.body)).toEqual(['{"state":"completed"}', '{"state":"not_completed"}'])
    for (const call of writes(backend)) {
      expect(call.credentials).toBe('same-origin')
      expect(call.headers['x-xsrf-token']).toBe(call.cookieAtSend)
    }
    expect((await snapshot())[1].progress.plannedLessons).toBe(199)
  })

  it('remembers the last opened stage independently and resumes it', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend, true)
    renderApp('/stages/java-stage-2')
    await screen.findByRole('heading', { name: 'Trabajo de Segunda etapa' })
    await waitFor(() => expect(writes(backend)).toHaveLength(1))
    expect(writes(backend)[0].path).toBe('/api/me/courses/java/stage-continuation')
    expect(writes(backend)[0].body).toBe('{"stageId":"java-stage-2"}')
    fireEvent.click(screen.getByRole('link', { name: 'Proyecto del curso' }))
    expect(await screen.findByRole('link', { name: 'Retomar proyecto' })).toHaveAttribute('href', '/stages/java-stage-2')
    const [project, lesson] = await snapshot()
    expect(lesson.continuation.lastOpenedLessonId).toBe('java-02.01')
    expect(project.continuation.lastOpenedStageId).toBe('java-stage-2')
    expect(project.stageStates).toEqual([])
    expect(writes(backend)).toHaveLength(1)
  })

  it('keeps stored state and both counters unchanged on a rejected update without retry', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend)
    const before = await snapshot()
    backend.route('PUT /api/me/stages/java-stage-1/state', error(500, 'INTERNAL_ERROR'))
    renderApp('/stages/java-stage-1')
    fireEvent.click(await screen.findByRole('radio', { name: 'Completada' }))
    expect(await screen.findByText(/Algo salió mal/)).toBeVisible()
    expect(screen.getByRole('radio', { name: 'No completada' })).toBeChecked()
    expect(screen.queryByText(/Guardado/)).not.toBeInTheDocument()
    expect(screen.getByText(/0 de 2 etapas disponibles/)).toBeVisible()
    expect(await snapshot()).toEqual(before)
    expect(writes(backend)).toHaveLength(1)
  })

  it('auto-adds through manual state without initializing lesson continuation', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend)
    renderApp('/stages/java-stage-1')
    fireEvent.click(await screen.findByRole('radio', { name: 'Completada' }))
    expect(await screen.findByText('Java se añadió a Mis cursos')).toBeVisible()
    const [project, lesson] = await snapshot()
    expect(project.added).toBe(true)
    expect(project.continuation.lastOpenedStageId).toBe('java-stage-1')
    expect(lesson.added).toBe(true)
    expect(lesson.continuation.lastOpenedLessonId).toBeNull()
    expect(lesson.progress).toEqual({ completedLessons: 0, availableLessons: 1, plannedLessons: 199, percentage: 0 })
    expect(lesson.lessonStates).toEqual([])
    expect(lesson.exerciseStates).toEqual([])
    expect(writes(backend).map((call) => call.path)).toEqual(['/api/me/stages/java-stage-1/state'])
  })

  it('clears the private stage view on session expiration', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend)
    backend.route('PUT /api/me/stages/java-stage-1/state', error(401, 'AUTHENTICATION_REQUIRED'))
    renderApp('/stages/java-stage-1')
    fireEvent.click(await screen.findByRole('radio', { name: 'Completada' }))
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
    expect(screen.queryByText('Trabajo de Primera etapa')).not.toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    expect(writes(backend)).toHaveLength(1)
  })

  it('shows unavailable direct stages without reading progress or writing', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend)
    renderApp('/stages/java-stage-3')
    expect(await screen.findByRole('heading', { name: 'Esta etapa todavía no está disponible.' })).toBeVisible()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    expect(apiCalls(backend)).not.toContain('GET /api/me/courses/java/stage-progress')
    expect(writes(backend)).toEqual([])
  })

  it('preserves lesson links, counts and continuation alongside the project link', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend, true)
    renderApp('/courses/java')
    expect(await screen.findByRole('link', { name: 'Continuar: Tu primer programa' })).toHaveAttribute('href', '/lessons/java-02.01')
    expect(screen.getByText(/lecciones disponibles completadas/)).toHaveTextContent('199 previstas')
    fireEvent.click(screen.getByRole('link', { name: 'Tu primer programa' }))
    expect(await screen.findByRole('heading', { name: 'Qué aprenderás' })).toBeVisible()
    expect(screen.getAllByRole('group', { name: 'Estado de la lección' })).toHaveLength(2)
    expect(apiCalls(backend)).not.toContain('PUT /api/me/courses/java/stage-continuation')
  })
})
