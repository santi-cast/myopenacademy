import { Link, useParams } from 'react-router'
import { apiGet } from '../api/client'
import type { StageIndex, StageProgress, Syllabus } from '../api/types'
import { StageSummary } from '../stage/StageSummary'
import { LoadFailure } from './LoadFailure'
import { useLoad } from './useApi'

async function loadProject(id: string) {
  const path = encodeURIComponent(id)
  const [index, progress, syllabus] = await Promise.all([
    apiGet<StageIndex>(`/api/courses/${path}/stages`),
    apiGet<StageProgress>(`/api/me/courses/${path}/stage-progress`),
    apiGet<Syllabus>(`/api/courses/${path}`),
  ])
  return { index, progress, syllabus }
}

export function ProjectPage() {
  const courseId = useParams().courseId ?? ''
  const [loaded] = useLoad(courseId, loadProject)
  if (loaded.status === 'loading') return <div className="page"><p role="status">Cargando…</p></div>
  if (loaded.status === 'failed') return <div className="page"><LoadFailure error={loaded.error} /></div>
  const { index, progress, syllabus } = loaded.data
  const last = index.stages.find((stage) => stage.available && stage.id === progress.continuation.lastOpenedStageId)
  return <div className="index">
    <div className="index-head">
      <div><p className="kicker">Proyecto evolutivo</p><h1>{syllabus.course.title} · Proyecto evolutivo</h1><StageSummary counts={progress.progress} /></div>
      {last && <Link className="btn btn-end" to={`/stages/${encodeURIComponent(last.id)}`}>Retomar proyecto</Link>}
    </div>
    <p><Link to={`/courses/${encodeURIComponent(courseId)}`}>Índice del curso</Link></p>
    <p>Progreso independiente de las lecciones. Marcá vos cada etapa como completada o no completada.</p>
    <p hidden id="pending-stage-description">Etapa pendiente: todavía no disponible, no se puede abrir.</p>
    {index.stages.length === 0 ? <p>Todavía no hay etapas en este curso.</p> : <table className="phase-table">
      <caption>Etapas del proyecto</caption>
      <thead><tr><th scope="col">Etapa</th><th scope="col">Título</th><th scope="col">Estado</th></tr></thead>
      <tbody>{index.stages.map((stage) => {
        const completed = progress.stageStates.some((state) => state.stageId === stage.id && state.state === 'completed')
        return <tr key={stage.id} className={stage.available ? undefined : 'is-pending'}>
          <td>{stage.position}</td>
          <td>{stage.available ? <Link to={`/stages/${encodeURIComponent(stage.id)}`}>{stage.title}</Link>
            : <span tabIndex={0} aria-describedby="pending-stage-description">{stage.title}</span>}</td>
          <td><span className={`badge ${!stage.available ? 'badge-pending' : completed ? 'badge-done' : 'badge-todo'}`}>
            {!stage.available ? 'Todavía no disponible' : completed ? 'Completada' : 'No completada'}
          </span></td>
        </tr>
      })}</tbody>
    </table>}
  </div>
}
