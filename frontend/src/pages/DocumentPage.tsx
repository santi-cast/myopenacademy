import { memo, useEffect, useRef, type MouseEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { apiGet } from '../api/client'
import type { DocumentView, Syllabus } from '../api/types'
import { copyText } from '../lesson/clipboard'
import { codeTextFor, decorateCodeBlocks, drawDiagrams, highlightBlocks } from '../lesson/enhance'
import { challengesLabel, groupByPhase } from '../syllabus'
import { currentTheme } from '../theme/theme'
import { LoadFailure } from './LoadFailure'
import { useLoad } from './useApi'
import '../lesson/lesson.css'

type Loaded = { view: DocumentView; syllabus: Syllabus }

async function loadDocument(id: string): Promise<Loaded> {
  const view = await apiGet<DocumentView>(`/api/documents/${encodeURIComponent(id)}`)
  const syllabus = await apiGet<Syllabus>(`/api/courses/${encodeURIComponent(view.document.courseId)}`)
  return { view, syllabus }
}

const Body = memo(function Body({ html }: { html: string }) {
  return <div className="lesson-body" dangerouslySetInnerHTML={{ __html: html }} />
})

/**
 * A document without progress (H8): the challenges of a phase or the course glossary. Same body treatment as a lesson
 * or a stage (code, Mermaid, anchors), no state to mark: the challenges are optional and nothing is recorded.
 */
function DocumentContent({ view, syllabus }: Loaded) {
  const root = useRef<HTMLElement>(null)
  const location = useLocation()
  const navigate = useNavigate()
  const { document: entry } = view
  const course = syllabus.course
  const groups = groupByPhase(syllabus.phases, syllabus.lessons)
  const phase = entry.phaseId === null ? null : groups.find((group) => group.key === entry.phaseId) ?? null
  // «02.01 Cómo funciona un ordenador», as the lesson pager writes them.
  const codes = new Map(groups.flatMap((group) => group.rows.map(({ lesson, code }) => [lesson.id, `${code} ${lesson.title}`] as const)))

  useEffect(() => {
    if (!root.current) return
    let cancelled = false
    const blocks = decorateCodeBlocks(root.current)
    void highlightBlocks(blocks.highlight, () => cancelled)
    const draw = () => { void drawDiagrams(blocks.diagrams, currentTheme(), () => cancelled) }
    draw()
    const observer = new MutationObserver(draw)
    observer.observe(window.document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => { cancelled = true; observer.disconnect() }
  }, [view])

  useEffect(() => {
    if (!location.hash) { window.scrollTo(0, 0); return }
    let id = location.hash.slice(1)
    try { id = decodeURIComponent(id) } catch { /* Literal fragment. */ }
    window.document.getElementById(id)?.scrollIntoView?.({ block: 'start' })
  }, [location.hash, location.key])

  async function onContentClick(event: MouseEvent<HTMLElement>) {
    const target = event.target instanceof Element ? event.target : null
    const link = target?.closest<HTMLAnchorElement>('a[href]')
    const href = link?.getAttribute('href') ?? ''
    // Links to lessons, stages, documents and courses stay in the application, like a lesson's.
    if (link && /^\/(lessons|stages|documents|courses)\//.test(href) && !event.ctrlKey && !event.metaKey && !event.shiftKey) {
      event.preventDefault()
      void navigate(href)
      return
    }
    const button = target?.closest<HTMLElement>('[data-copy-code]')
    if (!button) return
    const text = codeTextFor(button)
    const label = button.querySelector('.copy-label')
    if (text === null || !label) return
    label.textContent = await copyText(text) ? 'Copiado' : 'No se pudo copiar'
  }

  const firstIsTitle = new DOMParser().parseFromString(view.bodyHtml, 'text/html').body.firstElementChild?.localName === 'h1'
  const kicker = entry.kind === 'retos'
    ? `${course.title} · ${phase ? phase.label : challengesLabel(syllabus.phases, entry.phaseId)} · Opcionales, sin seguimiento`
    : `${course.title} · ${({ glosario: 'Glosario', ruta: 'Ruta de aprendizaje', 'ruta-minima': 'Ruta mínima', chuleta: 'Chuleta global' } as const)[entry.kind]}`
  return <div className="page">
    <p><Link to={`/courses/${encodeURIComponent(course.id)}`}>Índice del curso</Link></p>
    <article className="reading" ref={root} aria-label={entry.title} onClick={onContentClick}>
      <header className="lesson-head">
        <p className="kicker">{kicker}</p>
        {!firstIsTitle && <h1>{entry.title}</h1>}
      </header>
      <Body html={view.bodyHtml} />
      {view.resources.length > 0 && <section className="lesson-files"><h2>Archivos</h2><ul>
        {view.resources.map((resource) => <li key={resource.resourceId}><a className="resource-link" href={resource.url}>{resource.fileName}</a></li>)}
      </ul></section>}
      {entry.kind === 'retos' && (
        // Between the last module of the phase and the first module of the next one (H8).
        <nav className="pager" aria-label="Lecciones anterior y siguiente">
          {[view.previousLessonId, view.nextLessonId].map((id, position) => {
            const title = id ? codes.get(id) : undefined
            const className = position === 1 ? 'pager-item pager-next' : 'pager-item'
            const direction = position === 0 ? '← Anterior' : 'Siguiente →'
            return id && title
              ? <Link key={position} className={className} to={`/lessons/${encodeURIComponent(id)}`}>
                <span className="pager-dir">{direction}</span><span className="pager-title">{title}</span>
              </Link>
              : <div key={position} className={`${className} is-empty`}>
                <span className="pager-dir">{direction}</span>
                <span className="pager-title">No hay lección {position === 0 ? 'anterior' : 'siguiente'} disponible</span>
              </div>
          })}
        </nav>
      )}
    </article>
  </div>
}

export function DocumentPage() {
  const id = useParams().documentId ?? ''
  const [loaded] = useLoad(id, loadDocument)
  if (loaded.status === 'loading') return <div className="page"><p role="status">Cargando…</p></div>
  if (loaded.status === 'failed') return <div className="page"><LoadFailure error={loaded.error} /></div>
  return <DocumentContent key={id} {...loaded.data} />
}
