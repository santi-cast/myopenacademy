import { useState } from 'react'
import { Link } from 'react-router'
import type { Counts, LessonEntry } from '../api/types'
import { progressText } from '../progress'
import type { PhaseGroup } from '../syllabus'

export const SYLLABUS_ID = 'lesson-syllabus'

function LessonBadge({ lesson, completed }: { lesson: LessonEntry; completed: boolean }) {
  if (!lesson.available) return <span className="p">Pendiente</span>
  return completed
    ? <span className="badge badge-done">Completada</span>
    : <span className="badge badge-todo">No completada</span>
}

/** Left column (04): phases collapsible, the current one open; a toggle above the lesson below 1100px. */
export function Syllabus({ courseTitle, groups, currentLessonId, completed, counts }: {
  courseTitle: string
  groups: PhaseGroup[]
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
            {groups.map((group) => (
              <details key={group.key} open={group.rows.some(({ lesson }) => lesson.id === currentLessonId)}>
                <summary>
                  {group.label}
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
            ))}
          </div>
        </div>
      </div>
    </nav>
  )
}
