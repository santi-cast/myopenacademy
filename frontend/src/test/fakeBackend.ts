import { vi } from 'vitest'
import type { Counts, Course, LessonEntry, Phase, Progress, User } from '../api/types'

// A fetch double at the API-client boundary. It behaves like the real backend where the
// client depends on it: a cookie jar for XSRF-TOKEN (set on a safe request when absent,
// expired by login and logout), CSRF checks on unsafe requests and the session.

export type Call = {
  method: string
  path: string
  headers: Record<string, string>
  credentials: RequestCredentials | undefined
  body: string | undefined
  /** XSRF-TOKEN cookie value when the request left the client. */
  cookieAtSend: string | null
}

export type Reply = { status: number; body?: unknown; expireCsrf?: boolean }

export type Handler = (call: Call, backend: FakeBackend) => Reply | Promise<Reply>

export type FakeBackend = {
  calls: Call[]
  user: User | null
  accounts: Map<string, { user: User; password: string }>
  issuedTokens: string[]
  route: (key: string, handler: Handler | Reply) => void
}

export const javaCourse: Course = { id: 'java', title: 'Java' }

// An excerpt of an old syllabus with gaps in the phase positions: the numbers shown still follow the order.
export const javaPhases: Phase[] = [
  { id: 'java-phase-00', title: 'Lógica de programación', position: 1 },
  { id: 'java-phase-02', title: 'Fundamentos de Java', position: 3 },
  { id: 'java-phase-03', title: 'Programación orientada a objetos', position: 4 },
]

export const javaLessons: LessonEntry[] = [
  { id: 'java-00.01', courseId: 'java', title: 'Qué es programar', position: 1, phaseId: 'java-phase-00', available: false },
  { id: 'java-00.02', courseId: 'java', title: 'Algoritmos, pseudocódigo y diagramas de flujo', position: 2, phaseId: 'java-phase-00', available: false },
  { id: 'java-02.01', courseId: 'java', title: 'Tu primer programa', position: 3, phaseId: 'java-phase-02', available: true },
  { id: 'java-02.02', courseId: 'java', title: 'Variables y tipos de datos', position: 4, phaseId: 'java-phase-02', available: false },
  { id: 'java-03.01', courseId: 'java', title: 'Clases y objetos', position: 5, phaseId: 'java-phase-03', available: false },
]

export function counts(completedLessons: number, availableLessons: number, plannedLessons: number): Counts {
  return {
    completedLessons,
    availableLessons,
    plannedLessons,
    percentage: availableLessons === 0 ? null : (completedLessons * 100) / availableLessons,
  }
}

export function notAddedProgress(): Progress {
  return {
    courseId: 'java',
    added: false,
    lessonStates: [],
    exerciseStates: [],
    continuation: { lastOpenedLessonId: null },
    progress: counts(0, 1, 199),
  }
}

export const ana: User = { id: '7f1c2a4e-0000-4000-8000-000000000001', username: 'Ana' }
export const beto: User = { id: '7f1c2a4e-0000-4000-8000-000000000002', username: 'Beto' }

export function error(status: number, code: string, message = 'Error.'): Reply {
  return { status, body: { code, message } }
}

const authRequired = error(401, 'AUTHENTICATION_REQUIRED', 'Authentication is required.')

export function readXsrfCookie(): string | null {
  for (const part of document.cookie.split(';')) {
    const [name, ...rest] = part.trim().split('=')
    if (name === 'XSRF-TOKEN') return decodeURIComponent(rest.join('='))
  }
  return null
}

export function setXsrfCookie(value: string) {
  document.cookie = `XSRF-TOKEN=${encodeURIComponent(value)}; path=/`
}

export function expireXsrfCookie() {
  document.cookie = 'XSRF-TOKEN=; path=/; max-age=0'
}

function authenticated(handler: Handler): Handler {
  return (call, backend) => (backend.user ? handler(call, backend) : authRequired)
}

function defaultRoutes(): Record<string, Handler | Reply> {
  return {
    'GET /actuator/health': { status: 200, body: { status: 'UP' } },
    'GET /api/me': authenticated((_call, backend) => ({ status: 200, body: backend.user })),
    'POST /api/auth/login': (call, backend) => {
      const { username, password } = JSON.parse(call.body ?? '{}') as { username: string; password: string }
      const account = backend.accounts.get(username.toLowerCase())
      if (!account || account.password !== password) {
        return error(401, 'AUTHENTICATION_FAILED', 'Invalid username or password.')
      }
      backend.user = account.user
      return { status: 200, body: account.user, expireCsrf: true }
    },
    'POST /api/auth/logout': (_call, backend) => {
      backend.user = null
      return { status: 204, expireCsrf: true }
    },
    'POST /api/auth/register': (call) => {
      const { username } = JSON.parse(call.body ?? '{}') as { username: string }
      return { status: 201, body: { id: '7f1c2a4e-0000-4000-8000-000000000099', username } }
    },
    'GET /api/courses': authenticated(() => ({ status: 200, body: { courses: [javaCourse] } })),
    'GET /api/me/courses': authenticated(() => ({ status: 200, body: { courses: [] } })),
    'PUT /api/me/courses/java': authenticated(() => ({
      status: 200,
      body: { courseId: 'java', added: true, continuation: { lastOpenedLessonId: null }, progress: counts(0, 1, 199) },
    })),
    'GET /api/courses/java': authenticated(() => ({ status: 200, body: { course: javaCourse, phases: javaPhases, lessons: javaLessons, project: { kind: 'etapas', title: 'Mi Biblioteca', lessonId: null }, parts: [] } })),
    'GET /api/me/courses/java/progress': authenticated(() => ({ status: 200, body: notAddedProgress() })),
    'GET /api/lessons/java-02.01': authenticated(() => ({
      status: 200,
      body: {
        lesson: javaLessons[2],
        bodyHtml: '<h2 id="qué-aprenderás">Qué aprenderás</h2>',
        exercises: [],
        resources: [],
        previousLessonId: null,
        nextLessonId: null,
      },
    })),
  }
}

/** Installs the fake as global fetch. `loggedInAs` starts with a live session. */
export function installFakeBackend(options: { loggedInAs?: User } = {}): FakeBackend {
  const routes = defaultRoutes()
  const backend: FakeBackend = {
    calls: [],
    user: options.loggedInAs ?? null,
    accounts: new Map([
      ['ana', { user: ana, password: 'una frase bastante larga' }],
      ['beto', { user: beto, password: 'otra frase bastante larga' }],
    ]),
    issuedTokens: [],
    route: (key, handler) => {
      routes[key] = handler
    },
  }

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const method = (init.method ?? 'GET').toUpperCase()
    const call: Call = {
      method,
      path: String(input),
      headers: Object.fromEntries(new Headers(init.headers).entries()),
      credentials: init.credentials,
      body: typeof init.body === 'string' ? init.body : undefined,
      cookieAtSend: readXsrfCookie(),
    }
    backend.calls.push(call)
    const safe = method === 'GET' || method === 'HEAD'

    let reply: Reply
    if (!safe && (!call.cookieAtSend || call.headers['x-xsrf-token'] !== call.cookieAtSend)) {
      reply = error(403, 'CSRF_REJECTED', 'CSRF token is missing or invalid.')
    } else {
      const route = routes[`${method} ${call.path}`]
      reply = route === undefined
        ? error(404, 'NOT_FOUND', 'Not found.')
        : typeof route === 'function' ? await route(call, backend) : route
    }

    if (reply.expireCsrf) {
      expireXsrfCookie()
    } else if (safe && readXsrfCookie() === null) {
      const token = `token-${backend.issuedTokens.length + 1}`
      backend.issuedTokens.push(token)
      setXsrfCookie(token)
    }
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
      status: reply.status,
      headers: reply.body === undefined ? {} : { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
  return backend
}

/** Every request the client sent, as "METHOD path". */
export function apiCalls(backend: FakeBackend): string[] {
  return backend.calls.map((call) => `${call.method} ${call.path}`)
}
