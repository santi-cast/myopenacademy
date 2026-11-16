import { act, configure, fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiGet } from './api/client'
import type { Progress } from './api/types'
import { ana, error, installFakeBackend, type Reply } from './test/fakeBackend'
import { renderApp } from './test/renderApp'
import { stageFixture } from './test/stageFixtures'

configure({ asyncUtilTimeout: 1000 })

// Mermaid double at the module boundary: it always succeeds, so any fallback seen on the stage
// page comes from the same gates the lesson view applies (strict policy reuse).
const mermaid = vi.hoisted(() => ({ initialize: vi.fn(), render: vi.fn() }))
vi.mock('mermaid', () => ({ default: mermaid }))
const SVG_NS = 'xmlns="http://www.w3.org/2000/svg"'
beforeEach(() => {
  mermaid.initialize.mockReset()
  mermaid.render.mockReset()
  mermaid.render.mockImplementation(async (id: string) => ({ svg: `<svg ${SVG_NS} id="${id}"><text>ok</text></svg>` }))
})
const escape = (t: string) => t.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
const puts = (b: ReturnType<typeof installFakeBackend>) => b.calls.filter((c) => c.method === 'PUT')

describe('verifier probes', () => {
  it('A: 401 on the automatic stage-continuation PUT clears the private stage view; credentials+CSRF present; no retry', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend, true)
    backend.route('PUT /api/me/courses/java/stage-continuation', error(401, 'AUTHENTICATION_REQUIRED'))
    renderApp('/stages/java-stage-1')
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeVisible()
    const sent = puts(backend)
    expect(sent).toHaveLength(1)
    expect(sent[0].path).toBe('/api/me/courses/java/stage-continuation')
    expect(sent[0].body).toBe('{"stageId":"java-stage-1"}')
    expect(sent[0].credentials).toBe('same-origin')
    expect(sent[0].cookieAtSend).not.toBeNull()
    expect(sent[0].headers['x-xsrf-token']).toBe(sent[0].cookieAtSend)
    expect(screen.queryByText(/Trabajo de Primera etapa/)).not.toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    await act(async () => { await new Promise((r) => setTimeout(r, 120)) })
    expect(puts(backend)).toHaveLength(1)
    expect(backend.calls.filter((c) => c.path === '/api/me/courses/java/stage-continuation')).toHaveLength(1)
  })

  it('B: a stage-state save released after leaving the stage never changes the new stage radios, counters or notice', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend)
    let release!: (reply: Reply) => void
    backend.route('PUT /api/me/stages/java-stage-1/state', () => new Promise<Reply>((resolve) => { release = resolve }))
    renderApp('/stages/java-stage-1')
    fireEvent.click(await screen.findByRole('radio', { name: 'Completada' }))
    await waitFor(() => expect(screen.getByText('Guardando…')).toBeVisible())
    fireEvent.click(screen.getByRole('link', { name: /Segunda etapa/ }))
    expect(await screen.findByRole('heading', { name: 'Trabajo de Segunda etapa' })).toBeVisible()
    await act(async () => {
      release({ status: 200, body: { courseId: 'java', stageId: 'java-stage-1', state: 'completed', updatedAt: '2026-10-01T10:00:00Z', courseAutoAdded: true, continuation: { lastOpenedStageId: 'java-stage-1' }, progress: { completedStages: 1, availableStages: 2, plannedStages: 3, percentage: 50 } } })
      await new Promise((r) => setTimeout(r, 120))
    })
    expect(screen.getByRole('radio', { name: 'No completada' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'Completada' })).not.toBeChecked()
    expect(screen.getByText(/0 de 2 etapas disponibles completadas/)).toBeVisible()
    expect(screen.queryByText(/Guardado/)).not.toBeInTheDocument()
    expect(screen.queryByText(/se añadió a Mis cursos/)).not.toBeInTheDocument()
    expect(screen.queryByText('Guardando…')).not.toBeInTheDocument()
    expect(puts(backend).map((c) => c.path)).toEqual(['/api/me/stages/java-stage-1/state'])
  })

  it('C: 199-lesson denominator plus lesson/exercise states and lesson continuation survive a stage state change', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend, true)
    const rich: Progress = {
      courseId: 'java', added: true,
      lessonStates: [
        { lessonId: 'java-02.01', state: 'completed', updatedAt: '2026-09-01T10:00:00Z' },
        { lessonId: 'java-02.02', state: 'not_completed', updatedAt: null },
      ],
      exerciseStates: [{ exerciseId: 'java-02.01-e1', state: 'solved', updatedAt: '2026-09-02T10:00:00Z' }],
      continuation: { lastOpenedLessonId: 'java-02.01' },
      progress: { completedLessons: 1, availableLessons: 4, plannedLessons: 199, percentage: 25 },
    }
    backend.route('GET /api/me/courses/java/progress', { status: 200, body: rich })
    renderApp('/stages/java-stage-1')
    const before = await apiGet<Progress>('/api/me/courses/java/progress')
    expect(before.progress.plannedLessons).toBe(199)
    fireEvent.click(await screen.findByRole('radio', { name: 'Completada' }))
    expect(await screen.findByText(/Guardado/)).toBeVisible()
    const after = await apiGet<Progress>('/api/me/courses/java/progress')
    expect(after).toEqual(before)
    expect(after.progress.plannedLessons).toBe(199)
    expect(after.lessonStates).toHaveLength(2)
    expect(after.exerciseStates).toEqual(rich.exerciseStates)
    expect(after.continuation.lastOpenedLessonId).toBe('java-02.01')
    expect(backend.calls.filter((c) => c.method === 'PUT' && c.path === '/api/me/courses/java')).toHaveLength(0)
  })

  it('D1: the project index lists an unavailable stage as unopenable and makes no request', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend, true)
    renderApp('/courses/java/project')
    expect(await screen.findByRole('heading', { level: 1, name: /^Java · / })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Etapa pendiente' })).not.toBeInTheDocument()
    expect(screen.getByText('Todavía no disponible')).toBeVisible()
    const beforeCalls = backend.calls.length
    fireEvent.click(screen.getByText('Etapa pendiente'))
    await act(async () => { await new Promise((r) => setTimeout(r, 60)) })
    expect(backend.calls.length).toBe(beforeCalls)
    expect(screen.queryByRole('heading', { name: 'Trabajo de Etapa pendiente' })).not.toBeInTheDocument()
  })

  it('D2: a direct unavailable stage URL shows the unavailable notice and reads no private progress', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend, true)
    renderApp('/stages/java-stage-3')
    expect(await screen.findByRole('heading', { name: 'Esta etapa todavía no está disponible.' })).toBeVisible()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    expect(screen.queryByText(/etapas disponibles completadas/)).not.toBeInTheDocument()
    expect(backend.calls.some((c) => c.path === '/api/me/stages/java-stage-3/state')).toBe(false)
    expect(backend.calls.some((c) => c.path === '/api/me/courses/java/stage-progress')).toBe(false)
    expect(puts(backend)).toHaveLength(0)
  })

  it('E: stage body reuses the strict lesson diagram gate and the code-reading aids', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    stageFixture(backend, true)
    const body = '<h2 id="trabajo">Trabajo de Primera etapa</h2>'
      + `<pre><code class="language-mermaid">${escape('flowchart LR\n A["<b>x</b>"]\n click A href "https://evil.example"')}</code></pre>\n`
      + `<pre><code class="language-mermaid">${escape('flowchart LR\n A-->B')}</code></pre>\n`
      + '<pre><code class="language-java">class A {}</code></pre>\n'
    backend.route('GET /api/stages/java-stage-1', () => ({
      status: 200,
      body: {
        stage: { id: 'java-stage-1', courseId: 'java', title: 'Primera etapa', position: 1, available: true },
        bodyHtml: body, resources: [], previousStageId: null, nextStageId: 'java-stage-2',
      },
    }))
    renderApp('/stages/java-stage-1')
    expect(await screen.findByRole('heading', { name: 'Trabajo de Primera etapa' })).toBeVisible()
    await waitFor(() => expect(document.querySelectorAll('.lesson-body [data-ui-diagram]').length).toBeGreaterThan(0))
    const diagrams = [...document.querySelectorAll('.lesson-body [data-ui-diagram]')]
    expect(diagrams.length).toBe(2)
    const refused = diagrams.filter((d) => d.classList.contains('diagram-error'))
    expect(refused).toHaveLength(1)
    expect(document.querySelectorAll('.lesson-body svg')).toHaveLength(1)
    expect(document.querySelector('.lesson-body svg a[href^="https:"], .lesson-body svg foreignObject, .lesson-body img')).toBeNull()
    const copy = document.querySelector('.lesson-body [data-copy-code]')
    expect(copy).not.toBeNull()
    await waitFor(() => expect(document.querySelector('.lesson-body pre code.language-java')!.innerHTML).toMatch(/hljs/))
  })
})
