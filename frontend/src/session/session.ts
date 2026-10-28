import { createContext, useContext } from 'react'
import type { User } from '../api/types'

export type SessionState =
  | { status: 'loading' }
  | { status: 'anonymous'; expired: boolean }
  | { status: 'authenticated'; user: User; key: number }

export type Session = {
  state: SessionState
  /** Stores the user returned by a confirmed login. */
  signedIn: (user: User) => void
  /** POST /api/auth/logout; on success every private datum is dropped. Throws on failure. */
  signOut: () => Promise<void>
}

export const SessionContext = createContext<Session | null>(null)

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (session === null) throw new Error('useSession outside SessionProvider')
  return session
}

export function useUser(): User {
  const { state } = useSession()
  if (state.status !== 'authenticated') throw new Error('useUser without a session')
  return state.user
}
