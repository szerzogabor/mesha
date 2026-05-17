import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: "https://3b4fb38591182c918261fecf4ed7509d@o4511348602241024.ingest.de.sentry.io/4511406177648720",

  integrations: [
    // send console.log, console.warn, and console.error calls as logs to Sentry
    Sentry.consoleLoggingIntegration({ levels: ["log", "warn", "error"] }),
  ],

  // Enable logs to be sent to Sentry
  enableLogs: true,

  // Define how likely traces are sampled. Adjust this value in production, or use tracesSampler for greater control.
  tracesSampleRate: 1,

  // Setting this option to true will print useful information to the console while you're setting up Sentry.
  debug: false,
});
