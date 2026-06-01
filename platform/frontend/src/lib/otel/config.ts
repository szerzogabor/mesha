export const otelConfig = {
  serviceName: process.env.NEXT_PUBLIC_OTEL_SERVICE_NAME ?? "mesha-frontend",
  serviceVersion: process.env.NEXT_PUBLIC_APP_VERSION ?? "0.0.0",
  environment: process.env.NEXT_PUBLIC_ENVIRONMENT ?? "local",

  // Grafana Cloud OTLP gateway — e.g. https://otlp-gateway-prod-eu-west-0.grafana.net/otlp
  // In the browser, route through the same-origin Next.js rewrite proxy (/otlp/*) to avoid CORS.
  otlpEndpoint: (() => {
    const configured = (process.env.NEXT_PUBLIC_OTEL_EXPORTER_OTLP_ENDPOINT ?? "").replace(/\/$/, "");
    if (!configured) return "";
    if (typeof window !== "undefined") {
      return `${window.location.origin}/otlp`;
    }
    return configured;
  })(),

  // Base64-encoded "<instanceId>:<apiToken>" for Grafana Cloud OTLP basic-auth
  otlpAuthHeader: process.env.NEXT_PUBLIC_OTEL_EXPORTER_OTLP_AUTH ?? "",

  // Trace sample rate (0–1). Validated to avoid NaN or out-of-range values from env.
  tracesSampleRate: (() => {
    const rate = Number(process.env.NEXT_PUBLIC_OTEL_TRACES_SAMPLE_RATE ?? "1");
    return isNaN(rate) || rate < 0 || rate > 1 ? 1 : rate;
  })(),

  // Single source of truth for the backend API URL, shared with api-client and tracer.
  apiUrl: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080",

  get enabled(): boolean {
    return Boolean(this.otlpEndpoint);
  },

  get authorizationHeader(): string | undefined {
    return this.otlpAuthHeader ? `Basic ${this.otlpAuthHeader}` : undefined;
  },
};
