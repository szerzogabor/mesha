export const otelConfig = {
  serviceName: process.env.NEXT_PUBLIC_OTEL_SERVICE_NAME ?? "mesha-frontend",
  serviceVersion: process.env.NEXT_PUBLIC_APP_VERSION ?? "0.0.0",
  environment: process.env.NEXT_PUBLIC_ENVIRONMENT ?? "local",

  // OTLP data is sent to the local Next.js proxy (/api/otel) to avoid browser CORS restrictions.
  // The proxy forwards to Grafana Cloud server-side using server-only env vars.
  otlpEndpoint: "/api/otel",

  // Trace sample rate (0–1). Validated to avoid NaN or out-of-range values from env.
  tracesSampleRate: (() => {
    const rate = Number(process.env.NEXT_PUBLIC_OTEL_TRACES_SAMPLE_RATE ?? "1");
    return isNaN(rate) || rate < 0 || rate > 1 ? 1 : rate;
  })(),

  // Single source of truth for the backend API URL, shared with api-client and tracer.
  apiUrl: process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080",

  // OTel is enabled when NEXT_PUBLIC_OTEL_ENABLED=true (set in .env.production).
  get enabled(): boolean {
    return process.env.NEXT_PUBLIC_OTEL_ENABLED === "true";
  },
};
