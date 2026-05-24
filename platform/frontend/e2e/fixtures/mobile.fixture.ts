import { test as base, devices } from "@playwright/test";

export const mobileTest = base.extend({
  viewport: [
    async ({}, use) => {
      await use(devices["iPhone 14"].viewport);
    },
  ],
  userAgent: [
    async ({}, use) => {
      await use(devices["iPhone 14"].userAgent);
    },
  ],
});
