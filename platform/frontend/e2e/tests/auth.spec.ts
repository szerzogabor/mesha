import { test, expect } from "@playwright/test";
import { signIn, signOut } from "../helpers/auth";
import { TIMEOUTS } from "../helpers/production";

test.describe("Authentication", () => {
  test("sign-in page renders required fields", async ({ page }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);

    await expect(page.getByLabel(/email/i)).toBeVisible({
      timeout: TIMEOUTS.navigation,
    });
    await expect(
      page.getByRole("button", { name: /continue/i })
    ).toBeVisible();
  });

  test("invalid credentials show an error", async ({ page }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);

    await page.getByLabel(/email/i).fill("invalid@example.com");
    await page.getByRole("button", { name: /continue/i }).click();

    await page.getByLabel(/password/i).fill("wrong-password");
    await page.getByRole("button", { name: /sign in|continue/i }).click();

    await expect(
      page.getByRole("alert").or(page.locator("[data-error]")).first()
    ).toBeVisible({ timeout: TIMEOUTS.apiResponse });
  });

  test.skip(
    !process.env.E2E_TEST_EMAIL,
    "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
  );

  test("valid credentials redirect to workspaces", async ({ page }) => {
    const email = process.env.E2E_TEST_EMAIL!;
    const password = process.env.E2E_TEST_PASSWORD!;

    await signIn(page, email, password);

    await expect(page).toHaveURL(/workspaces/, {
      timeout: TIMEOUTS.navigation,
    });

    await signOut(page);
  });

  test("protected route redirects unauthenticated users to sign-in", async ({
    page,
  }) => {
    await page.goto("/workspaces");
    await page.waitForURL(/sign-in/, { timeout: TIMEOUTS.navigation });
  });
});
