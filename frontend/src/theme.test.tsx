import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ana, installFakeBackend } from './test/fakeBackend'
import { renderApp } from './test/renderApp'

function prefersDark(matches: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query === '(prefers-color-scheme: dark)' ? matches : false,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  }))
}

describe('theme', () => {
  it('starts dark from the system preference and the label names the action', async () => {
    prefersDark(true)
    installFakeBackend()
    renderApp('/login')

    const toggle = await screen.findByRole('button', { name: 'Modo claro' })
    expect(toggle).toHaveAttribute('title', 'Cambiar a modo claro')
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
  })

  it('starts light from the system preference, toggles and remembers the choice', async () => {
    prefersDark(false)
    installFakeBackend({ loggedInAs: ana })
    renderApp('/')

    fireEvent.click(await screen.findByRole('button', { name: 'Modo oscuro' }))

    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(screen.getByRole('button', { name: 'Modo claro' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Modo oscuro' })).not.toBeInTheDocument()
    expect(window.localStorage.getItem('ca.theme')).toBe('dark')

    fireEvent.click(screen.getByRole('button', { name: 'Modo claro' }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'light')
    expect(window.localStorage.getItem('ca.theme')).toBe('light')
  })

  it('a remembered choice wins over the system preference', async () => {
    prefersDark(true)
    window.localStorage.setItem('ca.theme', 'light')
    installFakeBackend()
    renderApp('/login')

    expect(await screen.findByRole('button', { name: 'Modo oscuro' })).toBeVisible()
    expect(document.documentElement).toHaveAttribute('data-theme', 'light')
  })

  it('ignores an unknown stored value', async () => {
    prefersDark(true)
    window.localStorage.setItem('ca.theme', 'sepia')
    installFakeBackend()
    renderApp('/login')

    expect(await screen.findByRole('button', { name: 'Modo claro' })).toBeVisible()
  })

  it('still works when storage and matchMedia are unavailable', async () => {
    vi.stubGlobal('matchMedia', undefined)
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError')
    })
    installFakeBackend()
    renderApp('/login')

    fireEvent.click(await screen.findByRole('button', { name: 'Modo oscuro' }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(screen.getByRole('button', { name: 'Modo claro' })).toBeVisible()
  })
})
