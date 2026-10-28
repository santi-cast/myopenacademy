// Plain-text copy (L48): the Clipboard API where the page may use it, else a hidden textarea and
// execCommand('copy'), which also works over plain HTTP on another host.

function fallbackCopy(text: string): boolean {
  const buffer = document.createElement('textarea')
  buffer.value = text
  buffer.readOnly = true
  buffer.className = 'clipboard-buffer'
  const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
  document.body.appendChild(buffer)
  buffer.focus()
  buffer.select()
  let copied = false
  try {
    copied = document.execCommand('copy')
  } catch {
    copied = false
  }
  buffer.remove()
  previous?.focus()
  return copied
}

export async function copyText(text: string): Promise<boolean> {
  const clipboard = typeof navigator === 'undefined' ? undefined : navigator.clipboard
  if (clipboard && window.isSecureContext) {
    try {
      await clipboard.writeText(text)
      return true
    } catch {
      // Permission denied or unavailable: try the fallback.
    }
  }
  return fallbackCopy(text)
}
