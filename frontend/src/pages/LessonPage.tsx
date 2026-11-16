import { Link, useParams } from 'react-router'
import { apiGet } from '../api/client'
import { errorCode } from '../api/errors'
import type { LessonView as LessonPayload, Progress, Syllabus } from '../api/types'
import { LessonView, type LessonData } from '../lesson/LessonView'
import { LoadFailure } from './LoadFailure'
import { useLoad } from './useApi'

async function loadLesson(lessonId: string): Promise<LessonData> {
  const view = await apiGet<LessonPayload>(`/api/lessons/${encodeURIComponent(lessonId)}`)
  const courseId = encodeURIComponent(view.lesson.courseId)
  const [syllabus, progress] = await Promise.all([
    apiGet<Syllabus>(`/api/courses/${courseId}`),
    apiGet<Progress>(`/api/me/courses/${courseId}/progress`),
  ])
  return { view, syllabus, progress }
}

export function LessonPage() {
  const lessonId = useParams().lessonId ?? ''
  const [loaded] = useLoad(lessonId, loadLesson)

  if (loaded.status === 'loading') return <div className="page"><p className="page-status" role="status">Cargando…</p></div>
  if (loaded.status === 'failed') {
    if (errorCode(loaded.error) === 'LESSON_UNAVAILABLE') {
      return (
        <div className="page">
          <h1>Esta lección todavía no está disponible.</h1>
          <p>Puedes verla en el índice del curso, pero todavía no se puede abrir.</p>
          <p><Link to="/">Volver a Mis cursos</Link></p>
        </div>
      )
    }
    return <div className="page"><LoadFailure error={loaded.error} /></div>
  }
  return <LessonView key={lessonId} data={loaded.data} />
}
