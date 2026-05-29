import { context, trace } from "@opentelemetry/api";

export function generateCorrelationId(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback for environments without crypto.randomUUID
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export interface TraceContext {
  traceId: string | undefined;
  spanId: string | undefined;
  correlationId: string;
}

export function getActiveTraceContext(correlationId: string): TraceContext {
  const span = trace.getActiveSpan();
  const spanContext = span?.spanContext();
  return {
    traceId: spanContext?.traceId,
    spanId: spanContext?.spanId,
    correlationId,
  };
}

/** Returns W3C traceparent header value for the currently active span, if any. */
export function getTraceparent(): string | undefined {
  const span = trace.getActiveSpan();
  if (!span) return undefined;
  const sc = span.spanContext();
  if (!trace.isSpanContextValid(sc)) return undefined;
  // W3C traceparent: version-traceId-spanId-flags
  const flags = sc.traceFlags.toString(16).padStart(2, "0");
  return `00-${sc.traceId}-${sc.spanId}-${flags}`;
}

/** Injects W3C trace-context + correlation-id into a headers dict. */
export function injectTraceHeaders(
  headers: Record<string, string>,
  correlationId: string
): void {
  const traceparent = getTraceparent();
  if (traceparent) headers["traceparent"] = traceparent;
  headers["x-correlation-id"] = correlationId;
}
