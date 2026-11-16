import { useEffect, useState } from 'react'
import { apiGet } from '../api/client'
import type { Instance } from '../api/types'
import brand from '../brand/brand.json'

/** The name the academy shows when its administrator has not set one: the brand's, from src/brand/brand.json (H10). */
export const DEFAULT_NAME = brand.name

let cached: Promise<Instance> | null = null
const listeners = new Set<(instance: Instance) => void>()

/** The public view of this academy (H9), read once and shared; a change from the panel refreshes it. */
export function loadInstance(refresh = false): Promise<Instance> {
  if (!cached || refresh) {
    cached = apiGet<Instance>('/api/instance').catch(() => ({ name: null, registrationOpen: true }))
    void cached.then((instance) => listeners.forEach((listener) => listener(instance)))
  }
  return cached
}

export function useInstance(): Instance | null {
  const [instance, setInstance] = useState<Instance | null>(null)
  useEffect(() => {
    let active = true
    listeners.add(setInstance)
    void loadInstance().then((value) => { if (active) setInstance(value) })
    return () => { active = false; listeners.delete(setInstance) }
  }, [])
  return instance
}

export function instanceName(instance: Instance | null): string {
  return instance?.name ?? DEFAULT_NAME
}

/** For the tests: forget the cached view, so each test reads its own. */
export function forgetInstance() {
  cached = null
}
