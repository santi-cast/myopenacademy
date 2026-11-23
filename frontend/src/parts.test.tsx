import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { DocumentEntry, LessonEntry, Part, Progress } from './api/types'
import { ana, apiCalls, counts, installFakeBackend, javaCourse, javaPhases, notAddedProgress, type FakeBackend } from './test/fakeBackend'
import { lessonView } from './test/lessonFixtures'
import { renderApp } from './test/renderApp'

// H12, T4 (S1, S5 to S8): the parts of a course in the index, the lesson syllabus and «Mis cursos».
const mermaid = vi.hoisted(() => ({ initialize: vi.fn(), render: vi.fn() }))
vi.mock('mermaid', () => ({ default: mermaid }))

beforeEach(() => {
  Object.defineProperty(Element.prototype, 'scrollIntoView', { configurable: true, value: () => {} })
})

afterEach(() => {
  delete (Element.prototype as Partial<Element>).scrollIntoView
})

const [phase00, phase02, phase03] = javaPhases.map((phase) => phase.id)

const lesson = (id: string, title: string, position: number, phaseId: string, available = true): LessonEntry =>
  ({ id, courseId: 'java', title, position, phaseId, available })

const lessons: LessonEntry[] = [
  lesson('java-00.01', 'Qué es programar', 1, phase00),
  lesson('java-00.02', 'Algoritmos', 2, phase00),
  lesson('java-02.01', 'Tu primer programa', 3, phase02),
  lesson('java-02.02', 'Variables y tipos de datos', 4, phase02),
  lesson('java-03.01', 'Clases y objetos', 5, phase03),
  lesson('java-03.02', 'Herencia', 6, phase03, false),
  lesson('java-03.03', 'Interfaces', 7, phase03),
]

// Out of order in the response: parts follow their position, phases the order of phaseIds.
const parts: Part[] = [
  { id: 'java.avanzado', title: 'Java avanzado', position: 2, phaseIds: [phase03] },
  { id: 'java.basico', title: 'Java básico', position: 1, phaseIds: [phase02, phase00] },
]

// The challenges of phase 02 have no progress: they never count (S8).
const retos: DocumentEntry = { id: 'java.fase.fundamentos.retos', courseId: 'java', kind: 'retos', title: 'Retos', phaseId: phase02 }

const completedAt = (lessonId: string) => ({ lessonId, state: 'completed' as const, updatedAt: '2026-11-20T10:00:00Z' })

function progress(lastOpenedLessonId: string | null = 'java-02.01'): Progress {
  return {
    ...notAddedProgress(),
    added: true,
    // 03.02 is pending: its saved state does not count, like in the course progress.
    lessonStates: ['java-00.01', 'java-02.01', 'java-03.01', 'java-03.02'].map(completedAt),
    continuation: { lastOpenedLessonId },
    progress: counts(3, 6, 7),
  }
}

function routeCourse(backend: FakeBackend, withParts = true) {
  backend.route('GET /api/courses/java', { status: 200, body: {
    course: javaCourse, phases: javaPhases, lessons, documents: [retos], project: null, parts: withParts ? parts : [],
  } })
  backend.route('GET /api/me/courses/java/progress', { status: 200, body: progress() })
}

const captions = () => screen.getAllByRole('table').map((table) => table.querySelector('caption')?.textContent)

describe('Índice del curso con partes (S5, S8)', () => {
  it('shows each part as a block with its title and progress, and its phases in its order, each with its progress', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeCourse(backend)
    renderApp('/courses/java')

    await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })
    const blocks = screen.getAllByRole('region')
    expect(blocks).toHaveLength(2)
    expect(blocks[0]).toHaveAccessibleName('Parte 1 · Java básico')
    expect(within(blocks[0]).getByRole('heading', { level: 2, name: 'Parte 1 · Java básico' })).toBeVisible()
    expect(within(blocks[0]).getByText('2 de 4 lecciones')).toBeVisible()
    expect(within(blocks[0]).getAllByRole('table').map((table) => table.querySelector('caption')?.textContent)).toEqual([
      'Fase 02 · Fundamentos de Java 2 de 2 disponibles · 1 de 2 completadas',
      'Fase 01 · Lógica de programación 2 de 2 disponibles · 1 de 2 completadas',
    ])
    expect(blocks[1]).toHaveAccessibleName('Parte 2 · Java avanzado')
    // 03.02 is completed but pending: it counts neither as available nor as completed.
    expect(within(blocks[1]).getByText('1 de 2 lecciones')).toBeVisible()
    expect(within(blocks[1]).getAllByRole('table').map((table) => table.querySelector('caption')?.textContent)).toEqual([
      'Fase 03 · Programación orientada a objetos 2 de 3 disponibles · 1 de 2 completadas',
    ])
    // The modules are as before, with their code, link and state; the challenges close their phase.
    const phase02Table = within(blocks[0]).getAllByRole('table')[0]
    expect(within(phase02Table).getByRole('link', { name: 'Tu primer programa' })).toHaveAttribute('href', '/lessons/java-02.01')
    expect(within(phase02Table).getByText('02.01')).toBeVisible()
    expect(within(phase02Table).getByRole('link', { name: 'Retos de la fase' })).toBeVisible()
  })
})

describe('Índice del curso sin partes (S1)', () => {
  it('keeps the phases as today, with no part blocks, and adds the progress of each phase', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeCourse(backend, false)
    renderApp('/courses/java')

    await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })
    expect(screen.queryAllByRole('region')).toHaveLength(0)
    expect(screen.queryByRole('heading', { level: 2 })).not.toBeInTheDocument()
    expect(screen.queryByText(/ lecciones$/)).not.toBeInTheDocument()
    expect(captions()).toEqual([
      'Fase 01 · Lógica de programación 2 de 2 disponibles · 1 de 2 completadas',
      'Fase 02 · Fundamentos de Java 2 de 2 disponibles · 1 de 2 completadas',
      'Fase 03 · Programación orientada a objetos 2 de 3 disponibles · 1 de 2 completadas',
    ])
  })
})

async function openLesson(withParts: boolean) {
  const backend = installFakeBackend({ loggedInAs: ana })
  routeCourse(backend, withParts)
  backend.route('GET /api/lessons/java-02.01', { status: 200, body: lessonView({ lesson: lessons[2], nextLessonId: null }) })
  renderApp('/lessons/java-02.01')
  await screen.findByText(/posición/)
  return screen.getByRole('navigation', { name: 'Temario del curso' })
}

const summaries = (scope: HTMLElement) => [...scope.querySelectorAll('summary')].map((summary) => summary.textContent)

describe('Menú lateral con partes (S6, S8)', () => {
  it('opens only the part of the current lesson; every part and phase shows its «N de M»', async () => {
    const syllabus = await openLesson(true)
    const toggles = within(syllabus).getAllByRole('button', { name: /^Parte / })
    expect(toggles.map((toggle) => toggle.textContent)).toEqual(['Parte 1 · Java básico2 de 4', 'Parte 2 · Java avanzado1 de 2'])
    expect(toggles[0]).toHaveAttribute('aria-expanded', 'true')
    expect(toggles[1]).toHaveAttribute('aria-expanded', 'false')

    const first = document.getElementById(toggles[0].getAttribute('aria-controls')!)!
    const second = document.getElementById(toggles[1].getAttribute('aria-controls')!)!
    expect(first).toBeVisible()
    expect(second).not.toBeVisible()
    expect(summaries(first)).toEqual([
      'Fase 02 · Fundamentos de Java1 de 22/2',
      'Fase 01 · Lógica de programación1 de 22/2',
    ])
    expect(summaries(second)).toEqual(['Fase 03 · Programación orientada a objetos1 de 22/3'])
    // Inside the open part, the phases stay collapsible as before: the current phase is open.
    expect(first.querySelectorAll('details')[0]).toHaveAttribute('open')
    expect(first.querySelectorAll('details')[1]).not.toHaveAttribute('open')
    expect(within(first).getByRole('link', { name: 'Tu primer programa' })).toHaveAttribute('aria-current', 'page')

    fireEvent.click(toggles[1])
    expect(toggles[1]).toHaveAttribute('aria-expanded', 'true')
    expect(second).toBeVisible()
    fireEvent.click(toggles[0])
    expect(toggles[0]).toHaveAttribute('aria-expanded', 'false')
    expect(first).not.toBeVisible()
  })
})

describe('Menú lateral sin partes (S1)', () => {
  it('lists the phases as today, with no part toggles, and each phase with its «N de M»', async () => {
    const syllabus = await openLesson(false)
    expect(within(syllabus).queryByRole('button', { name: /^Parte / })).not.toBeInTheDocument()
    expect(summaries(syllabus)).toEqual([
      'Fase 01 · Lógica de programación1 de 22/2',
      'Fase 02 · Fundamentos de Java1 de 22/2',
      'Fase 03 · Programación orientada a objetos1 de 22/3',
    ])
    const details = syllabus.querySelectorAll('details')
    expect([...details].map((item) => item.hasAttribute('open'))).toEqual([false, true, false])
  })
})

function myCourses(lastOpenedLessonId: string | null) {
  return { status: 200, body: { courses: [{
    course: javaCourse,
    enrollment: { courseId: 'java', added: true, continuation: { lastOpenedLessonId }, progress: counts(3, 6, 7) },
  }] } }
}

describe('Mis cursos con partes (S7, S8)', () => {
  it('says which part «Continuar» opens, with its number and its progress', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeCourse(backend)
    backend.route('GET /api/me/courses', myCourses('java-03.03'))
    renderApp('/')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(within(course).getByRole('link', { name: 'Continuar' })).toHaveAttribute('href', '/lessons/java-03.03')
    expect(await within(course).findByText(/^Vas por:/)).toHaveTextContent(/^Vas por: 2\. Java avanzado \(1 de 2\)$/)
  })

  it('uses the part of the lesson that «Continuar» opens', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeCourse(backend)
    backend.route('GET /api/me/courses', myCourses('java-00.02'))
    renderApp('/')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(await within(course).findByText(/^Vas por:/)).toHaveTextContent(/^Vas por: 1\. Java básico \(2 de 4\)$/)
  })
})

describe('Mis cursos sin partes (S1)', () => {
  it('shows no «Vas por» line for a course without parts', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeCourse(backend, false)
    backend.route('GET /api/me/courses', myCourses('java-02.01'))
    renderApp('/')

    const course = await screen.findByRole('article', { name: 'Java' })
    await waitFor(() => expect(apiCalls(backend)).toContain('GET /api/courses/java'))
    // Let the syllabus answer settle before checking that nothing was added.
    await new Promise((done) => setTimeout(done, 0))
    expect(within(course).queryByText(/Vas por/)).not.toBeInTheDocument()
    expect(within(course).getByRole('link', { name: 'Continuar' })).toBeVisible()
  })

  it('shows no «Vas por» line without a lesson to continue', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeCourse(backend)
    backend.route('GET /api/me/courses', myCourses(null))
    renderApp('/')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(within(course).getByRole('link', { name: 'Ver el índice del curso' })).toBeVisible()
    expect(within(course).queryByText(/Vas por/)).not.toBeInTheDocument()
  })
})
