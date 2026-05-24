import { test as base, expect, type Page } from "@playwright/test";
import { signIn, signOut } from "../helpers/auth";

type AuthFixtures = {
  authenticatedPage: Page;
};

export const test = base.extend<AuthFixtures>({
  authenticatedPage: async ({ page }, use) => {
    const email = process.env.E2E_TEST_EMAIL;
    const password = process.env.E2E_TEST_PASSWORD;

    if (!email || !password) {
      throw new Error(
        "E2E_TEST_EMAIL and E2E_TEST_PASSWORD must be set for authenticated tests"
      );
    }

    await signIn(page, email, password);
    await use(page);
    await signOut(page);
  },
});

export { expect };
