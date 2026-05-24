import { test, expect } from "../fixtures/auth.fixture";
import { navigateToFirstProject } from "../helpers/navigation";
import { TIMEOUTS } from "../helpers/production";

test.describe("GitHub Integration", () => {
  test.skip(
    !process.env.E2E_TEST_EMAIL,
    "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
  );

  test("GitHub integration page renders without errors", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace found for test account");

    await page.goto(`/workspaces/${ctx!.workspaceId}/github`);
    await expect(page).not.toHaveURL(/sign-in/, {
      timeout: TIMEOUTS.navigation,
    });

    await page.waitForTimeout(TIMEOUTS.animation);

    // No full-page server error text visible
    await expect(
      page.getByText(/something went wrong|internal server error|500/i)
    ).not.toBeVisible();
  });

  test("Install GitHub App button is visible on the integrations page", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace found for test account");

    await page.goto(`/workspaces/${ctx!.workspaceId}/github`);
    await page.waitForTimeout(TIMEOUTS.apiResponse);

    // The install button links out to GitHub's app installation
    const installButton = page
      .getByRole("link", { name: /install/i })
      .or(page.getByRole("button", { name: /install/i }));
    await expect(installButton.first()).toBeVisible({
      timeout: TIMEOUTS.apiResponse,
    });
  });

  test("installation state fetch completes without 5xx errors", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace found for test account");

    const serverErrors: string[] = [];
    page.on("response", (response) => {
      if (
        response.status() >= 500 &&
        !response.url().includes("_next") &&
        !response.url().includes("clerk")
      ) {
        serverErrors.push(`${response.status()} ${response.url()}`);
      }
    });

    await page.goto(`/workspaces/${ctx!.workspaceId}/github`);
    await page.waitForTimeout(TIMEOUTS.apiResponse);

    expect(
      serverErrors,
      `Unexpected server errors: ${serverErrors.join(", ")}`
    ).toHaveLength(0);
  });

  test("repository list section renders with connect button or repo cards", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace found for test account");

    await page.goto(`/workspaces/${ctx!.workspaceId}/github`);
    await page.waitForTimeout(TIMEOUTS.apiResponse);

    const hasRepoCards = (await page
      .locator("a[href*='/repositories/']")
      .count()) > 0;
    const hasEmptyState = (await page
      .getByText(/no repositories/i)
      .count()) > 0;
    const hasConnectButton = (await page
      .getByRole("button", { name: /connect/i })
      .count()) > 0;

    expect(
      hasRepoCards || hasEmptyState || hasConnectButton,
      "repository section should show repo cards, empty state, or connect button"
    ).toBeTruthy();
  });

  test("GitHub integration link is present in workspace sidebar", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace found for test account");

    const githubLink = page
      .locator(`a[href*='/workspaces/${ctx!.workspaceId}/github']`)
      .first();
    await expect(githubLink).toBeVisible({ timeout: TIMEOUTS.apiResponse });
  });
});
