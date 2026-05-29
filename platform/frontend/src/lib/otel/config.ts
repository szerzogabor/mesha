export const otelConfig = {
  serviceName: process.env.NEXT_PUBLIC_OTEL_SERVICE_NAME ?? "mesha-frontend",
  serviceVersion: process.env.NEXT_PUBLIC_APP_VERSION ?? "0.0.0",
  environment: process.env.NEXT_PUBLIC_ENVIRONMENT ?? "local",

  // Grafana Cloud OTLP gateway — e.g. https://otlp-gateway-prod-eu-west-0.grafana.net/otlp
  otlpEndpoint: process.env.NEXT_PUBLIC_OTEL_EXPORTER_OTLP_ENDPOINT ?? "",

  // Base64-encoded "<instanceId>:<apiToken>" for Grafana Cloud OTLP basic-auth
  otlpAuthHeader: process.env.NEXT_PUBLIC_OTEL_EXPORTER_OTLP_AUTH ?? "",

  // Trace/log sample rates (0–1)
  tracesSampleRate: Number(process.env.NEXT_PUBLIC_OTEL_TRACES_SAMPLE_RATE ?? "1"),

  get enabled(): boolean {
    return Boolean(this.otlpEndpoint);
  },

  get authorizationHeader(): string | undefined {
    return this.otlpAuthHeader ? `Basic ${this.otlpAuthHeader}` : undefined;
  },
};
