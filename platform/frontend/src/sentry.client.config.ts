import * as Sentry from "@sentry/nextjs";

const SENTRY_DSN = process.env.NEXT_PUBLIC_SENTRY_DSN;
const ENVIRONMENT = process.env.NEXT_PUBLIC_ENVIRONMENT ?? process.env.NODE_ENV ?? "development";
const RELEASE = process.env.NEXT_PUBLIC_APP_VERSION;

Sentry.init({
  dsn: SENTRY_DSN,
  environment: ENVIRONMENT,
  release: RELEASE,
  enabled: !!SENTRY_DSN,

  // Performance monitoring
  tracesSampleRate: ENVIRONMENT === "production" ? 0.1 : 1.0,
  tracePropagationTargets: [
    "localhost",
    /^https:\/\/.*\.mesha\.app/,
    process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080",
  ],

  // Session replay — 10% of all sessions, 100% of sessions with an error
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0,

  // Structured logs forwarded to Sentry (experimental in SDK v8)
  _experiments: { enableLogs: true },

  integrations: [
    Sentry.browserTracingIntegration(),
    Sentry.replayIntegration({
      maskAllText: true,
      maskAllInputs: true,
      blockAllMedia: false,
      networkDetailAllowUrls: [
        process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080",
      ],
    }),
  ],
});

