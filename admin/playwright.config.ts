import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  timeout: 90000,
  expect: { timeout: 15000 },
  fullyParallel: false,
  workers: 1,
  reporter: "list",
  use: {
    baseURL: process.env.ADMIN_E2E_URL ?? "http://127.0.0.1:15234",
    viewport: { width: 1920, height: 1080 },
    browserName: "chromium",
    channel: process.env.PLAYWRIGHT_CHANNEL ?? "chromium",
    trace: "off",
    screenshot: "only-on-failure",
    actionTimeout: 15000,
  },
  outputDir: "test-results",
});
