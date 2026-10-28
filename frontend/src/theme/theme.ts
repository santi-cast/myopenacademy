export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'ca.theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

function isTheme(value: unknown): value is Theme {
  return value === 'light' || value === 'dark'
}

export function storedTheme(): Theme | null {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY)
    return isTheme(value) ? value : null
  } catch {
    return null
  }
}

function darkQuery(): MediaQueryList | null {
  try {
    return typeof window.matchMedia === 'function' ? window.matchMedia(DARK_QUERY) : null
  } catch {
    return null
  }
}

/** The applied theme, else the remembered choice, else the system preference. */
export function currentTheme(): Theme {
  const applied = document.documentElement.dataset.theme
  if (isTheme(applied)) return applied
  return storedTheme() ?? (darkQuery()?.matches ? 'dark' : 'light')
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
}

export function rememberTheme(theme: Theme) {
  try {
    window.localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage unavailable: the choice lasts for this page only.
  }
}

/** Follows system changes until the user chooses; returns the unsubscribe function. */
export function followSystemTheme(onChange: (theme: Theme) => void): () => void {
  const query = darkQuery()
  if (!query || typeof query.addEventListener !== 'function') return () => {}
  const listener = (event: MediaQueryListEvent) => {
    if (storedTheme() === null) onChange(event.matches ? 'dark' : 'light')
  }
  query.addEventListener('change', listener)
  return () => query.removeEventListener('change', listener)
}
