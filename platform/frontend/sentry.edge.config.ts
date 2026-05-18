import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: "https://3b4fb38591182c918261fecf4ed7509d@o4511348602241024.ingest.de.sentry.io/4511406177648720",

  tracesSampleRate: 1,

  debug: false,
});
