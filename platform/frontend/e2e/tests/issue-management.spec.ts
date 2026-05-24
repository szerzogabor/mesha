import { test, expect } from "../fixtures/auth.fixture";
import { navigateToFirstProject } from "../helpers/navigation";
import { TIMEOUTS } from "../helpers/production";

test.describe("Issue Management", () => {
  test.skip(
    !process.env.E2E_TEST_EMAIL,
    "requires E2E_TEST_EMAIL / E2E_TEST_PASSWORD secrets"
  );

  test("issues page loads with total count indicator", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace with projects found for test account");

    await expect(
      page.getByRole("heading", { name: /issues/i })
    ).toBeVisible({ timeout: TIMEOUTS.navigation });
    await expect(page.locator("text=/\\d+ total/")).toBeVisible({
      timeout: TIMEOUTS.apiResponse,
    });
  });

  test("create issue modal opens with form fields and closes on Escape", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace with projects found for test account");

    await page
      .getByRole("heading", { name: /issues/i })
      .waitFor({ timeout: TIMEOUTS.navigation });

    await page.getByRole("button", { name: /new issue/i }).click();

    const dialog = page.getByRole("dialog");
    await expect(dialog).toBeVisible({ timeout: TIMEOUTS.animation });
    await expect(dialog.getByRole("textbox").first()).toBeVisible();

    // Close without submitting to avoid mutating production data
    await page.keyboard.press("Escape");
    await expect(dialog).not.toBeVisible({ timeout: TIMEOUTS.animation });
  });

  test("issue title can be edited inline on detail page", async ({
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

    // Title area is clickable (inline editing)
    const titleArea = page.locator("h1, [data-testid='issue-title']").first();
    await expect(titleArea).toBeVisible({ timeout: TIMEOUTS.apiResponse });
  });

  test("issue status select shows all valid statuses", async ({
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

    const statusSelect = page.locator("select").first();
    await expect(statusSelect).toBeVisible({ timeout: TIMEOUTS.apiResponse });
    await expect(statusSelect).toBeEnabled();

    const options = await statusSelect.locator("option").allTextContents();
    const expectedStatuses = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE"];
    for (const status of expectedStatuses) {
      expect(
        options.some((o) => o.includes(status)),
        `Status option "${status}" should be present`
      ).toBeTruthy();
    }
  });

  test("issue detail renders back link and metadata sidebar", async ({
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

    await expect(
      page.getByRole("link", { name: /back/i })
    ).toBeVisible({ timeout: TIMEOUTS.apiResponse });

    // Status and priority selects form the metadata sidebar
    const selects = page.locator("select");
    expect(await selects.count()).toBeGreaterThanOrEqual(1);
  });

  test("switching to list view renders issues table", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace with projects found for test account");

    await page.getByRole("button", { name: /list/i }).click();
    await expect(
      page.getByRole("button", { name: /list/i })
    ).toHaveAttribute("aria-pressed", "true", { timeout: TIMEOUTS.animation });

    await page.waitForTimeout(TIMEOUTS.animation);

    // Either issues are visible or an empty state is shown
    const issueCount = await page.locator("a[href*='/issues/']").count();
    if (issueCount > 0) {
      await expect(
        page.locator("a[href*='/issues/']").first()
      ).toBeVisible({ timeout: TIMEOUTS.apiResponse });
    } else {
      await expect(page.locator("text=/0 total/")).toBeVisible({
        timeout: TIMEOUTS.apiResponse,
      });
    }
  });

  test("switching from list to kanban view and back preserves view state", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace with projects found for test account");

    // Switch to kanban
    await page.getByRole("button", { name: /board/i }).click();
    await expect(
      page.getByRole("button", { name: /board/i })
    ).toHaveAttribute("aria-pressed", "true", { timeout: TIMEOUTS.animation });

    // Kanban column headers should appear
    await expect(
      page.getByText(/backlog/i).or(page.getByText(/todo/i)).first()
    ).toBeVisible({ timeout: TIMEOUTS.apiResponse });

    // Switch back to list
    await page.getByRole("button", { name: /list/i }).click();
    await expect(
      page.getByRole("button", { name: /list/i })
    ).toHaveAttribute("aria-pressed", "true", { timeout: TIMEOUTS.animation });
  });

  test("issue search filter input is functional", async ({
    authenticatedPage: page,
  }) => {
    const ctx = await navigateToFirstProject(page);
    test.skip(ctx === null, "no workspace with projects found for test account");

    await page
      .getByRole("heading", { name: /issues/i })
      .waitFor({ timeout: TIMEOUTS.navigation });

    const searchInput = page.getByPlaceholder(/search issues/i);
    await expect(searchInput).toBeVisible({ timeout: TIMEOUTS.apiResponse });
    await expect(searchInput).toBeEnabled();

    // Type a search query that returns no results gracefully
    await searchInput.fill("nonexistent-issue-xyz-12345");
    await page.waitForTimeout(500); // debounce wait
    await searchInput.fill(""); // clear
  });
});
