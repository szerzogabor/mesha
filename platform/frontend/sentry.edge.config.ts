import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: process.env.NEXT_PUBLIC_SENTRY_DSN,

  environment: process.env.NEXT_PUBLIC_ENVIRONMENT ?? "local",

  release: process.env.NEXT_PUBLIC_APP_VERSION
    ? `mesha-frontend@${process.env.NEXT_PUBLIC_APP_VERSION}`
    : undefined,

  tracesSampleRate: process.env.NEXT_PUBLIC_ENVIRONMENT === "production" ? 0.1 : 1.0,

  enableLogs: true,

  debug: false,
});
