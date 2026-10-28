import { errorCode, errorMessage, isAuthRequired } from '../api/errors'
import { NotFoundPage } from './NotFoundPage'

/** A failed load: nothing for a lost session (login follows), «No encontrado» for 404. */
export function LoadFailure({ error }: { error: unknown }) {
  if (isAuthRequired(error)) return null
  if (errorCode(error) === 'NOT_FOUND') return <NotFoundPage />
  return <p className="form-error" role="alert">{errorMessage(error)}</p>
}
