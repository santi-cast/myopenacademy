import react from '@vitejs/plugin-react'
import brand from './src/brand/brand.json' with { type: 'json' }
import { defineConfig } from 'vitest/config'

// Backend behind the dev proxy. The journey E2E points it at its own disposable backend; the
// value is read here in Node only and never reaches the client bundle (no VITE_ prefix).
const backendUrl = process.env.CODE_ACADEMY_BACKEND_URL ?? `http://127.0.0.1:${process.env.APP_PORT ?? '8080'}`

export default defineConfig({
  plugins: [
    react(),
    // The name and the logo in one place (H10): src/brand/brand.json fills the title and the icon of the page.
    { name: 'brand', transformIndexHtml: (html) => html.replaceAll('%BRAND_NAME%', brand.name).replaceAll('%BRAND_LOGO%', brand.logo) },
  ],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': backendUrl,
      '^/actuator/health$': backendUrl,
    },
  },
  preview: {
    host: '127.0.0.1',
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
