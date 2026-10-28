import type { Exercise, ExerciseState, LessonEntry, LessonView, Progress, Resource } from '../api/types'
import { counts, javaCourse, javaLessons, javaPhases, notAddedProgress, type FakeBackend, type Reply } from './fakeBackend'

// Lesson payloads shaped like the backend's sanitized output (docs/api.md, «Contenido, ejercicios
// y recursos»): heading IDs from the GitHub algorithm, rewritten internal links, target="_blank"
// on external links only, code blocks as <pre><code class="language-x"> with escaped text.

/** 02.01 and 02.02 available; 03.01 still pending. */
export const lessonsWithNext: LessonEntry[] = javaLessons.map((lesson) =>
  lesson.id === 'java-02.02' ? { ...lesson, available: true } : lesson,
)

export const BODY_LINKS =
  '<h2 id="qué-aprenderás">Qué aprenderás</h2>\n' +
  '<p>Repasá <a href="#25-unir-textos-y-números">la sección 2.5</a>, el <a href="#java-02.01-e08">ejercicio 8</a>, ' +
  'la <a href="/lessons/java-02.02">lección siguiente</a>, la pendiente <a href="/lessons/java-03.01">Clases y objetos</a>, ' +
  'el <a href="/courses/java">índice del curso</a>, el archivo <a href="/api/resources/java-r-hello">Hello.java</a> ' +
  'y <a href="https://dev.java/learn/" target="_blank" rel="noopener noreferrer">dev.java</a>.</p>\n' +
  '<h3 id="25-unir-textos-y-números">2.5. Unir textos y números</h3>\n' +
  '<p>Texto de la sección.</p>\n'

export const BODY_CODE =
  '<h2 id="ejemplos">Ejemplos</h2>\n' +
  '<pre><code class="language-java">void main() {\n    IO.println("&lt;b&gt;Hola&lt;/b&gt;");\n}\n</code></pre>\n' +
  '<pre><code class="language-text">¡Hola, mundo!\n</code></pre>\n' +
  '<pre><code class="language-cobol">DISPLAY \'HOLA\'.\n</code></pre>\n' +
  '<pre><code class="language-html">&lt;p class="x"&gt;Hola&lt;/p&gt;\n</code></pre>\n'

export const MERMAID_SOURCE = 'flowchart LR\n    A["Hello.java"] --> B["Hello.class"]\n'
export const BROKEN_MERMAID_SOURCE = 'esto no es un diagrama <img src=x onerror=alert(1)>\n'

const escapeText = (text: string) => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')

export const BODY_DIAGRAMS =
  '<h2 id="diagramas">Diagramas</h2>\n' +
  `<pre><code class="language-mermaid">${escapeText(MERMAID_SOURCE)}</code></pre>\n` +
  '<p>Entre diagramas.</p>\n' +
  `<pre><code class="language-mermaid">${escapeText(BROKEN_MERMAID_SOURCE)}</code></pre>\n` +
  '<p>El resto de la lección sigue.</p>\n'

export function exercise(n: string, title: string): Exercise {
  return {
    id: `java-02.01-e${n}`,
    lessonId: 'java-02.01',
    courseId: 'java',
    title,
    statementHtml: `<p>Enunciado del ejercicio ${n}.</p>\n`,
    solutionHtml: `<details>\n<summary>Ver solución</summary>\n<pre><code class="language-java">void main() { IO.println(${n}); }\n</code></pre>\n</details>\n`,
  }
}

export const exercises: Exercise[] = [exercise('01', 'La ficha de la tienda'), exercise('08', '¿Qué main se ejecuta?')]

export const resources: Resource[] = [
  { resourceId: 'java-r-hello', url: '/api/resources/java-r-hello', mediaType: 'text/plain; charset=utf-8', fileName: 'Hello.java' },
]

export function lessonView(overrides: Partial<LessonView> = {}): LessonView {
  return {
    lesson: javaLessons[2],
    bodyHtml: BODY_LINKS,
    exercises,
    resources,
    previousLessonId: null,
    nextLessonId: 'java-02.02',
    ...overrides,
  }
}

export function addedProgress(exerciseStates: ExerciseState[] = []): Progress {
  return {
    ...notAddedProgress(),
    added: true,
    exerciseStates,
    continuation: { lastOpenedLessonId: 'java-02.01' },
  }
}

/** Routes for lesson 02.01 (and a plain 02.02) with the given payload and progress. */
export function routeLesson(backend: FakeBackend, view: LessonView = lessonView(), progress: Progress = notAddedProgress()) {
  const ok = (body: unknown): Reply => ({ status: 200, body })
  backend.route('GET /api/courses/java', ok({ course: javaCourse, phases: javaPhases, lessons: lessonsWithNext }))
  backend.route('GET /api/me/courses/java/progress', ok(progress))
  backend.route('GET /api/lessons/java-02.01', ok(view))
  backend.route('GET /api/lessons/java-02.02', ok({
    lesson: lessonsWithNext[3],
    bodyHtml: '<h2 id="intro">Intro</h2>\n<p>Volvé al <a href="/lessons/java-02.01#java-02.01-e08">ejercicio 8 de 02.01</a> ' +
      'o a <a href="/lessons/java-02.01#25-unir-textos-y-números">la sección 2.5</a>.</p>\n',
    exercises: [],
    resources: [],
    previousLessonId: 'java-02.01',
    nextLessonId: null,
  } satisfies LessonView))
  backend.route('PUT /api/me/courses/java/continuation', ok({
    courseId: 'java', added: true, continuation: { lastOpenedLessonId: 'java-02.01' }, progress: counts(0, 2, 199),
  }))
}
