import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: "https://3b4fb38591182c918261fecf4ed7509d@o4511348602241024.ingest.de.sentry.io/4511406177648720",

  integrations: [
    Sentry.replayIntegration(),
    Sentry.consoleLoggingIntegration({ levels: ["log", "warn", "error"] }),
  ],

  tracesSampleRate: 1,

  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0,

  enableLogs: true,

  debug: false,
});

export const onRouterTransitionStart = Sentry.captureRouterTransitionStart;
