import type { NextConfig } from "next";
import { withSentryConfig } from "@sentry/nextjs";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    const otlpTarget = process.env.NEXT_PUBLIC_OTEL_EXPORTER_OTLP_ENDPOINT?.replace(/\/$/, "");
    if (!otlpTarget) return [];
    return [
      {
        source: "/otlp/:path*",
        destination: `${otlpTarget}/:path*`,
      },
    ];
  },
};

export default withSentryConfig(nextConfig, {
  org: process.env.SENTRY_ORG,
  project: process.env.SENTRY_PROJECT,
  authToken: process.env.SENTRY_AUTH_TOKEN,
  silent: !process.env.CI,

  widenClientFileUpload: true,

  automaticVercelMonitors: true,
});
