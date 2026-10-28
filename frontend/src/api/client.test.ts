import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiGet, apiSend, setAuthRequiredHandler } from './client'
import { apiCalls, ana, error, installFakeBackend, setXsrfCookie } from '../test/fakeBackend'

afterEach(() => setAuthRequiredHandler(null))

describe('API client', () => {
  it('sends Accept: application/json with same-origin credentials on every request', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })

    await apiGet('/api/me')
    await apiSend('PUT', '/api/me/courses/java')

    for (const call of backend.calls) {
      expect(call.headers.accept).toBe('application/json')
      expect(call.credentials).toBe('same-origin')
    }
  })

  it('primes with one GET /actuator/health when no XSRF-TOKEN cookie exists, then sends the token', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })

    await apiSend('PUT', '/api/me/courses/java')

    expect(apiCalls(backend)).toEqual(['GET /actuator/health', 'PUT /api/me/courses/java'])
    expect(backend.calls[1].headers['x-xsrf-token']).toBe('token-1')
  })

  it('does not prime when the cookie is present, and sends no token on safe requests', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    setXsrfCookie('token-present')

    await apiGet('/api/me')
    await apiSend('PUT', '/api/me/courses/java')

    expect(apiCalls(backend)).toEqual(['GET /api/me', 'PUT /api/me/courses/java'])
    expect(backend.calls[0].headers['x-xsrf-token']).toBeUndefined()
    expect(backend.calls[1].headers['x-xsrf-token']).toBe('token-present')
  })

  it('reads the cookie right before each unsafe request and never reuses an earlier token', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    setXsrfCookie('first')
    await apiSend('PUT', '/api/me/courses/java')
    setXsrfCookie('second')
    await apiSend('PUT', '/api/me/courses/java')

    const puts = backend.calls.filter((call) => call.method === 'PUT')
    expect(puts.map((call) => call.headers['x-xsrf-token'])).toEqual(['first', 'second'])
    for (const call of puts) expect(call.headers['x-xsrf-token']).toBe(call.cookieAtSend)
  })

  it('primes again after the server expired the cookie (login renews the token on the next GET)', async () => {
    const backend = installFakeBackend()
    await apiSend('POST', '/api/auth/login', { username: 'Ana', password: 'una frase bastante larga' })
    await apiSend('PUT', '/api/me/courses/java')

    expect(apiCalls(backend)).toEqual([
      'GET /actuator/health',
      'POST /api/auth/login',
      'GET /actuator/health',
      'PUT /api/me/courses/java',
    ])
    expect(backend.calls[3].headers['x-xsrf-token']).toBe('token-2')
  })

  it('never retries an unsafe request that failed', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    setXsrfCookie('stale')
    backend.route('PUT /api/me/courses/java', error(403, 'CSRF_REJECTED'))

    await expect(apiSend('PUT', '/api/me/courses/java')).rejects.toMatchObject({ status: 403, code: 'CSRF_REJECTED' })
    expect(apiCalls(backend)).toEqual(['PUT /api/me/courses/java'])
  })

  it('parses errors as {code, message} and maps unreadable bodies and network failures to stable codes', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/courses/x', error(404, 'NOT_FOUND', 'Not found.'))
    const thrown = await apiGet('/api/courses/x').catch((caught: unknown) => caught)
    expect(thrown).toBeInstanceOf(ApiError)
    expect(thrown).toMatchObject({ status: 404, code: 'NOT_FOUND', message: 'Not found.' })

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Bad gateway</html>', { status: 502 })))
    await expect(apiGet('/api/me')).rejects.toMatchObject({ status: 502, code: 'UNKNOWN_ERROR' })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(apiGet('/api/me')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('calls the auth-required handler on 401 AUTHENTICATION_REQUIRED only', async () => {
    const backend = installFakeBackend()
    const handler = vi.fn()
    setAuthRequiredHandler(handler)

    await expect(apiGet('/api/me/courses')).rejects.toMatchObject({ code: 'AUTHENTICATION_REQUIRED' })
    expect(handler).toHaveBeenCalledTimes(1)

    await expect(apiSend('POST', '/api/auth/login', { username: 'Ana', password: 'x' }))
      .rejects.toMatchObject({ code: 'AUTHENTICATION_FAILED' })
    expect(handler).toHaveBeenCalledTimes(1)
    expect(apiCalls(backend)).toContain('POST /api/auth/login')
  })

  it('sends a JSON body with Content-Type only when there is a body', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    setXsrfCookie('t')
    await apiSend('POST', '/api/auth/register', { username: 'Nuevo', password: ' quince puntos  ' })
    await apiSend('PUT', '/api/me/courses/java')

    expect(backend.calls[0].headers['content-type']).toBe('application/json')
    expect(backend.calls[0].body).toBe('{"username":"Nuevo","password":" quince puntos  "}')
    expect(backend.calls[1].headers['content-type']).toBeUndefined()
    expect(backend.calls[1].body).toBeUndefined()
  })
})
