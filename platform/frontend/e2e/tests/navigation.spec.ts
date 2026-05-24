import { test, expect } from "../fixtures/auth.fixture";
import { navigateToFirstProject } from "../helpers/navigation";
import { TIMEOUTS } from "../helpers/production";

test.describe("Navigation Flows", () => {
  test.skip(
    !process.env.E2E_TEST_EMAIL,
    "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
  );

  test("workspaces dashboard loads with heading", async ({
    authenticatedPage: page,
  }) => {
    await page.goto("/workspaces");
    await expect(
      page.getByRole("heading", { name: /your workspaces/i })
    ).toBeVisible({ timeout: TIMEOUTS.navigation });
  });

  test("workspace page loads after selecting a workspace", async ({
    authenticatedPage: page,
  }) => {
    await page.goto("/workspaces");
    await page
      .getByRole("heading", { name: /your workspaces/i })
      .waitFor({ timeout: TIMEOUTS.navigation });

    const workspaceLinks = page.locator("a[href^='/workspaces/']");
    test.skip(
      (await workspaceLinks.count()) === 0,
      "no workspaces found for test account"
    );

    await workspaceLinks.first().click();
    await page.waitForURL(/workspaces\/[^/]+/, {
      timeout: TIMEOUTS.navigation,
    });
    expect(page.url()).toMatch(/\/workspaces\//);
  });

  test("project issues page loads after workspace navigation", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace with projects found for test account");

    await expect(
      page.getByRole("heading", { name: /issues/i })
    ).toBeVisible({ timeout: TIMEOUTS.navigation });
  });

  test("issue detail page loads after clicking an issue", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace with projects found for test account");

    await page
      .getByRole("heading", { name: /issues/i })
      .waitFor({ timeout: TIMEOUTS.navigation });

    const issueLink = page.locator("a[href*='/issues/']").first();
    test.skip(
      (await issueLink.count()) === 0,
      "no issues found in the test project"
    );

    await issueLink.click();
    await page.waitForURL(/\/issues\/[^/]+/, { timeout: TIMEOUTS.navigation });
    expect(page.url()).toMatch(/\/issues\//);
  });

  test("GitHub integration page loads without redirecting to sign-in", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace found for test account");

    await page.goto(`/workspaces/${ctx!.workspaceId}/github`);
    await expect(page).not.toHaveURL(/sign-in/, {
      timeout: TIMEOUTS.navigation,
    });
    await expect(page).toHaveURL(/\/github/, { timeout: TIMEOUTS.navigation });
  });

  test("browser back navigation returns to previous page", async ({
    authenticatedPage: page,
  }) => {
    await page.goto("/workspaces");
    await page
      .getByRole("heading", { name: /your workspaces/i })
      .waitFor({ timeout: TIMEOUTS.navigation });

    const workspaceLinks = page.locator("a[href^='/workspaces/']");
    test.skip(
      (await workspaceLinks.count()) === 0,
      "no workspaces found for test account"
    );

    await workspaceLinks.first().click();
    await page.waitForURL(/workspaces\/[^/]+/, {
      timeout: TIMEOUTS.navigation,
    });

    await page.goBack();
    await expect(page).toHaveURL(/\/workspaces$/, {
      timeout: TIMEOUTS.navigation,
    });
    await expect(
      page.getByRole("heading", { name: /your workspaces/i })
    ).toBeVisible({ timeout: TIMEOUTS.navigation });
  });
});
