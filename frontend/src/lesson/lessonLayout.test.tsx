import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ana, installFakeBackend } from '../test/fakeBackend'
import { BODY_LINKS, lessonView, routeLesson } from '../test/lessonFixtures'
import { renderApp } from '../test/renderApp'

// Lesson layout defects found by the real-browser journey (T10): the code bar must not stick out
// past its block, and the page has one <h1> even when the delivered lesson HTML brings its own.
const mermaid = vi.hoisted(() => ({ initialize: vi.fn(), render: vi.fn() }))
vi.mock('mermaid', () => ({ default: mermaid }))

beforeEach(() => {
  Object.defineProperty(Element.prototype, 'scrollIntoView', { configurable: true, value: () => {} })
})

afterEach(() => {
  delete (Element.prototype as Partial<Element>).scrollIntoView
})

// Read from disk: Vitest serves CSS imports as empty strings (see styles.test.ts).
type Fs = { readFileSync: (path: string, encoding: 'utf8') => string }
const NODE_FS = 'node:fs'
const fs = (await import(/* @vite-ignore */ NODE_FS)) as Fs
const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd()
const lessonCss = fs.readFileSync(`${cwd}/src/lesson/lesson.css`, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')

/** Every `selector list { … margin-inline: value … }` in the stylesheet, nested @media rules included. */
function marginInlineRules(css: string) {
  return [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)]
    .map(([, selectors, body]) => ({
      selectors: selectors.split(',').map((selector) => selector.trim().replace(/\s+/g, ' ')),
      value: /(?:^|;)\s*margin-inline\s*:\s*([^;]+)/.exec(body)?.[1].trim(),
    }))
    .filter((rule) => rule.value !== undefined)
}

describe('code bar and its block share one outset (720 px horizontal scroll)', () => {
  const rules = marginInlineRules(lessonCss)
  const touching = rules.filter((rule) => rule.selectors.some((selector) => /\.code-head|(^|[ >])pre\b/.test(selector)))

  it('every margin-inline rule for the code bar also applies to its <pre>, with the same value', () => {
    expect(touching.length).toBeGreaterThan(0)
    for (const rule of touching) {
      expect(rule.selectors).toEqual(expect.arrayContaining(['.lesson-body > pre', '.lesson-body > .code-head']))
    }
  })

  it('the outset comes from the real column width, never from a font-relative measure, and never goes negative', () => {
    const outset = touching.find((rule) => rule.value !== '0')!
    expect(outset.value).toBe('calc(-1 * clamp(0px, (var(--container-width) - 100%) / 2 - var(--space-2), var(--code-outset-max)))')
    expect(outset.value).not.toContain('--measure')
  })
})

describe('one <h1> per lesson page', () => {
  async function openLesson(bodyHtml: string) {
    const backend = installFakeBackend({ loggedInAs: ana })
    routeLesson(backend, lessonView({ bodyHtml }))
    renderApp('/lessons/java-02.01')
    await screen.findByText(/posición/)
    return document.querySelector<HTMLElement>('.lesson-body')!
  }

  it('when the delivered HTML starts with its own <h1>, the view adds none and the article is labelled by it', async () => {
    const delivered = '<h1 id="0201-tu-primer-programa">02.01 Tu primer programa</h1>\n<p>Texto.</p>\n'
    const body = await openLesson(delivered)
    const headings = screen.getAllByRole('heading', { level: 1 })
    expect(headings).toHaveLength(1)
    expect(headings[0]).toHaveAttribute('id', '0201-tu-primer-programa')
    expect(document.getElementById('lesson-title')).toBeNull()
    expect(screen.getByRole('article', { name: '02.01 Tu primer programa' })).toBe(document.querySelector('article.reading'))
    expect(body.innerHTML).toBe(delivered)
    expect(document.querySelector('.lesson-head .kicker')).toHaveTextContent('Java')
    expect(document.querySelector('.lesson-head .position')).toHaveTextContent('Lección 02.01 · posición')
  })

  it('when the delivered HTML does not start with an <h1>, the view keeps its own title', async () => {
    await openLesson(BODY_LINKS)
    const headings = screen.getAllByRole('heading', { level: 1 })
    expect(headings).toHaveLength(1)
    expect(headings[0]).toHaveAttribute('id', 'lesson-title')
    expect(headings[0]).toHaveTextContent('Tu primer programa')
    expect(screen.getByRole('article', { name: 'Tu primer programa' })).toBe(document.querySelector('article.reading'))
  })

  it('an <h1> later in the delivered HTML does not replace the view title', async () => {
    await openLesson('<p>Intro.</p>\n<h1 id="otro">Otro</h1>\n')
    expect(document.getElementById('lesson-title')).toHaveTextContent('Tu primer programa')
    expect(screen.getByRole('article', { name: 'Tu primer programa' })).toBeInTheDocument()
  })
})
