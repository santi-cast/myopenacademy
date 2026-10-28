// Loaded with import() from the lesson view only (S19): highlight.js core with exactly the
// approved languages. No auto-detection: callers pass only blocks whose class names one of them.
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import kotlin from 'highlight.js/lib/languages/kotlin'
import powershell from 'highlight.js/lib/languages/powershell'
import properties from 'highlight.js/lib/languages/properties'
import sql from 'highlight.js/lib/languages/sql'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'

const languages = { bash, css, java, javascript, json, kotlin, powershell, properties, sql, xml, yaml }
for (const [name, language] of Object.entries(languages)) hljs.registerLanguage(name, language)

/**
 * Highlights a <code class="language-x"> in place. highlight.js reads the element's text and
 * writes escaped markup back; it never receives an HTML string from us.
 */
export function highlightCode(code: HTMLElement) {
  if (code.dataset.highlighted) return
  hljs.highlightElement(code)
}
