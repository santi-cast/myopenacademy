import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { apiSend } from '../api/client'
import { errorMessage } from '../api/errors'
import { ApiError } from '../api/client'

const USERNAME = /^[A-Za-z0-9_]{3,32}$/

/** The registration policy of docs/api.md, checked before sending; the server checks it again. */
function policyError(username: string, password: string): string | null {
  if (!USERNAME.test(username)) return errorMessage(new ApiError(400, 'INVALID_USERNAME', ''))
  const codePoints = [...password].length
  if (codePoints < 15 || codePoints > 128) return errorMessage(new ApiError(400, 'INVALID_PASSWORD', ''))
  return null
}

export function RegisterPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const invalid = policyError(username, password)
    setFailure(invalid)
    if (invalid) return
    setBusy(true)
    try {
      // Registration does not sign in: the login screen confirms the new account.
      await apiSend('POST', '/api/auth/register', { username, password })
      navigate('/login', { state: { registered: true } })
    } catch (caught) {
      setFailure(errorMessage(caught))
      setBusy(false)
    }
  }

  return (
    <section className="auth-card" aria-labelledby="register-title">
      <h1 id="register-title">Crear una cuenta</h1>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="register-username">Usuario</label>
          <input
            id="register-username"
            name="username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            aria-describedby="register-username-hint"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
          <p className="field-hint" id="register-username-hint">Entre 3 y 32 caracteres: letras sin acentos, números o guion bajo.</p>
        </div>
        <div className="field">
          <label htmlFor="register-password">Contraseña</label>
          <input
            id="register-password"
            name="password"
            type="password"
            autoComplete="new-password"
            aria-describedby="register-password-hint"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <p className="field-hint" id="register-password-hint">Entre 15 y 128 caracteres. Podés usar espacios y frases.</p>
        </div>
        {failure && <p className="form-error" role="alert">{failure}</p>}
        <button type="submit" className="btn" disabled={busy}>Crear cuenta</button>
      </form>
      <p className="auth-alt">¿Ya tenés cuenta? <Link to="/login">Iniciar sesión</Link></p>
    </section>
  )
}
