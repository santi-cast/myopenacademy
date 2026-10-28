import { useEffect, useState } from 'react'

export type Loaded<T> = { status: 'loading' } | { status: 'ready'; data: T } | { status: 'failed'; error: unknown }

const LOADING = { status: 'loading' } as const

/**
 * Loads `load(key)` again whenever `key` changes; results arriving after unmount or for an
 * older key are dropped. Pass a module-level `load` so it stays stable.
 */
export function useLoad<T>(key: string, load: (key: string) => Promise<T>): [Loaded<T>, (data: T) => void] {
  const [result, setResult] = useState<{ key: string; loaded: Loaded<T> } | null>(null)

  useEffect(() => {
    let active = true
    load(key).then(
      (data) => {
        if (active) setResult({ key, loaded: { status: 'ready', data } })
      },
      (error: unknown) => {
        if (active) setResult({ key, loaded: { status: 'failed', error } })
      },
    )
    return () => {
      active = false
    }
  }, [key, load])

  const loaded: Loaded<T> = result?.key === key ? result.loaded : LOADING
  return [loaded, (data: T) => setResult({ key, loaded: { status: 'ready', data } })]
}
