import { Link } from 'react-router'
import { apiGet } from '../api/client'
import type { MyCourses } from '../api/types'
import { LoadFailure } from './LoadFailure'
import { ProgressSummary } from './ProgressSummary'
import { useLoad } from './useApi'

const loadMyCourses = () => apiGet<MyCourses>('/api/me/courses')

export function MyCoursesPage() {
  const [loaded] = useLoad('me', loadMyCourses)

  return (
    <div className="page">
      <h1>Mis cursos</h1>
      {loaded.status === 'loading' && <p className="page-status" role="status">Cargando…</p>}
      {loaded.status === 'failed' && <LoadFailure error={loaded.error} />}
      {loaded.status === 'ready' && loaded.data.courses.length === 0 && (
        <div className="empty">
          <p>Todavía no añadiste ningún curso.</p>
          <Link className="btn" to="/courses">Explorar el catálogo</Link>
        </div>
      )}
      {loaded.status === 'ready' && loaded.data.courses.length > 0 && (
        <ul className="course-list">
          {loaded.data.courses.map(({ course, enrollment }) => {
            const lessonId = enrollment.continuation.lastOpenedLessonId
            const titleId = `my-course-${course.id}`
            return (
              <li key={course.id}>
                <article className="course-card" aria-labelledby={titleId}>
                  <h2 id={titleId}>{course.title}</h2>
                  <ProgressSummary counts={enrollment.progress} />
                  {lessonId
                    ? <Link className="btn" to={`/lessons/${encodeURIComponent(lessonId)}`}>Continuar</Link>
                    : <Link className="btn" to={`/courses/${encodeURIComponent(course.id)}`}>Ver el índice del curso</Link>}
                </article>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
