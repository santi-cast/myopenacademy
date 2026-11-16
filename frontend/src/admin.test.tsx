import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { AdminUser, User } from './api/types'
import { ana, apiCalls, installFakeBackend } from './test/fakeBackend'
import { renderApp } from './test/renderApp'

// H9: the administration, the profile and the instance seen from the login page.
const admin: User = { ...ana, role: 'admin' }
const bruno: AdminUser = { id: 'b-1', username: 'Bruno', role: 'user', active: true, createdAt: '2026-11-16T10:00:00Z' }
const self: AdminUser = { id: ana.id, username: 'Ana', role: 'admin', active: true, createdAt: '2026-11-16T09:00:00Z' }

function adminRoutes(backend: ReturnType<typeof installFakeBackend>) {
  backend.route('GET /api/admin/settings', { status: 200, body: { registrationOpen: true, instanceName: null } })
  backend.route('GET /api/admin/users', { status: 200, body: { users: [self, bruno] } })
  backend.route('GET /api/admin/courses', { status: 200, body: { courses: [
    { id: 'inicial', title: 'Fundamentos para programar', version: '1.0.0', tag: 'v1.0.0', commit: '9f556614325698bef0ae0cf9db39c2da0fb04b16', repository: null },
  ] } })
}

describe('Administración (H9)', () => {
  it('only an administrator sees the link to the administration', async () => {
    installFakeBackend({ loggedInAs: { ...ana, role: 'user' } })
    renderApp('/')
    const nav = await screen.findByRole('navigation', { name: 'Principal' })
    expect(within(nav).queryByRole('link', { name: 'Administración' })).not.toBeInTheDocument()
  })

  it('lists users and courses, and deactivates a user only when the server confirms', async () => {
    const backend = installFakeBackend({ loggedInAs: admin })
    adminRoutes(backend)
    backend.route('PUT /api/admin/users/b-1/active', { status: 200, body: { ...bruno, active: false } })
    renderApp('/admin')

    expect(await screen.findByRole('heading', { level: 1, name: 'Administración' })).toBeVisible()
    expect(within(screen.getByRole('navigation', { name: 'Principal' })).getByRole('link', { name: 'Administración' })).toBeVisible()
    expect(screen.getByText('Fundamentos para programar')).toBeVisible()
    expect(screen.getByText('1.0.0 · v1.0.0')).toBeVisible()
    // Nobody deactivates themselves: no button on one's own row.
    expect(screen.queryByRole('button', { name: 'Desactivar a Ana' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Desactivar a Bruno' }))
    expect(await screen.findByRole('button', { name: 'Reactivar a Bruno' })).toBeVisible()
    expect(apiCalls(backend)).toContain('PUT /api/admin/users/b-1/active')
  })

  it('saves the settings and the new name reaches the header', async () => {
    const backend = installFakeBackend({ loggedInAs: admin })
    adminRoutes(backend)
    let name: string | null = null
    backend.route('PUT /api/admin/settings', () => {
      name = 'Academia de casa'
      return { status: 200, body: { registrationOpen: false, instanceName: name } }
    })
    backend.route('GET /api/instance', () => ({ status: 200, body: { name, registrationOpen: name === null } }))
    renderApp('/admin')

    await screen.findByRole('heading', { level: 1, name: 'Administración' })
    fireEvent.click(screen.getByRole('checkbox'))
    fireEvent.change(screen.getByLabelText('Nombre de la academia'), { target: { value: 'Academia de casa' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar los ajustes' }))
    expect(await screen.findByText('Ajustes guardados.')).toBeVisible()
    await waitFor(() => expect(screen.getByRole('link', { name: /Academia de casa/ })).toBeVisible())
  })
})

describe('Mi perfil (H9)', () => {
  it('changes the password with the current one and checks the repetition first', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    backend.route('PUT /api/me/password', { status: 200, body: ana })
    renderApp('/profile')

    await screen.findByRole('heading', { level: 1, name: 'Mi perfil' })
    fireEvent.change(screen.getByLabelText('Contraseña actual'), { target: { value: 'una frase bastante larga' } })
    fireEvent.change(screen.getByLabelText('Contraseña nueva'), { target: { value: 'una frase nueva y larga' } })
    fireEvent.change(screen.getByLabelText('Repite la contraseña nueva'), { target: { value: 'otra cosa distinta aquí' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar la contraseña' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Las dos contraseñas nuevas no coinciden.')
    expect(apiCalls(backend)).not.toContain('PUT /api/me/password')

    fireEvent.change(screen.getByLabelText('Repite la contraseña nueva'), { target: { value: 'una frase nueva y larga' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar la contraseña' }))
    expect(await screen.findByText('Contraseña cambiada.')).toBeVisible()
  })
})

describe('Registro cerrado (H9)', () => {
  it('the login page does not offer an account and the registration page says why', async () => {
    const backend = installFakeBackend()
    backend.route('GET /api/instance', { status: 200, body: { name: null, registrationOpen: false } })
    renderApp('/login')
    expect(await screen.findByText(/El registro está cerrado/)).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Crear una cuenta' })).not.toBeInTheDocument()
  })
})
