import * as Sentry from "@sentry/nextjs";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

let _tokenGetter: (() => Promise<string | null>) | null = null;

export function setTokenGetter(getter: () => Promise<string | null>) {
  _tokenGetter = getter;
}

async function getAuthToken(): Promise<string | null> {
  if (typeof window === "undefined") return null;
  if (_tokenGetter) return _tokenGetter();
  return null;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await getAuthToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const method = init?.method ?? "GET";

  const res = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });

  if (!res.ok) {
    let errorBody: string;
    try {
      const json = await res.json();
      errorBody = json?.message ?? json?.error ?? JSON.stringify(json);
    } catch {
      errorBody = await res.text().catch(() => res.statusText);
    }
    const errorMessage = `API error ${res.status}: ${errorBody}`;
    const error = new Error(errorMessage);

    // Auth failures get tagged differently for filtering
    if (res.status === 401 || res.status === 403) {
      Sentry.logger.warn(`Auth failure: ${method} ${path} → ${res.status}`, {
        url: path,
        method,
        status: res.status,
      });
    } else {
      Sentry.logger.error(errorMessage, { url: path, method, status: res.status });
      Sentry.captureException(error, {
        tags: { "api.path": path, "api.method": method, "api.status": res.status },
      });
    }

    throw error;
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: "DELETE" }),
};
