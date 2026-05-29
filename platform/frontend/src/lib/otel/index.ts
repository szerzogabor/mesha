import { initTracer, getTracer } from "./tracer";
import { initLogProvider, getOtelLogger, SeverityNumber } from "./log-provider";
import { generateCorrelationId, injectTraceHeaders, getActiveTraceContext } from "./correlation";
import { otelConfig } from "./config";

export { getTracer, getOtelLogger, SeverityNumber };
export { generateCorrelationId, injectTraceHeaders, getActiveTraceContext };
export { otelConfig };

let _initialized = false;

/** Bootstrap OpenTelemetry. Safe to call multiple times — initialises once. */
export function initOtel(): void {
  if (typeof window === "undefined") return; // server/edge: skip
  if (_initialized) return;
  _initialized = true;

  initTracer();
  initLogProvider();
}
