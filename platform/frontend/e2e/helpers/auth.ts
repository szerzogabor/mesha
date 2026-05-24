import type { Page } from "@playwright/test";

export async function signIn(
  page: Page,
  email: string,
  password: string
): Promise<void> {
  const signInUrl = process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
  await page.goto(signInUrl);

  await page.getByLabel(/email/i).fill(email);
  await page.getByRole("button", { name: /continue/i }).click();

  await page.getByLabel(/password/i).fill(password);
  await page.getByRole("button", { name: /sign in|continue/i }).click();

  // Wait for redirect after successful sign-in
  const fallbackUrl =
    process.env.NEXT_PUBLIC_CLERK_SIGN_IN_FALLBACK_REDIRECT_URL ||
    "/workspaces";
  await page.waitForURL(
    (url) =>
      !url.pathname.startsWith("/sign-in") &&
      url.pathname !== "/" &&
      url.pathname.startsWith(fallbackUrl.split("/")[1] ? `/${fallbackUrl.split("/")[1]}` : "/"),
    { timeout: 15_000 }
  );
}

export async function signOut(page: Page): Promise<void> {
  // Clerk renders a user button — click it then hit sign-out
  const userButton = page.locator('[data-testid="clerk-user-button-trigger"], button[aria-label*="Open user"]').first();
  if (await userButton.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await userButton.click();
    const signOutButton = page.getByRole("menuitem", { name: /sign out/i });
    if (await signOutButton.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await signOutButton.click();
      await page.waitForURL(/sign-in/, { timeout: 10_000 });
    }
  }
}

export async function isAuthenticated(page: Page): Promise<boolean> {
  const signInUrl =
    process.env.NEXT_PUBLIC_CLERK_SIGN_IN_URL || "/sign-in";
  return !page.url().includes(signInUrl);
}
