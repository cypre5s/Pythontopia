import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  use: {
    channel: process.env.PLAYWRIGHT_CHANNEL,
    baseURL: process.env.E2E_URL ?? "http://127.0.0.1:15174",
    trace: "retain-on-failure",
    actionTimeout: 15000,
    navigationTimeout: 20000,
  },
  projects: [
    {
      name: "desktop",
      use: {
        browserName: "chromium",
        viewport: { width: 1920, height: 1080 },
        isMobile: false,
        hasTouch: false,
      },
    },
  ],
});
