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

  const res = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });

  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new Error(`API error ${res.status}: ${text}`);
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
