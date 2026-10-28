import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './styles/tokens.css'
import './index.css'
import App from './App.tsx'
import { applyTheme, currentTheme } from './theme/theme'

// Before the first paint, so the page never flashes the other theme.
applyTheme(currentTheme())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
