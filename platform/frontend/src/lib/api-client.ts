import * as Sentry from "@sentry/nextjs";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

function getAuthToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("mesha_auth_token");
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getAuthToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const method = init?.method ?? "GET";

  return Sentry.startSpan(
    { name: `${method} ${path}`, op: "http.client", attributes: { "http.method": method, "http.url": path } },
    async () => {
      const res = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });

      if (!res.ok) {
        const text = await res.text().catch(() => res.statusText);
        const errorMessage = `API error ${res.status}: ${text}`;
        const error = new Error(errorMessage);

        // Auth failures get tagged differently for filtering
        if (res.status === 401 || res.status === 403) {
          Sentry.addBreadcrumb({
            type: "http",
            level: "warning",
            category: "auth",
            message: `Auth failure: ${method} ${path} → ${res.status}`,
            data: { url: path, method, status: res.status },
          });
        } else {
          Sentry.captureException(error, {
            tags: { "api.path": path, "api.method": method, "api.status": res.status },
          });
        }

        Sentry.addBreadcrumb({
          type: "http",
          level: "error",
          category: "fetch",
          message: errorMessage,
          data: { url: path, method, status: res.status },
        });

        throw error;
      }

      if (res.status === 204) return undefined as T;
      return res.json() as Promise<T>;
    }
  );
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: "DELETE" }),
};
