import type { NextConfig } from "next";
import { withSentryConfig } from "@sentry/nextjs";

const nextConfig: NextConfig = {
  output: "standalone",
};

export default withSentryConfig(nextConfig, {
  org: process.env.SENTRY_ORG,
  project: process.env.SENTRY_PROJECT,
  authToken: process.env.SENTRY_AUTH_TOKEN,

  // Upload source maps to Sentry during production builds
  sourcemaps: {
    disable: process.env.NODE_ENV !== "production",
  },

  // Suppress Sentry build-time logs
  silent: !process.env.CI,

  // Automatically tree-shake Sentry SDK logger in production
  disableLogger: true,

  // Allows Sentry to run automatically in the instrumentation hook
  autoInstrumentMiddleware: true,

  // Automatically wrap server components
  autoInstrumentServerFunctions: true,
});
