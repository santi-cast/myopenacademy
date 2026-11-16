import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { forgetInstance } from '../instance/instance'

// Under a loaded machine the default 1 s wait makes async queries flaky; behaviour is unchanged.
configure({ asyncUtilTimeout: 5000 })

// jsdom has no layout and reports window.scrollTo as not implemented; the lesson view calls it.
window.scrollTo = () => {}

afterEach(() => {
  cleanup()
  forgetInstance()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  for (const part of document.cookie.split(';')) {
    const name = part.split('=')[0].trim()
    if (name) document.cookie = `${name}=; path=/; max-age=0`
  }
  window.localStorage.clear()
  delete document.documentElement.dataset.theme
})
