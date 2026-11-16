import { Link, useParams } from 'react-router'
import { apiGet } from '../api/client'
import type { DocumentEntry, LessonEntry, Progress, Syllabus } from '../api/types'
import { groupByPhase, type SyllabusRow } from '../syllabus'
import { LoadFailure } from './LoadFailure'
import { ProgressSummary } from './ProgressSummary'
import { useLoad } from './useApi'

type CourseIndex = { syllabus: Syllabus; progress: Progress }

async function loadIndex(courseId: string): Promise<CourseIndex> {
  const path = encodeURIComponent(courseId)
  const [syllabus, progress] = await Promise.all([
    apiGet<Syllabus>(`/api/courses/${path}`),
    apiGet<Progress>(`/api/me/courses/${path}/progress`),
  ])
  return { syllabus, progress }
}

const PENDING_DESCRIPTION_ID = 'pending-lesson-description'

function LessonState({ lesson, completed }: { lesson: LessonEntry; completed: boolean }) {
  if (!lesson.available) return <span className="badge badge-pending">Todavía no disponible</span>
  // No saved state means «no completada» (docs/api.md, «Progreso y continuación»).
  return completed
    ? <span className="badge badge-done">Completada</span>
    : <span className="badge badge-todo">No completada</span>
}

function LessonTable({ caption, count, rows, completed, challenges }: {
  caption: string
  count: string
  rows: SyllabusRow[]
  completed: Set<string>
  challenges?: DocumentEntry
}) {
  return (
    <table className="phase-table">
      <caption>{caption} <span>{count}</span></caption>
      <thead>
        <tr>
          <th scope="col">Código</th>
          <th scope="col">Lección</th>
          <th scope="col">Estado</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(({ lesson, code }) => (
          <tr key={lesson.id} className={lesson.available ? undefined : 'is-pending'}>
            <td>{code}</td>
            <td>
              {lesson.available
                ? <Link to={`/lessons/${encodeURIComponent(lesson.id)}`}>{lesson.title}</Link>
                : <span className="lesson-pending" tabIndex={0} aria-describedby={PENDING_DESCRIPTION_ID}>{lesson.title}</span>}
            </td>
            <td><LessonState lesson={lesson} completed={completed.has(lesson.id)} /></td>
          </tr>
        ))}
      </tbody>
      {challenges && (
        // The challenges close their phase: optional, with no progress and outside the counts (FORMATO.md 6).
        <tfoot>
          <tr className="phase-challenges">
            <td>Retos</td>
            <td><Link to={`/documents/${encodeURIComponent(challenges.id)}`}>Retos de la fase</Link></td>
            <td><span className="badge badge-optional">Opcionales</span></td>
          </tr>
        </tfoot>
      )}
    </table>
  )
}

export function CourseIndexPage() {
  const courseId = useParams().courseId ?? ''
  const [loaded] = useLoad(courseId, loadIndex)

  if (loaded.status === 'loading') return <div className="page"><p className="page-status" role="status">Cargando…</p></div>
  if (loaded.status === 'failed') return <div className="page"><LoadFailure error={loaded.error} /></div>

  const { syllabus, progress } = loaded.data
  const completed = new Set(progress.lessonStates.filter((state) => state.state === 'completed').map((state) => state.lessonId))
  const continuation = syllabus.lessons.find((lesson) => lesson.id === progress.continuation.lastOpenedLessonId)
  const phases = groupByPhase(syllabus.phases, syllabus.lessons)
  const documents = syllabus.documents ?? []
  const glossary = documents.find((document) => document.kind === 'glosario')

  return (
    <div className="index">
      <div className="index-head">
        <div>
          <p className="kicker">Curso</p>
          <h1>{syllabus.course.title} · Índice del curso</h1>
          <ProgressSummary counts={progress.progress} />
          {glossary && <p className="index-glossary"><Link to={`/documents/${encodeURIComponent(glossary.id)}`}>Glosario del curso</Link></p>}
        </div>
        {continuation && (
          <Link className="btn btn-end" to={`/lessons/${encodeURIComponent(continuation.id)}`}>
            Continuar: {continuation.title}
          </Link>
        )}
      </div>
      {courseId === 'java' && <p><Link to={`/courses/${encodeURIComponent(courseId)}/project`}>Proyecto evolutivo</Link></p>}
      <p hidden id={PENDING_DESCRIPTION_ID}>Lección pendiente: todavía no disponible, no se puede abrir.</p>
      <div className="phase-grid">
        {phases.map((phase) => (
          <LessonTable
            key={phase.key}
            caption={phase.label}
            count={`${phase.rows.filter(({ lesson }) => lesson.available).length} de ${phase.rows.length} ${phase.rows.length === 1 ? 'disponible' : 'disponibles'}`}
            rows={phase.rows}
            completed={completed}
            challenges={documents.find((document) => document.kind === 'retos' && document.phaseId === phase.key)}
          />
        ))}
      </div>
    </div>
  )
}
