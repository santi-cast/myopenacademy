import { describe, expect, it } from 'vitest'

// S20: «que ningún componente tenga colores, tipografías ni espaciados escritos a mano: todo
// sale de las variables de diseño». Rule enforced here, for every src/**/*.css except the
// tokens file and for inline `style={{…}}` objects in src/**/*.tsx:
//   - no custom property definitions (`--x: …`): every design variable lives in tokens.css;
//   - no literal colors: hex, color functions (rgb, hsl, hwb, lab, lch, oklab, oklch,
//     color, color-mix) or CSS named colors; `transparent` and `currentColor` are allowed;
//   - `font-family` and `font` take only `var(--…)` or `inherit`;
//   - no lengths with a unit (px, rem, em, ch, vw, …) other than 0: sizes, spacing, radii,
//     borders and the reading measure come from tokens. Unitless numbers (line-height,
//     font-weight, z-index, flex), percentages, fr, times and angles are allowed;
//   - `var()` fallbacks are not allowed, so a literal cannot hide inside one;
//   - every `var(--x)` used must be defined in tokens.css.
// Exception: at-rule preludes (`@media (max-width: 640px)`) may hold breakpoints, because
// custom properties are not valid there; they still may not hold colors.

const TOKENS_PATH = './styles/tokens.css'

// Read from disk: Vitest serves CSS imports (even `?raw`) as empty strings. The module name is
// a variable so the app's TypeScript program does not need Node's types.
type Fs = {
  readdirSync: (path: URL, options: { recursive: true }) => string[]
  readFileSync: (path: URL, encoding: 'utf8') => string
}
type Url = { pathToFileURL: (path: string) => URL }
const [NODE_FS, NODE_URL] = ['node:fs', 'node:url']
const fs = (await import(/* @vite-ignore */ NODE_FS)) as Fs
const { pathToFileURL } = (await import(/* @vite-ignore */ NODE_URL)) as Url
// Vitest runs from frontend/ (vite.config.ts); jsdom gives import.meta.url a non-file scheme.
const SRC = pathToFileURL(`${(globalThis as unknown as { process: { cwd: () => string } }).process.cwd()}/src/`)

function sources(accept: (path: string) => boolean): Record<string, string> {
  return Object.fromEntries(
    fs.readdirSync(SRC, { recursive: true })
      .map((path) => `./${path.replaceAll('\\', '/')}`)
      .filter(accept)
      .sort()
      .map((path) => [path, fs.readFileSync(new URL(path, SRC), 'utf8')]),
  )
}

const cssFiles = sources((path) => path.endsWith('.css'))
const tsxFiles = sources((path) => path.endsWith('.tsx') && !path.endsWith('.test.tsx'))

const NAMED_COLORS = 'aliceblue antiquewhite aqua aquamarine azure beige bisque black blanchedalmond blue blueviolet brown burlywood cadetblue chartreuse chocolate coral cornflowerblue cornsilk crimson cyan darkblue darkcyan darkgoldenrod darkgray darkgreen darkgrey darkkhaki darkmagenta darkolivegreen darkorange darkorchid darkred darksalmon darkseagreen darkslateblue darkslategray darkslategrey darkturquoise darkviolet deeppink deepskyblue dimgray dimgrey dodgerblue firebrick floralwhite forestgreen fuchsia gainsboro ghostwhite gold goldenrod gray green greenyellow grey honeydew hotpink indianred indigo ivory khaki lavender lavenderblush lawngreen lemonchiffon lightblue lightcoral lightcyan lightgoldenrodyellow lightgray lightgreen lightgrey lightpink lightsalmon lightseagreen lightskyblue lightslategray lightslategrey lightsteelblue lightyellow lime limegreen linen magenta maroon mediumaquamarine mediumblue mediumorchid mediumpurple mediumseagreen mediumslateblue mediumspringgreen mediumturquoise mediumvioletred midnightblue mintcream mistyrose moccasin navajowhite navy oldlace olive olivedrab orange orangered orchid palegoldenrod palegreen paleturquoise palevioletred papayawhip peachpuff peru pink plum powderblue purple rebeccapurple red rosybrown royalblue saddlebrown salmon sandybrown seagreen seashell sienna silver skyblue slateblue slategray slategrey snow springgreen steelblue tan teal thistle tomato turquoise violet wheat white whitesmoke yellow yellowgreen'.split(' ')

const HEX = /#[0-9a-f]{3,8}\b/i
const COLOR_FUNCTION = /\b(rgba?|hsla?|hwb|lab|lch|oklab|oklch|color|color-mix)\(/i
const NAMED = new RegExp(`(^|[^\\w-])(${NAMED_COLORS.join('|')})(?![\\w-])`, 'i')
const LENGTH = /(^|[^\w.#-])-?(\d*\.?\d+)(px|rem|em|ch|ex|lh|rlh|vw|vh|vi|vb|vmin|vmax|svh|lvh|dvh|svw|lvw|dvw|cqi|cqb|cqw|cqh|pt|pc|cm|mm|in|q)(?![\w-])/i
const VAR_REF = /var\(\s*(--[\w-]+)\s*\)/g

function stripComments(css: string) {
  return css.replace(/\/\*[\s\S]*?\*\//g, '')
}

function findOffenders(css: string): string[] {
  const offenders: string[] = []
  const source = stripComments(css)

  for (const match of source.matchAll(/@[\w-]+([^{;]*)[{;]/g)) {
    const prelude = match[1]
    if (HEX.test(prelude) || COLOR_FUNCTION.test(prelude)) offenders.push(`color in at-rule: ${match[0].trim()}`)
  }

  for (const match of source.matchAll(/([-\w]+)\s*:\s*([^;{}]+?)\s*(?=;|})/g)) {
    const [, property, value] = match
    offenders.push(...checkDeclaration(property, value))
  }
  return offenders
}

function checkDeclaration(property: string, value: string): string[] {
  const where = `${property}: ${value}`
  if (property.startsWith('--')) return [`custom property defined outside tokens: ${where}`]
  if (/\bvar\([^)]*,/.test(value)) return [`var() fallback: ${where}`]
  const literal = value.replace(VAR_REF, ' ')
  const problems: string[] = []
  if ((property === 'font-family' || property === 'font') && !/^(inherit|var\(--[\w-]+\))$/.test(value.trim())) {
    problems.push(`font family not from tokens: ${where}`)
  }
  if (HEX.test(literal) || COLOR_FUNCTION.test(literal) || NAMED.test(literal)) problems.push(`literal color: ${where}`)
  const length = LENGTH.exec(literal)
  if (length && Number(length[2]) !== 0) problems.push(`literal length: ${where}`)
  return problems
}

function definedTokens(css: string) {
  return new Set([...stripComments(css).matchAll(/(--[\w-]+)\s*:/g)].map((match) => match[1]))
}

function block(css: string, selector: string) {
  const start = css.indexOf(`${selector} {`)
  if (start < 0) return ''
  return css.slice(start, css.indexOf('}', start))
}

function inlineStyles(tsx: string) {
  return [...tsx.matchAll(/style=\{\{([^}]*)\}\}/g)].map((match) => match[1])
}

describe('S20 design tokens', () => {
  const tokens = cssFiles[TOKENS_PATH]
  const componentCss = Object.entries(cssFiles).filter(([path]) => path !== TOKENS_PATH)

  it('finds the component stylesheets with their content, and the tokens file', () => {
    expect(componentCss.length).toBeGreaterThan(0)
    for (const [path, css] of componentCss) expect(css.length, path).toBeGreaterThan(0)
    expect(tokens).toBeTypeOf('string')
  })

  it('the checker reports every kind of literal (self-test of the rule)', () => {
    expect(findOffenders(`
      .a { color: #fff; background: rgb(0 0 0); border-color: white; }
      .b { font-family: Georgia, serif; padding: 4px; margin: 0.5rem 0; max-width: 68ch; }
      .c { --local: 1px; gap: var(--space-2, 8px); }
      @media (prefers-contrast: more) and (min-width: 40em) { .d { outline-color: #000 } }
    `)).toEqual([
      'literal color: color: #fff',
      'literal color: background: rgb(0 0 0)',
      'literal color: border-color: white',
      'font family not from tokens: font-family: Georgia, serif',
      'literal length: padding: 4px',
      'literal length: margin: 0.5rem 0',
      'literal length: max-width: 68ch',
      'custom property defined outside tokens: --local: 1px',
      'var() fallback: gap: var(--space-2, 8px)',
      'literal color: outline-color: #000',
    ])
    expect(findOffenders(`
      .ok { margin: 0; padding: var(--space-2) var(--space-3); width: 100%; line-height: 1.5; flex: 1;
        border: var(--border-width) solid var(--border); background: transparent; color: currentColor;
        font-family: var(--font-ui); font: inherit; z-index: 40; transition: opacity .15s;
        margin-inline: calc(-1 * var(--space-2)); grid-template-columns: minmax(0, 1fr) 2fr; }
      @media (max-width: 640px) { .ok { gap: var(--space-1) } }
    `)).toEqual([])
  })

  it('component CSS uses only design variables', () => {
    const offenders = componentCss.flatMap(([path, css]) => findOffenders(css).map((problem) => `${path}: ${problem}`))
    expect(offenders).toEqual([])
  })

  it('inline styles in components use only design variables', () => {
    const offenders = Object.entries(tsxFiles).flatMap(([path, tsx]) =>
      inlineStyles(tsx).flatMap((style) =>
        findOffenders(`.inline { ${style.replace(/(\w+)\s*:\s*([^,]+),?/g, (_all, property: string, value: string) => `${property}: ${value.replace(/['`]/g, '')};`)} }`)
          .map((problem) => `${path}: ${problem}`)),
    )
    expect(offenders).toEqual([])
  })

  it('every variable used is defined in tokens.css', () => {
    const defined = definedTokens(tokens)
    const sources = [...componentCss.map(([path, css]) => [path, css] as const), ...Object.entries(tsxFiles)]
    const undefinedRefs = sources.flatMap(([path, text]) =>
      [...text.matchAll(VAR_REF)].map((match) => match[1]).filter((name) => !defined.has(name)).map((name) => `${path}: ${name}`),
    )
    expect(undefinedRefs).toEqual([])
  })

  it('the dark theme redefines every color token of the light theme', () => {
    const source = stripComments(tokens)
    const light = block(source, ':root')
    const dark = block(source, '[data-theme="dark"]')
    const colorTokens = [...light.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)]
      .filter(([, , value]) => HEX.test(value) || COLOR_FUNCTION.test(value))
      .map(([, name]) => name)
    expect(colorTokens.length).toBeGreaterThan(20)
    expect(colorTokens.filter((name) => !definedTokens(dark).has(name))).toEqual([])
  })
})
