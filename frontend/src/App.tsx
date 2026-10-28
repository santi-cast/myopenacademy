import { useEffect, useState } from 'react'
import './App.css'

type HealthState = 'loading' | 'healthy' | 'unavailable'

const healthMessages: Record<HealthState, string> = {
  loading: 'Comprobando conexión con el backend…',
  healthy: 'Backend disponible.',
  unavailable: 'Backend no disponible.',
}

function App() {
  const [health, setHealth] = useState<HealthState>('loading')

  useEffect(() => {
    let active = true

    async function checkHealth() {
      let nextHealth: HealthState = 'unavailable'

      try {
        const response = await fetch('/actuator/health', { method: 'GET' })
        if (response.ok) {
          const payload: unknown = await response.json()
          if (
            typeof payload === 'object'
            && payload !== null
            && !Array.isArray(payload)
            && 'status' in payload
            && payload.status === 'UP'
          ) {
            nextHealth = 'healthy'
          }
        }
      } catch {
        nextHealth = 'unavailable'
      }

      if (active) {
        setHealth(nextHealth)
      }
    }

    void checkHealth()
    return () => {
      active = false
    }
  }, [])

  return (
    <main className="technical-shell">
      <h1>Code Academy</h1>
      <p>Base técnica. Todavía no hay cursos disponibles.</p>
      <p className="health-status" role="status">{healthMessages[health]}</p>
    </main>
  )
}

export default App
