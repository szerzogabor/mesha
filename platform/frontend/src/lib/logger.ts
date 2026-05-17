import * as Sentry from "@sentry/nextjs";

export type LogLevel = "debug" | "info" | "warn" | "error";

export interface LogContext {
  [key: string]: unknown;
}

function buildMessage(message: string, context?: LogContext): string {
  if (!context || Object.keys(context).length === 0) return message;
  return `${message} ${JSON.stringify(context)}`;
}

function addBreadcrumb(
  level: Sentry.SeverityLevel,
  category: string,
  message: string,
  data?: LogContext
) {
  Sentry.addBreadcrumb({ type: "default", level, category, message, data });
}

export const logger = {
  debug(message: string, context?: LogContext) {
    if (process.env.NODE_ENV === "development") {
      console.debug(`[debug] ${buildMessage(message, context)}`);
    }
    addBreadcrumb("debug", "app", message, context);
    Sentry.logger.debug(message, context);
  },

  info(message: string, context?: LogContext) {
    console.info(`[info] ${buildMessage(message, context)}`);
    addBreadcrumb("info", "app", message, context);
    Sentry.logger.info(message, context);
  },

  warn(message: string, context?: LogContext) {
    console.warn(`[warn] ${buildMessage(message, context)}`);
    addBreadcrumb("warning", "app", message, context);
    Sentry.logger.warn(message, context);
  },

  error(message: string, error?: unknown, context?: LogContext) {
    console.error(`[error] ${buildMessage(message, context)}`, error);
    addBreadcrumb("error", "app", message, context);
    Sentry.logger.error(message, { ...context, error: String(error) });
    if (error instanceof Error) {
      Sentry.captureException(error, { extra: context });
    }
  },

  // Structured domain-specific loggers

  api: {
    failure(path: string, status: number, message: string) {
      const ctx = { path, status, message };
      console.error(`[api] failure ${path} → ${status}: ${message}`);
      addBreadcrumb("error", "api", `API failure: ${path}`, ctx);
      Sentry.logger.error(`API failure: ${path}`, ctx);
    },
    authFailure(path: string, status: number) {
      const ctx = { path, status };
      console.warn(`[api] auth failure ${path} → ${status}`);
      addBreadcrumb("warning", "auth", `Auth failure: ${path}`, ctx);
      Sentry.logger.warn(`Auth failure on ${path}`, ctx);
    },
  },

  auth: {
    failure(reason: string, context?: LogContext) {
      console.warn(`[auth] failure: ${reason}`, context);
      addBreadcrumb("warning", "auth", `Auth failure: ${reason}`, context);
      Sentry.logger.warn(`Auth failure: ${reason}`, context);
    },
  },

  routing: {
    error(path: string, error: unknown) {
      console.error(`[routing] error on ${path}`, error);
      addBreadcrumb("error", "routing", `Routing error: ${path}`);
      Sentry.logger.error(`Routing error: ${path}`);
      if (error instanceof Error) Sentry.captureException(error, { tags: { path } });
    },
  },

  websocket: {
    failure(event: string, context?: LogContext) {
      console.error(`[websocket] failure: ${event}`, context);
      addBreadcrumb("error", "websocket", `WebSocket failure: ${event}`, context);
      Sentry.logger.error(`WebSocket failure: ${event}`, context);
    },
  },

  hydration: {
    error(component: string, error: unknown) {
      console.error(`[hydration] error in ${component}`, error);
      addBreadcrumb("error", "hydration", `Hydration error: ${component}`);
      Sentry.logger.error(`Hydration error: ${component}`);
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { component, type: "hydration" } });
      }
    },
  },

  ai: {
    workflowError(workflow: string, error: unknown, context?: LogContext) {
      console.error(`[ai] workflow error: ${workflow}`, error, context);
      addBreadcrumb("error", "ai", `AI workflow error: ${workflow}`, context);
      Sentry.logger.error(`AI workflow error: ${workflow}`, context);
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { workflow, type: "ai-workflow" }, extra: context });
      }
    },
  },

  pr: {
    renderFailure(prId: string, error: unknown) {
      console.error(`[pr] render failure: PR ${prId}`, error);
      addBreadcrumb("error", "pr", `PR render failure: ${prId}`);
      Sentry.logger.error(`PR render failure: ${prId}`);
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { prId, type: "pr-render" } });
      }
    },
  },

  mobile: {
    renderIssue(component: string, context?: LogContext) {
      console.warn(`[mobile] render issue: ${component}`, context);
      addBreadcrumb("warning", "mobile", `Mobile render issue: ${component}`, context);
      Sentry.logger.warn(`Mobile render issue: ${component}`, context);
    },
  },

  ui: {
    interactionFailure(action: string, error: unknown, context?: LogContext) {
      console.error(`[ui] interaction failure: ${action}`, error, context);
      addBreadcrumb("error", "ui", `UI interaction failure: ${action}`, context);
      Sentry.logger.error(`UI interaction failure: ${action}`, context);
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { action, type: "ui-interaction" }, extra: context });
      }
    },
  },
};
