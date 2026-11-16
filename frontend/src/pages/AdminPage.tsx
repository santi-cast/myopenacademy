import { useState, type FormEvent } from 'react'
import { apiGet, apiSend } from '../api/client'
import { errorMessage, isAuthRequired } from '../api/errors'
import type { AdminSettings, AdminUser, InstalledCourse } from '../api/types'
import { loadInstance } from '../instance/instance'
import { useUser } from '../session/session'
import { LoadFailure } from './LoadFailure'
import { useLoad } from './useApi'

type Panel = { settings: AdminSettings; users: AdminUser[]; courses: InstalledCourse[] }

async function loadPanel(): Promise<Panel> {
  const [settings, users, courses] = await Promise.all([
    apiGet<AdminSettings>('/api/admin/settings'),
    apiGet<{ users: AdminUser[] }>('/api/admin/users'),
    apiGet<{ courses: InstalledCourse[] }>('/api/admin/courses'),
  ])
  return { settings, users: users.users, courses: courses.courses }
}

function SettingsForm({ initial }: { initial: AdminSettings }) {
  const [open, setOpen] = useState(initial.registrationOpen)
  const [name, setName] = useState(initial.instanceName ?? '')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true); setFailure(null); setSaved(false)
    try {
      const result = await apiSend<AdminSettings>('PUT', '/api/admin/settings', { registrationOpen: open, instanceName: name })
      setOpen(result.registrationOpen)
      setName(result.instanceName ?? '')
      setSaved(true)
      void loadInstance(true)
    } catch (caught) {
      if (!isAuthRequired(caught)) setFailure(errorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-labelledby="admin-settings">
      <h2 id="admin-settings">Ajustes</h2>
      <div className="field">
        <label><input type="checkbox" checked={open} onChange={(e) => setOpen(e.target.checked)} /> Registro abierto: cualquiera puede crear una cuenta</label>
      </div>
      <div className="field">
        <label htmlFor="admin-name">Nombre de la academia</label>
        <input id="admin-name" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
        <p className="field-hint">Vacío: MyOpenAcademy.</p>
      </div>
      <button type="submit" className="btn" disabled={busy}>{busy ? 'Guardando…' : 'Guardar los ajustes'}</button>
      {failure && <p className="form-error" role="alert">{failure}</p>}
      {saved && <p className="saved" role="status">Ajustes guardados.</p>}
    </form>
  )
}

function UserRow({ user, self, onChange }: { user: AdminUser; self: boolean; onChange: (user: AdminUser) => void }) {
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [resetting, setResetting] = useState(false)
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')

  async function toggle() {
    setBusy(true); setFailure(null); setNotice(null)
    try {
      onChange(await apiSend<AdminUser>('PUT', `/api/admin/users/${encodeURIComponent(user.id)}/active`, { active: !user.active }))
    } catch (caught) {
      if (!isAuthRequired(caught)) setFailure(errorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  async function reset(event: FormEvent) {
    event.preventDefault()
    setFailure(null); setNotice(null)
    if (password !== repeat) { setFailure('Las dos contraseñas no coinciden.'); return }
    setBusy(true)
    try {
      await apiSend<AdminUser>('PUT', `/api/admin/users/${encodeURIComponent(user.id)}/password`, { password })
      setResetting(false); setPassword(''); setRepeat('')
      setNotice(`Contraseña temporal guardada. Pásasela a ${user.username} por tu cuenta: al entrar tendrá que elegir una nueva, que solo sabrá él o ella. Sus sesiones abiertas se cierran.`)
    } catch (caught) {
      if (!isAuthRequired(caught)) setFailure(errorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <tr className={user.active ? undefined : 'is-pending'}>
      <td>{user.username}{self && ' (tú)'}</td>
      <td>{user.role === 'admin' ? 'Administrador' : 'Usuario'}</td>
      <td>{user.active ? 'Activo' : 'Desactivado'}{user.mustChangePassword && ' · con contraseña temporal'}</td>
      <td>
        {!self && <button type="button" className="btn btn-quiet" onClick={toggle} disabled={busy}>
          {user.active ? `Desactivar a ${user.username}` : `Reactivar a ${user.username}`}
        </button>}{' '}
        {/* One's own password is changed in «Mi perfil», never as a temporary one. */}
        {!self && <button type="button" className="btn btn-quiet" onClick={() => setResetting(!resetting)} disabled={busy}>
          Contraseña temporal para {user.username}
        </button>}
        {resetting && !self && (
          <form onSubmit={reset} noValidate aria-label={`Contraseña nueva para ${user.username}`}>
            <div className="field">
              <label htmlFor={`reset-${user.id}`}>Contraseña temporal</label>
              <input id={`reset-${user.id}`} type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor={`repeat-${user.id}`}>Repite la contraseña</label>
              <input id={`repeat-${user.id}`} type="password" autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
            </div>
            <button type="submit" className="btn" disabled={busy}>Guardar la contraseña</button>
          </form>
        )}
        {failure && <p className="form-error" role="alert">{failure}</p>}
        {notice && <p className="saved" role="status">{notice}</p>}
      </td>
    </tr>
  )
}

/** The administration (H9): settings, users and installed courses (only to see them: they install from the console). */
export function AdminPage() {
  const me = useUser()
  const [loaded, setLoaded] = useLoad('admin', loadPanel)
  if (me.role !== 'admin') return <div className="page"><h1>Administración</h1><p>Solo un administrador puede ver esta página.</p></div>
  if (loaded.status === 'loading') return <div className="page"><p className="page-status" role="status">Cargando…</p></div>
  if (loaded.status === 'failed') return <div className="page"><LoadFailure error={loaded.error} /></div>
  const { settings, users, courses } = loaded.data

  return (
    <div className="page admin">
      <h1>Administración</h1>
      <SettingsForm initial={settings} />
      <section aria-labelledby="admin-users">
        <h2 id="admin-users">Usuarios</h2>
        <table className="phase-table admin-table">
          <caption>{users.length} {users.length === 1 ? 'cuenta' : 'cuentas'}</caption>
          <thead><tr><th scope="col">Usuario</th><th scope="col">Rol</th><th scope="col">Estado</th><th scope="col">Acciones</th></tr></thead>
          <tbody>
            {users.map((user) => (
              <UserRow key={user.id} user={user} self={user.id === me.id}
                onChange={(changed) => setLoaded({ ...loaded.data, users: users.map((entry) => entry.id === changed.id ? changed : entry) })} />
            ))}
          </tbody>
        </table>
      </section>
      <section aria-labelledby="admin-courses">
        <h2 id="admin-courses">Cursos instalados</h2>
        <p className="field-hint">Se instalan y actualizan por consola (content-import y content-update), no desde aquí.</p>
        <table className="phase-table admin-table">
          <caption>{courses.length} {courses.length === 1 ? 'curso' : 'cursos'}</caption>
          <thead><tr><th scope="col">Curso</th><th scope="col">Versión</th><th scope="col">Commit</th></tr></thead>
          <tbody>
            {courses.map((course) => (
              <tr key={course.id}>
                <td>{course.title} <span className="field-hint">({course.id})</span></td>
                <td>{course.version ?? '—'}{course.tag ? ` · ${course.tag}` : ' · sin etiqueta'}</td>
                <td><code>{course.commit.slice(0, 7)}</code></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  )
}
