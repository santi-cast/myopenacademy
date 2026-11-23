import { Link } from 'react-router'
import { apiGet } from '../api/client'
import type { MyCourses, Progress, Syllabus } from '../api/types'
import { countText } from '../progress'
import { countLessons, partOfLesson } from '../syllabus'
import { LoadFailure } from './LoadFailure'
import { ProgressSummary } from './ProgressSummary'
import { useLoad } from './useApi'

const loadMyCourses = () => apiGet<MyCourses>('/api/me/courses')

// «Vas por: 2. Java avanzado (5 de 30)» (H12, S7): the part of the lesson that «Continuar» opens, with its lessons
// completed of those available (S8). Null for a course without parts: its card stays as it was (S1).
async function loadCurrentPart(key: string): Promise<string | null> {
  const [courseId, lessonId] = JSON.parse(key) as [string, string]
  const path = encodeURIComponent(courseId)
  const syllabus = await apiGet<Syllabus>(`/api/courses/${path}`)
  const part = partOfLesson(syllabus.parts, syllabus.lessons.find((lesson) => lesson.id === lessonId))
  if (!part) return null
  const progress = await apiGet<Progress>(`/api/me/courses/${path}/progress`)
  const completed = new Set(progress.lessonStates.filter((state) => state.state === 'completed').map((state) => state.lessonId))
  const lessons = syllabus.lessons.filter((lesson) => part.phaseIds.includes(lesson.phaseId))
  return `Vas por: ${part.position}. ${part.title} (${countText(countLessons(lessons, completed))})`
}

// Only a guide: while it loads, or if it cannot load, the card shows nothing more.
function CurrentPart({ courseId, lessonId }: { courseId: string; lessonId: string }) {
  const [loaded] = useLoad(JSON.stringify([courseId, lessonId]), loadCurrentPart)
  return loaded.status === 'ready' && loaded.data ? <p className="current-part">{loaded.data}</p> : null
}

export function MyCoursesPage() {
  const [loaded] = useLoad('me', loadMyCourses)

  return (
    <div className="page">
      <h1>Mis cursos</h1>
      {loaded.status === 'loading' && <p className="page-status" role="status">Cargando…</p>}
      {loaded.status === 'failed' && <LoadFailure error={loaded.error} />}
      {loaded.status === 'ready' && loaded.data.courses.length === 0 && (
        <div className="empty">
          <p>Todavía no has añadido ningún curso.</p>
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
                  {lessonId && <CurrentPart courseId={course.id} lessonId={lessonId} />}
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
