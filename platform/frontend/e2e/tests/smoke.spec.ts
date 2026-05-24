import { test, expect } from "@playwright/test";
import { TIMEOUTS } from "../helpers/production";

test.describe("Smoke", () => {
  test("sign-in page loads", async ({ page }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);
    await expect(page).toHaveTitle(/mesha/i, { timeout: TIMEOUTS.navigation });
    await expect(page.getByLabel(/email/i)).toBeVisible();
  });

  test("root redirects to sign-in or workspaces", async ({ page }) => {
    await page.goto("/");
    await page.waitForURL(
      (url) =>
        url.pathname.startsWith("/sign-in") ||
        url.pathname.startsWith("/workspaces"),
      { timeout: TIMEOUTS.navigation }
    );
  });

  test("404 page renders", async ({ page }) => {
    const response = await page.goto("/this-route-does-not-exist");
    expect(response?.status()).toBe(404);
  });
});
