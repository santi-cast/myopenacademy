import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { isRenderable, withEntityPlaceholders, withoutInlineMarkup } from './lesson/diagrams'
import { ana, installFakeBackend } from './test/fakeBackend'
import { lessonView, routeLesson } from './test/lessonFixtures'
import { renderApp } from './test/renderApp'

// Mermaid policy (docs/api.md «Contenido, ejercicios y recursos»: «sin HTML arbitrario ni
// interacciones», «El SVG generado no elude controles de seguridad»; L54, L63 B1). Mermaid is a
// double at the module boundary: it always "succeeds", so every fallback seen here comes from the
// view's own gates, before rendering (the source) or after it (the SVG).
const mermaid = vi.hoisted(() => ({ initialize: vi.fn(), render: vi.fn() }))
vi.mock('mermaid', () => ({ default: mermaid }))

const SVG_NS = 'xmlns="http://www.w3.org/2000/svg"'
const XLINK_NS = 'xmlns:xlink="http://www.w3.org/1999/xlink"'
const okSvg = (id: string) => `<svg ${SVG_NS} id="${id}" data-testid="diagram" role="img"><text>ok</text></svg>`

beforeEach(() => {
  mermaid.initialize.mockReset()
  mermaid.render.mockReset()
  mermaid.render.mockImplementation(async (id: string) => ({ svg: okSvg(id) }))
  Object.defineProperty(Element.prototype, 'scrollIntoView', { configurable: true, value: () => {} })
})

afterEach(() => {
  delete (Element.prototype as Partial<Element>).scrollIntoView
})

const escapeText = (text: string) => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
const diagramBlock = (source: string) => `<pre><code class="language-mermaid">${escapeText(source)}</code></pre>\n`

async function openLesson(body: string) {
  const backend = installFakeBackend({ loggedInAs: ana })
  routeLesson(backend, lessonView({ bodyHtml: body }))
  renderApp('/lessons/java-02.01')
  await screen.findByRole('heading', { level: 1, name: 'Tu primer programa' })
  return backend
}

function lessonBody() {
  const body = document.querySelector<HTMLElement>('.lesson-body')
  if (!body) throw new Error('lesson body not rendered')
  return body
}

/** Nothing a refused diagram could have put in the page. */
function expectNoDrawing() {
  expect(lessonBody().querySelectorAll('[data-ui-diagram] svg, svg, a[href^="https:"], foreignObject, image, img, use, script')).toHaveLength(0)
  expect(lessonBody().querySelector('[data-ui-diagram]')).toHaveClass('diagram-error')
}

// Verifier's cases (L63) first, then variants of each rule: case, tabs, CRLF, spacing, `;`.
const REFUSED_SOURCES = [
  'flowchart LR\n A-->B\n click A href "https://evil.example"',
  '%%{init: {"htmlLabels":true}}%%\nflowchart LR\n A["<b>x</b>"]',
  '---\nconfig:\n  htmlLabels: true\n---\nflowchart LR\n A-->B',
  'sequenceDiagram\n participant A\n link A: X @ https://evil.example',
  'flowchart TD\n A@{ img: "https://evil.example/i.png" }',
  'flowchart LR\n A-->B\n CLICK A "https://evil.example"',
  'flowchart LR\n\tA-->B\n\tClick A call alert()',
  'flowchart LR\r\n A-->B\r\n click A "https://evil.example"',
  'flowchart LR\r A-->B\r click A "https://evil.example"',
  'flowchart LR\n A-->B; click A "https://evil.example"',
  '%%{ init : {"htmlLabels":true} }%%\nflowchart LR\n A-->B',
  'flowchart LR\n A-->B\n %%{init: {"flowchart":{"htmlLabels":true}}}%%',
  '%% {init: {"htmlLabels":true}}%%\nflowchart LR\n A-->B',
  '  \n---\nconfig:\n  htmlLabels: true\n---\nflowchart LR\n A-->B',
  '\t---\r\nconfig:\r\n  flowchart:\r\n    htmlLabels: true\r\n---\r\nflowchart LR\r\n A-->B',
  'sequenceDiagram\n participant A\n LINKS A: {"x": "https://evil.example"}',
  'sequenceDiagram\n participant A\n\tLink A: X @ https://evil.example',
  'classDiagram\n class Foo\n callback Foo "fn"',
  'classDiagram\n class Foo\n link Foo "https://evil.example"',
  'flowchart LR\n A-->B\n style A fill:#fff\n HREF A "https://evil.example"',
  'flowchart TD\n A@{ IMG: "https://evil.example/i.png" }',
  'flowchart TD\n A@{ icon: "fa:user", label: "x" }',
  'flowchart TD\n A @ {\n   label: "x",\n   img: "https://evil.example/i.png"\n }',
  'flowchart TD\n A@{ "img": "https://evil.example/i.png" }',
]

describe('Mermaid source gate: never rendered, shown as escaped source', () => {
  it.each(REFUSED_SOURCES)('a diagram with interactions, directives or external images falls back to escaped source: %s', async (source) => {
    expect(isRenderable(source)).toBe(false) // as written, CR included
    await openLesson(`<h2 id="d">D</h2>\n${diagramBlock(source)}<p>Sigue.</p>\n`)
    expect(await screen.findByText('No se pudo dibujar el diagrama')).toBeVisible()
    expect(mermaid.render).not.toHaveBeenCalled()
    const code = lessonBody().querySelector('code.language-mermaid')!
    expect(code).toBeVisible()
    // The HTML parser turns CR and CRLF into LF before the code element holds the text.
    expect(code.textContent).toBe(source.replace(/\r\n?/g, '\n'))
    expect(code.children).toHaveLength(0)
    expectNoDrawing()
    expect(screen.getByText('Sigue.')).toBeVisible()
  })

  it('a refused diagram does not stop the next one from rendering', async () => {
    await openLesson(diagramBlock(REFUSED_SOURCES[0]) + diagramBlock('flowchart LR\n A-->B\n'))
    expect(await screen.findAllByTestId('diagram')).toHaveLength(1)
    expect(screen.getAllByText('No se pudo dibujar el diagrama')).toHaveLength(1)
    expect(mermaid.render.mock.calls.map(([, text]) => text)).toEqual(['flowchart LR\n A-->B\n'])
  })

  it('lets through the two real diagrams of java-02.01 and ordinary words that only contain the keywords', () => {
    // Copied verbatim from content/java/lessons/java-02.01.md (sections 1.4 and 1.11).
    const real = [
      'flowchart TD\n    A([Inicio de main]) --> B[/"IO.println(&quot;Pedido recibido&quot;)"/]\n    B --> C[/"IO.println(&quot;En preparación&quot;)"/]\n    C --> D[/"IO.println(&quot;Gracias por tu compra&quot;)"/]\n    D --> E([Fin de main: el programa termina])\n',
      'flowchart LR\n    A["Hello.java<br/>(texto que escribes tú)"] -->|javac comprueba y traduce| B["Hello.class<br/>(bytecode)"]\n    B -->|la JVM carga la clase| C["Busca main"]\n    C -->|ejecuta sus instrucciones<br/>de arriba abajo| D["Salida en pantalla"]\n',
    ]
    for (const source of real) expect(isRenderable(source)).toBe(true)
    expect(isRenderable('flowchart LR\n  clicker --> linked\n  A["imagen"] --> B@{ shape: rect, label: "icono" }\n')).toBe(true)
    expect(isRenderable('sequenceDiagram\n  A->>B: clicks %% comentario\n')).toBe(true)
  })
})

describe('Mermaid SVG gate: checked in a separate document before insertion', () => {
  const HOSTILE_SVGS: [string, string][] = [
    ['link', `<svg ${SVG_NS} ${XLINK_NS}><a xlink:href="https://evil.example/d"><text>go</text></a></svg>`],
    ['foreignObject', `<svg ${SVG_NS}><foreignObject width="10" height="10"><div xmlns="http://www.w3.org/1999/xhtml"><b>x</b></div></foreignObject></svg>`],
    ['external image', `<svg ${SVG_NS}><image href="https://evil.example/i.png" width="10" height="10"></image></svg>`],
  ]

  it('rejects a rendered SVG that carries links, foreignObject or external images', async () => {
    const queue = HOSTILE_SVGS.map(([, svg]) => svg)
    mermaid.render.mockImplementation(async () => ({ svg: queue.shift() }))
    await openLesson(HOSTILE_SVGS.map(([name]) => diagramBlock(`flowchart LR\n ${name.replace(' ', '_')}-->B`)).join(''))
    await waitFor(() => expect(screen.getAllByText('No se pudo dibujar el diagrama')).toHaveLength(3))
    expect(mermaid.render).toHaveBeenCalledTimes(3)
    expect(lessonBody().querySelectorAll('svg, a, foreignObject, image, div[xmlns], b')).toHaveLength(0)
    expect(document.querySelectorAll('a[href^="https://evil"], image')).toHaveLength(0)
  })

  it.each([
    ['script', `<svg ${SVG_NS}><script>window.parent.x=1</script></svg>`],
    ['event handler', `<svg ${SVG_NS}><rect onclick="x()" width="1" height="1"/></svg>`],
    ['uppercase event handler', `<svg ${SVG_NS}><g ONMOUSEOVER="x()"></g></svg>`],
    ['use with an external href', `<svg ${SVG_NS}><use href="https://evil.example/s.svg#x"/></svg>`],
    ['use with a local xlink:href', `<svg ${SVG_NS} ${XLINK_NS}><use xlink:href="#x"/></svg>`],
    ['href on another element', `<svg ${SVG_NS}><text href="https://evil.example">x</text></svg>`],
    ['external url() in an attribute', `<svg ${SVG_NS}><rect fill="url(https://evil.example/p.png)"/></svg>`],
    ['external url() in a style attribute', `<svg ${SVG_NS}><rect style="fill: URL( 'https://evil.example/p.png' )"/></svg>`],
    ['external url() in a <style>', `<svg ${SVG_NS}><style>.n{background:url(//evil.example/p.png)}</style></svg>`],
    ['@import in a <style>', `<svg ${SVG_NS}><style>@import "https://evil.example/x.css";</style></svg>`],
    ['CSS escape in a <style>', `<svg ${SVG_NS}><style>.n{background:\\75rl(https://evil.example/p.png)}</style></svg>`],
    ['iframe', `<svg ${SVG_NS}><iframe src="https://evil.example"></iframe></svg>`],
    ['animation that could set an href', `<svg ${SVG_NS}><set attributeName="fill" to="red"/></svg>`],
    ['markup beside the SVG', `<svg ${SVG_NS}><text>ok</text></svg><img src="https://evil.example/i.png">`],
    ['markup before the SVG', `<style>*{}</style><svg ${SVG_NS}><text>ok</text></svg>`],
    ['not an SVG', '<div><b>x</b></div>'],
    ['no markup at all', ''],
  ])('rejects a rendered SVG with %s', async (_name, svg) => {
    mermaid.render.mockImplementation(async () => ({ svg }))
    await openLesson(diagramBlock('flowchart LR\n A-->B'))
    expect(await screen.findByText('No se pudo dibujar el diagrama')).toBeVisible()
    expect(mermaid.render).toHaveBeenCalledTimes(1)
    expectNoDrawing()
  })
})

// Shapes used by the course's own diagrams (advisor check of the 179 Mermaid blocks at 0c2df35):
// HTML line breaks and bold/italic inside labels, and class stereotypes.
const REAL_LABELS = 'flowchart LR\n    service["<b>service</b><br/>LibraryService · reglas"] --> repo["<i>repository</i><br/>BookRepository"]\n'
const REAL_STEREOTYPES =
  'classDiagram\n    class Shape {\n        <<interface>>\n        +area() double\n    }\n    class Point {\n        <<record>>\n    }\n' +
  '    class Base {\n        <<abstract>>\n    }\n    class Color {\n        <<enumeration>>\n        RED\n    }\n    Shape <|.. Point\n'
// Elements and attributes real Mermaid produced (12.2.0; 12.3.0 draws the 81 diagrams of the base course with this policy) for those shapes in Chromium (strict, no HTML labels).
const MERMAID_LIKE_SVG = `<svg ${SVG_NS} id="m" data-testid="diagram" width="100%" viewBox="0 0 200 100" style="max-width: 200px;" role="graphics-document document" aria-roledescription="class">` +
  '<style>#m{font-family:"trebuchet ms",verdana,arial,sans-serif;font-size:16px;fill:#333;}#m .node rect{fill:#ECECFF;stroke:#9370DB;}</style>' +
  '<g><defs><filter id="m-drop-shadow"><feDropShadow dx="4" dy="4" stdDeviation="0" flood-opacity="0.06"/></filter></defs>' +
  '<marker id="m_class-extensionEnd" class="marker extension class" refX="18" refY="7" markerWidth="190" markerHeight="240" orient="auto"><path d="M 1,1 V 13 L18,7 Z"/></marker></g>' +
  '<g class="root"><g class="edgePaths"><path marker-end="url(#m_class-extensionEnd)" d="M0,0L10,10" style="stroke-dasharray: 3;"/></g>' +
  '<g class="nodes"><g class="node default" id="classId-Shape-0" transform="translate(50, 50)"><rect filter="url(#m-drop-shadow)" width="80" height="40"/>' +
  '<circle r="2"/><polygon points="0,0 1,1 0,1"/>' +
  '<g class="label"><text y="-10.1"><tspan class="text-outer-tspan" x="0" y="-0.1em" dy="1.1em"><tspan font-style="normal" class="text-inner-tspan" font-weight="normal">«interface»</tspan></tspan></text>' +
  '<text><tspan class="text-outer-tspan" x="0" dy="1.1em"><tspan font-weight="bold">Shape</tspan></tspan></text>' +
  '<text><tspan class="text-outer-tspan" x="0" dy="1.1em"><tspan>service</tspan></tspan><tspan class="text-outer-tspan" x="0" dy="1.1em"><tspan>LibraryService</tspan></tspan></text></g></g></g></g></svg>'

describe('the course\'s own label shapes', () => {
  it('a label with <br/> and <b>/<i> passes the source gate; Mermaid gets the <br/> and the words, without the bold/italic tags', async () => {
    expect(isRenderable(REAL_LABELS)).toBe(true)
    await openLesson(diagramBlock(REAL_LABELS))
    await screen.findByTestId('diagram')
    expect(mermaid.render.mock.calls.map(([, text]) => text)).toEqual([
      'flowchart LR\n    service["service<br/>LibraryService · reglas"] --> repo["repository<br/>BookRepository"]\n',
    ])
    expect(withoutInlineMarkup('A["< B >x</ b ><I>y</i><strong>z</strong><em>w</EM><br>v<br />u"]')).toBe('A["xyzw<br>v<br />u"]')
  })

  it('class stereotypes <<interface>>, <<record>>, <<abstract>>, <<enumeration>> reach Mermaid unchanged and draw', async () => {
    expect(isRenderable(REAL_STEREOTYPES)).toBe(true)
    await openLesson(diagramBlock(REAL_STEREOTYPES))
    await screen.findByTestId('diagram')
    expect(mermaid.render.mock.calls.map(([, text]) => text)).toEqual([REAL_STEREOTYPES])
    expect(screen.queryByText('No se pudo dibujar el diagrama')).toBeNull()
  })

  it('the SVG gate keeps what Mermaid draws for line breaks and stereotypes (g, text, tspan, markers, filters)', async () => {
    mermaid.render.mockImplementation(async () => ({ svg: MERMAID_LIKE_SVG }))
    await openLesson(diagramBlock(REAL_STEREOTYPES))
    const drawn = await screen.findByTestId('diagram')
    expect(drawn.querySelectorAll('text')).toHaveLength(3)
    expect(drawn.querySelectorAll('text')[2].querySelectorAll(':scope > tspan')).toHaveLength(2)
    expect(drawn).toHaveTextContent('«interface»')
    // jsdom's HTML parser lowercases feDropShadow; Chromium's keeps the SVG spelling.
    const names = [...drawn.querySelectorAll('*')].map((node) => node.localName.toLowerCase())
    expect(names).toEqual(expect.arrayContaining(['fedropshadow', 'marker', 'circle', 'polygon', 'style']))
    expect(screen.queryByText('No se pudo dibujar el diagrama')).toBeNull()
  })
})

describe('HTML entities in labels', () => {
  it('draws entities as characters even when Mermaid splits a label across text nodes', async () => {
    // Real Mermaid wraps labels into several <tspan>, at any character, and prints entities literally.
    mermaid.render.mockImplementation(async (id: string, source: string) => {
      const label = /\[\/"(.*)"\/\]/.exec(source)?.[1] ?? ''
      const escaped = label.replaceAll('&', '&amp;')
      const cut = escaped.indexOf('quo') + 2
      return { svg: `<svg ${SVG_NS} id="${id}"><text><tspan>${escaped.slice(0, cut)}</tspan><tspan>${escaped.slice(cut)}</tspan></text></svg>` }
    })
    await openLesson(diagramBlock('flowchart TD\n B[/"IO.println(&quot;En preparación&quot;) a &amp; b &lt;c&gt;"/]'))

    const svg = await waitFor(() => {
      const drawn = lessonBody().querySelector('figure.diagram svg')
      if (!drawn) throw new Error('not drawn')
      return drawn
    })
    expect(svg.textContent).toBe('IO.println("En preparación") a & b <c>')
    expect(mermaid.render.mock.calls[0][1]).not.toMatch(/&(quot|amp|lt|gt);/)
    // Decoding only ever writes text: no element appears from «&lt;c&gt;».
    expect(svg.querySelectorAll('*').length).toBe(3)
  })
})

describe('entities cannot smuggle refused syntax past the source gate', () => {
  it.each([
    'flowchart LR\n A-->B\n &#99;lick A &#104;ref "https://evil.example"',
    'flowchart LR\n A-->B\n c&#x6C;ick A call&#98;ack',
    '&#37;&#37;{init: {"htmlLabels":true}}%%\nflowchart LR\n A-->B',
    'flowchart TD\n A@{ &#105;mg: "https://evil.example/i.png" }',
  ])('%s', async (source) => {
    await openLesson(diagramBlock(source))
    expect(await screen.findByText('No se pudo dibujar el diagrama')).toBeVisible()
    expect(mermaid.render).not.toHaveBeenCalled()
  })
})

describe('only the four entities the course uses are supported', () => {
  it.each([
    ['a numeric entity', 'flowchart LR\n  A["&#99;lick"]-->B'],
    ['a hex entity', 'flowchart LR\n  A["&#x63;"]-->B'],
    ['another named entity', 'flowchart LR\n  A["a&nbsp;b"]-->B'],
    ['a private-use placeholder already in the source', 'flowchart LR\n  A["\ue000x\ue000"]-->B'],
  ])('%s is shown as source, never rendered', async (_case, source) => {
    await openLesson(diagramBlock(source))
    expect(await screen.findByText('No se pudo dibujar el diagrama')).toBeVisible()
    expect(mermaid.render).not.toHaveBeenCalled()
    expect(lessonBody().textContent).toContain(source.split('\n')[1].trim().slice(0, 6))
  })
})

describe('sources that could load something during rendering', () => {
  // Mermaid fetches these while drawing, before the SVG gate can act, so they must never reach render().
  it.each([
    'flowchart TD\n  A@{ "\\u0069mg": "https://e.example/a.png" }',
    'flowchart TD\n  A@{ "&#92;u0069mg": "https://e.example/a.png" }',
    'flowchart TD\n  A@{ \\x69mg: "https://e.example/a.png" }',
    'classDiagram\n  class Foo\n  style Foo fill:url(https://e.example/a.png)',
    'classDiagram\n  class Foo\n  style Foo fill&#58;url&#40;https://e.example/a.png&#41;',
    'stateDiagram-v2\n  s1\n  classDef x fill:url(https://e.example/a.png)\n  class s1 x',
    'block-beta\n  a\n  classDef x fill:url(https://e.example/a.png)\n  class a x',
    'flowchart LR\n  A-->B\n  classDef x fill:url( "https://e.example/a.png")',
    'flowchart LR\n  A-->B\n  style A fill:\\75rl(https://e.example/a.png)',
    'flowchart LR\n  A-->B\n  linkStyle 0 stroke:#f00,@import url(https://e.example/x.css)',
  ])('%s', (source) => {
    const decoded = withEntityPlaceholders(source).replace(/[\ue000-\ue006]/g, (p) => '"&<>\'#;'[p.charCodeAt(0) - 0xe000])
    expect(isRenderable(source) && isRenderable(decoded)).toBe(false)
  })

  it('still draws ordinary class definitions and in-diagram references', () => {
    expect(isRenderable('flowchart LR\n  A-->B\n  classDef ok fill:#ECECFF,stroke:#9370DB\n  class A ok\n  style B fill:url(#grad)')).toBe(true)
  })
})

describe('Mermaid still draws ordinary diagrams', () => {
  it('a normal flowchart renders, with local markers and inline styles, as the checked element', async () => {
    const svg = `<svg ${SVG_NS} id="d" data-testid="diagram" role="img" style="max-width: 200px;"><style>#d .node rect{fill:#eee;stroke:#333}</style>` +
      '<defs><marker id="d_arrow"><path d="M0,0 L10,5 z"/></marker></defs><path marker-end="url(#d_arrow)" d="M0,0 L1,1"/>' +
      '<g class="node"><rect width="10" height="10"/><text><tspan>Hello.java</tspan></text></g></svg>'
    mermaid.render.mockImplementation(async () => ({ svg }))
    await openLesson(diagramBlock('flowchart LR\n    A["Hello.java"] --> B["Hello.class"]\n'))
    const drawn = await screen.findByTestId('diagram')
    expect(drawn.namespaceURI).toBe('http://www.w3.org/2000/svg')
    expect(drawn.ownerDocument).toBe(document)
    expect(drawn.parentElement).toHaveClass('diagram')
    expect(drawn.querySelector('path[marker-end="url(#d_arrow)"]')).not.toBeNull()
    expect(drawn.querySelector('tspan')).toHaveTextContent('Hello.java')
    expect(screen.queryByText('No se pudo dibujar el diagrama')).toBeNull()
  })

  it('initialize keeps directives away from security, label and interaction settings', async () => {
    await openLesson(diagramBlock('flowchart LR\n A-->B'))
    await screen.findByTestId('diagram')
    expect(mermaid.initialize).toHaveBeenCalledWith(expect.objectContaining({
      startOnLoad: false,
      securityLevel: 'strict',
      htmlLabels: false,
      flowchart: { htmlLabels: false },
      secure: [
        'secure', 'securityLevel', 'startOnLoad', 'maxTextSize', 'suppressErrorRendering', 'maxEdges',
        'htmlLabels', 'flowchart', 'markdownAutoWrap', 'dompurifyConfig', 'themeCSS', 'fontFamily', 'altFontFamily',
        'arrowMarkerAbsolute', 'layout', 'elk', 'legacyMathML', 'forceLegacyMathML',
      ],
    }))
  })
})
