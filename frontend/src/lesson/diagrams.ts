// Mermaid, loaded with import() from the lesson view only (S5). Strict security level, no HTML
// labels, never started on load; the source is always the code element's text (L54).
import type { Theme } from '../theme/theme'

type Mermaid = (typeof import('mermaid'))['default']

let mermaid: Promise<Mermaid> | null = null
let counter = 0

function load(): Promise<Mermaid> {
  mermaid ??= import('mermaid').then((module) => module.default)
  return mermaid
}

/** Renders each source in order; a failed diagram yields null and does not stop the others. */
export async function renderDiagrams(sources: string[], theme: Theme): Promise<(string | null)[]> {
  const library = await load()
  library.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    theme: theme === 'dark' ? 'dark' : 'default',
    htmlLabels: false,
    suppressErrorRendering: true,
  })
  const results: (string | null)[] = []
  for (const source of sources) {
    counter += 1
    try {
      results.push((await library.render(`lesson-diagram-${counter}`, source)).svg)
    } catch {
      results.push(null)
    }
  }
  return results
}
