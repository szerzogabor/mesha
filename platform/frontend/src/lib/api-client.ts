const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

async function request<T>(
  path: string,
  init?: RequestInit,
  token?: string | null
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string>),
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const res = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  if (!res.ok) {
    throw new Error(`API error ${res.status}: ${res.statusText}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export function createApiClient(token: string | null) {
  return {
    get: <T>(path: string) => request<T>(path, undefined, token),
    post: <T>(path: string, body: unknown) =>
      request<T>(path, { method: "POST", body: JSON.stringify(body) }, token),
    patch: <T>(path: string, body: unknown) =>
      request<T>(path, { method: "PATCH", body: JSON.stringify(body) }, token),
    delete: (path: string) =>
      request<void>(path, { method: "DELETE" }, token),
  };
}

// Unauthenticated client kept for backwards compatibility
export const apiClient = createApiClient(null);
