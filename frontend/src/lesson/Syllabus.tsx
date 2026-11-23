import { useState } from 'react'
import { Link } from 'react-router'
import type { Counts, LessonEntry } from '../api/types'
import { countText, progressText } from '../progress'
import { countLessons, countRows, type PartGroup, type PhaseGroup } from '../syllabus'

export const SYLLABUS_ID = 'lesson-syllabus'

function LessonBadge({ lesson, completed }: { lesson: LessonEntry; completed: boolean }) {
  if (!lesson.available) return <span className="p">Pendiente</span>
  return completed
    ? <span className="badge badge-done">Completada</span>
    : <span className="badge badge-todo">No completada</span>
}

function PhaseDetails({ group, currentLessonId, completed }: { group: PhaseGroup; currentLessonId: string; completed: Set<string> }) {
  return (
    <details open={group.rows.some(({ lesson }) => lesson.id === currentLessonId)}>
      <summary>
        {group.label}
        {/* Its progress (H12, S1), then its availability as before. */}
        <span className="ph-count">{countText(countRows(group.rows, completed))}</span>
        <span className="ph-count">{group.rows.filter(({ lesson }) => lesson.available).length}/{group.rows.length}</span>
      </summary>
      <ol>
        {group.rows.map(({ lesson, code }) => {
          const current = lesson.id === currentLessonId
          return (
            <li key={lesson.id} className={current ? 'current' : undefined}>
              <span className="c">{code}</span>
              {lesson.available
                ? <Link className="t" to={`/lessons/${encodeURIComponent(lesson.id)}`} aria-current={current ? 'page' : undefined}>{lesson.title}</Link>
                : <span className="t">{lesson.title}</span>}
              <LessonBadge lesson={lesson} completed={completed.has(lesson.id)} />
            </li>
          )
        })}
      </ol>
    </details>
  )
}

// A part of the course (H12, S6): a disclosure button over its phases, open at first only for the current lesson.
function PartSection({ part, bodyId, currentLessonId, completed }: {
  part: PartGroup; bodyId: string; currentLessonId: string; completed: Set<string>
}) {
  const [open, setOpen] = useState(() => part.phases.some((group) => group.rows.some(({ lesson }) => lesson.id === currentLessonId)))
  const lessons = part.phases.flatMap((group) => group.rows.map(({ lesson }) => lesson))
  return (
    <div className="part-nav">
      <button type="button" className="part-toggle" aria-expanded={open} aria-controls={bodyId} onClick={() => setOpen(!open)}>
        <span>{part.label}</span>
        <span className="ph-count">{countText(countLessons(lessons, completed))}</span>
      </button>
      <div id={bodyId} className="part-body" hidden={!open}>
        {part.phases.map((group) => <PhaseDetails key={group.key} group={group} currentLessonId={currentLessonId} completed={completed} />)}
      </div>
    </div>
  )
}

/**
 * Left column (04): phases collapsible, the current one open; a toggle above the lesson below 1100px.
 * A course with parts (H12) groups the phases in collapsible parts, only the current one open.
 */
export function Syllabus({ courseTitle, groups, parts = [], rest = groups, currentLessonId, completed, counts }: {
  courseTitle: string
  groups: PhaseGroup[]
  parts?: PartGroup[]
  rest?: PhaseGroup[]
  currentLessonId: string
  completed: Set<string>
  counts: Counts
}) {
  const [open, setOpen] = useState(false)
  return (
    <nav className="mix-side" id={SYLLABUS_ID} aria-label="Temario del curso">
      <div className={open ? 'collapsible is-open' : 'collapsible'}>
        <button type="button" className="collapse-btn" aria-expanded={open} aria-controls={`${SYLLABUS_ID}-body`} onClick={() => setOpen(!open)}>
          <span>Temario del curso · {groups.length} fases</span>
          <svg aria-hidden="true" className="icon-small" viewBox="0 0 24 24"><path fill="currentColor" d="M7 10l5 5 5-5z" /></svg>
        </button>
        <div className="collapsible-body" id={`${SYLLABUS_ID}-body`}>
          <div className="side-course">
            <h2>{courseTitle}</h2>
            <p>{progressText(counts)}</p>
          </div>
          <div className="phase-nav">
            {parts.map((part, index) => (
              <PartSection key={part.key} part={part} bodyId={`${SYLLABUS_ID}-part-${index + 1}`} currentLessonId={currentLessonId} completed={completed} />
            ))}
            {(parts.length === 0 ? groups : rest).map((group) => (
              <PhaseDetails key={group.key} group={group} currentLessonId={currentLessonId} completed={completed} />
            ))}
          </div>
        </div>
      </div>
    </nav>
  )
}
