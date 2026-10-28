// Mermaid, loaded with import() from the lesson view only (S5). Strict security level, no HTML
// labels, never started on load; the source is always the code element's text (L54). Two gates
// around it (docs/api.md, «sin HTML arbitrario ni interacciones»): a source that could change the
// configuration, add interactions or load images is never rendered, and the SVG Mermaid returns is
// checked in a document of its own before it reaches the page.
import type { Theme } from '../theme/theme'

type Mermaid = (typeof import('mermaid'))['default']

let mermaid: Promise<Mermaid> | null = null
let counter = 0

/**
 * Configuration keys a diagram's own directives can never change. Mermaid replaces its default
 * list with this one, so the defaults come first; it removes these names at every nesting level,
 * so `htmlLabels` also covers `flowchart.htmlLabels` and the other per-diagram copies.
 */
export const SECURE_KEYS = [
  'secure', 'securityLevel', 'startOnLoad', 'maxTextSize', 'suppressErrorRendering', 'maxEdges',
  'htmlLabels', 'flowchart', 'markdownAutoWrap', 'dompurifyConfig', 'themeCSS', 'fontFamily', 'altFontFamily',
  'arrowMarkerAbsolute', 'layout', 'elk', 'legacyMathML', 'forceLegacyMathML',
]

// Statements that add links or callbacks (flowchart/class/gantt `click`, sequence `link`/`links`,
// class `callback`), matched case-insensitively at the start of any line or `;`-separated statement.
const INTERACTION_STATEMENT = /^(click|links?|callback|href)(?![\w-])/i
// `@{ … }` node metadata naming an image or an icon, which Mermaid fetches while it draws.
const SHAPE_METADATA = /@\s*\{([^}]*)/g
const IMAGE_KEY = /(?<![\w-])(img|icon)(?![\w-])/i
const STYLE_STATEMENT = /^(style|classDef|linkStyle|cssClass)(?![\w-])/i

/** False for a source that must not reach Mermaid: it is shown as escaped text instead. */
export function isRenderable(source: string): boolean {
  if (/[\ue000-\ue006]/.test(source)) return false // the private-use placeholders below
  // Only &quot; &lt; &gt; &amp; are supported (the only ones the course uses); any other entity, named or
  // numeric, never reaches Mermaid, so no entity can spell a refused keyword.
  for (const [, name] of source.matchAll(/&(#x?[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);/g)) {
    if (!(name in ALLOWED_ENTITIES)) return false
  }
  if (/url\s*\(\s*(?!['"]?\s*#)/i.test(source)) return false // anything loaded through CSS
  if (/@import|image-set\s*\(/i.test(source)) return false
  if (/%%\s*\{/.test(source)) return false // init/config directives
  if (/^\s*---/.test(source)) return false // YAML frontmatter with `config:`
  if (/(?<![\w-])href(?![\w-])/i.test(source)) return false
  for (const statement of source.split(/\r\n|\r|\n|;/)) {
    if (INTERACTION_STATEMENT.test(statement.trimStart())) return false
  }
  for (const [, metadata] of source.matchAll(SHAPE_METADATA)) {
    // A backslash is a YAML escape that could spell `img` or `icon`.
    if (IMAGE_KEY.test(metadata) || metadata.includes('\\')) return false
  }
  for (const statement of source.split(/\r\n|\r|\n|;/)) {
    // A backslash in styling is a CSS escape that could spell url( or @import.
    if (STYLE_STATEMENT.test(statement.trimStart()) && statement.includes('\\')) return false
  }
  return true
}

/**
 * Text given to Mermaid. Without HTML labels it would print `<b>`/`<i>` (used in course labels)
 * literally, so those tags are dropped and their words kept as plain text; `<br/>` stays, since
 * Mermaid turns it into a new SVG text line.
 */
export function withoutInlineMarkup(source: string): string {
  return source
    .replace(/<\s*\/?\s*(b|i|strong|em)\s*>/gi, '')
}

// The supported entities and the private-use code point each one travels as: a single character, which no line
// wrapping can split, restored in the drawn SVG's text.
const ALLOWED_ENTITIES: Record<string, string> = { quot: '\ue000', amp: '\ue001', lt: '\ue002', gt: '\ue003' }
const RESTORED = new Map([['\ue000', '"'], ['\ue001', '&'], ['\ue002', '<'], ['\ue003', '>']])

/**
 * Lesson authors write &quot; &lt; &gt; &amp; in labels (GitHub draws Mermaid labels as HTML); without HTML labels
 * Mermaid would print them literally. Each becomes its placeholder; isRenderable has already refused any other entity.
 */
export function withEntityPlaceholders(source: string): string {
  return source.replace(/&(quot|amp|lt|gt);/g, (_entity, name: string) => ALLOWED_ENTITIES[name])
}

/** Restores placeholders in text nodes outside <style>, assigned as text and never parsed as markup. */
export function restorePlaceholders(svg: Element) {
  const walker = svg.ownerDocument.createTreeWalker(svg, NodeFilter.SHOW_TEXT)
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (node.parentElement?.closest('style')) continue
    const text = node.nodeValue ?? ''
    if (/[\ue000-\ue003]/.test(text)) node.nodeValue = text.replace(/[\ue000-\ue003]/g, (p) => RESTORED.get(p) ?? p)
  }
}

const SVG_NS = 'http://www.w3.org/2000/svg'
// Elements that link, embed, reference or animate other content: none belongs in a lesson diagram.
const FORBIDDEN_ELEMENTS = new Set([
  'a', 'foreignobject', 'image', 'script', 'iframe', 'object', 'embed', 'use', 'feimage',
  'animate', 'animatemotion', 'animatetransform', 'set', 'discard', 'handler', 'listener',
])

/** CSS that may load something: any url() not pointing inside the SVG, @import, image-set() or escapes. */
function loadsCss(css: string): boolean {
  if (/@import|image-set\s*\(|\\/i.test(css)) return true
  for (const [, target] of css.matchAll(/url\s*\(\s*['"]?\s*([^'")\s]*)/gi)) {
    if (!target.startsWith('#')) return true
  }
  return false
}

function isSafeElement(node: Element): boolean {
  if (node.namespaceURI !== SVG_NS || FORBIDDEN_ELEMENTS.has(node.localName.toLowerCase())) return false
  for (const attribute of node.attributes) {
    const name = attribute.localName.toLowerCase()
    if (name.startsWith('on') || name === 'href' || name === 'src' || /javascript:/i.test(attribute.value)) return false
    if (loadsCss(attribute.value)) return false
  }
  if (node.localName.toLowerCase() === 'style' && loadsCss(node.textContent ?? '')) return false
  return true
}

/**
 * The SVG element from Mermaid's markup, or null when it holds anything a diagram should not.
 * The markup is parsed by DOMParser into a separate, inert document (no scripts, no loads; it is
 * HTML-serialized, so the HTML parser reads it as Mermaid wrote it); the element returned is the
 * one that was checked, never markup parsed a second time.
 */
export function checkedSvg(markup: string): SVGSVGElement | null {
  const parsed = new DOMParser().parseFromString(markup, 'text/html')
  if (parsed.head.childNodes.length > 0) return null
  const nodes = [...parsed.body.childNodes].filter((node) => !(node.nodeType === Node.TEXT_NODE && (node.textContent ?? '').trim() === ''))
  const svg = nodes[0]
  if (nodes.length !== 1 || !isElement(svg) || svg.localName !== 'svg') return null
  for (const node of [svg, ...svg.querySelectorAll('*')]) if (!isSafeElement(node)) return null
  return document.importNode(svg, true) as SVGSVGElement
}

function isElement(node: Node): node is Element {
  return node.nodeType === Node.ELEMENT_NODE
}

function load(): Promise<Mermaid> {
  mermaid ??= import('mermaid').then((module) => module.default)
  return mermaid
}

/** Renders each source in order; a refused or failed diagram yields null and does not stop the others. */
export async function renderDiagrams(sources: string[], theme: Theme): Promise<(SVGSVGElement | null)[]> {
  const library = await load()
  library.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    secure: SECURE_KEYS,
    theme: theme === 'dark' ? 'dark' : 'default',
    htmlLabels: false,
    flowchart: { htmlLabels: false },
    suppressErrorRendering: true,
  })
  const results: (SVGSVGElement | null)[] = []
  for (const source of sources) {
    if (!isRenderable(source)) {
      results.push(null)
      continue
    }
    counter += 1
    try {
      const prepared = withEntityPlaceholders(withoutInlineMarkup(source))
      const svg = checkedSvg((await library.render(`lesson-diagram-${counter}`, prepared)).svg)
      if (svg) restorePlaceholders(svg)
      results.push(svg)
    } catch {
      results.push(null)
    }
  }
  return results
}
