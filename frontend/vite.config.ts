import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Backend behind the dev proxy. The journey E2E points it at its own disposable backend; the
// value is read here in Node only and never reaches the client bundle (no VITE_ prefix).
const backendUrl = process.env.CODE_ACADEMY_BACKEND_URL ?? 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react()],
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
