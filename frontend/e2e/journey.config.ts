import { resolve } from 'node:path'
import { defineConfig, devices } from '@playwright/test'

// Started by scripts/e2e-journey.mjs (npm run test:e2e:journey), which owns the disposable
// database and backend and passes their loopback addresses here.
const frontendUrl = process.env.E2E_FRONTEND_URL
const backendUrl = process.env.E2E_BACKEND_URL
const runDir = process.env.E2E_RUN_DIR
if (!frontendUrl || !backendUrl || !runDir) throw new Error('Run the journey with: npm run test:e2e:journey')

export default defineConfig({
  testDir: './journey',
  outputDir: resolve(runDir, 'playwright'),
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 300_000,
  expect: { timeout: 15_000 },
  reporter: 'list',
  use: {
    baseURL: frontendUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${new URL(frontendUrl).port} --strictPort`,
    cwd: resolve(import.meta.dirname, '..'),
    url: frontendUrl,
    env: { CODE_ACADEMY_BACKEND_URL: backendUrl },
    reuseExistingServer: false,
    timeout: 60_000,
  },
})
