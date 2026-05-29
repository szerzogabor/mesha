export function generateCorrelationId(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback for environments without crypto.randomUUID
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Injects a correlation ID into a request headers dict.
 *
 * traceparent is intentionally NOT injected here — FetchInstrumentation
 * automatically propagates it for all matching requests, creating a proper
 * parent-child span hierarchy. Injecting it manually would produce sibling
 * spans instead of a correct trace tree.
 */
export function injectTraceHeaders(
  headers: Record<string, string>,
  correlationId: string
): void {
  headers["x-correlation-id"] = correlationId;
}
