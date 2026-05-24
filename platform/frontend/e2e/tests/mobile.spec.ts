import { test, expect } from "@playwright/test";
import { TIMEOUTS } from "../helpers/production";

test.describe("Mobile emulation", () => {
  test.use({ ...require("@playwright/test").devices["iPhone 14"] });

  test("sign-in page is mobile-responsive", async ({ page }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);

    await expect(page.getByLabel(/email/i)).toBeVisible({
      timeout: TIMEOUTS.navigation,
    });

    // Ensure there is no horizontal scroll
    const scrollWidth = await page.evaluate(
      () => document.body.scrollWidth
    );
    const clientWidth = await page.evaluate(
      () => document.body.clientWidth
    );
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });
});

test.describe("Mobile emulation - Android", () => {
  test.use({ ...require("@playwright/test").devices["Pixel 7"] });

  test("sign-in page is mobile-responsive on Android", async ({ page }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);

    await expect(page.getByLabel(/email/i)).toBeVisible({
      timeout: TIMEOUTS.navigation,
    });

    const scrollWidth = await page.evaluate(
      () => document.body.scrollWidth
    );
    const clientWidth = await page.evaluate(
      () => document.body.clientWidth
    );
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });
});
