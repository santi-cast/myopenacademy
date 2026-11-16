import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ExerciseStateResult, LessonStateResult } from './api/types'
import { ana, apiCalls, counts, error, installFakeBackend, javaCourse, javaPhases, notAddedProgress, type Reply } from './test/fakeBackend'
import {
  BODY_CODE, BODY_DIAGRAMS, BODY_LINKS, BROKEN_MERMAID_SOURCE, MERMAID_SOURCE, addedProgress, lessonView, lessonsWithNext, routeLesson,
} from './test/lessonFixtures'
import { renderApp } from './test/renderApp'

// T9b lesson view (S2.4, S5, S16, S19, S21, L19, L48, L54). Mermaid is replaced by a double at the
// module boundary so the calls can be inspected; highlight.js is the real library.
const mermaid = vi.hoisted(() => ({ initialize: vi.fn(), render: vi.fn() }))
vi.mock('mermaid', () => ({ default: mermaid }))

let scrolled: Element[] = []

beforeEach(() => {
  scrolled = []
  mermaid.initialize.mockReset()
  mermaid.render.mockReset()
  mermaid.render.mockImplementation(async (id: string, text: string) => {
    if (text.startsWith('esto no')) throw new Error('Parse error on line 1')
    return { svg: `<svg id="${id}" data-testid="diagram" role="img"><text>ok</text></svg>` }
  })
  // jsdom has no layout: record the scroll target instead.
  Object.defineProperty(Element.prototype, 'scrollIntoView', {
    configurable: true,
    value: function (this: Element) {
      scrolled.push(this)
    },
  })
})

afterEach(() => {
  delete (Element.prototype as Partial<Element>).scrollIntoView
})

async function openLesson(options: { body?: string; progress?: ReturnType<typeof notAddedProgress>; path?: string } = {}) {
  const backend = installFakeBackend({ loggedInAs: ana })
  routeLesson(backend, lessonView(options.body === undefined ? {} : { bodyHtml: options.body }), options.progress)
  const view = renderApp(options.path ?? '/lessons/java-02.01')
  await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })
  // Wait for the reading aids added after the HTML is in the page, not just for the title.
  await waitFor(() => expect(document.querySelector('[data-enhanced="true"]')).not.toBeNull())
  return { backend, ...view }
}

function lessonBody() {
  const body = document.querySelector<HTMLElement>('.lesson-body')
  if (!body) throw new Error('lesson body not rendered')
  return body
}

/** Clicks like a user and reports whether the app cancelled the browser's own navigation. */
function click(element: Element): boolean {
  let prevented = false
  const listener = (event: Event) => {
    prevented = event.defaultPrevented
    event.preventDefault() // jsdom does not navigate; keep it from trying
  }
  window.addEventListener('click', listener)
  fireEvent.click(element)
  window.removeEventListener('click', listener)
  return prevented
}

/** HTML of a lesson container without the UI controls the view adds next to the content. */
function withoutUi(element: Element) {
  const copy = element.cloneNode(true) as Element
  copy.querySelectorAll('[data-ui]').forEach((node) => node.remove())
  return copy.innerHTML
}

function deferred(): { promise: Promise<Reply>; resolve: (reply: Reply) => void } {
  let resolve: (reply: Reply) => void = () => {}
  const promise = new Promise<Reply>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

function hhmm(iso: string) {
  const date = new Date(iso)
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function lessonResult(state: LessonStateResult['state'], courseAutoAdded = false): LessonStateResult {
  return {
    courseId: 'java', lessonId: 'java-02.01', state, updatedAt: new Date().toISOString(), courseAutoAdded,
    continuation: { lastOpenedLessonId: 'java-02.01' }, progress: counts(state === 'completed' ? 1 : 0, 2, 199),
  }
}

describe('lesson HTML as delivered', () => {
  it('inserts bodyHtml verbatim: React adds no elements inside it', async () => {
    const plain = '<p>Texto <strong>fuerte</strong> y <a href="https://dev.java/" target="_blank" rel="noopener noreferrer">dev.java</a>.</p>\n<ul>\n<li>uno</li>\n</ul>\n'
    await openLesson({ body: plain })
    expect(lessonBody().innerHTML).toBe(plain)
  })

  it('keeps the delivered HTML intact next to the added controls (anchors, statement, solution)', async () => {
    await openLesson()
    expect(withoutUi(lessonBody())).toBe(BODY_LINKS.replace('href="/lessons/java-03.01"', 'href="/lessons/java-03.01" class="link-pending" aria-disabled="true" aria-describedby="pending-link-description"'))
    const card = document.getElementById('java-02.01-e08')!
    expect(withoutUi(card.querySelector('.ex-statement')!)).toBe('<p>Enunciado del ejercicio 08.</p>\n')
    expect(card.querySelectorAll('details')).toHaveLength(1)
  })

  it('renders every exercise in a container whose id is the exercise ID', async () => {
    await openLesson()
    const card = document.getElementById('java-02.01-e08')
    expect(card).not.toBeNull()
    expect(within(card!).getByRole('heading', { name: '¿Qué main se ejecuta?' })).toBeVisible()
    expect(document.getElementById('java-02.01-e01')).not.toBeNull()
  })
})

describe('the challenges between two phases (H8)', () => {
  it('the last module of a phase leads to «Retos de la fase NN» instead of the next lesson', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend, lessonView({ nextDocumentId: 'java.fase.fundamentos.retos' }))
    backend.route('GET /api/courses/java', { status: 200, body: { course: javaCourse, phases: javaPhases, lessons: lessonsWithNext,
      documents: [{ id: 'java.fase.fundamentos.retos', courseId: 'java', kind: 'retos', title: 'Retos', phaseId: javaPhases[1].id }] } })
    renderApp('/lessons/java-02.01')
    const pager = await screen.findByRole('navigation', { name: 'Lecciones anterior y siguiente' })
    const next = await within(pager).findByRole('link', { name: /Siguiente →/ })
    expect(next).toHaveAttribute('href', '/documents/java.fase.fundamentos.retos')
    expect(next).toHaveTextContent('Retos de la fase 02')
  })
})

describe('«Mejorar esta lección» (H8, T2)', () => {
  it('links to the edit page of the module in a new tab, and is absent without a repository', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    const url = 'https://github.com/myopenacademy/curso-java/edit/main/fase-02/01-tu-primer-programa.md'
    routeLesson(backend, lessonView({ improveUrl: url }))
    renderApp('/lessons/java-02.01')
    const link = await screen.findByRole('link', { name: 'Mejorar esta lección' })
    expect(link).toHaveAttribute('href', url)
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  })

  it('shows no link when the course has no repository', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend, lessonView({ improveUrl: null }))
    renderApp('/lessons/java-02.01')
    await screen.findByRole('heading', { name: 'Al terminar la lección' })
    expect(screen.queryByRole('link', { name: 'Mejorar esta lección' })).not.toBeInTheDocument()
  })
})

describe('links inside the lesson (L19)', () => {
  it('a fragment link scrolls with getElementById, also for IDs that start with a digit', async () => {
    await openLesson()
    const byId = vi.spyOn(document, 'getElementById')
    const query = vi.spyOn(document, 'querySelector')

    expect(click(screen.getByRole('link', { name: 'la sección 2.5' }))).toBe(true)
    await waitFor(() => expect(byId).toHaveBeenCalledWith('25-unir-textos-y-números'))
    expect(scrolled.at(-1)).toBe(document.getElementById('25-unir-textos-y-números'))

    expect(click(screen.getByRole('link', { name: 'ejercicio 8' }))).toBe(true)
    await waitFor(() => expect(scrolled.at(-1)).toBe(document.getElementById('java-02.01-e08')))
    expect(byId).toHaveBeenCalledWith('java-02.01-e08')
    expect(query.mock.calls.filter(([selector]) => String(selector).startsWith('#'))).toEqual([])
  })

  it('a link to another lesson goes through the router, then to its fragment once loaded', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend)
    renderApp('/lessons/java-02.02')
    await screen.findByRole('heading', { level: 1, name: 'Variables y tipos de datos' })

    expect(click(screen.getByRole('link', { name: 'ejercicio 8 de 02.01' }))).toBe(true)
    expect(await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })).toBeVisible()
    await waitFor(() => expect(scrolled.at(-1)).toBe(document.getElementById('java-02.01-e08')))
    expect(apiCalls(backend).filter((call) => call === 'GET /api/lessons/java-02.01')).toHaveLength(1)
  })

  it('a heading fragment with accents and a leading digit is found after navigating', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend)
    renderApp('/lessons/java-02.02')
    await screen.findByRole('heading', { level: 1, name: 'Variables y tipos de datos' })

    click(screen.getByRole('link', { name: 'la sección 2.5' }))
    await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })
    await waitFor(() => expect(scrolled.at(-1)).toBe(document.getElementById('25-unir-textos-y-números')))
  })

  it('the course link goes through the router', async () => {
    await openLesson()
    expect(click(screen.getByRole('link', { name: 'índice del curso' }))).toBe(true)
    expect(await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })).toBeVisible()
  })

  it('resource and external links are left to the browser', async () => {
    await openLesson()
    expect(click(within(lessonBody()).getByRole('link', { name: 'Hello.java' }))).toBe(false)
    const external = screen.getByRole('link', { name: 'dev.java' })
    expect(external).toHaveAttribute('target', '_blank')
    expect(click(external)).toBe(false)
    expect(screen.getByRole('heading', { level: 1, name: 'Tu primer programa' })).toBeVisible()
  })

  it('a link to a pending lesson is marked, described and does not navigate', async () => {
    const { backend } = await openLesson()
    const pending = screen.getByRole('link', { name: /Clases y objetos/ })
    expect(pending).toHaveAttribute('aria-disabled', 'true')
    expect(pending).toHaveClass('link-pending')
    expect(pending).toHaveAccessibleDescription('Lección pendiente: todavía no disponible, no se puede abrir.')

    expect(click(pending)).toBe(true)
    await act(async () => {})
    expect(apiCalls(backend)).not.toContain('GET /api/lessons/java-03.01')
    expect(screen.getByRole('heading', { level: 1, name: 'Tu primer programa' })).toBeVisible()
  })

  it('opening a pending lesson by URL shows «todavía no disponible» (409)', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/lessons/java-03.01', error(409, 'LESSON_UNAVAILABLE'))
    renderApp('/lessons/java-03.01')
    expect(await screen.findByRole('heading', { level: 1, name: 'Esta lección todavía no está disponible.' })).toBeVisible()
    expect(apiCalls(backend)).not.toContain('PUT /api/me/courses/java/continuation')
  })
})

describe('code highlighting (S19)', () => {
  it('highlights registered languages from the element text and leaves text, unknown and mermaid untouched', async () => {
    await openLesson({ body: BODY_CODE + BODY_DIAGRAMS })
    const java = lessonBody().querySelector('code.language-java')!
    await waitFor(() => expect(java.querySelector('.hljs-string')).not.toBeNull(), { timeout: 5000 })
    // The escaped markup stays text: no <b> element appears.
    expect(java.querySelector('b')).toBeNull()
    expect(java.textContent).toContain('IO.println("<b>Hola</b>");')

    const html = lessonBody().querySelector('code.language-html')!
    await waitFor(() => expect(html.querySelector('.hljs-tag')).not.toBeNull())
    expect(html.querySelector('p')).toBeNull()

    for (const language of ['text', 'cobol']) {
      const code = lessonBody().querySelector(`code.language-${language}`)!
      expect(code.children).toHaveLength(0)
      expect(code).not.toHaveClass('hljs')
    }
    const mermaidCode = lessonBody().querySelector('code.language-mermaid')!
    expect(mermaidCode.children).toHaveLength(0)
    expect(mermaidCode).not.toHaveClass('hljs')
  })
})

describe('Mermaid diagrams (S5, L54)', () => {
  it('renders in strict mode from the code element textContent', async () => {
    await openLesson({ body: BODY_DIAGRAMS })
    expect(await screen.findAllByTestId('diagram')).toHaveLength(1)
    expect(mermaid.initialize).toHaveBeenCalledWith(expect.objectContaining({
      startOnLoad: false, securityLevel: 'strict', htmlLabels: false, theme: 'default',
    }))
    const sources = mermaid.render.mock.calls.map(([, text]) => text)
    expect(sources).toEqual([MERMAID_SOURCE, BROKEN_MERMAID_SOURCE])
  })

  it('a broken diagram keeps its source as escaped text with a note, and the lesson keeps working', async () => {
    await openLesson({ body: BODY_DIAGRAMS })
    expect(await screen.findByText('No se pudo dibujar el diagrama')).toBeVisible()
    const broken = [...lessonBody().querySelectorAll('code.language-mermaid')].at(-1)!
    expect(broken).toBeVisible()
    expect(broken.textContent).toBe(BROKEN_MERMAID_SOURCE)
    expect(lessonBody().querySelector('img')).toBeNull()
    expect(screen.getByText('El resto de la lección sigue.')).toBeVisible()
    expect(screen.getAllByRole('radio', { name: 'Completada' })[0]).toBeEnabled()
  })

  it('follows the dark theme', async () => {
    document.documentElement.dataset.theme = 'dark'
    window.localStorage.setItem('ca.theme', 'dark')
    await openLesson({ body: BODY_DIAGRAMS })
    await screen.findAllByTestId('diagram')
    expect(mermaid.initialize).toHaveBeenCalledWith(expect.objectContaining({ theme: 'dark', securityLevel: 'strict' }))
    expect(screen.getByRole('button', { name: /Modo claro/ })).toBeVisible()
  })
})

describe('exercises (S21)', () => {
  it('shows a solution <details> with the retrieval hint in its summary, and opening it records nothing', async () => {
    const { backend } = await openLesson()
    const card = document.getElementById('java-02.01-e08')!
    const summary = card.querySelector('summary')!
    expect(summary).toHaveTextContent('Ver solución')
    expect(summary).toHaveTextContent('Antes de abrirla, intenta explicar la solución con tus palabras.')
    expect(card.querySelectorAll('details')).toHaveLength(1)
    expect(within(card).getByText('Marca tú el estado: la academia no corrige los ejercicios.')).toBeVisible()

    const before = backend.calls.length
    fireEvent.click(summary)
    await act(async () => {})
    expect(backend.calls.slice(before).filter((call) => call.method !== 'GET')).toEqual([])
  })

  it('saves an exercise state and counts only solved ones in the bar', async () => {
    const { backend } = await openLesson({ progress: addedProgress([{ exerciseId: 'java-02.01-e01', state: 'in_progress', updatedAt: '2026-10-01T10:00:00Z' }]) })
    expect(screen.getByText('Ejercicios 0/2')).toBeVisible()
    const result: ExerciseStateResult = {
      courseId: 'java', exerciseId: 'java-02.01-e08', state: 'solved', updatedAt: new Date().toISOString(), courseAutoAdded: false,
      continuation: { lastOpenedLessonId: 'java-02.01' }, progress: counts(0, 2, 199),
    }
    backend.route('PUT /api/me/exercises/java-02.01-e08/state', { status: 200, body: result })

    const card = document.getElementById('java-02.01-e08')!
    fireEvent.click(within(card).getByRole('radio', { name: 'Resuelto' }))
    expect(await within(card).findByText(`Guardado · hoy, ${hhmm(result.updatedAt)}`)).toBeVisible()
    expect(backend.calls.find((call) => call.path === '/api/me/exercises/java-02.01-e08/state')?.body).toBe('{"state":"solved"}')
    expect(screen.getByText('Ejercicios 1/2')).toBeVisible()
  })
})

describe('lesson state (PLAN §3 rules 5–8)', () => {
  it('shows «Guardando…» and only shows the change as saved after the server confirms, in both controls', async () => {
    const { backend } = await openLesson()
    const reply = deferred()
    backend.route('PUT /api/me/lessons/java-02.01/state', () => reply.promise)

    const [top, end] = screen.getAllByRole('group', { name: 'Estado de la lección' })
    fireEvent.click(within(top).getByRole('radio', { name: 'Completada' }))
    expect(await within(top).findByText('Guardando…')).toBeVisible()
    expect(screen.queryByText(/Guardado ·/)).toBeNull()

    const result = lessonResult('completed')
    await act(async () => reply.resolve({ status: 200, body: result }))
    expect(await within(top).findByText(`Guardado · hoy, ${hhmm(result.updatedAt)}`)).toBeVisible()
    expect(within(end).getByRole('radio', { name: 'Completada' })).toBeChecked()
    expect(backend.calls.find((call) => call.path === '/api/me/lessons/java-02.01/state')?.body).toBe('{"state":"completed"}')
    expect(within(document.querySelector('.lbar')! as HTMLElement).getByText('Completada')).toBeVisible()
  })

  it('a failed save shows the error and does not pretend the change was kept', async () => {
    const { backend } = await openLesson()
    backend.route('PUT /api/me/lessons/java-02.01/state', error(500, 'INTERNAL_ERROR'))
    const [top] = screen.getAllByRole('group', { name: 'Estado de la lección' })
    fireEvent.click(within(top).getByRole('radio', { name: 'Completada' }))

    expect(await within(top).findByText('Algo salió mal. Inténtalo de nuevo en unos minutos.')).toBeVisible()
    expect(within(top).getByRole('radio', { name: 'No completada' })).toBeChecked()
    expect(screen.queryByText(/Guardado ·/)).toBeNull()
    expect(apiCalls(backend).filter((call) => call.startsWith('PUT /api/me/lessons'))).toHaveLength(1)
  })

  it('announces «Java se añadió a Mis cursos» when the save added the course', async () => {
    const { backend } = await openLesson()
    backend.route('PUT /api/me/lessons/java-02.01/state', { status: 200, body: lessonResult('completed', true) })
    const [top] = screen.getAllByRole('group', { name: 'Estado de la lección' })
    fireEvent.click(within(top).getByRole('radio', { name: 'Completada' }))
    expect(await screen.findByText('Java se añadió a Mis cursos')).toBeVisible()
  })
})

describe('continuation (docs/api.md «Progreso y continuación»)', () => {
  it('records the opened lesson when the course is added', async () => {
    const { backend } = await openLesson({ progress: addedProgress() })
    await waitFor(() => expect(apiCalls(backend)).toContain('PUT /api/me/courses/java/continuation'))
    expect(backend.calls.find((call) => call.path === '/api/me/courses/java/continuation')?.body).toBe('{"lessonId":"java-02.01"}')
  })

  it('never calls it while exploring a course that is not added', async () => {
    const { backend } = await openLesson()
    await act(async () => {})
    expect(apiCalls(backend)).not.toContain('PUT /api/me/courses/java/continuation')
  })
})

describe('reading aids (L48, S21)', () => {
  it('copies a code block as plain text with the textarea fallback and announces «Copiado»', async () => {
    let copied: string | null = null
    Object.defineProperty(document, 'execCommand', {
      configurable: true,
      value: (command: string) => {
        if (command === 'copy') copied = (document.activeElement as HTMLTextAreaElement).value
        return true
      },
    })
    try {
      await openLesson({ body: BODY_CODE })
      const java = lessonBody().querySelector('code.language-java')!
      await waitFor(() => expect(java.querySelector('.hljs-string')).not.toBeNull(), { timeout: 5000 })
      const buttons = screen.getAllByRole('button', { name: /Copiar código/ })
      expect(buttons.length).toBeGreaterThanOrEqual(4)
      fireEvent.click(buttons[0])
      await waitFor(() => expect(copied).toBe('void main() {\n    IO.println("<b>Hola</b>");\n}\n'))
      expect(await screen.findByText('Copiado')).toBeVisible()
      expect(screen.getByText('Código copiado al portapapeles')).toBeInTheDocument()
      expect(document.querySelector('textarea')).toBeNull()
    } finally {
      delete (document as Partial<Document>).execCommand
    }
  })

  it('has no copy button on diagrams', async () => {
    await openLesson({ body: BODY_DIAGRAMS })
    await screen.findAllByTestId('diagram')
    // The body holds only diagrams; the two exercise solutions hold Java code.
    expect(within(lessonBody()).queryAllByRole('button', { name: 'Copiar código' })).toEqual([])
    expect(screen.getAllByRole('button', { name: 'Copiar código' })).toHaveLength(2)
  })

  it('adds a copy-link button to headings and lists them in the lesson index', async () => {
    await openLesson()
    expect(screen.getByRole('button', { name: 'Copiar enlace a la sección «2.5. Unir textos y números»' })).toBeInTheDocument()
    const indexes = await screen.findAllByRole('navigation', { name: 'En esta lección' })
    expect(within(indexes[0]).getByRole('link', { name: '2.5. Unir textos y números' })).toHaveAttribute('href', '#25-unir-textos-y-números')
  })

  it('focus mode hides the syllabus and is remembered', async () => {
    const view = await openLesson()
    fireEvent.click(screen.getByRole('button', { name: 'Ocultar temario' }))
    expect(screen.getByRole('button', { name: 'Mostrar temario' })).toBeVisible()
    expect(window.localStorage.getItem('ca.temario')).toBe('oculto')
    view.unmount()

    renderApp('/lessons/java-02.01')
    expect(await screen.findByRole('button', { name: 'Mostrar temario' })).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Mostrar temario' }))
    expect(window.localStorage.getItem('ca.temario')).toBeNull()
  })

  it('focus mode still works when storage is unavailable', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    await openLesson()
    fireEvent.click(screen.getByRole('button', { name: 'Ocultar temario' }))
    expect(screen.getByRole('button', { name: 'Mostrar temario' })).toBeVisible()
  })

  it('shows the syllabus with the current lesson and the pager without a missing destination', async () => {
    await openLesson()
    const syllabus = screen.getByRole('navigation', { name: 'Temario del curso' })
    expect(within(syllabus).getByRole('link', { name: 'Tu primer programa' })).toHaveAttribute('aria-current', 'page')
    expect(within(syllabus).queryByRole('link', { name: 'Clases y objetos' })).toBeNull()

    const pager = screen.getByRole('navigation', { name: 'Lecciones anterior y siguiente' })
    expect(within(pager).getByText('No hay lección anterior disponible')).toBeVisible()
    expect(within(pager).getByRole('link', { name: /Variables y tipos de datos/ })).toHaveAttribute('href', '/lessons/java-02.02')
  })

  it('lists the lesson resources by their original file name', async () => {
    await openLesson()
    const files = screen.getByRole('region', { name: 'Archivos de la lección' })
    expect(within(files).getByRole('link', { name: 'Hello.java' })).toHaveAttribute('href', '/api/resources/java-r-hello')
  })
})
