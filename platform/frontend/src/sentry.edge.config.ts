import * as Sentry from "@sentry/nextjs";

const SENTRY_DSN = process.env.SENTRY_DSN ?? process.env.NEXT_PUBLIC_SENTRY_DSN;
const ENVIRONMENT = process.env.ENVIRONMENT ?? process.env.NODE_ENV ?? "development";
const RELEASE = process.env.APP_VERSION ?? process.env.NEXT_PUBLIC_APP_VERSION;

Sentry.init({
  dsn: SENTRY_DSN,
  environment: ENVIRONMENT,
  release: RELEASE,
  enabled: !!SENTRY_DSN,

  tracesSampleRate: ENVIRONMENT === "production" ? 0.1 : 1.0,

  // Structured logs forwarded to Sentry (experimental in SDK v8)
  _experiments: { enableLogs: true },
});

export default {};
