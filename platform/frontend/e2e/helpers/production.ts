/**
 * Production-safe test helpers.
 *
 * All operations here are read-only or clearly scoped to test data so they
 * are safe to run against the production environment. Never delete, update,
 * or mutate shared resources from E2E tests.
 */

export const TEST_USER = {
  email: process.env.E2E_TEST_EMAIL ?? "",
  password: process.env.E2E_TEST_PASSWORD ?? "",
};

export const TIMEOUTS = {
  navigation: 15_000,
  animation: 500,
  apiResponse: 10_000,
} as const;

export function assertTestEnv(): void {
  if (!process.env.E2E_TEST_EMAIL || !process.env.E2E_TEST_PASSWORD) {
    throw new Error(
      "E2E_TEST_EMAIL and E2E_TEST_PASSWORD environment variables are required"
    );
  }
}

export function getBaseUrl(): string {
  return (
    process.env.PLAYWRIGHT_BASE_URL ||
    process.env.VERCEL_PREVIEW_URL ||
    "http://localhost:3000"
  );
}

export function isProductionRun(): boolean {
  return process.env.E2E_TEST_ENV === "production";
}

export function isPreviewRun(): boolean {
  return process.env.E2E_TEST_ENV === "preview";
}
