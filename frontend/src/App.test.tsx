import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ana, apiCalls, error, installFakeBackend } from './test/fakeBackend'
import { renderApp } from './test/renderApp'

// H3's health-screen tests were removed with the screen: the main UI no longer shows the
// backend status. The last test below pins that removal.

describe('app routes', () => {
  it('the lesson route shows the lesson title (placeholder for part b)', async () => {
    installFakeBackend({ loggedInAs: ana })
    renderApp('/lessons/java-02.01')

    expect(await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })).toBeVisible()
  })

  it('a pending lesson by URL shows «todavía no disponible»', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('GET /api/lessons/java-02.02', error(409, 'LESSON_UNAVAILABLE'))
    renderApp('/lessons/java-02.02')

    expect(await screen.findByRole('heading', { level: 1, name: 'Esta lección todavía no está disponible.' })).toBeVisible()
  })

  it('an unknown route shows «No encontrado»', async () => {
    installFakeBackend({ loggedInAs: ana })
    renderApp('/esto/no/existe')

    expect(await screen.findByRole('heading', { level: 1, name: 'No encontrado' })).toBeVisible()
  })

  it('the H3 technical health screen is gone from the main UI', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    renderApp('/')

    await screen.findByRole('heading', { level: 1, name: 'Mis cursos' })
    expect(document.body).not.toHaveTextContent('Backend disponible.')
    expect(document.body).not.toHaveTextContent('Base técnica.')
    expect(apiCalls(backend)).not.toContain('GET /actuator/health')
  })
})
