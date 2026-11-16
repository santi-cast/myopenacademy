import { Link, useParams } from 'react-router'
import { apiGet } from '../api/client'
import { errorCode } from '../api/errors'
import type { StageIndex, StageProgress, StageView, Syllabus } from '../api/types'
import { StageContent } from '../stage/StageContent'
import { LoadFailure } from './LoadFailure'
import { useLoad } from './useApi'

async function loadStage(id: string) {
  const view = await apiGet<StageView>(`/api/stages/${encodeURIComponent(id)}`)
  const course = encodeURIComponent(view.stage.courseId)
  const [progress, index, syllabus] = await Promise.all([
    apiGet<StageProgress>(`/api/me/courses/${course}/stage-progress`),
    apiGet<StageIndex>(`/api/courses/${course}/stages`),
    apiGet<Syllabus>(`/api/courses/${course}`),
  ])
  return { view, progress, index, courseTitle: syllabus.course.title }
}

export function StagePage() {
  const id = useParams().stageId ?? ''
  const [loaded] = useLoad(id, loadStage)
  if (loaded.status === 'loading') return <div className="page"><p role="status">Cargando…</p></div>
  if (loaded.status === 'failed') return <div className="page">
    {errorCode(loaded.error) === 'STAGE_UNAVAILABLE'
      ? <><h1>Esta etapa todavía no está disponible.</h1><p>Puedes verla en el índice del proyecto, pero todavía no se puede abrir.</p><Link to="/">Volver a Mis cursos</Link></>
      : <LoadFailure error={loaded.error} />}
  </div>
  return <StageContent key={id} {...loaded.data} />
}
