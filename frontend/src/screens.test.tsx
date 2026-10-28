import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Counts, LessonEntry } from './api/types'
import { ana, apiCalls, counts, error, installFakeBackend, javaCourse, javaLessons, notAddedProgress, type Reply } from './test/fakeBackend'
import { renderApp } from './test/renderApp'

function myCourses(progress: Counts, lastOpenedLessonId: string | null = null): Reply {
  return {
    status: 200,
    body: {
      courses: [{
        course: javaCourse,
        enrollment: { courseId: 'java', added: true, continuation: { lastOpenedLessonId }, progress },
      }],
    },
  }
}

function deferred() {
  let resolve!: (reply: Reply) => void
  const promise = new Promise<Reply>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('Mis cursos', () => {
  it.each([
    ['none completed', counts(0, 1, 199), '0 de 1 lecciones disponibles completadas · 0 % · 199 previstas'],
    ['all completed', counts(1, 1, 199), '1 de 1 lecciones disponibles completadas · 100 % · 199 previstas'],
    ['a fraction, formatted es-ES', counts(12, 129, 199), '12 de 129 lecciones disponibles completadas · 9,3 % · 199 previstas'],
    ['a third', counts(1, 3, 10), '1 de 3 lecciones disponibles completadas · 33,3 % · 10 previstas'],
    ['zero available, no division', counts(0, 0, 199), 'sin lecciones disponibles · 199 previstas'],
  ])('shows the progress in the contract format: %s', async (_name, progress, text) => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses', myCourses(progress))
    renderApp('/')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(within(course).getByText(/lecciones disponibles|sin lecciones/)).toHaveTextContent(text)
  })

  it('uses the server percentage, not a recomputation', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses', myCourses({ completedLessons: 2, availableLessons: 3, plannedLessons: 9, percentage: 66.66666666666667 }))
    renderApp('/')

    expect(await screen.findByText(/lecciones disponibles completadas/))
      .toHaveTextContent('2 de 3 lecciones disponibles completadas · 66,7 % · 9 previstas')
  })

  it('links «Continuar» to the last opened lesson when there is one', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses', myCourses(counts(0, 1, 199), 'java-02.01'))
    renderApp('/')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(within(course).getByRole('link', { name: 'Continuar' })).toHaveAttribute('href', '/lessons/java-02.01')
    expect(within(course).queryByRole('link', { name: 'Ver el índice del curso' })).not.toBeInTheDocument()
  })

  it('links to the course index when there is no continuation', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses', myCourses(counts(0, 1, 199)))
    renderApp('/')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(within(course).getByRole('link', { name: 'Ver el índice del curso' })).toHaveAttribute('href', '/courses/java')
    expect(within(course).queryByRole('link', { name: 'Continuar' })).not.toBeInTheDocument()
  })

  it('offers the catalog when no course was added', async () => {
    installFakeBackend({ loggedInAs: ana })
    renderApp('/')

    expect(await screen.findByText('Todavía no añadiste ningún curso.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Explorar el catálogo' })).toHaveAttribute('href', '/courses')
  })

  it('reports a load failure without inventing data', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses', error(500, 'INTERNAL_ERROR'))
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Algo salió mal.')
    expect(screen.queryByText('Todavía no añadiste ningún curso.')).not.toBeInTheDocument()
  })
})

describe('Catálogo', () => {
  it('lists courses with a link to the index', async () => {
    installFakeBackend({ loggedInAs: ana })
    renderApp('/courses')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(within(course).getByRole('link', { name: 'Java' })).toHaveAttribute('href', '/courses/java')
  })

  it('shows the course as added only after the server confirms with 200', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    const pending = deferred()
    backend.route('PUT /api/me/courses/java', () => pending.promise)
    renderApp('/courses')

    fireEvent.click(await screen.findByRole('button', { name: 'Añadir Java a Mis cursos' }))

    expect(await screen.findByRole('button', { name: 'Añadiendo Java…' })).toBeDisabled()
    expect(screen.queryByText('Java está en Mis cursos.')).not.toBeInTheDocument()

    pending.resolve({
      status: 200,
      body: { courseId: 'java', added: true, continuation: { lastOpenedLessonId: null }, progress: counts(0, 1, 199) },
    })
    expect(await screen.findByText('Java está en Mis cursos.')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Añadir Java/ })).not.toBeInTheDocument()
    expect(apiCalls(backend).filter((call) => call.startsWith('PUT'))).toEqual(['PUT /api/me/courses/java'])
  })

  it('a failed add shows an error and keeps the course as not added', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('PUT /api/me/courses/java', error(500, 'INTERNAL_ERROR'))
    renderApp('/courses')

    fireEvent.click(await screen.findByRole('button', { name: 'Añadir Java a Mis cursos' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo añadir Java. Algo salió mal.')
    expect(screen.getByRole('button', { name: 'Añadir Java a Mis cursos' })).toBeEnabled()
    expect(screen.queryByText('Java está en Mis cursos.')).not.toBeInTheDocument()
  })

  it('shows courses already in Mis cursos as added, from the server list', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses', myCourses(counts(0, 1, 199)))
    renderApp('/courses')

    expect(await screen.findByText('Java está en Mis cursos.')).toBeVisible()
    expect(screen.queryByRole('button', { name: /Añadir Java/ })).not.toBeInTheDocument()
  })
})

describe('Índice del curso', () => {
  it('groups the full syllabus by phase with availability and state', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses/java/progress', {
      status: 200,
      body: {
        ...notAddedProgress(),
        added: true,
        lessonStates: [{ lessonId: 'java-02.01', state: 'completed', updatedAt: '2026-10-01T10:00:00Z' }],
        progress: counts(1, 1, 199),
      },
    })
    renderApp('/courses/java')

    expect(await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })).toBeVisible()
    expect(screen.getByText(/lecciones disponibles completadas/)).toHaveTextContent('1 de 1 lecciones disponibles completadas · 100 % · 199 previstas')
    const tables = screen.getAllByRole('table')
    expect(tables).toHaveLength(3)
    expect(tables[0]).toHaveAccessibleName('Fase 00 0 de 2 disponibles')
    expect(tables[1]).toHaveAccessibleName('Fase 02 1 de 2 disponibles')
    expect(tables[2]).toHaveAccessibleName('Fase 03 0 de 1 disponibles')
    const phase02 = tables[1]
    const rows = within(phase02).getAllByRole('row').slice(1)
    expect(within(rows[0]).getByText('02.01')).toBeVisible()
    expect(within(rows[0]).getByRole('link', { name: 'Tu primer programa' })).toHaveAttribute('href', '/lessons/java-02.01')
    expect(within(rows[0]).getByText('Completada')).toBeVisible()
    expect(within(rows[1]).getByText('Todavía no disponible')).toBeVisible()
  })

  it('shows pending lessons muted, not as links, focusable with a description', async () => {
    installFakeBackend({ loggedInAs: ana })
    renderApp('/courses/java')

    const title = await screen.findByText('Variables y tipos de datos')
    expect(screen.queryByRole('link', { name: 'Variables y tipos de datos' })).not.toBeInTheDocument()
    expect(title.closest('a')).toBeNull()
    expect(title).toHaveAttribute('tabindex', '0')
    expect(title).toHaveAccessibleDescription('Lección pendiente: todavía no disponible, no se puede abrir.')
    const available = screen.getByRole('link', { name: 'Tu primer programa' })
    expect(within(available.closest('tr')!).getByText('No completada')).toBeVisible()
  })

  it('offers «Continuar» only when the progress has a continuation', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses/java/progress', {
      status: 200,
      body: { ...notAddedProgress(), added: true, continuation: { lastOpenedLessonId: 'java-02.01' } },
    })
    renderApp('/courses/java')

    expect(await screen.findByRole('link', { name: 'Continuar: Tu primer programa' })).toHaveAttribute('href', '/lessons/java-02.01')
  })

  it('falls back to a flat ordered list when lesson IDs carry no phase', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    const lessons: LessonEntry[] = [
      { id: 'java-intro', courseId: 'java', title: 'Introducción', position: 1, available: true },
      { id: 'java-02.01', courseId: 'java', title: 'Tu primer programa', position: 2, available: false },
    ]
    backend.route('GET /api/courses/java', { status: 200, body: { course: javaCourse, lessons } })
    renderApp('/courses/java')

    const table = await screen.findByRole('table', { name: /Temario/ })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell')[0].textContent)).toEqual(['1', '2'])
    expect(screen.getAllByRole('table')).toHaveLength(1)
  })

  it('shows «No encontrado» for an unknown course', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/courses/nada', error(404, 'NOT_FOUND'))
    backend.route('GET /api/me/courses/nada/progress', error(404, 'NOT_FOUND'))
    renderApp('/courses/nada')

    expect(await screen.findByRole('heading', { level: 1, name: 'No encontrado' })).toBeVisible()
  })

  it('keeps every lesson of the syllabus', async () => {
    installFakeBackend({ loggedInAs: ana })
    renderApp('/courses/java')

    await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })
    for (const lesson of javaLessons) expect(screen.getByText(lesson.title)).toBeVisible()
  })
})
