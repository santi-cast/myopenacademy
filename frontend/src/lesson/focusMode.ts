// Focus mode (L48): «Ocultar temario» is remembered in this browser only.
const STORAGE_KEY = 'ca.temario'
const HIDDEN = 'oculto'

export function storedFocusMode(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === HIDDEN
  } catch {
    return false
  }
}

export function rememberFocusMode(hidden: boolean) {
  try {
    if (hidden) window.localStorage.setItem(STORAGE_KEY, HIDDEN)
    else window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage unavailable: the choice lasts while the page is open.
  }
}
