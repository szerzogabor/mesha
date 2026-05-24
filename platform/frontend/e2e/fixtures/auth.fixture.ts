import { test as base, expect, type Page } from "@playwright/test";
import { signIn } from "../helpers/auth";
import * as fs from "fs";
import * as path from "path";

export const AUTH_STATE_PATH = path.join(__dirname, "../.auth/user.json");

type AuthFixtures = {
  authenticatedPage: Page;
};

export const test = base.extend<AuthFixtures>({
  authenticatedPage: async ({ browser }, use) => {
    const email = process.env.E2E_TEST_EMAIL;
    const password = process.env.E2E_TEST_PASSWORD;

    if (!email || !password) {
      throw new Error(
        "E2E_TEST_EMAIL and E2E_TEST_PASSWORD must be set for authenticated tests"
      );
    }

    // Reuse a previously saved auth state to avoid a full sign-in on every test.
    const hasSavedState = fs.existsSync(AUTH_STATE_PATH);
    const context = await browser.newContext({
      storageState: hasSavedState ? AUTH_STATE_PATH : undefined,
    });
    const page = await context.newPage();

    if (!hasSavedState) {
      await signIn(page, email, password);
      fs.mkdirSync(path.dirname(AUTH_STATE_PATH), { recursive: true });
      await context.storageState({ path: AUTH_STATE_PATH });
    }

    await use(page);
    await context.close();
  },
});

export { expect };
