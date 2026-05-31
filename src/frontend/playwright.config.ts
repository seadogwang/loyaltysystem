import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 3,
  reporter: [['html', { outputFolder: 'playwright-report' }], ['list']],
  timeout: 30000,
  use: {
    baseURL: 'http://localhost:6173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  ],
  webServer: {
    command: 'npm run dev -- --port 6173 --strictPort',
    url: 'http://localhost:6173',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
});