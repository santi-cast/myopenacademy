import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

const loading = 'Comprobando conexión con el backend…'
const healthy = 'Backend disponible.'
const unavailable = 'Backend no disponible.'

function mockHealthResponse(body: string, status = 200) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(body, {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function expectOnlyHealthGet(fetchMock: ReturnType<typeof vi.fn>) {
  expect(fetchMock).toHaveBeenCalledTimes(1)
  expect(fetchMock).toHaveBeenCalledWith('/actuator/health', {
    method: 'GET',
  })
}

describe('technical foundation', () => {
  it('shows the technical-only notice and loading state while health is pending', () => {
    const fetchMock = vi.fn(() => new Promise<Response>(() => {}))
    vi.stubGlobal('fetch', fetchMock)

    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'Code Academy' })).toBeVisible()
    expect(screen.getByText('Base técnica. Todavía no hay cursos disponibles.')).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent(loading)
    expect(screen.queryByText(healthy)).not.toBeInTheDocument()
    expect(screen.queryByText(unavailable)).not.toBeInTheDocument()
    expectOnlyHealthGet(fetchMock)
  })

  it('reports available only after a successful UP response', async () => {
    const fetchMock = mockHealthResponse('{"status":"UP"}')
    render(<App />)

    expect(screen.getByRole('status')).toHaveTextContent(loading)
    expect(await screen.findByText(healthy)).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent(healthy)
    expect(screen.queryByText(loading)).not.toBeInTheDocument()
    expect(screen.queryByText(unavailable)).not.toBeInTheDocument()
    expectOnlyHealthGet(fetchMock)
  })

  it.each([
    ['HTTP error even with UP', '{"status":"UP","detail":"private-diagnostic"}', 503],
    ['non-UP status', '{"status":"DOWN","detail":"private-diagnostic"}', 200],
    ['missing status', '{"detail":"private-diagnostic"}', 200],
    ['null payload', 'null', 200],
    ['invalid JSON', 'private-diagnostic', 200],
  ])('reports unavailable without exposing response content: %s', async (_name, body, status) => {
    const fetchMock = mockHealthResponse(body, status)
    render(<App />)

    expect(await screen.findByText(unavailable)).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent(unavailable)
    expect(screen.queryByText(loading)).not.toBeInTheDocument()
    expect(screen.queryByText(healthy)).not.toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('private-diagnostic')
    expectOnlyHealthGet(fetchMock)
  })

  it('reports unavailable on network failure without exposing error details', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('private-diagnostic'))
    vi.stubGlobal('fetch', fetchMock)
    render(<App />)

    expect(await screen.findByText(unavailable)).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent(unavailable)
    expect(screen.queryByText(loading)).not.toBeInTheDocument()
    expect(screen.queryByText(healthy)).not.toBeInTheDocument()
    expect(document.body).not.toHaveTextContent('private-diagnostic')
    expectOnlyHealthGet(fetchMock)
  })
})
