import { test, expect, devices } from "@playwright/test";
import { signIn } from "../helpers/auth";
import { TIMEOUTS } from "../helpers/production";

/**
 * PWA + mobile install experience tests.
 *
 * The landing (`/`), `/download`, and the web manifest are public routes, so
 * these run without auth. Authenticated mobile-navigation checks are skipped
 * unless E2E credentials are provided (matching the rest of the suite).
 */

test.describe("PWA — Web App Manifest", () => {
  test("serves a valid, installable manifest", async ({ request, baseURL }) => {
    const res = await request.get(`${baseURL}/manifest.webmanifest`);
    expect(res.ok()).toBeTruthy();

    const manifest = await res.json();
    expect(manifest.name).toContain("Mesha");
    expect(manifest.display).toBe("standalone");
    expect(manifest.start_url).toBe("/workspaces");
    expect(manifest.theme_color).toBeTruthy();

    // Installable: needs at least one 192px and one 512px icon, plus a maskable.
    const sizes = manifest.icons.map((i: { sizes: string }) => i.sizes);
    expect(sizes).toContain("192x192");
    expect(sizes).toContain("512x512");
    const purposes = manifest.icons.map((i: { purpose?: string }) => i.purpose);
    expect(purposes).toContain("maskable");
  });

  test("exposes manifest link, theme-color, and apple meta tags", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator('link[rel="manifest"]')).toHaveCount(1);
    await expect(page.locator('meta[name="theme-color"]').first()).toHaveCount(1);
    await expect(
      page.locator('meta[name="apple-mobile-web-app-capable"]')
    ).toHaveCount(1);
  });

  test("service worker and offline shell are reachable", async ({ request, baseURL }) => {
    const sw = await request.get(`${baseURL}/sw.js`);
    expect(sw.ok()).toBeTruthy();
    expect((await sw.text())).toContain("offline.html");

    const offline = await request.get(`${baseURL}/offline.html`);
    expect(offline.ok()).toBeTruthy();
    expect(await offline.text()).toContain("offline");

    const icon = await request.get(`${baseURL}/icons/icon-512.png`);
    expect(icon.ok()).toBeTruthy();
  });
});

test.describe("Landing + Download experience", () => {
  test("landing page shows the Download CTA and links to /download", async ({ page }) => {
    await page.goto("/");
    const cta = page.getByRole("link", { name: /download the app/i });
    await expect(cta).toBeVisible({ timeout: TIMEOUTS.navigation });
    await cta.click();
    await expect(page).toHaveURL(/\/download/);
  });

  test("download page shows install steps and current version", async ({ page }) => {
    await page.goto("/download");
    await expect(
      page.getByRole("heading", { name: /install mesha/i })
    ).toBeVisible({ timeout: TIMEOUTS.navigation });
    await expect(page.getByText(/release notes/i)).toBeVisible();
    await expect(page.getByText(/^v\d+\.\d+\.\d+/i).first()).toBeVisible();
  });
});

test.describe("Mobile responsiveness — public pages (Pixel 7)", () => {
  test.use({ ...devices["Pixel 7"] });

  for (const path of ["/", "/download"]) {
    test(`no horizontal overflow at ${path}`, async ({ page }) => {
      await page.goto(path);
      await page.waitForLoadState("domcontentloaded");
      const { scrollWidth, clientWidth } = await page.evaluate(() => ({
        scrollWidth: document.body.scrollWidth,
        clientWidth: document.body.clientWidth,
      }));
      expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
    });
  }
});

test.describe("Mobile bottom navigation (Pixel 7)", () => {
  test.use({ ...devices["Pixel 7"] });

  test("bottom nav is present with all five tabs after sign-in", async ({ page }) => {
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

    const nav = page.getByRole("navigation", { name: /primary/i });
    await expect(nav).toBeVisible({ timeout: TIMEOUTS.navigation });
    for (const label of ["Home", "Issues", "Sessions", "Projects", "Settings"]) {
      await expect(nav.getByRole("link", { name: label })).toBeVisible();
    }
  });

  test("bottom nav is hidden on desktop viewport", async ({ browser }) => {
    test.skip(
      !process.env.E2E_TEST_EMAIL,
      "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
    );
    const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
    const page = await context.newPage();
    await signIn(
      page,
      process.env.E2E_TEST_EMAIL!,
      process.env.E2E_TEST_PASSWORD!
    );
    await page.waitForURL(/workspaces/, { timeout: TIMEOUTS.navigation });
    const nav = page.getByRole("navigation", { name: /primary/i });
    await expect(nav).toBeHidden();
    await context.close();
  });
});
