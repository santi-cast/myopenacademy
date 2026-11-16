import { describe, expect, it } from 'vitest'
import { DEFAULT_NAME } from '../instance/instance'
import brand from './brand.json'

// Read from disk as styles.test.ts does: the module name is a variable, so the app's TypeScript needs no Node types.
type Fs = { readFileSync: (path: string, encoding: 'utf8') => string }
const NODE_FS = 'node:fs'
const { readFileSync } = (await import(/* @vite-ignore */ NODE_FS)) as Fs

// H10: the name and the logo live in src/brand/brand.json and nowhere else.
describe('brand in one place', () => {
  it('names the academy MyOpenAcademy, with its logo file', () => {
    expect(brand.name).toBe('MyOpenAcademy')
    expect(DEFAULT_NAME).toBe(brand.name)
    expect(readFileSync(`public${brand.logo}`, 'utf8')).toContain('<svg')
  })

  it('index.html takes the title and the icon from the brand, never written there', () => {
    const html = readFileSync('index.html', 'utf8')
    expect(html).toContain('<title>%BRAND_NAME%</title>')
    expect(html).toContain('href="%BRAND_LOGO%"')
    expect(html).not.toContain('MyOpenAcademy')
    expect(html).not.toContain('Code Academy')
  })
})
