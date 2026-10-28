import { Link, useParams } from 'react-router'
import { apiGet } from '../api/client'
import { errorCode } from '../api/errors'
import type { LessonView } from '../api/types'
import { LoadFailure } from './LoadFailure'
import { useLoad } from './useApi'

const loadLesson = (lessonId: string) => apiGet<LessonView>(`/api/lessons/${encodeURIComponent(lessonId)}`)

// Placeholder until T9 part b: title only. It records no continuation.
export function LessonPage() {
  const lessonId = useParams().lessonId ?? ''
  const [loaded] = useLoad(lessonId, loadLesson)

  if (loaded.status === 'loading') return <div className="page"><p className="page-status" role="status">Cargando…</p></div>
  if (loaded.status === 'failed') {
    if (errorCode(loaded.error) === 'LESSON_UNAVAILABLE') {
      return (
        <div className="page">
          <h1>Esta lección todavía no está disponible.</h1>
          <p><Link to="/">Volver a Mis cursos</Link></p>
        </div>
      )
    }
    return <div className="page"><LoadFailure error={loaded.error} /></div>
  }

  const { lesson } = loaded.data
  return (
    <div className="page">
      <p className="kicker">Lección</p>
      <h1>{lesson.title}</h1>
      <p><Link to={`/courses/${encodeURIComponent(lesson.courseId)}`}>Ver el índice del curso</Link></p>
    </div>
  )
}
