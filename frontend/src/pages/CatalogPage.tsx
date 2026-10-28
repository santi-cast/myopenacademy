import { useState } from 'react'
import { Link } from 'react-router'
import { apiGet, apiSend } from '../api/client'
import { errorMessage, isAuthRequired } from '../api/errors'
import type { Course, Courses, Enrollment, MyCourses } from '../api/types'
import { LoadFailure } from './LoadFailure'
import { useLoad } from './useApi'

type Catalog = { courses: Course[]; added: Set<string> }

async function loadCatalog(): Promise<Catalog> {
  const [catalog, mine] = await Promise.all([apiGet<Courses>('/api/courses'), apiGet<MyCourses>('/api/me/courses')])
  return {
    courses: catalog.courses,
    added: new Set(mine.courses.filter(({ enrollment }) => enrollment.added).map(({ course }) => course.id)),
  }
}

function CatalogEntry({ course, added, onAdded }: { course: Course; added: boolean; onAdded: () => void }) {
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const titleId = `catalog-${course.id}`

  async function add() {
    setBusy(true)
    setFailure(null)
    try {
      // Shown as added only once the server confirms; never retried automatically.
      const enrollment = await apiSend<Enrollment>('PUT', `/api/me/courses/${encodeURIComponent(course.id)}`)
      if (enrollment.added) onAdded()
    } catch (caught) {
      if (!isAuthRequired(caught)) setFailure(`No se pudo añadir ${course.title}. ${errorMessage(caught)}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="course-card" aria-labelledby={titleId}>
      <h2 id={titleId}>
        <Link to={`/courses/${encodeURIComponent(course.id)}`}>{course.title}</Link>
      </h2>
      {added
        ? (
            <p className="saved" role="status">
              <span>{course.title} está en Mis cursos.</span> <Link to="/">Ir a Mis cursos</Link>
            </p>
          )
        : (
            <button type="button" className="btn" onClick={add} disabled={busy}>
              {busy ? `Añadiendo ${course.title}…` : `Añadir ${course.title} a Mis cursos`}
            </button>
          )}
      {failure && <p className="form-error" role="alert">{failure}</p>}
    </article>
  )
}

export function CatalogPage() {
  const [loaded, setLoaded] = useLoad('catalog', loadCatalog)

  return (
    <div className="page">
      <h1>Catálogo</h1>
      {loaded.status === 'loading' && <p className="page-status" role="status">Cargando…</p>}
      {loaded.status === 'failed' && <LoadFailure error={loaded.error} />}
      {loaded.status === 'ready' && (
        <ul className="course-list">
          {loaded.data.courses.map((course) => (
            <li key={course.id}>
              <CatalogEntry
                course={course}
                added={loaded.data.added.has(course.id)}
                onAdded={() => setLoaded({ ...loaded.data, added: new Set(loaded.data.added).add(course.id) })}
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
