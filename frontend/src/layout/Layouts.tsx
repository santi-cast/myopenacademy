import { Link, NavLink, Navigate, Outlet } from 'react-router'
import { ThemeToggle } from '../theme/ThemeToggle'
import { useSession } from '../session/session'
import { UserMenu } from './UserMenu'

function Brand() {
  return (
    <Link className="brand" to="/">
      <span className="brand-mark" aria-hidden="true">{'{ }'}</span>
      <span>Code Academy</span>
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
  if (state.status === 'loading') return <Loading />
  if (state.status === 'anonymous') return <Navigate to="/login" replace />
  return (
    <div key={state.key} className="app-shell">
      <SkipLink />
      <header className="topbar">
        <Brand />
        <nav className="mainnav" aria-label="Principal">
          <NavLink to="/" end>Mis cursos</NavLink>
          <NavLink to="/courses" end>Catálogo</NavLink>
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
