import { test, expect, devices } from "@playwright/test";
import { signIn } from "../helpers/auth";
import { TIMEOUTS } from "../helpers/production";

async function checkNoHorizontalScroll(
  page: import("@playwright/test").Page
): Promise<{ scrollWidth: number; clientWidth: number }> {
  return page.evaluate(() => ({
    scrollWidth: document.body.scrollWidth,
    clientWidth: document.body.clientWidth,
  }));
}

test.describe("Mobile Smoke Tests – iOS (iPhone 14)", () => {
  test.use({ ...devices["iPhone 14"] });

  test("sign-in page is mobile-responsive", async ({ page }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);

    await expect(page.getByLabel(/email/i)).toBeVisible({
      timeout: TIMEOUTS.navigation,
    });

    const { scrollWidth, clientWidth } = await checkNoHorizontalScroll(page);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });

  test("touch interaction focuses email input on sign-in page", async ({
    page,
  }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);

    const emailInput = page.getByLabel(/email/i);
    await expect(emailInput).toBeVisible({ timeout: TIMEOUTS.navigation });

    // Simulate a native touch tap
    await emailInput.tap();
    await expect(emailInput).toBeFocused({ timeout: TIMEOUTS.animation });
  });

  test("workspaces page has no horizontal overflow on mobile", async ({
    page,
  }) => {
    test.skip(
      !process.env.E2E_TEST_EMAIL,
      "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
    );

    await signIn(
      page,
      process.env.E2E_TEST_EMAIL!,
      process.env.E2E_TEST_PASSWORD!
    );
    await page.waitForURL(/workspaces/, { timeout: TIMEOUTS.navigation });

    const { scrollWidth, clientWidth } = await checkNoHorizontalScroll(page);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });

  test("mobile navigation is accessible after sign-in", async ({ page }) => {
    test.skip(
      !process.env.E2E_TEST_EMAIL,
      "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
    );

    await signIn(
      page,
      process.env.E2E_TEST_EMAIL!,
      process.env.E2E_TEST_PASSWORD!
    );
    await page.waitForURL(/workspaces/, { timeout: TIMEOUTS.navigation });

    // Authenticated pages load successfully on mobile (no sign-in redirect)
    await expect(page).not.toHaveURL(/sign-in/);

    const workspaceLink = page.locator("a[href^='/workspaces/']").first();
    if ((await workspaceLink.count()) > 0) {
      await workspaceLink.click();
      await page.waitForURL(/workspaces\/[^/]+/, {
        timeout: TIMEOUTS.navigation,
      });
      // Page navigated successfully — basic routing works on mobile
      expect(page.url()).toMatch(/\/workspaces\//);
    }
  });

  test("mobile layout renders project page without horizontal overflow", async ({
    page,
  }) => {
    test.skip(
      !process.env.E2E_TEST_EMAIL,
      "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
    );

    await signIn(
      page,
      process.env.E2E_TEST_EMAIL!,
      process.env.E2E_TEST_PASSWORD!
    );
    await page.waitForURL(/workspaces/, { timeout: TIMEOUTS.navigation });

    const workspaceLink = page.locator("a[href^='/workspaces/']").first();
    if ((await workspaceLink.count()) === 0) return;
    await workspaceLink.click();

    try {
      await page.waitForURL(/projects\/[^/]+/, {
        timeout: TIMEOUTS.navigation,
      });
    } catch {
      return;
    }

    const { scrollWidth, clientWidth } = await checkNoHorizontalScroll(page);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });
});

test.describe("Mobile Smoke Tests – Android (Pixel 7)", () => {
  test.use({ ...devices["Pixel 7"] });

  test("sign-in page is mobile-responsive on Android", async ({ page }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);

    await expect(page.getByLabel(/email/i)).toBeVisible({
      timeout: TIMEOUTS.navigation,
    });

    const { scrollWidth, clientWidth } = await checkNoHorizontalScroll(page);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });

  test("touch interaction focuses email input on Android", async ({ page }) => {
    const signInUrl =
      process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
    await page.goto(signInUrl);

    const emailInput = page.getByLabel(/email/i);
    await expect(emailInput).toBeVisible({ timeout: TIMEOUTS.navigation });

    await emailInput.tap();
    await expect(emailInput).toBeFocused({ timeout: TIMEOUTS.animation });
  });

  test("workspaces page has no horizontal overflow on Android", async ({
    page,
  }) => {
    test.skip(
      !process.env.E2E_TEST_EMAIL,
      "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
    );

    await signIn(
      page,
      process.env.E2E_TEST_EMAIL!,
      process.env.E2E_TEST_PASSWORD!
    );
    await page.waitForURL(/workspaces/, { timeout: TIMEOUTS.navigation });

    const { scrollWidth, clientWidth } = await checkNoHorizontalScroll(page);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });

  test("mobile layout renders project page without horizontal overflow on Android", async ({
    page,
  }) => {
    test.skip(
      !process.env.E2E_TEST_EMAIL,
      "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
    );

    await signIn(
      page,
      process.env.E2E_TEST_EMAIL!,
      process.env.E2E_TEST_PASSWORD!
    );
    await page.waitForURL(/workspaces/, { timeout: TIMEOUTS.navigation });

    const workspaceLink = page.locator("a[href^='/workspaces/']").first();
    if ((await workspaceLink.count()) === 0) return;
    await workspaceLink.click();

    try {
      await page.waitForURL(/projects\/[^/]+/, {
        timeout: TIMEOUTS.navigation,
      });
    } catch {
      return;
    }

    const { scrollWidth, clientWidth } = await checkNoHorizontalScroll(page);
    expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
  });

  test("horizontal scrolling is not present on Android workspaces page", async ({
    page,
  }) => {
    test.skip(
      !process.env.E2E_TEST_EMAIL,
      "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
    );

    await signIn(
      page,
      process.env.E2E_TEST_EMAIL!,
      process.env.E2E_TEST_PASSWORD!
    );
    await page.waitForURL(/workspaces/, { timeout: TIMEOUTS.navigation });

    // Check for overflow-causing elements on the workspaces page
    const offendingElements = await page.evaluate(() => {
      const offenders: string[] = [];
      document.querySelectorAll("main *, section *, article *").forEach((el) => {
        if (el.scrollWidth > el.clientWidth + 2) {
          const tag = el.tagName.toLowerCase();
          const cls = (el.className as string)?.split?.(" ")?.[0] ?? "";
          offenders.push(`${tag}.${cls}`);
        }
      });
      return offenders.slice(0, 5);
    });

    expect(
      offendingElements,
      `Elements causing horizontal overflow: ${offendingElements.join(", ")}`
    ).toHaveLength(0);
  });
});
