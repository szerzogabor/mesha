import { WebTracerProvider } from "@opentelemetry/sdk-trace-web";
import {
  BatchSpanProcessor,
  ParentBasedSampler,
  TraceIdRatioBasedSampler,
} from "@opentelemetry/sdk-trace-base";
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http";
import { Resource } from "@opentelemetry/resources";
import {
  ATTR_SERVICE_NAME,
  ATTR_SERVICE_VERSION,
} from "@opentelemetry/semantic-conventions";
import { W3CTraceContextPropagator } from "@opentelemetry/core";
import { registerInstrumentations } from "@opentelemetry/instrumentation";
import { DocumentLoadInstrumentation } from "@opentelemetry/instrumentation-document-load";
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch";
import { UserInteractionInstrumentation } from "@opentelemetry/instrumentation-user-interaction";
import { trace } from "@opentelemetry/api";
import { otelConfig } from "./config";

let _provider: WebTracerProvider | null = null;

export function initTracer(): WebTracerProvider | null {
  if (!otelConfig.enabled) return null;
  if (_provider) return _provider;

  const resource = new Resource({
    [ATTR_SERVICE_NAME]: otelConfig.serviceName,
    [ATTR_SERVICE_VERSION]: otelConfig.serviceVersion,
    "deployment.environment": otelConfig.environment,
  });

  const exporter = new OTLPTraceExporter({
    url: `${otelConfig.otlpEndpoint}/v1/traces`,
    headers: otelConfig.authorizationHeader
      ? { Authorization: otelConfig.authorizationHeader }
      : undefined,
  });

  _provider = new WebTracerProvider({
    resource,
    sampler: new ParentBasedSampler({
      root: new TraceIdRatioBasedSampler(otelConfig.tracesSampleRate),
    }),
  });

  // Span processor must be added explicitly — the constructor config does not accept it.
  _provider.addSpanProcessor(new BatchSpanProcessor(exporter));

  // Register as global provider with W3C TraceContext propagation.
  // On the browser, Sentry v10 does not use the global OTel provider so this is safe.
  _provider.register({
    propagator: new W3CTraceContextPropagator(),
  });

  // Scope fetch propagation to our API to avoid leaking traceparent to third-party services.
  // FetchInstrumentation will automatically inject traceparent on matching requests — no
  // manual header injection is needed in api-client.ts.
  const apiOriginPattern = new RegExp(
    otelConfig.apiUrl.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
  );

  registerInstrumentations({
    tracerProvider: _provider,
    instrumentations: [
      new DocumentLoadInstrumentation(),
      new FetchInstrumentation({
        propagateTraceHeaderCorsUrls: [apiOriginPattern],
        clearTimingResources: true,
      }),
      new UserInteractionInstrumentation(),
    ],
  });

  return _provider;
}

export function getTracer(name = otelConfig.serviceName) {
  if (_provider) return _provider.getTracer(name, otelConfig.serviceVersion);
  return trace.getTracer(name, otelConfig.serviceVersion);
}
