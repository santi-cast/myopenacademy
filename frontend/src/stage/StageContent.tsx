import { memo, useEffect, useRef, useState, type MouseEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { apiSend } from '../api/client'
import { errorMessage, isAuthRequired } from '../api/errors'
import type { StageIndex, StageProgress, StageState, StageStateResult, StageView } from '../api/types'
import { codeTextFor, decorateCodeBlocks, drawDiagrams, highlightBlocks } from '../lesson/enhance'
import { copyText } from '../lesson/clipboard'
import type { SaveState } from '../lesson/saveStatus'
import { StateControl } from '../lesson/StateControl'
import { currentTheme } from '../theme/theme'
import { StageSummary } from './StageSummary'
import '../lesson/lesson.css'

type Value = StageState['state']
const options: { value: Value; label: string }[] = [
  { value: 'not_completed', label: 'No completada' },
  { value: 'completed', label: 'Completada' },
]
const Body = memo(function Body({ html }: { html: string }) {
  return <div className="lesson-body" dangerouslySetInnerHTML={{ __html: html }} />
})

export function StageContent({ view, progress: initial, index, courseTitle }: {
  view: StageView; progress: StageProgress; index: StageIndex; courseTitle: string
}) {
  const [progress, setProgress] = useState(initial)
  const [save, setSave] = useState<SaveState<Value>>({ pending: null, status: { kind: 'idle' } })
  const [notice, setNotice] = useState<string | null>(null)
  const sent = useRef(false)
  const root = useRef<HTMLElement>(null)
  const location = useLocation()
  const { stage } = view
  const saved = progress.stageStates.find((entry) => entry.stageId === stage.id)?.state ?? 'not_completed'

  useEffect(() => {
    if (sent.current || !initial.added) return
    sent.current = true
    let active = true
    apiSend<StageProgress>('PUT', `/api/me/courses/${encodeURIComponent(stage.courseId)}/stage-continuation`, { stageId: stage.id }).then(
      (result) => { if (active) setProgress((current) => ({ ...current, continuation: result.continuation })) },
      () => { /* No saved claim and no automatic retry, as with lesson continuation. */ },
    )
    return () => { active = false }
  }, [initial.added, stage.courseId, stage.id])

  // Use the same strict Mermaid and highlighting policy as lessons, not another renderer.
  useEffect(() => {
    if (!root.current) return
    let cancelled = false
    const blocks = decorateCodeBlocks(root.current)
    void highlightBlocks(blocks.highlight, () => cancelled)
    const draw = () => { void drawDiagrams(blocks.diagrams, currentTheme(), () => cancelled) }
    draw()
    const observer = new MutationObserver(draw)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => { cancelled = true; observer.disconnect() }
  }, [view])

  useEffect(() => {
    if (!location.hash) { window.scrollTo(0, 0); return }
    let id = location.hash.slice(1)
    try { id = decodeURIComponent(id) } catch { /* Literal fragment. */ }
    document.getElementById(id)?.scrollIntoView?.({ block: 'start' })
  }, [location.hash, location.key])

  async function change(state: Value) {
    setSave({ pending: state, status: { kind: 'saving' } })
    try {
      const result = await apiSend<StageStateResult>('PUT', `/api/me/stages/${encodeURIComponent(stage.id)}/state`, { state })
      setProgress((current) => ({
        ...current, added: true, continuation: result.continuation, progress: result.progress,
        stageStates: [...current.stageStates.filter((entry) => entry.stageId !== result.stageId),
          { stageId: result.stageId, state: result.state, updatedAt: result.updatedAt }],
      }))
      setSave({ pending: null, status: { kind: 'saved', updatedAt: result.updatedAt } })
      if (result.courseAutoAdded) setNotice(`${courseTitle} se añadió a Mis cursos`)
    } catch (caught) {
      setSave({ pending: null, status: isAuthRequired(caught) ? { kind: 'idle' } : { kind: 'failed', message: errorMessage(caught) } })
    }
  }

  async function onContentClick(event: MouseEvent<HTMLElement>) {
    const button = event.target instanceof Element ? event.target.closest<HTMLElement>('[data-copy-code]') : null
    if (!button) return
    const text = codeTextFor(button)
    const label = button.querySelector('.copy-label')
    if (text === null || !label) return
    label.textContent = await copyText(text) ? 'Copiado' : 'No se pudo copiar'
  }

  const firstIsTitle = new DOMParser().parseFromString(view.bodyHtml, 'text/html').body.firstElementChild?.localName === 'h1'
  return <div className="page">
    <p><Link to={`/courses/${encodeURIComponent(stage.courseId)}/project`}>Proyecto del curso</Link> · <Link to={`/courses/${encodeURIComponent(stage.courseId)}`}>Índice del curso</Link></p>
    <article className="reading" ref={root} aria-label={stage.title} onClick={onContentClick}>
      <header className="lesson-head">
        <p className="kicker">{courseTitle} · Proyecto del curso · Etapa {stage.position}</p>
        {!firstIsTitle && <h1>{stage.title}</h1>}
        <StageSummary counts={progress.progress} />
        <StateControl legend="Estado de la etapa" options={options} saved={saved} save={save} onChange={change} />
      </header>
      <Body html={view.bodyHtml} />
      {view.resources.length > 0 && <section className="lesson-files"><h2>Archivos de la etapa</h2><ul>
        {view.resources.map((resource) => <li key={resource.resourceId}><a className="resource-link" href={resource.url}>{resource.fileName}</a></li>)}
      </ul></section>}
      <nav className="pager" aria-label="Etapas anterior y siguiente">
        {[view.previousStageId, view.nextStageId].map((id, position) => {
          const entry = index.stages.find((candidate) => candidate.id === id && candidate.available)
          return entry ? <Link key={position} className="pager-item" to={`/stages/${encodeURIComponent(entry.id)}`}>
            <span className="pager-dir">{position === 0 ? '← Anterior' : 'Siguiente →'}</span><span className="pager-title">{entry.title}</span>
          </Link> : <span key={position} className="pager-item is-empty">No hay etapa {position === 0 ? 'anterior' : 'siguiente'} disponible</span>
        })}
      </nav>
    </article>
    {notice && <p className="notice" role="status">{notice}</p>}
  </div>
}
