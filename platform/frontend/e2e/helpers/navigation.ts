import type { Page } from "@playwright/test";
import { TIMEOUTS } from "./production";

export interface ProjectContext {
  workspaceId: string;
  projectId: string;
}

export async function navigateToFirstProject(
  page: Page
): Promise<ProjectContext | null> {
  await page.goto("/workspaces");
  await page
    .getByRole("heading", { name: /your workspaces/i })
    .waitFor({ timeout: TIMEOUTS.navigation });

  const workspaceLink = page.locator("a[href^='/workspaces/']").first();
  if ((await workspaceLink.count()) === 0) return null;
  await workspaceLink.click();

  try {
    await page.waitForURL(/workspaces\/[^/]+\/projects\/[^/]+/, {
      timeout: TIMEOUTS.navigation,
    });
  } catch {
    return null;
  }

  const match = page.url().match(/workspaces\/([^/]+)\/projects\/([^/]+)/);
  return match ? { workspaceId: match[1], projectId: match[2] } : null;
}
