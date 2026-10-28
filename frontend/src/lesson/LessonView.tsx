import { memo, useCallback, useEffect, useMemo, useRef, useState, type MouseEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { apiSend } from '../api/client'
import { errorMessage, isAuthRequired } from '../api/errors'
import type {
  Enrollment, Exercise, ExerciseState, ExerciseStateResult, LessonState, LessonStateResult, LessonView as LessonPayload, Progress,
  Syllabus as SyllabusData,
} from '../api/types'
import { groupByPhase } from '../syllabus'
import { currentTheme } from '../theme/theme'
import { copyText } from './clipboard'
import {
  PENDING_LINK_DESCRIPTION_ID, addHeadingAnchors, addSolutionHints, codeTextFor, decorateCodeBlocks, drawDiagrams, highlightBlocks,
  lessonIdOf, markPendingLinks, plainText,
} from './enhance'
import { rememberFocusMode, storedFocusMode } from './focusMode'
import { TocDropdown, TocRail, type TocEntry } from './LessonToc'
import type { SaveState } from './saveStatus'
import { StateControl } from './StateControl'
import { SYLLABUS_ID, Syllabus } from './Syllabus'
import './lesson.css'

export type LessonData = { view: LessonPayload; syllabus: SyllabusData; progress: Progress }

type LessonValue = LessonState['state']
type ExerciseValue = ExerciseState['state']

const LESSON_OPTIONS: { value: LessonValue; label: string }[] = [
  { value: 'not_completed', label: 'No completada' },
  { value: 'completed', label: 'Completada' },
]

const EXERCISE_OPTIONS: { value: ExerciseValue; label: string }[] = [
  { value: 'pending', label: 'Pendiente' },
  { value: 'in_progress', label: 'En curso' },
  { value: 'solved', label: 'Resuelto' },
]

const EXERCISE_BADGES: Record<ExerciseValue, string> = { pending: 'badge-pending', in_progress: 'badge-todo', solved: 'badge-done' }
const EXERCISES_ID = 'lesson-exercises'
const END_ID = 'lesson-end'
const IDLE = { pending: null, status: { kind: 'idle' } } as const
const FEEDBACK_MS = 2000

/** Delivered lesson HTML, inserted as is. Memoized: React never touches it again for the same string. */
const LessonHtml = memo(function LessonHtml({ html, className }: { html: string; className: string }) {
  return <div className={className} dangerouslySetInnerHTML={{ __html: html }} />
})

/**
 * The delivered lesson's own title: its first element, when that is an <h1>. Read from an inert
 * copy made by DOMParser; the HTML put in the page is still the delivered string, untouched.
 */
function deliveredTitle(html: string): { id: string } | null {
  const first = new DOMParser().parseFromString(html, 'text/html').body.firstElementChild
  return first?.localName === 'h1' ? { id: first.id } : null
}

function prefersReducedMotion(): boolean {
  try {
    return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
  } catch {
    return false
  }
}

/** Target of a location hash. IDs may start with a digit or hold dots and accents (L19): never a CSS selector. */
function fragmentTarget(hash: string): HTMLElement | null {
  if (hash.length <= 1) return null
  const raw = hash.slice(1)
  let decoded = raw
  try {
    decoded = decodeURIComponent(raw)
  } catch {
    // Not percent-encoded: use it as written.
  }
  return document.getElementById(decoded) ?? (decoded === raw ? null : document.getElementById(raw))
}

function exerciseCounts(exercises: Exercise[], states: Map<string, ExerciseValue>) {
  const counts = { pending: 0, in_progress: 0, solved: 0 }
  for (const exercise of exercises) counts[states.get(exercise.id) ?? 'pending'] += 1
  return counts
}

function ExerciseCard({ exercise, saved, save, onChange }: {
  exercise: Exercise
  saved: ExerciseValue
  save: SaveState<ExerciseValue>
  onChange: (value: ExerciseValue) => void
}) {
  const label = EXERCISE_OPTIONS.find((option) => option.value === saved)?.label
  return (
    <section className={`exercise is-${saved}`} id={exercise.id} aria-labelledby={`${exercise.id}-title`} tabIndex={-1}>
      <div className="ex-head">
        <h3 id={`${exercise.id}-title`}>{exercise.title}</h3>
        <span className={`badge ${EXERCISE_BADGES[saved]}`}>{label}</span>
      </div>
      <LessonHtml className="ex-statement" html={exercise.statementHtml} />
      <LessonHtml className="ex-solution solution" html={exercise.solutionHtml} />
      <StateControl legend="Estado del ejercicio" options={EXERCISE_OPTIONS} saved={saved} save={save} onChange={onChange} />
    </section>
  )
}

function Pager({ label, direction, lessonId, titles }: {
  label: 'anterior' | 'siguiente'
  direction: string
  lessonId: string | null
  titles: Map<string, string>
}) {
  const className = label === 'siguiente' ? 'pager-item pager-next' : 'pager-item'
  if (lessonId === null) {
    return (
      <div className={`${className} is-empty`}>
        <span className="pager-dir">{direction}</span>
        <span className="pager-title">No hay lección {label} disponible</span>
      </div>
    )
  }
  return (
    <Link className={className} to={`/lessons/${encodeURIComponent(lessonId)}`}>
      <span className="pager-dir">{direction}</span>
      <span className="pager-title">{titles.get(lessonId) ?? lessonId}</span>
    </Link>
  )
}

export function LessonView({ data }: { data: LessonData }) {
  const { view, syllabus } = data
  const { lesson } = view
  const navigate = useNavigate()
  const location = useLocation()
  const contentRef = useRef<HTMLElement>(null)
  const continuationSent = useRef(false)
  const opensAtFragment = useRef(location.hash.length > 1)

  const [progress, setProgress] = useState(data.progress)
  const [lessonSave, setLessonSave] = useState<SaveState<LessonValue>>(IDLE)
  const [exerciseSaves, setExerciseSaves] = useState<Record<string, SaveState<ExerciseValue>>>({})
  const [toast, setToast] = useState<string | null>(null)
  const [live, setLive] = useState('')
  const [focusMode, setFocusMode] = useState(storedFocusMode)
  const [toc, setToc] = useState<TocEntry[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)

  const bodyTitle = useMemo(() => deliveredTitle(view.bodyHtml), [view.bodyHtml])
  const groups = useMemo(() => groupByPhase(syllabus.phases, syllabus.lessons), [syllabus])
  const pendingLessons = useMemo(() => new Set(syllabus.lessons.filter((entry) => !entry.available).map((entry) => entry.id)), [syllabus])
  const isPending = useCallback((lessonId: string) => pendingLessons.has(lessonId), [pendingLessons])
  const titles = useMemo(() => {
    const codes = new Map(groups.flatMap((group) => group.rows.map(({ lesson: entry, code }) => [entry.id, code] as const)))
    return new Map(syllabus.lessons.map((entry) => [entry.id, `${codes.get(entry.id) ?? ''} ${entry.title}`.trim()]))
  }, [groups, syllabus])
  const group = groups.find((candidate) => candidate.rows.some((row) => row.lesson.id === lesson.id))
  const code = group?.rows.find((row) => row.lesson.id === lesson.id)?.code ?? ''

  const completed = new Set(progress.lessonStates.filter((state) => state.state === 'completed').map((state) => state.lessonId))
  const savedLesson: LessonValue = completed.has(lesson.id) ? 'completed' : 'not_completed'
  const exerciseStates = new Map(progress.exerciseStates.map((state) => [state.exerciseId, state.state]))
  const counts = exerciseCounts(view.exercises, exerciseStates)

  // Continuation only for an added course, once the lesson loaded (docs/api.md, «Progreso y continuación»).
  useEffect(() => {
    if (continuationSent.current || !data.progress.added) return
    continuationSent.current = true
    apiSend<Enrollment>('PUT', `/api/me/courses/${encodeURIComponent(lesson.courseId)}/continuation`, { lessonId: lesson.id }).then(
      (enrollment) => setProgress((current) => ({ ...current, continuation: enrollment.continuation })),
      () => {
        // Not shown: nothing claimed it was saved, and the next opened lesson tries again.
      },
    )
  }, [data.progress.added, lesson.courseId, lesson.id])

  // Reading aids around the delivered HTML.
  useEffect(() => {
    const root = contentRef.current
    const body = root?.querySelector('.lesson-body')
    if (!root || !body) return
    let cancelled = false
    const isCancelled = () => cancelled
    markPendingLinks(root, isPending)
    setToc([
      ...[...body.querySelectorAll<HTMLElement>('h2[id], h3[id]')].map((heading) => ({
        id: heading.id,
        title: plainText(heading),
        level: heading.tagName === 'H2' ? 2 as const : 3 as const,
      })),
      ...(view.exercises.length > 0 ? [{ id: EXERCISES_ID, title: 'Ejercicios prácticos resueltos', level: 2 as const }] : []),
      { id: END_ID, title: 'Al terminar la lección', level: 2 as const },
    ])
    addHeadingAnchors(body)
    addSolutionHints(root)
    const blocks = decorateCodeBlocks(root)
    // The synchronous reading aids are in place (anchors, hints, code bars, pending links).
    root.dataset.enhanced = 'true'
    void highlightBlocks(blocks.highlight, isCancelled)
    let theme = currentTheme()
    void drawDiagrams(blocks.diagrams, theme, isCancelled)
    const themeObserver = blocks.diagrams.length > 0 && typeof MutationObserver === 'function'
      ? new MutationObserver(() => {
          if (currentTheme() === theme) return
          theme = currentTheme()
          void drawDiagrams(blocks.diagrams, theme, isCancelled)
        })
      : null
    themeObserver?.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => {
      cancelled = true
      themeObserver?.disconnect()
    }
  }, [view, isPending])

  // A new lesson starts at the top unless the URL names a fragment.
  useEffect(() => {
    // Only when this lesson mounts (the view is keyed by lesson); fragments are handled below.
    if (!opensAtFragment.current) window.scrollTo(0, 0)
  }, [])

  // Fragment: after the lesson is in the page, and on every in-page link (L19).
  useEffect(() => {
    const target = fragmentTarget(location.hash)
    if (!target) return
    target.scrollIntoView?.({ behavior: prefersReducedMotion() ? 'auto' : 'smooth', block: 'start' })
    if (target.getAttribute('tabindex') === '-1') target.focus({ preventScroll: true })
  }, [location.key, location.hash])

  // Active section in the lesson index: the last heading above 30 % of the viewport (04).
  useEffect(() => {
    if (toc.length === 0 || typeof IntersectionObserver !== 'function') return
    const headings = toc.map((entry) => document.getElementById(entry.id)).filter((heading) => heading !== null)
    const update = () => {
      const line = window.innerHeight * 0.3
      let active = headings[0]
      for (const heading of headings) if (heading.getBoundingClientRect().top <= line) active = heading
      if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) {
        for (const heading of headings) if (heading.getBoundingClientRect().top < window.innerHeight) active = heading
      }
      setActiveId(active?.id ?? null)
    }
    const band = new IntersectionObserver(update, { rootMargin: '0px 0px -70% 0px', threshold: [0, 1] })
    const viewport = new IntersectionObserver(update, { threshold: [0] })
    for (const heading of headings) {
      band.observe(heading)
      viewport.observe(heading)
    }
    return () => {
      band.disconnect()
      viewport.disconnect()
    }
  }, [toc])

  function announce(message: string) {
    setLive(message)
    window.setTimeout(() => setLive((current) => (current === message ? '' : current)), FEEDBACK_MS)
  }

  async function copyCode(button: HTMLElement) {
    const text = codeTextFor(button)
    const label = button.querySelector('.copy-label')
    if (text === null || !label) return
    const copied = await copyText(text)
    label.textContent = copied ? 'Copiado' : 'No se pudo copiar'
    button.classList.toggle('is-done', copied)
    announce(copied ? 'Código copiado al portapapeles' : 'No se pudo copiar el código')
    window.setTimeout(() => {
      label.textContent = 'Copiar'
      button.classList.remove('is-done')
    }, FEEDBACK_MS)
  }

  async function copyLink(button: HTMLElement, id: string) {
    const url = new URL(location.pathname, window.location.origin)
    url.hash = id
    const copied = await copyText(url.href)
    button.textContent = copied ? '✓ Enlace copiado' : 'No se pudo copiar'
    button.classList.toggle('is-done', copied)
    announce(copied ? 'Enlace copiado' : 'No se pudo copiar el enlace')
    window.setTimeout(() => {
      button.textContent = '#'
      button.classList.remove('is-done')
    }, FEEDBACK_MS)
  }

  // One delegated handler for everything inside the lesson column (L19).
  function onContentClick(event: MouseEvent<HTMLElement>) {
    const target = event.target
    if (!(target instanceof Element)) return
    const codeButton = target.closest<HTMLElement>('[data-copy-code]')
    if (codeButton) {
      void copyCode(codeButton)
      return
    }
    const linkButton = target.closest<HTMLElement>('[data-copy-link]')
    if (linkButton) {
      void copyLink(linkButton, linkButton.dataset.copyLink ?? '')
      return
    }
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
    const link = target.closest('a')
    const href = link?.getAttribute('href')
    // External links (target="_blank" from the sanitizer), downloads and /api/resources stay with the browser.
    if (!link || href == null || link.hasAttribute('target') || link.hasAttribute('download')) return
    if (href.startsWith('#')) {
      event.preventDefault()
      navigate({ pathname: location.pathname, search: location.search, hash: href })
      return
    }
    if (href.startsWith('/lessons/') || href.startsWith('/courses/')) {
      event.preventDefault()
      const lessonId = lessonIdOf(href)
      if (lessonId !== null && isPending(lessonId)) return
      navigate(href)
    }
  }

  function toggleFocusMode() {
    const next = !focusMode
    rememberFocusMode(next)
    setFocusMode(next)
  }

  function confirmed(result: LessonStateResult | ExerciseStateResult) {
    if (result.courseAutoAdded) setToast(`${syllabus.course.title} se añadió a Mis cursos`)
  }

  async function saveLesson(state: LessonValue) {
    setLessonSave({ pending: state, status: { kind: 'saving' } })
    try {
      const result = await apiSend<LessonStateResult>('PUT', `/api/me/lessons/${encodeURIComponent(lesson.id)}/state`, { state })
      setProgress((current) => ({
        ...current,
        added: true,
        continuation: result.continuation,
        progress: result.progress,
        lessonStates: [
          ...current.lessonStates.filter((entry) => entry.lessonId !== result.lessonId),
          { lessonId: result.lessonId, state: result.state, updatedAt: result.updatedAt },
        ],
      }))
      setLessonSave({ pending: null, status: { kind: 'saved', updatedAt: result.updatedAt } })
      confirmed(result)
    } catch (caught) {
      setLessonSave({ pending: null, status: isAuthRequired(caught) ? { kind: 'idle' } : { kind: 'failed', message: errorMessage(caught) } })
    }
  }

  async function saveExercise(exerciseId: string, state: ExerciseValue) {
    const update = (save: SaveState<ExerciseValue>) => setExerciseSaves((current) => ({ ...current, [exerciseId]: save }))
    update({ pending: state, status: { kind: 'saving' } })
    try {
      const result = await apiSend<ExerciseStateResult>('PUT', `/api/me/exercises/${encodeURIComponent(exerciseId)}/state`, { state })
      setProgress((current) => ({
        ...current,
        added: true,
        continuation: result.continuation,
        progress: result.progress,
        exerciseStates: [
          ...current.exerciseStates.filter((entry) => entry.exerciseId !== result.exerciseId),
          { exerciseId: result.exerciseId, state: result.state, updatedAt: result.updatedAt },
        ],
      }))
      update({ pending: null, status: { kind: 'saved', updatedAt: result.updatedAt } })
      confirmed(result)
    } catch (caught) {
      update({ pending: null, status: isAuthRequired(caught) ? { kind: 'idle' } : { kind: 'failed', message: errorMessage(caught) } })
    }
  }

  const lessonBadge = savedLesson === 'completed'
    ? <span className="badge badge-done">Completada</span>
    : <span className="badge badge-todo">No completada</span>
  const total = view.exercises.length

  return (
    <div className={focusMode ? 'lesson-layout temario-oculto' : 'lesson-layout'}>
      <div className="mix">
        <Syllabus
          courseTitle={syllabus.course.title}
          groups={groups}
          currentLessonId={lesson.id}
          completed={completed}
          counts={progress.progress}
        />
        <div className="mix-main" onClick={onContentClick}>
          <div className="lbar" role="region" aria-label="Progreso de la lección">
            <button type="button" className="focus-btn" aria-controls={SYLLABUS_ID} onClick={toggleFocusMode}>
              <svg aria-hidden="true" className="icon" viewBox="0 0 24 24"><path fill="currentColor" d="M3 4h18v16H3V4zm2 2v12h4V6H5zm6 0v12h8V6h-8z" /></svg>
              <span>{focusMode ? 'Mostrar temario' : 'Ocultar temario'}</span>
            </button>
            <span className="lbar-title"><span className="lbar-code">{code}</span> {lesson.title}</span>
            <span className="lbar-lesson">Lección: {lessonBadge}</span>
            {total > 0 && (
              <a className="lbar-ex" href={`#${EXERCISES_ID}`}>
                <span>Ejercicios {counts.solved}/{total}</span>
                <span className="meter" aria-hidden="true">
                  {view.exercises.map((exercise) => <i key={exercise.id} data-s={exerciseStates.get(exercise.id) ?? 'pending'} />)}
                </span>
                <span className="vh">{counts.solved} resueltos, {counts.in_progress} en curso, {counts.pending} pendientes</span>
              </a>
            )}
          </div>
          <div className="mix-body">
            <div className="reading-wrap">
              {/* One <h1> per page: the delivered one when the lesson HTML starts with it. */}
              <article
                className="reading"
                aria-labelledby={bodyTitle === null ? 'lesson-title' : bodyTitle.id || undefined}
                aria-label={bodyTitle !== null && bodyTitle.id === '' ? lesson.title : undefined}
                ref={contentRef}
              >
                <header className="lesson-head">
                  <p className="kicker">{syllabus.course.title}{group ? ` · ${group.label}` : ''}</p>
                  {bodyTitle === null && <h1 id="lesson-title">{lesson.title}</h1>}
                  <p className="position">
                    Lección <strong>{code}</strong> · posición {lesson.position} de {syllabus.lessons.length} en el temario
                  </p>
                  <div className="head-state">
                    <StateControl legend="Estado de la lección" options={LESSON_OPTIONS} saved={savedLesson} save={lessonSave} onChange={saveLesson} />
                  </div>
                </header>
                <TocDropdown entries={toc} activeId={activeId} />
                <LessonHtml className="lesson-body" html={view.bodyHtml} />
                {total > 0 && (
                  <section className="exercises" id={EXERCISES_ID} aria-labelledby={`${EXERCISES_ID}-title`} tabIndex={-1}>
                    <h2 id={`${EXERCISES_ID}-title`}>Ejercicios prácticos resueltos</h2>
                    <p className="ex-summary">{counts.solved} resueltos, {counts.in_progress} en curso, {counts.pending} pendientes</p>
                    {view.exercises.map((exercise) => (
                      <ExerciseCard
                        key={exercise.id}
                        exercise={exercise}
                        saved={exerciseStates.get(exercise.id) ?? 'pending'}
                        save={exerciseSaves[exercise.id] ?? IDLE}
                        onChange={(state) => void saveExercise(exercise.id, state)}
                      />
                    ))}
                  </section>
                )}
                {view.resources.length > 0 && (
                  <section className="lesson-files" aria-labelledby="lesson-files-title">
                    <h2 id="lesson-files-title">Archivos de la lección</h2>
                    <ul>
                      {view.resources.map((resource) => (
                        <li key={resource.resourceId}><a className="resource-link" href={resource.url}>{resource.fileName}</a></li>
                      ))}
                    </ul>
                  </section>
                )}
                <section className="lesson-end" id={END_ID} aria-labelledby={`${END_ID}-title`} tabIndex={-1}>
                  <h2 id={`${END_ID}-title`}>Al terminar la lección</h2>
                  {total > 0 && <p>Ejercicios: {counts.solved} resueltos, {counts.in_progress} en curso, {counts.pending} pendientes.</p>}
                  <StateControl legend="Estado de la lección" options={LESSON_OPTIONS} saved={savedLesson} save={lessonSave} onChange={saveLesson} />
                </section>
                <nav className="pager" aria-label="Lecciones anterior y siguiente">
                  <Pager label="anterior" direction="← Anterior" lessonId={view.previousLessonId} titles={titles} />
                  <Pager label="siguiente" direction="Siguiente →" lessonId={view.nextLessonId} titles={titles} />
                </nav>
              </article>
            </div>
            <TocRail entries={toc} activeId={activeId} />
          </div>
        </div>
      </div>
      <p hidden id={PENDING_LINK_DESCRIPTION_ID}>Lección pendiente: todavía no disponible, no se puede abrir.</p>
      <div className="vh" aria-live="polite">{live}</div>
      {toast !== null && (
        <div className="toast" role="status">
          <svg aria-hidden="true" className="icon" viewBox="0 0 24 24"><path fill="currentColor" d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" /></svg>
          <span className="toast-text">{toast}</span>
          <button type="button" className="toast-close" aria-label="Cerrar aviso" onClick={() => setToast(null)}>×</button>
        </div>
      )}
    </div>
  )
}
