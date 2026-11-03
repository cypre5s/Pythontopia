import { defineConfig } from "@playwright/test";

const responsiveSpecs = ["**/rebornJourney.spec.ts", "**/uiUsability.spec.ts"];

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
    { name: "desktop", use: { viewport: { width: 1440, height: 900 } } },
    {
      name: "laptop",
      testMatch: responsiveSpecs,
      use: { viewport: { width: 1024, height: 640 } },
    },
    {
      name: "phone-landscape",
      testMatch: responsiveSpecs,
      use: {
        viewport: { width: 844, height: 390 },
        isMobile: true,
        hasTouch: true,
      },
    },
    {
      name: "ultrawide",
      testMatch: "**/uiUsability.spec.ts",
      use: { viewport: { width: 2560, height: 1080 } },
    },
  ],
});
