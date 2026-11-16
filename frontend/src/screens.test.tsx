import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Counts, DocumentEntry, LessonEntry, Phase } from './api/types'
import { ana, apiCalls, counts, error, installFakeBackend, javaCourse, javaLessons, javaPhases, notAddedProgress, type Reply } from './test/fakeBackend'
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

    expect(await screen.findByText('Todavía no has añadido ningún curso.')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Explorar el catálogo' })).toHaveAttribute('href', '/courses')
  })

  it('reports a load failure without inventing data', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses', error(500, 'INTERNAL_ERROR'))
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Algo salió mal.')
    expect(screen.queryByText('Todavía no has añadido ningún curso.')).not.toBeInTheDocument()
  })
})

describe('Catálogo', () => {
  it('lists courses with a link to the index', async () => {
    installFakeBackend({ loggedInAs: ana })
    renderApp('/courses')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(within(course).getByRole('link', { name: 'Java' })).toHaveAttribute('href', '/courses/java')
  })

  it('shows the description and the recommended courses, linked only when installed', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    const inicial = { id: 'inicial', title: 'Fundamentos para programar', description: 'Lo que conviene saber antes.', recommended: [] }
    const java = {
      ...javaCourse,
      description: 'De no saber programar a trabajar con Java.',
      recommended: [{ id: 'inicial', title: 'Fundamentos para programar' }, { id: 'sql', title: null }],
    }
    backend.route('GET /api/courses', { status: 200, body: { courses: [inicial, java] } })
    renderApp('/courses')

    const course = await screen.findByRole('article', { name: 'Java' })
    expect(within(course).getByText('De no saber programar a trabajar con Java.')).toBeVisible()
    const recommended = within(course).getByText(/Conviene saber antes/)
    expect(recommended).toHaveTextContent('Conviene saber antes: Fundamentos para programar, sql (no está instalado en esta academia)')
    expect(within(recommended).getByRole('link', { name: 'Fundamentos para programar' })).toHaveAttribute('href', '/courses/inicial')
    expect(within(recommended).queryByRole('link', { name: /sql/ })).not.toBeInTheDocument()
    // Only a recommendation: Java can be added all the same.
    expect(within(course).getByRole('button', { name: 'Añadir Java a Mis cursos' })).toBeEnabled()
    const first = await screen.findByRole('article', { name: 'Fundamentos para programar' })
    expect(within(first).queryByText(/Conviene saber antes/)).not.toBeInTheDocument()
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
    expect(tables[0]).toHaveAccessibleName('Fase 01 · Lógica de programación 0 de 2 disponibles')
    expect(tables[1]).toHaveAccessibleName('Fase 02 · Fundamentos de Java 1 de 2 disponibles')
    expect(tables[2]).toHaveAccessibleName('Fase 03 · Programación orientada a objetos 0 de 1 disponible')
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

  // S22.4: the phase comes from phaseId and the API phases, never from the shape of a lesson ID.
  it('groups by phaseId whatever the lesson IDs look like', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    const phases: Phase[] = [
      { id: 'java-phase-b', title: 'Segunda', position: 2 },
      { id: 'java-phase-a', title: 'Primera', position: 1 },
      // Decoy: the phase the misleading ID would point to; it has no lessons, so it never shows.
      { id: 'java-phase-03', title: 'Señuelo', position: 3 },
    ]
    const lessons: LessonEntry[] = [
      { id: 'lección-sin-número', courseId: 'java', title: 'Sin número', position: 3, phaseId: 'java-phase-b', available: false },
      { id: 'x-a', courseId: 'java', title: 'Equis', position: 1, phaseId: 'java-phase-a', available: true },
      // Looks like a lesson of «Fase 03», but its phase is the first one.
      { id: 'java-03.07', courseId: 'java', title: 'Engañosa', position: 2, phaseId: 'java-phase-a', available: false },
    ]
    backend.route('GET /api/courses/java', { status: 200, body: { course: javaCourse, phases, lessons } })
    renderApp('/courses/java')

    await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })
    const tables = screen.getAllByRole('table')
    expect(tables).toHaveLength(2)
    expect(tables[0]).toHaveAccessibleName('Fase 01 · Primera 1 de 2 disponibles')
    expect(tables[1]).toHaveAccessibleName('Fase 02 · Segunda 0 de 1 disponible')
    const cells = (table: HTMLElement) => within(table).getAllByRole('row').slice(1)
      .map((row) => within(row).getAllByRole('cell').slice(0, 2).map((cell) => cell.textContent))
    expect(cells(tables[0])).toEqual([['01.01', 'Equis'], ['01.02', 'Engañosa']])
    expect(cells(tables[1])).toEqual([['02.01', 'Sin número']])
    expect(within(tables[0]).getByRole('link', { name: 'Equis' })).toHaveAttribute('href', '/lessons/x-a')
  })

  it('keeps a lesson whose phase is not in the phase list, after the phases', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    const lessons: LessonEntry[] = [
      { ...javaLessons[0], phaseId: 'java-phase-desconocida' },
      ...javaLessons.slice(1),
    ]
    // Phases out of order in the response: the index follows their position.
    const phases = [javaPhases[1], javaPhases[0], javaPhases[2]]
    backend.route('GET /api/courses/java', { status: 200, body: { course: javaCourse, phases, lessons } })
    renderApp('/courses/java')

    await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })
    const tables = screen.getAllByRole('table')
    expect(tables.map((table) => table.querySelector('caption')?.textContent)).toEqual([
      'Fase 01 · Lógica de programación 0 de 1 disponible',
      'Fase 02 · Fundamentos de Java 1 de 2 disponibles',
      'Fase 03 · Programación orientada a objetos 0 de 1 disponible',
      'Sin fase 0 de 1 disponible',
    ])
    expect(within(tables[3]).getByText('Qué es programar')).toBeVisible()
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

describe('Documentos del curso (H8, T3)', () => {
  const retos: DocumentEntry = { id: 'java.fase.fundamentos.retos', courseId: 'java', kind: 'retos', title: 'Retos de la fase 02 — Fundamentos de Java', phaseId: javaPhases[1].id }
  const glosario: DocumentEntry = { id: 'java.glosario', courseId: 'java', kind: 'glosario', title: 'Glosario', phaseId: null }

  it('closes a phase with its optional challenges and links the glossary, outside the counts', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/courses/java', { status: 200, body: { course: javaCourse, phases: javaPhases, lessons: javaLessons, documents: [retos, glosario] } })
    renderApp('/courses/java')

    await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })
    expect(screen.getByRole('link', { name: 'Glosario del curso' })).toHaveAttribute('href', '/documents/java.glosario')
    const tables = screen.getAllByRole('table')
    // The challenges do not change the count of the phase.
    expect(tables[1]).toHaveAccessibleName('Fase 02 · Fundamentos de Java 1 de 2 disponibles')
    expect(within(tables[1]).getByRole('link', { name: 'Retos de la fase' })).toHaveAttribute('href', '/documents/java.fase.fundamentos.retos')
    expect(within(tables[1]).getByText('Opcionales')).toBeVisible()
    expect(within(tables[0]).queryByRole('link', { name: 'Retos de la fase' })).not.toBeInTheDocument()
  })

  it('shows a document with its phase, its body and its files, and no state to mark', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/courses/java', { status: 200, body: { course: javaCourse, phases: javaPhases, lessons: javaLessons, documents: [retos, glosario] } })
    backend.route('GET /api/documents/java.fase.fundamentos.retos', { status: 200, body: {
      document: retos,
      bodyHtml: '<h1 id="retos">Retos de la fase 02 — Fundamentos de Java</h1>\n<h2 id="reto-1--la-tienda">Reto 1 · La tienda</h2>\n<p>Repasa <a href="/lessons/java-02.01">el primer programa</a>.</p>\n',
      resources: [{ resourceId: 'java.r', url: '/api/resources/java.r', mediaType: 'text/plain; charset=utf-8', fileName: 'tienda.csv' }],
    } })
    renderApp('/documents/java.fase.fundamentos.retos')

    expect(await screen.findByRole('heading', { level: 1, name: 'Retos de la fase 02 — Fundamentos de Java' })).toBeVisible()
    expect(screen.getByText('Java · Fase 02 · Fundamentos de Java · Opcionales, sin seguimiento')).toBeVisible()
    expect(screen.getByRole('link', { name: 'tienda.csv' })).toHaveAttribute('href', '/api/resources/java.r')
    expect(screen.queryByRole('group', { name: /Estado/ })).not.toBeInTheDocument()
    expect(apiCalls(backend).filter((call) => call.startsWith('PUT'))).toEqual([])
    // An internal link stays in the application: the lesson is requested, the page is not reloaded.
    expect(fireEvent.click(screen.getByRole('link', { name: 'el primer programa' }))).toBe(false)
    await waitFor(() => expect(apiCalls(backend)).toContain('GET /api/lessons/java-02.01'))
  })

  it('marks the module that is the course project and links it; a course without project shows nothing', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/courses/java', { status: 200, body: { course: javaCourse, phases: javaPhases, lessons: javaLessons,
      project: { kind: 'modulo', title: 'Mi primer programa', lessonId: 'java-02.01' } } })
    renderApp('/courses/java')

    const link = await screen.findByRole('link', { name: 'Proyecto del curso: Mi primer programa (Tu primer programa)' })
    expect(link).toHaveAttribute('href', '/lessons/java-02.01')
    const row = screen.getByRole('link', { name: 'Tu primer programa' }).closest('tr')!
    expect(within(row).getByText('Proyecto del curso')).toBeVisible()
    expect(screen.queryByRole('link', { name: /Proyecto evolutivo/ })).not.toBeInTheDocument()
  })
})

