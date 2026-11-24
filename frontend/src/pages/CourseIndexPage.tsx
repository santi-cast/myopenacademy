import { Link, useParams } from 'react-router'
import { apiGet } from '../api/client'
import type { DocumentEntry, LessonEntry, Progress, Syllabus } from '../api/types'
import { partProgressText, phaseProgressText } from '../progress'
import { countLessons, countRows, groupByPart, groupByPhase, type PhaseGroup, type SyllabusRow } from '../syllabus'
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

const COURSE_DOCUMENT_LABELS: Record<DocumentEntry['kind'], string> = {
  'ruta': 'Ruta de aprendizaje', 'ruta-minima': 'Ruta mínima', 'chuleta': 'Chuleta global', 'glosario': 'Glosario del curso',
  'retos': 'Retos de la fase',
}

function LessonState({ lesson, completed }: { lesson: LessonEntry; completed: boolean }) {
  if (!lesson.available) return <span className="badge badge-pending">Todavía no disponible</span>
  // No saved state means «no completada» (docs/api.md, «Progreso y continuación»).
  return completed
    ? <span className="badge badge-done">Completada</span>
    : <span className="badge badge-todo">No completada</span>
}

function LessonTable({ caption, count, rows, completed, challenges, projectLessonId }: {
  caption: string
  count: string
  rows: SyllabusRow[]
  completed: Set<string>
  challenges?: DocumentEntry
  projectLessonId?: string | null
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
              {lesson.id === projectLessonId && <> <span className="badge badge-project">Proyecto del curso</span></>}
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

// The course project, from curso.json (FORMATO.md 8), never from the course ID: a project by stages has its own
// page; a module project is a lesson of the syllabus, marked in its row and linked here.
function ProjectLink({ courseId, syllabus }: { courseId: string; syllabus: Syllabus }) {
  const project = syllabus.project
  if (!project) return null
  const name = project.title ? `Proyecto del curso: ${project.title}` : 'Proyecto del curso'
  if (project.kind === 'etapas') {
    return <p className="index-project"><Link to={`/courses/${encodeURIComponent(courseId)}/project`}>{name}</Link></p>
  }
  const lesson = syllabus.lessons.find((entry) => entry.id === project.lessonId)
  if (!lesson) return null
  return <p className="index-project">
    {lesson.available
      ? <Link to={`/lessons/${encodeURIComponent(lesson.id)}`}>{name} ({lesson.title})</Link>
      : <span>{name} ({lesson.title}, todavía no disponible)</span>}
  </p>
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
  // A course with parts groups its phases by part (H12, S5); one without parts keeps them as they were (S1).
  const { parts, rest } = groupByPart(syllabus.parts, phases)
  const documents = syllabus.documents ?? []
  // The course-wide documents (FORMATO.md 4 and 9), in this order, when the course has them.
  const courseDocuments = (['ruta', 'ruta-minima', 'chuleta', 'glosario'] as const)
    .map((kind) => documents.find((document) => document.kind === kind))
    .filter((document): document is DocumentEntry => document !== undefined)
  const phaseTable = (phase: PhaseGroup) => {
    const availability = `${phase.rows.filter(({ lesson }) => lesson.available).length} de ${phase.rows.length} ${phase.rows.length === 1 ? 'disponible' : 'disponibles'}`
    return (
      <LessonTable
        key={phase.key}
        caption={phase.label}
        count={`${availability} · ${phaseProgressText(countRows(phase.rows, completed))}`}
        rows={phase.rows}
        completed={completed}
        challenges={documents.find((document) => document.kind === 'retos' && document.phaseId === phase.key)}
        projectLessonId={syllabus.project?.kind === 'modulo' ? syllabus.project.lessonId : null}
      />
    )
  }

  return (
    <div className="index">
      <div className="index-head">
        <div>
          <p className="kicker">Curso</p>
          <h1>{syllabus.course.title} · Índice del curso</h1>
          <ProgressSummary counts={progress.progress} />
          {courseDocuments.length > 0 && (
            <p className="index-glossary">
              {courseDocuments.map((document, index) => (
                <span key={document.id}>
                  {index > 0 && ' · '}
                  <Link to={`/documents/${encodeURIComponent(document.id)}`}>{COURSE_DOCUMENT_LABELS[document.kind]}</Link>
                </span>
              ))}
            </p>
          )}
        </div>
        {continuation && (
          <Link className="btn btn-end" to={`/lessons/${encodeURIComponent(continuation.id)}`}>
            Continuar: {continuation.title}
          </Link>
        )}
      </div>
      <ProjectLink courseId={courseId} syllabus={syllabus} />
      <p hidden id={PENDING_DESCRIPTION_ID}>Lección pendiente: todavía no disponible, no se puede abrir.</p>
      {parts.length === 0
        ? <div className="phase-grid">{phases.map(phaseTable)}</div>
        : <>
          {parts.map((part) => {
            const headingId = `part-${part.key}`
            return (
              <section key={part.key} className="part-block" aria-labelledby={headingId}>
                <div className="part-head">
                  <h2 id={headingId}>{part.label}</h2>
                  <p className="part-progress">{partProgressText(countLessons(part.phases.flatMap((phase) => phase.rows.map(({ lesson }) => lesson)), completed))}</p>
                </div>
                <div className="phase-grid">{part.phases.map(phaseTable)}</div>
              </section>
            )
          })}
          {rest.length > 0 && <div className="phase-grid">{rest.map(phaseTable)}</div>}
        </>}
    </div>
  )
}
