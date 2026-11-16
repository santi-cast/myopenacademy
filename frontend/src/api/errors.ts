import { ApiError } from './client'

// The UI decides by `code` only; the server `message` is never shown.
const messages: Record<string, string> = {
  AUTHENTICATION_FAILED: 'Usuario o contraseña incorrectos.',
  USERNAME_TAKEN: 'Ese nombre de usuario ya está en uso. Elige otro.',
  INVALID_USERNAME: 'El usuario debe tener entre 3 y 32 caracteres: letras sin acentos, números o guion bajo.',
  INVALID_PASSWORD: 'La contraseña debe tener entre 15 y 128 caracteres. Puedes usar espacios y frases.',
  // H9: accounts and administration.
  REGISTRATION_CLOSED: 'El registro está cerrado en esta academia. Pide una cuenta a quien la administra.',
  ACCOUNT_DISABLED: 'Esta cuenta está desactivada. Habla con quien administra la academia.',
  WRONG_PASSWORD: 'La contraseña actual no es correcta.',
  SAME_PASSWORD: 'La contraseña nueva tiene que ser distinta de la actual.',
  PASSWORD_CHANGE_REQUIRED: 'Antes de seguir, elige una contraseña nueva en «Mi perfil».',
  CANNOT_DEACTIVATE_SELF: 'No puedes desactivar tu propia cuenta.',
  LAST_ADMIN: 'No se puede desactivar al último administrador activo.',
  USER_NOT_FOUND: 'Ese usuario ya no existe.',
  INVALID_INSTANCE_NAME: 'El nombre debe tener como mucho 80 caracteres.',
  CSRF_REJECTED: 'La sesión cambió. Recarga la página e inténtalo de nuevo.',
  ACCESS_DENIED: 'No tienes permiso para esta acción.',
  NOT_FOUND: 'No encontrado.',
  LESSON_UNAVAILABLE: 'Esta lección todavía no está disponible.',
  NETWORK_ERROR: 'No se pudo conectar con el servidor. Comprueba que el backend esté en marcha.',
}

const GENERIC = 'Algo salió mal. Inténtalo de nuevo en unos minutos.'

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
