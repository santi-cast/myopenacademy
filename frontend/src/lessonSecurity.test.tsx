import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import hljs from 'highlight.js/lib/core'
import { StrictMode } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { apiSend, setAuthRequiredHandler } from './api/client'
import { decorateCodeBlocks } from './lesson/enhance'
import { highlightCode } from './lesson/highlight'
import { ana, apiCalls, error, installFakeBackend } from './test/fakeBackend'
import { addedProgress, lessonView, routeLesson } from './test/lessonFixtures'
import { renderApp } from './test/renderApp'

// Frontend security probes of the independent T9 verify (L63), kept as regression tests: link
// interception, highlight.js, session and storage. Mermaid is a double at the module boundary.
const mermaid = vi.hoisted(() => ({ initialize: vi.fn(), render: vi.fn() }))
vi.mock('mermaid', () => ({ default: mermaid }))

beforeEach(() => {
  mermaid.render.mockReset()
  mermaid.render.mockImplementation(async (id: string) => ({ svg: `<svg xmlns="http://www.w3.org/2000/svg" id="${id}"><text>ok</text></svg>` }))
  Object.defineProperty(Element.prototype, 'scrollIntoView', { configurable: true, value: () => {} })
})

afterEach(() => {
  delete (Element.prototype as Partial<Element>).scrollIntoView
  setAuthRequiredHandler(null)
})

/** Clicks like a user; jsdom does not navigate, so the browser's own default is cancelled afterwards. */
function click(element: Element) {
  const listener = (event: Event) => event.preventDefault()
  window.addEventListener('click', listener)
  fireEvent.click(element)
  window.removeEventListener('click', listener)
}

const HOSTILE_HREFS = [
  ['protocol-relative', '//evil.example/lessons/java-02.02'],
  ['double slash', '/lessons//evil.example'],
  ['query', '/lessons/java-02.02?redirect=https://evil.example#intro'],
  ['encoded dots', '/lessons/..%2F..%2Fapi%2Fme'],
  ['dot segments', '/courses/java/../../lessons/java-03.01'],
  ['prefix only', '/coursesevil'],
  ['absolute same host', `${window.location.origin}/lessons/java-02.02`],
  ['hash with markup', '#%3Cimg%20src=x%20onerror=alert(1)%3E'],
  ['uppercase', '/LESSONS/java-02.02'],
  ['backslash', '/lessons/\\evil.example'],
] as const

describe('link interception with hostile hrefs', () => {
  it.each(HOSTILE_HREFS)('%s: no request outside /api or /actuator, none with a query or another origin', async (_name, href) => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend, lessonView({ bodyHtml: `<p><a id="x" href="${href.replaceAll('"', '&quot;')}">x</a></p>` }))
    renderApp('/lessons/java-02.01')
    await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })
    const before = backend.calls.length
    click(document.getElementById('x')!)
    await act(async () => {
      await new Promise((done) => setTimeout(done, 50))
    })
    const after = apiCalls(backend).slice(before)
    expect(after.every((call) => / \/(api|actuator)\//.test(call))).toBe(true)
    expect(after.some((call) => call.includes('?') || call.includes('https:'))).toBe(false)
  })
})

describe('highlight.js', () => {
  it('turns no text into markup and never auto-detects', () => {
    const auto = vi.spyOn(hljs, 'highlightAuto')
    const root = document.createElement('div')
    root.innerHTML =
      '<pre><code class="language-java">&lt;img src=x onerror=alert(1)&gt; "&lt;/span&gt;&lt;script&gt;x&lt;/script&gt;"</code></pre>' +
      '<pre><code class="language-html">&lt;img src=x onerror=alert(1)&gt;&lt;a href="javascript:x"&gt;</code></pre>' +
      '<pre><code class="language-java"><b>child</b> &lt;i&gt;</code></pre>' +
      '<pre><code>class X {}</code></pre>' +
      '<pre><code class="language-Java">class X {}</code></pre>' +
      '<pre><code class="lang-java">class X {}</code></pre>' +
      '<pre class="language-java"><code class="language-cobol">class X {}</code></pre>' +
      '<pre><code class="language-text">class X {}</code></pre>'
    const texts = [...root.querySelectorAll('code')].map((code) => code.textContent)
    const blocks = decorateCodeBlocks(root)
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    for (const code of blocks.highlight) highlightCode(code)
    expect([...root.querySelectorAll('code')].map((code) => code.textContent)).toEqual(texts.map((text, index) => (index === 2 ? 'child <i>' : text)))
    expect(root.querySelectorAll('code img, code script, code a, code b')).toHaveLength(0)
    expect([...root.querySelectorAll('code *')].every((node) => node.tagName === 'SPAN')).toBe(true)
    expect(auto).not.toHaveBeenCalled()
    expect(blocks.highlight.map((code) => code.className.split(' ')[0])).toEqual(['language-java', 'language-html', 'language-java'])
  })
})

describe('session, storage and continuation', () => {
  it('a 401 from the CSRF priming request does not trigger the auth-required handling', async () => {
    const backend = installFakeBackend()
    backend.route('GET /actuator/health', error(401, 'AUTHENTICATION_REQUIRED'))
    const handler = vi.fn()
    setAuthRequiredHandler(handler)
    await expect(apiSend('POST', '/api/auth/login', { username: 'a', password: 'b' })).rejects.toBeTruthy()
    expect(handler).not.toHaveBeenCalled()
    expect(apiCalls(backend)).toEqual(['GET /actuator/health', 'POST /api/auth/login'])
  })

  it('StrictMode: one continuation PUT per lesson load, none on in-page links; storage keys only ca.theme/ca.temario', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend, lessonView({
      bodyHtml: '<h2 id="s">S</h2><p><a id="frag" href="#s">f</a> <a id="same" href="/lessons/java-02.01#s">same</a> <a id="next" href="/lessons/java-02.02">n</a></p>',
    }), addedProgress())
    render(<StrictMode><MemoryRouter initialEntries={['/lessons/java-02.01']}><App /></MemoryRouter></StrictMode>)
    await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })
    await waitFor(() => expect(apiCalls(backend)).toContain('PUT /api/me/courses/java/continuation'))
    click(document.getElementById('frag')!)
    click(document.getElementById('same')!)
    await act(async () => {
      await new Promise((done) => setTimeout(done, 50))
    })
    expect(apiCalls(backend).filter((call) => call.startsWith('PUT'))).toEqual(['PUT /api/me/courses/java/continuation'])
    fireEvent.click(screen.getByRole('button', { name: 'Ocultar temario' }))
    expect(Object.keys(window.sessionStorage)).toEqual([])
    expect(Object.keys(window.localStorage).every((key) => key === 'ca.theme' || key === 'ca.temario')).toBe(true)
  })

  it('after logout, Back shows the login and leaves no lesson or private data, with no private requests', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend, lessonView(), addedProgress([{ exerciseId: 'java-02.01-e01', state: 'solved', updatedAt: '2026-01-01T00:00:00Z' }]))
    const app = renderApp('/lessons/java-02.01')
    await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })
    fireEvent.click(await screen.findByRole('button', { name: /Menú de usuario/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
    await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })
    const before = backend.calls.length
    await app.navigate(-1)
    await screen.findByRole('heading', { level: 1, name: 'Iniciar sesión' })
    expect(['Tu primer programa', 'Ana', 'Ejercicios', 'Resuelto', 'Hello.java'].filter((text) => document.body.textContent?.includes(text))).toEqual([])
    expect(apiCalls(backend).slice(before).filter((call) => !call.startsWith('GET /actuator'))).toEqual([])
  })

  it('a logout answered 403 CSRF_REJECTED keeps the session and shows the CSRF message', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('POST /api/auth/logout', error(403, 'CSRF_REJECTED'))
    renderApp('/')
    fireEvent.click(await screen.findByRole('button', { name: /Menú de usuario/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('La sesión cambió. Recarga la página e inténtalo de nuevo.')
    expect(apiCalls(backend).filter((call) => call.startsWith('POST'))).toEqual(['POST /api/auth/logout'])
    expect(screen.queryByRole('heading', { level: 1, name: 'Iniciar sesión' })).toBeNull()
  })
})
