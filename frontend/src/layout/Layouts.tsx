import { useEffect } from 'react'
import { Link, NavLink, Navigate, Outlet, useLocation } from 'react-router'
import { instanceName, useInstance } from '../instance/instance'
import { ThemeToggle } from '../theme/ThemeToggle'
import { useSession } from '../session/session'
import { UserMenu } from './UserMenu'

// The academy's name: the one its administrator set (H9), or the default. The tab title follows it.
function Brand() {
  const name = instanceName(useInstance())
  useEffect(() => { document.title = name }, [name])
  return (
    <Link className="brand" to="/">
      <span className="brand-mark" aria-hidden="true">{'{ }'}</span>
      <span>{name}</span>
    </Link>
  )
}

function SkipLink() {
  return <a className="skip" href="#main">Saltar al contenido</a>
}

function Loading() {
  return <p className="page-status" role="status">Cargando…</p>
}

/** Login and registration: only for anonymous visitors. */
export function PublicLayout() {
  const { state } = useSession()
  if (state.status === 'loading') return <Loading />
  if (state.status === 'authenticated') return <Navigate to="/" replace />
  return (
    <>
      <SkipLink />
      <header className="topbar">
        <Brand />
        <div className="tools">
          <ThemeToggle />
        </div>
      </header>
      <main id="main" className="auth-page">
        <Outlet />
      </main>
    </>
  )
}

/** Every other screen requires a session; the layout remounts for each sign-in. */
export function PrivateLayout() {
  const { state } = useSession()
  const location = useLocation()
  if (state.status === 'loading') return <Loading />
  if (state.status === 'anonymous') return <Navigate to="/login" replace />
  // A temporary password (H9, D3): nothing else until the user chooses a new one; the server refuses the rest too.
  if (state.user.mustChangePassword && location.pathname !== '/profile') return <Navigate to="/profile" replace />
  return (
    <div key={state.key} className="app-shell">
      <SkipLink />
      <header className="topbar">
        <Brand />
        <nav className="mainnav" aria-label="Principal">
          <NavLink to="/" end>Mis cursos</NavLink>
          <NavLink to="/courses" end>Catálogo</NavLink>
          {state.user.role === 'admin' && <NavLink to="/admin" end>Administración</NavLink>}
        </nav>
        <div className="tools">
          <ThemeToggle />
          <UserMenu />
        </div>
      </header>
      <main id="main">
        <Outlet />
      </main>
    </div>
  )
}
