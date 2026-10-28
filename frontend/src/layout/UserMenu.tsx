import { useEffect, useId, useRef, useState } from 'react'
import { errorMessage } from '../api/errors'
import { useSession, useUser } from '../session/session'

export function UserMenu() {
  const user = useUser()
  const { signOut } = useSession()
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const menuId = useId()
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        button.current?.focus()
      }
    }
    function onClick(event: MouseEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('click', onClick)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('click', onClick)
    }
  }, [open])

  async function logout() {
    setBusy(true)
    setFailure(null)
    try {
      await signOut()
    } catch (caught) {
      setFailure(`No se pudo cerrar la sesión. ${errorMessage(caught)}`)
      setBusy(false)
    }
  }

  return (
    <div className="usermenu" ref={root}>
      <button
        ref={button}
        type="button"
        className="user-btn"
        aria-label={`Menú de usuario: ${user.username}`}
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen(!open)}
      >
        <span className="avatar" aria-hidden="true">{user.username.slice(0, 1).toUpperCase()}</span>
        <span className="user-name">{user.username}</span>
        <svg aria-hidden="true" className="icon icon-small" viewBox="0 0 24 24"><path fill="currentColor" d="M7 10l5 5 5-5z" /></svg>
      </button>
      <div className="menu" id={menuId} hidden={!open}>
        <p className="menu-who">Sesión iniciada como <strong>{user.username}</strong></p>
        <button type="button" className="menu-item" onClick={logout} disabled={busy}>Cerrar sesión</button>
        {failure && <p className="form-error" role="alert">{failure}</p>}
      </div>
    </div>
  )
}
