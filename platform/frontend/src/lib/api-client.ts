import { logger } from "@/lib/logger";
import { generateCorrelationId, injectTraceHeaders } from "@/lib/otel/correlation";

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
  const correlationId = generateCorrelationId();

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(init?.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  // Inject W3C traceparent + correlation ID for frontend/backend trace correlation
  injectTraceHeaders(headers, correlationId);

  const method = init?.method ?? "GET";
  const startMs = Date.now();

  logger.api.request(method, path, { correlationId });

  const res = await fetch(`${API_BASE_URL}${path}`, { ...init, headers });
  const durationMs = Date.now() - startMs;

  if (!res.ok) {
    let errorBody: string;
    try {
      const json = await res.json();
      errorBody = json?.message ?? json?.error ?? json?.detail ?? JSON.stringify(json);
    } catch {
      errorBody = await res.text().catch(() => res.statusText);
    }
    const errorMessage = `API error ${res.status}: ${errorBody}`;
    const error = new Error(errorMessage) as Error & { status: number };
    error.status = res.status;

    if (res.status === 401 || res.status === 403) {
      logger.api.authFailure(path, res.status, method);
    } else {
      logger.api.failure(path, res.status, errorBody, method);
    }

    throw error;
  }

  logger.api.response(method, path, res.status, durationMs);

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

async function requestMultipart<T>(path: string, formData: FormData): Promise<T> {
  const token = await getAuthToken();
  const correlationId = generateCorrelationId();

  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;
  injectTraceHeaders(headers, correlationId);

  const startMs = Date.now();
  logger.api.request("POST", path, { correlationId });

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers,
    body: formData,
  });
  const durationMs = Date.now() - startMs;

  if (!res.ok) {
    let errorBody: string;
    try {
      const json = await res.json();
      errorBody = json?.message ?? json?.error ?? json?.detail ?? JSON.stringify(json);
    } catch {
      errorBody = await res.text().catch(() => res.statusText);
    }
    const error = new Error(`API error ${res.status}: ${errorBody}`) as Error & { status: number };
    error.status = res.status;
    logger.api.failure(path, res.status, errorBody, "POST");
    throw error;
  }

  logger.api.response("POST", path, res.status, durationMs);
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "PUT", body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: "DELETE" }),
  upload: <T>(path: string, formData: FormData) => requestMultipart<T>(path, formData),
};
