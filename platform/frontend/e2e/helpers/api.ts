import type { Page, Route } from "@playwright/test";

type JsonBody = Record<string, unknown> | unknown[];

export async function mockApiResponse(
  page: Page,
  urlPattern: string | RegExp,
  response: JsonBody,
  options: { status?: number; delay?: number } = {}
): Promise<void> {
  const { status = 200, delay = 0 } = options;

  await page.route(urlPattern, async (route: Route) => {
    if (delay > 0) {
      await new Promise((r) => setTimeout(r, delay));
    }
    await route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(response),
    });
  });
}

export async function mockApiError(
  page: Page,
  urlPattern: string | RegExp,
  status: number,
  message: string
): Promise<void> {
  await page.route(urlPattern, (route: Route) =>
    route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify({ message }),
    })
  );
}

export async function interceptApiCall(
  page: Page,
  urlPattern: string | RegExp
): Promise<{ request: unknown; response: unknown }> {
  const response = await page.waitForResponse((r) =>
    typeof urlPattern === "string"
      ? r.url().includes(urlPattern)
      : urlPattern.test(r.url())
  );
  const request = response.request();
  return {
    request: {
      url: request.url(),
      method: request.method(),
      headers: request.headers(),
    },
    response: {
      status: response.status(),
      body: await response.json().catch(() => null),
    },
  };
}

export function apiUrl(path: string): string {
  const base = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  return `${base}${path}`;
}
