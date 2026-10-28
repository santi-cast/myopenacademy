// Same-origin JSON client for docs/api.md. CSRF: the backend (csrf.spa()) keeps the token in
// the XSRF-TOKEN cookie and expects it back in X-XSRF-TOKEN on unsafe requests. Login and
// logout expire that cookie and the new token only appears on the next safe request, so the
// cookie is read right before every unsafe request and, when absent, one public GET primes it.
// Unsafe requests are never retried.

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
/** Public route that materializes the token without a session or a 401. */
const CSRF_PRIMING_PATH = '/actuator/health'

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

let onAuthRequired: (() => void) | null = null

/** Called on every 401 AUTHENTICATION_REQUIRED: the session is gone, private state must go too. */
export function setAuthRequiredHandler(handler: (() => void) | null) {
  onAuthRequired = handler
}

function readCookie(name: string): string | null {
  for (const part of document.cookie.split(';')) {
    const separator = part.indexOf('=')
    if (separator < 0) continue
    if (part.slice(0, separator).trim() === name) {
      const value = part.slice(separator + 1).trim()
      return value === '' ? null : decodeURIComponent(value)
    }
  }
  return null
}

async function send(method: string, path: string, headers: Record<string, string>, body?: string): Promise<Response> {
  try {
    return await fetch(path, {
      method,
      headers: { Accept: 'application/json', ...headers },
      credentials: 'same-origin',
      body,
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'The server could not be reached.')
  }
}

async function csrfToken(): Promise<string | null> {
  const current = readCookie(CSRF_COOKIE)
  if (current !== null) return current
  try {
    await send('GET', CSRF_PRIMING_PATH, {})
  } catch {
    // The unsafe request below reports the failure (network error or CSRF_REJECTED).
  }
  return readCookie(CSRF_COOKIE)
}

async function toError(response: Response): Promise<ApiError> {
  try {
    const payload: unknown = await response.json()
    if (typeof payload === 'object' && payload !== null && 'code' in payload && typeof payload.code === 'string') {
      const message = 'message' in payload && typeof payload.message === 'string' ? payload.message : ''
      return new ApiError(response.status, payload.code, message)
    }
  } catch {
    // Not JSON: fall through to a stable code.
  }
  return new ApiError(response.status, 'UNKNOWN_ERROR', 'Unexpected response.')
}

async function handle<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const failure = await toError(response)
    if (failure.status === 401 && failure.code === 'AUTHENTICATION_REQUIRED') onAuthRequired?.()
    throw failure
  }
  if (response.status === 204) return undefined as T
  try {
    return (await response.json()) as T
  } catch {
    throw new ApiError(response.status, 'UNKNOWN_ERROR', 'Unexpected response.')
  }
}

export async function apiGet<T>(path: string): Promise<T> {
  return handle<T>(await send('GET', path, {}))
}

export async function apiSend<T = undefined>(method: 'POST' | 'PUT', path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  const token = await csrfToken()
  if (token !== null) headers[CSRF_HEADER] = token
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  return handle<T>(await send(method, path, headers, body === undefined ? undefined : JSON.stringify(body)))
}
