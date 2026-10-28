import { useEffect, useState } from 'react'
import { applyTheme, currentTheme, followSystemTheme, rememberTheme, type Theme } from './theme'

// The label names the action (L47): «Modo oscuro» in light, «Modo claro» in dark.
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme)

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  useEffect(() => followSystemTheme(setTheme), [])

  const next: Theme = theme === 'dark' ? 'light' : 'dark'
  const label = next === 'dark' ? 'Modo oscuro' : 'Modo claro'

  function toggle() {
    rememberTheme(next)
    applyTheme(next)
    setTheme(next)
  }

  return (
    <button type="button" className="theme-toggle" title={`Cambiar a ${label.toLowerCase()}`} onClick={toggle}>
      {next === 'dark'
        ? (
            <svg aria-hidden="true" className="icon" viewBox="0 0 24 24">
              <path fill="currentColor" d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
            </svg>
          )
        : (
            <svg aria-hidden="true" className="icon" viewBox="0 0 24 24">
              <circle cx="12" cy="12" r="4.5" fill="currentColor" />
              <g stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M4.9 19.1l1.8-1.8M17.3 6.7l1.8-1.8" />
              </g>
            </svg>
          )}
      <span className="theme-toggle-label">{label}</span>
    </button>
  )
}
