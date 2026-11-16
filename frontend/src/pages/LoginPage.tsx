import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { apiSend } from '../api/client'
import { errorMessage } from '../api/errors'
import type { User } from '../api/types'
import { useInstance } from '../instance/instance'
import { useSession } from '../session/session'

export function LoginPage() {
  const instance = useInstance()
  const { state, signedIn } = useSession()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  const registered = (location.state as { registered?: boolean } | null)?.registered === true
  const expired = state.status === 'anonymous' && state.expired
  const notice = failure === null && registered
    ? 'Cuenta creada. Inicia sesión con tu usuario y contraseña.'
    : failure === null && expired ? 'Tu sesión terminó. Inicia sesión de nuevo.' : null

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setFailure(null)
    try {
      // Sent exactly as typed: login applies no trimming or registration policy.
      signedIn(await apiSend<User>('POST', '/api/auth/login', { username, password }))
    } catch (caught) {
      setFailure(errorMessage(caught))
      setBusy(false)
    }
  }

  return (
    <section className="auth-card" aria-labelledby="login-title">
      <h1 id="login-title">Iniciar sesión</h1>
      {notice && <p className="notice" role="status">{notice}</p>}
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="login-username">Usuario</label>
          <input
            id="login-username"
            name="username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="login-password">Contraseña</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>
        {failure && <p className="form-error" role="alert">{failure}</p>}
        <button type="submit" className="btn" disabled={busy}>Iniciar sesión</button>
      </form>
      {/* Only once the academy says whether registration is open (H9): no link that then disappears. */}
      {instance && (instance.registrationOpen
        ? <p className="auth-alt">¿No tienes cuenta? <Link to="/register">Crear una cuenta</Link></p>
        : <p className="auth-alt">El registro está cerrado. Pide una cuenta a quien administra la academia.</p>)}
      <p className="auth-help">
        ¿Olvidaste la contraseña? No hay recuperación por la web: se restablece desde una consola local con la herramienta --reset-password del backend (ver «Recuperar una contraseña» en docs/desarrollo-local.md).
      </p>
    </section>
  )
}
