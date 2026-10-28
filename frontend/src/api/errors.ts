import { ApiError } from './client'

// The UI decides by `code` only; the server `message` is never shown.
const messages: Record<string, string> = {
  AUTHENTICATION_FAILED: 'Usuario o contraseña incorrectos.',
  USERNAME_TAKEN: 'Ese nombre de usuario ya está en uso. Elegí otro.',
  INVALID_USERNAME: 'El usuario debe tener entre 3 y 32 caracteres: letras sin acentos, números o guion bajo.',
  INVALID_PASSWORD: 'La contraseña debe tener entre 15 y 128 caracteres. Podés usar espacios y frases.',
  CSRF_REJECTED: 'La sesión cambió. Recargá la página e intentá de nuevo.',
  ACCESS_DENIED: 'No tenés permiso para esta acción.',
  NOT_FOUND: 'No encontrado.',
  LESSON_UNAVAILABLE: 'Esta lección todavía no está disponible.',
  NETWORK_ERROR: 'No se pudo conectar con el servidor. Comprobá que el backend esté en marcha.',
}

const GENERIC = 'Algo salió mal. Intentá de nuevo en unos minutos.'

export function errorCode(caught: unknown): string {
  return caught instanceof ApiError ? caught.code : 'UNKNOWN_ERROR'
}

/** Message for an error code; NOT_ACCEPTABLE, INTERNAL_ERROR and anything unknown share the generic one. */
export function errorMessage(caught: unknown): string {
  return messages[errorCode(caught)] ?? GENERIC
}

/** The 401 handler already took the user to login; screens show nothing for it. */
export function isAuthRequired(caught: unknown): boolean {
  return errorCode(caught) === 'AUTHENTICATION_REQUIRED'
}
