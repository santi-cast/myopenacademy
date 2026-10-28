// Reading aids added around the lesson HTML after React inserted it as delivered (L54). Nothing
// here parses, rebuilds or rewrites that HTML: controls are new sibling or child elements marked
// with data-ui, pending lesson links get three attributes, highlight.js recolors code from its
// text and Mermaid draws next to the source it read with textContent. Every step can run twice
// (React StrictMode, theme changes) without adding anything twice.
import type { Theme } from '../theme/theme'
import { renderDiagrams } from './diagrams'

/** Highlighted languages (S19); `html` is highlight.js's xml. Anything else stays plain text. */
export const HIGHLIGHTED_LANGUAGES = new Set(['java', 'xml', 'html', 'sql', 'properties', 'bash', 'yaml', 'javascript', 'css', 'powershell', 'json', 'kotlin'])

export const PENDING_LINK_DESCRIPTION_ID = 'pending-link-description'
export const RETRIEVAL_HINT = 'Antes de abrirla, intentá explicar la solución con tus palabras.'

function element<K extends keyof HTMLElementTagNameMap>(tag: K, className: string, text?: string): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag)
  node.className = className
  if (text !== undefined) node.textContent = text
  return node
}

/** A top-level control added by the view; tests and the TOC tell it apart from lesson HTML. */
function uiElement<K extends keyof HTMLElementTagNameMap>(tag: K, className: string, text?: string): HTMLElementTagNameMap[K] {
  const node = element(tag, className, text)
  node.dataset.ui = ''
  return node
}

export function codeLanguage(code: Element): string | null {
  for (const name of code.classList) if (name.startsWith('language-')) return name.slice('language-'.length)
  return null
}

/** Text of a lesson element without the controls added here. */
export function plainText(node: Element): string {
  const copy = node.cloneNode(true) as Element
  copy.querySelectorAll('[data-ui]').forEach((control) => control.remove())
  return (copy.textContent ?? '').trim()
}

/** Lesson ID of a rewritten internal link (`/lessons/{id}` or `/lessons/{id}#fragment`), else null. */
export function lessonIdOf(href: string): string | null {
  if (!href.startsWith('/lessons/')) return null
  const rest = href.slice('/lessons/'.length)
  const end = rest.search(/[#?]/)
  const raw = end < 0 ? rest : rest.slice(0, end)
  if (raw === '') return null
  try {
    return decodeURIComponent(raw)
  } catch {
    return raw
  }
}

/** Links to pending lessons look and announce as unavailable (04); the click handler blocks them. */
export function markPendingLinks(root: Element, isPending: (lessonId: string) => boolean) {
  for (const link of root.querySelectorAll<HTMLAnchorElement>('a[href^="/lessons/"]')) {
    const lessonId = lessonIdOf(link.getAttribute('href') ?? '')
    if (lessonId === null || !isPending(lessonId) || link.classList.contains('link-pending')) continue
    link.classList.add('link-pending')
    link.setAttribute('aria-disabled', 'true')
    link.setAttribute('aria-describedby', PENDING_LINK_DESCRIPTION_ID)
  }
}

/** «#» button after each section heading: copies the link to that section (S21). */
export function addHeadingAnchors(body: Element) {
  for (const heading of body.querySelectorAll<HTMLElement>('h2[id], h3[id]')) {
    if (heading.querySelector(':scope > .anchor-btn')) continue
    const button = uiElement('button', 'anchor-btn', '#')
    button.type = 'button'
    button.dataset.copyLink = heading.id
    button.setAttribute('aria-label', `Copiar enlace a la sección «${plainText(heading)}»`)
    heading.append(button)
  }
}

/** Retrieval prompt inside the solution's own <summary>, read before opening it (S21). */
export function addSolutionHints(root: Element) {
  for (const summary of root.querySelectorAll('.ex-solution > details > summary')) {
    if (summary.querySelector(':scope > .sum-hint')) continue
    summary.append(uiElement('span', 'sum-hint', RETRIEVAL_HINT))
  }
}

export type CodeBlocks = { highlight: HTMLElement[]; diagrams: HTMLElement[] }

/** Adds the code bar with «Copiar» before every code block except diagrams, and sorts the blocks. */
export function decorateCodeBlocks(root: Element): CodeBlocks {
  const blocks: CodeBlocks = { highlight: [], diagrams: [] }
  for (const code of root.querySelectorAll<HTMLElement>('pre > code')) {
    const pre = code.parentElement as HTMLElement
    const language = codeLanguage(code)
    if (language === 'mermaid') {
      blocks.diagrams.push(pre)
      continue
    }
    if (language !== null && HIGHLIGHTED_LANGUAGES.has(language)) blocks.highlight.push(code)
    const previous = pre.previousElementSibling
    if (previous instanceof HTMLElement && previous.classList.contains('code-head') && previous.dataset.ui !== undefined) continue
    const head = uiElement('div', 'code-head')
    if (language !== null) head.append(element('span', 'code-lang', language))
    const button = element('button', 'copy-btn')
    button.type = 'button'
    button.dataset.copyCode = ''
    // The visible label turns into «Copiado» for a moment; the name stays and aria-live reports it.
    button.setAttribute('aria-label', 'Copiar código')
    button.append(element('span', 'copy-label', 'Copiar'))
    head.append(button)
    pre.before(head)
  }
  return blocks
}

/** Code text for a «Copiar» button: the block right after its bar, as plain text. */
export function codeTextFor(button: Element): string | null {
  const pre = button.closest('.code-head')?.nextElementSibling
  return pre?.querySelector('code')?.textContent ?? null
}

export async function highlightBlocks(codes: HTMLElement[], isCancelled: () => boolean) {
  if (codes.length === 0) return
  const { highlightCode } = await import('./highlight')
  if (isCancelled()) return
  for (const code of codes) highlightCode(code)
}

function clearDiagram(pre: HTMLElement) {
  const previous = pre.previousElementSibling
  if (previous instanceof HTMLElement && previous.dataset.uiDiagram !== undefined) previous.remove()
}

/**
 * Draws each Mermaid block from its code element's textContent. The drawing goes before the
 * source, which CSS then hides; a failure leaves the source visible, as text, under a note.
 */
export async function drawDiagrams(pres: HTMLElement[], theme: Theme, isCancelled: () => boolean) {
  if (pres.length === 0) return
  const svgs = await renderDiagrams(pres.map((pre) => pre.querySelector('code')?.textContent ?? ''), theme)
  if (isCancelled()) return
  pres.forEach((pre, index) => {
    clearDiagram(pre)
    const svg = svgs[index]
    if (svg === null) {
      const note = uiElement('div', 'diagram-error')
      note.dataset.uiDiagram = ''
      note.setAttribute('role', 'note')
      note.append(
        element('p', 'diagram-error-title', 'No se pudo dibujar el diagrama'),
        element('p', 'diagram-error-help', 'Se muestra su código original, sin interpretar.'),
      )
      pre.before(note)
      return
    }
    const figure = uiElement('figure', 'diagram')
    figure.dataset.uiDiagram = ''
    // Mermaid's own SVG, produced and sanitized under securityLevel 'strict'.
    figure.innerHTML = svg
    pre.before(figure)
  })
}
