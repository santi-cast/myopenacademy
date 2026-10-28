import { act, render } from '@testing-library/react'
import { useEffect } from 'react'
import { MemoryRouter, useNavigate, type NavigateFunction } from 'react-router'
import App from '../App'

/** Renders the whole app at `path`; `navigate` drives history like the browser buttons. */
export function renderApp(path = '/') {
  const holder: { navigate: NavigateFunction | null } = { navigate: null }
  function NavigateProbe() {
    const navigate = useNavigate()
    useEffect(() => {
      holder.navigate = navigate
    }, [navigate])
    return null
  }
  const utils = render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <NavigateProbe />
    </MemoryRouter>,
  )
  return {
    ...utils,
    navigate: async (to: string | number) => {
      await act(async () => {
        if (typeof to === 'number') await holder.navigate?.(to)
        else await holder.navigate?.(to)
      })
    },
  }
}
