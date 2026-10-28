import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { apiGet, apiSend, setAuthRequiredHandler } from '../api/client'
import type { User } from '../api/types'
import { SessionContext, type SessionState } from './session'

// The only private datum cached across screens is the user; screens keep their data in their
// own state, which unmounts with the private layout (keyed per sign-in) when the session ends.
export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: 'loading' })
  const signIns = useRef(0)
  const authenticated = useRef(false)
  const navigate = useNavigate()

  const signedIn = useCallback((user: User) => {
    authenticated.current = true
    signIns.current += 1
    setState({ status: 'authenticated', user, key: signIns.current })
  }, [])

  useEffect(() => {
    // A 401 AUTHENTICATION_REQUIRED while signed in means the server session is gone (it now
    // continues as anonymous): drop private state; the route guard then shows the login.
    setAuthRequiredHandler(() => {
      if (!authenticated.current) return
      authenticated.current = false
      setState({ status: 'anonymous', expired: true })
    })
    return () => setAuthRequiredHandler(null)
  }, [])

  useEffect(() => {
    let active = true
    apiGet<User>('/api/me').then(
      (user) => {
        if (active) signedIn(user)
      },
      () => {
        if (active) setState((current) => (current.status === 'loading' ? { status: 'anonymous', expired: false } : current))
      },
    )
    return () => {
      active = false
    }
  }, [signedIn])

  const signOut = useCallback(async () => {
    await apiSend('POST', '/api/auth/logout')
    authenticated.current = false
    setState({ status: 'anonymous', expired: false })
    navigate('/login')
  }, [navigate])

  const session = useMemo(() => ({ state, signedIn, signOut }), [state, signedIn, signOut])
  return <SessionContext value={session}>{children}</SessionContext>
}
