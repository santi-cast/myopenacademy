import { useState, type FormEvent } from 'react'
import { apiSend } from '../api/client'
import { errorMessage, isAuthRequired } from '../api/errors'
import type { User } from '../api/types'
import { useUser } from '../session/session'

/** The profile (H9, T4): change your own password with the current one; your other sessions close. */
export function ProfilePage() {
  const user = useUser()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setDone(false)
    if (next !== repeat) { setFailure('Las dos contraseñas nuevas no coinciden.'); return }
    if ([...next].length < 15 || [...next].length > 128) {
      setFailure('La contraseña debe tener entre 15 y 128 caracteres. Puedes usar espacios y frases.')
      return
    }
    setFailure(null)
    setBusy(true)
    try {
      await apiSend<User>('PUT', '/api/me/password', { currentPassword: current, newPassword: next })
      setCurrent(''); setNext(''); setRepeat('')
      setDone(true)
    } catch (caught) {
      if (!isAuthRequired(caught)) setFailure(errorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <h1>Mi perfil</h1>
      <p>Usuario: <strong>{user.username}</strong>{user.role === 'admin' && ' · administrador'}</p>
      <form className="auth-card" onSubmit={submit} noValidate aria-labelledby="password-title">
        <h2 id="password-title">Cambiar la contraseña</h2>
        <div className="field">
          <label htmlFor="profile-current">Contraseña actual</label>
          <input id="profile-current" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="profile-new">Contraseña nueva</label>
          <input id="profile-new" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="profile-repeat">Repite la contraseña nueva</label>
          <input id="profile-repeat" type="password" autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
        </div>
        <p className="field-hint">Entre 15 y 128 caracteres. Tus otras sesiones abiertas se cerrarán; esta sigue abierta.</p>
        <button type="submit" className="btn" disabled={busy}>{busy ? 'Guardando…' : 'Cambiar la contraseña'}</button>
        {failure && <p className="form-error" role="alert">{failure}</p>}
        {done && <p className="saved" role="status">Contraseña cambiada.</p>}
      </form>
    </div>
  )
}
