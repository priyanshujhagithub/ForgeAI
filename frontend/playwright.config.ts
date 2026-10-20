import { defineConfig } from '@playwright/test'

// Browser tests run the real app against a mocked API (see e2e/mockApi.ts),
// so they need neither the Java service nor a model.
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  // On CI, each failure is also published as a GitHub annotation: readable on
  // the run's page without opening the log, and through the public API. One
  // retry there tells a flaky test (reported as flaky) from a broken one.
  reporter: process.env.CI ? [['github'], ['list']] : [['list']],
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: 'http://localhost:5180',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { browserName: 'chromium', viewport: { width: 1280, height: 800 } } },
    {
      name: 'phone',
      use: {
        browserName: 'chromium',
        viewport: { width: 390, height: 844 },
        deviceScaleFactor: 2,
        hasTouch: true,
        isMobile: true,
      },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5180',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})
