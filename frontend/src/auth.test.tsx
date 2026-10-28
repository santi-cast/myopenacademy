import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ana, apiCalls, beto, counts, error, installFakeBackend, javaCourse, type FakeBackend } from './test/fakeBackend'
import { renderApp } from './test/renderApp'

const GENERIC = 'Algo salió mal. Intentá de nuevo en unos minutos.'
const CSRF = 'La sesión cambió. Recargá la página e intentá de nuevo.'

async function fillLogin(username: string, password: string) {
  fireEvent.change(await screen.findByLabelText('Usuario'), { target: { value: username } })
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
}

async function fillRegister(username: string, password: string) {
  fireEvent.change(await screen.findByLabelText('Usuario'), { target: { value: username } })
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Crear cuenta' }))
}

async function logout() {
  fireEvent.click(await screen.findByRole('button', { name: /Menú de usuario/ }))
  fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
  await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })
}

function sentBodies(backend: FakeBackend, key: string) {
  return backend.calls.filter((call) => `${call.method} ${call.path}` === key).map((call) => call.body)
}

describe('login', () => {
  it('sends exactly what was typed, without trimming, and lands on Mis cursos', async () => {
    const backend = installFakeBackend()
    backend.accounts.set('ana', { user: ana, password: '  una frase con espacios  ' })
    renderApp('/login')

    await fillLogin('Ana', '  una frase con espacios  ')

    expect(await screen.findByRole('heading', { level: 1, name: 'Mis cursos' })).toBeVisible()
    expect(sentBodies(backend, 'POST /api/auth/login')).toEqual(['{"username":"Ana","password":"  una frase con espacios  "}'])
    expect(screen.getByRole('button', { name: /Menú de usuario/ })).toHaveTextContent('Ana')
  })

  it('primes CSRF with one public GET /actuator/health and triggers no 401 handling when logged out', async () => {
    const backend = installFakeBackend()
    renderApp('/login')
    await screen.findByLabelText('Usuario')
    const before = backend.calls.length
    document.cookie = 'XSRF-TOKEN=; path=/; max-age=0'

    await fillLogin('Ana', 'una frase bastante larga')

    await screen.findByRole('heading', { level: 1, name: 'Mis cursos' })
    const calls = apiCalls(backend).slice(before)
    expect(calls.slice(0, 2)).toEqual(['GET /actuator/health', 'POST /api/auth/login'])
    const login = backend.calls[before + 1]
    expect(login.headers['x-xsrf-token']).toBe(login.cookieAtSend)
    expect(login.cookieAtSend).not.toBeNull()
    expect(calls.filter((call) => call.startsWith('GET /actuator/health'))).toHaveLength(1)
    expect(screen.queryByText(/Tu sesión terminó/)).not.toBeInTheDocument()
  })

  it.each([
    ['AUTHENTICATION_FAILED', error(401, 'AUTHENTICATION_FAILED'), 'Usuario o contraseña incorrectos.'],
    ['CSRF_REJECTED', error(403, 'CSRF_REJECTED'), CSRF],
    ['NOT_ACCEPTABLE', error(406, 'NOT_ACCEPTABLE'), GENERIC],
    ['INTERNAL_ERROR', error(500, 'INTERNAL_ERROR'), GENERIC],
  ])('maps %s to its message and stays on the login screen', async (_code, reply, message) => {
    const backend = installFakeBackend()
    backend.route('POST /api/auth/login', reply)
    renderApp('/login')

    await fillLogin('Ana', 'lo que sea')

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
    expect(screen.getByLabelText('Contraseña')).toHaveValue('lo que sea')
    expect(backend.user).toBeNull()
  })

  it('explains the local console recovery in one line', async () => {
    installFakeBackend()
    renderApp('/login')

    expect(await screen.findByText(/se restablece desde una consola local/)).toHaveTextContent(
      '¿Olvidaste la contraseña? No hay recuperación por la web: se restablece desde una consola local con la herramienta --reset-password del backend (ver «Recuperar una contraseña» en docs/desarrollo-local.md).',
    )
  })

  it('sends any input to login, even an empty or very short password', async () => {
    const backend = installFakeBackend()
    renderApp('/login')

    await fillLogin('a', '')

    expect(await screen.findByRole('alert')).toHaveTextContent('Usuario o contraseña incorrectos.')
    expect(sentBodies(backend, 'POST /api/auth/login')).toEqual(['{"username":"a","password":""}'])
  })
})

describe('registration', () => {
  it('creates the account, does not log in, and shows a confirmation on the login screen', async () => {
    const backend = installFakeBackend()
    renderApp('/register')

    await fillRegister('Nueva_1', ' quince puntos y más ')

    expect(await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent('Cuenta creada. Iniciá sesión con tu usuario y contraseña.')
    expect(sentBodies(backend, 'POST /api/auth/register')).toEqual(['{"username":"Nueva_1","password":" quince puntos y más "}'])
    expect(backend.user).toBeNull()
    expect(screen.queryByRole('button', { name: /Menú de usuario/ })).not.toBeInTheDocument()
  })

  it.each([
    ['USERNAME_TAKEN', error(409, 'USERNAME_TAKEN'), 'Ese nombre de usuario ya está en uso. Elegí otro.'],
    ['INVALID_USERNAME', error(400, 'INVALID_USERNAME'), 'El usuario debe tener entre 3 y 32 caracteres: letras sin acentos, números o guion bajo.'],
    ['INVALID_PASSWORD', error(400, 'INVALID_PASSWORD'), 'La contraseña debe tener entre 15 y 128 caracteres. Podés usar espacios y frases.'],
    ['CSRF_REJECTED', error(403, 'CSRF_REJECTED'), CSRF],
    ['NOT_ACCEPTABLE', error(406, 'NOT_ACCEPTABLE'), GENERIC],
    ['INTERNAL_ERROR', error(500, 'INTERNAL_ERROR'), GENERIC],
  ])('maps %s to its message and stays on registration', async (_code, reply, message) => {
    const backend = installFakeBackend()
    backend.route('POST /api/auth/register', reply)
    renderApp('/register')

    await fillRegister('Ana', 'una frase bastante larga')

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('heading', { level: 1, name: 'Crear una cuenta' })).toBeVisible()
  })

  it.each([
    ['too short', 'ab', 'una frase bastante larga', 'El usuario debe tener entre 3 y 32 caracteres'],
    ['too long', 'a'.repeat(33), 'una frase bastante larga', 'El usuario debe tener entre 3 y 32 caracteres'],
    ['accented', 'Añil', 'una frase bastante larga', 'El usuario debe tener entre 3 y 32 caracteres'],
    ['with a space', ' Ana', 'una frase bastante larga', 'El usuario debe tener entre 3 y 32 caracteres'],
    ['14 code points', 'Ana', 'catorce puntos', 'La contraseña debe tener entre 15 y 128 caracteres'],
    ['14 emoji (28 UTF-16 units)', 'Ana', '🙂'.repeat(14), 'La contraseña debe tener entre 15 y 128 caracteres'],
    ['129 code points', 'Ana', 'x'.repeat(129), 'La contraseña debe tener entre 15 y 128 caracteres'],
  ])('checks the contract before sending: %s', async (_name, username, password, message) => {
    const backend = installFakeBackend()
    renderApp('/register')

    await fillRegister(username, password)

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(apiCalls(backend)).not.toContain('POST /api/auth/register')
  })

  it.each([
    ['3-character username and 15 emoji', 'a_1', '🙂'.repeat(15)],
    ['32-character username and 128 code points', 'A'.repeat(32), 'x'.repeat(128)],
  ])('accepts the boundaries: %s', async (_name, username, password) => {
    const backend = installFakeBackend()
    renderApp('/register')

    await fillRegister(username, password)

    await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })
    expect(apiCalls(backend)).toContain('POST /api/auth/register')
  })
})

describe('session and CSRF across login and logout', () => {
  it('logs in and immediately adds a course with the renewed token (no 403)', async () => {
    const backend = installFakeBackend()
    renderApp('/login')
    await fillLogin('Ana', 'una frase bastante larga')
    await screen.findByRole('heading', { level: 1, name: 'Mis cursos' })

    fireEvent.click(screen.getByRole('link', { name: 'Catálogo' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Añadir Java a Mis cursos' }))

    expect(await screen.findByText('Java está en Mis cursos.')).toBeVisible()
    const login = backend.calls.find((call) => call.path === '/api/auth/login')!
    const put = backend.calls.find((call) => call.method === 'PUT')!
    expect(put.headers['x-xsrf-token']).toBe(put.cookieAtSend)
    expect(put.headers['x-xsrf-token']).not.toBe(login.headers['x-xsrf-token'])
    expect(put.headers['x-xsrf-token']).toBe(backend.issuedTokens.at(-1))
  })

  it('logs out, logs in as another user and writes with the newest token', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    renderApp('/courses')
    await screen.findByRole('button', { name: 'Añadir Java a Mis cursos' })

    await logout()
    await fillLogin('Beto', 'otra frase bastante larga')
    await screen.findByRole('heading', { level: 1, name: 'Mis cursos' })
    expect(screen.getByRole('button', { name: /Menú de usuario/ })).toHaveTextContent('Beto')
    fireEvent.click(screen.getByRole('link', { name: 'Catálogo' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Añadir Java a Mis cursos' }))

    expect(await screen.findByText('Java está en Mis cursos.')).toBeVisible()
    expect(backend.user).toEqual(beto)
    const unsafe = backend.calls.filter((call) => call.method !== 'GET')
    expect(unsafe.map((call) => `${call.method} ${call.path}`)).toEqual([
      'POST /api/auth/logout',
      'POST /api/auth/login',
      'PUT /api/me/courses/java',
    ])
    for (const call of unsafe) expect(call.headers['x-xsrf-token']).toBe(call.cookieAtSend)
    expect(new Set(unsafe.map((call) => call.headers['x-xsrf-token'])).size).toBe(3)
    expect(unsafe[2].headers['x-xsrf-token']).toBe(backend.issuedTokens.at(-1))
  })

  it('logout clears every private datum and back navigation shows nothing private', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/me/courses', {
      status: 200,
      body: {
        courses: [{
          course: javaCourse,
          enrollment: { courseId: 'java', added: true, continuation: { lastOpenedLessonId: null }, progress: counts(0, 1, 199) },
        }],
      },
    })
    const app = renderApp('/')
    expect(await screen.findByText(/0 de 1 lecciones disponibles completadas/)).toBeVisible()

    await logout()
    expect(backend.user).toBeNull()
    expect(document.body).not.toHaveTextContent('Ana')
    expect(document.body).not.toHaveTextContent('lecciones disponibles completadas')

    const callsAfterLogout = backend.calls.length
    await app.navigate(-1)
    expect(await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
    expect(document.body).not.toHaveTextContent('Ana')
    expect(document.body).not.toHaveTextContent('Java')
    expect(apiCalls(backend).slice(callsAfterLogout).filter((call) => call.startsWith('GET /api/me/'))).toEqual([])
  })

  it('a logout failure is reported and does not pretend the session ended', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('POST /api/auth/logout', error(500, 'INTERNAL_ERROR'))
    renderApp('/')
    fireEvent.click(await screen.findByRole('button', { name: /Menú de usuario/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo cerrar la sesión.')
    expect(screen.getByRole('heading', { level: 1, name: 'Mis cursos' })).toBeVisible()
  })

  it('401 AUTHENTICATION_REQUIRED on any screen clears private state and goes to login', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    renderApp('/courses')
    expect(await screen.findByRole('button', { name: 'Añadir Java a Mis cursos' })).toBeVisible()

    backend.user = null // the session went stale server-side; it now continues as anonymous
    fireEvent.click(screen.getByRole('button', { name: 'Añadir Java a Mis cursos' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent('Tu sesión terminó. Iniciá sesión de nuevo.')
    expect(document.body).not.toHaveTextContent('Ana')
    expect(document.body).not.toHaveTextContent('Java')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('without a session, private routes go to login and the probe does not show a session notice', async () => {
    const backend = installFakeBackend()
    renderApp('/courses/java')

    expect(await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeVisible()
    expect(screen.queryByText(/Tu sesión terminó/)).not.toBeInTheDocument()
    expect(apiCalls(backend)).toEqual(['GET /api/me'])
  })

  it('the register screen stays reachable without a session', async () => {
    installFakeBackend()
    renderApp('/register')

    expect(await screen.findByRole('heading', { level: 1, name: 'Crear una cuenta' })).toBeVisible()
    const nav = screen.queryByRole('navigation', { name: 'Principal' })
    expect(nav).not.toBeInTheDocument()
  })

  it('a logged-in user visiting /login goes to Mis cursos', async () => {
    installFakeBackend({ loggedInAs: ana })
    renderApp('/login')

    expect(await screen.findByRole('heading', { level: 1, name: 'Mis cursos' })).toBeVisible()
    const nav = screen.getByRole('navigation', { name: 'Principal' })
    expect(within(nav).getByRole('link', { name: 'Mis cursos' })).toHaveAttribute('href', '/')
    expect(within(nav).getByRole('link', { name: 'Catálogo' })).toHaveAttribute('href', '/courses')
  })

  it('ACCESS_DENIED gets its own generic message, distinct from CSRF', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('PUT /api/me/courses/java', error(403, 'ACCESS_DENIED'))
    renderApp('/courses')
    fireEvent.click(await screen.findByRole('button', { name: 'Añadir Java a Mis cursos' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No tenés permiso para esta acción.')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Añadir Java a Mis cursos' })).toBeEnabled())
  })

  it('CSRF_REJECTED on a write suggests reloading and is not retried', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('PUT /api/me/courses/java', error(403, 'CSRF_REJECTED'))
    renderApp('/courses')
    fireEvent.click(await screen.findByRole('button', { name: 'Añadir Java a Mis cursos' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(CSRF)
    expect(apiCalls(backend).filter((call) => call.startsWith('PUT'))).toHaveLength(1)
  })
})
