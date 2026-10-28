import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ana, installFakeBackend } from './test/fakeBackend'
import { BODY_CODE, BODY_DIAGRAMS, lessonView, routeLesson } from './test/lessonFixtures'
import { renderApp } from './test/renderApp'

// S5/S19: Mermaid and highlight.js load only in the lesson view. A module factory runs the first
// time its module is imported, so the counters show whether anything reached them. This file has
// its own module registry: nothing else imported them before.
const imported = vi.hoisted(() => ({ mermaid: 0, highlight: 0 }))

vi.mock('mermaid', () => {
  imported.mermaid += 1
  return { default: { initialize: () => {}, render: async (id: string) => ({ svg: `<svg id="${id}"></svg>` }) } }
})

vi.mock('highlight.js/lib/core', async (importOriginal) => {
  imported.highlight += 1
  return importOriginal()
})

describe('lazy lesson libraries', () => {
  it('are not imported by the app shell or other screens, only by the lesson view', async () => {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend, lessonView({ bodyHtml: BODY_CODE + BODY_DIAGRAMS }))
    const view = renderApp('/')
    await screen.findByRole('heading', { level: 1, name: 'Mis cursos' })
    await view.navigate('/courses')
    await screen.findByRole('heading', { level: 1, name: 'Catálogo' })
    await view.navigate('/courses/java')
    await screen.findByRole('heading', { level: 1, name: 'Java · Índice del curso' })
    expect(imported).toEqual({ mermaid: 0, highlight: 0 })

    await view.navigate('/lessons/java-02.01')
    await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })
    await waitFor(() => expect(imported).toEqual({ mermaid: 1, highlight: 1 }))
  })
})
