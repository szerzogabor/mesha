import * as Sentry from "@sentry/nextjs";

export type LogLevel = "debug" | "info" | "warn" | "error";

export interface LogContext {
  [key: string]: unknown;
}

function toAttrs(
  context?: LogContext
): Record<string, string | number | boolean> | undefined {
  if (!context) return undefined;
  const attrs: Record<string, string | number | boolean> = {};
  for (const [k, v] of Object.entries(context)) {
    if (typeof v === "string" || typeof v === "number" || typeof v === "boolean") {
      attrs[k] = v;
    } else if (v !== null && v !== undefined) {
      attrs[k] = String(v);
    }
  }
  return Object.keys(attrs).length > 0 ? attrs : undefined;
}

export const logger = {
  debug(message: string, context?: LogContext) {
    Sentry.logger.debug(message, toAttrs(context));
  },

  info(message: string, context?: LogContext) {
    Sentry.logger.info(message, toAttrs(context));
  },

  warn(message: string, context?: LogContext) {
    Sentry.logger.warn(message, toAttrs(context));
  },

  error(message: string, error?: unknown, context?: LogContext) {
    Sentry.logger.error(message, {
      ...toAttrs(context),
      ...(error instanceof Error ? { errorMessage: error.message } : {}),
    });
    if (error instanceof Error) {
      Sentry.captureException(error, { extra: context });
    }
  },

  // Structured domain-specific loggers

  api: {
    failure(path: string, status: number, message: string) {
      Sentry.logger.error(`API failure: ${path}`, { path, status, message });
    },
    authFailure(path: string, status: number) {
      Sentry.logger.warn(`Auth failure: ${path}`, { path, status });
    },
  },

  auth: {
    failure(reason: string, context?: LogContext) {
      Sentry.logger.warn(`Auth failure: ${reason}`, toAttrs(context));
    },
  },

  routing: {
    error(path: string, error: unknown) {
      Sentry.logger.error(`Routing error: ${path}`, { path });
      if (error instanceof Error) Sentry.captureException(error, { tags: { path } });
    },
  },

  websocket: {
    failure(event: string, context?: LogContext) {
      Sentry.logger.error(`WebSocket failure: ${event}`, toAttrs(context));
    },
  },

  hydration: {
    error(component: string, error: unknown) {
      Sentry.logger.error(`Hydration error: ${component}`, { component });
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { component, type: "hydration" } });
      }
    },
  },

  ai: {
    workflowError(workflow: string, error: unknown, context?: LogContext) {
      Sentry.logger.error(`AI workflow error: ${workflow}`, {
        workflow,
        ...toAttrs(context),
      });
      if (error instanceof Error) {
        Sentry.captureException(error, {
          tags: { workflow, type: "ai-workflow" },
          extra: context,
        });
      }
    },
  },

  pr: {
    renderFailure(prId: string, error: unknown) {
      Sentry.logger.error(`PR render failure: ${prId}`, { prId });
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { prId, type: "pr-render" } });
      }
    },
  },

  mobile: {
    renderIssue(component: string, context?: LogContext) {
      Sentry.logger.warn(`Mobile render issue: ${component}`, toAttrs(context));
    },
  },

  ui: {
    interactionFailure(action: string, error: unknown, context?: LogContext) {
      Sentry.logger.error(`UI interaction failure: ${action}`, {
        action,
        ...toAttrs(context),
      });
      if (error instanceof Error) {
        Sentry.captureException(error, {
          tags: { action, type: "ui-interaction" },
          extra: context,
        });
      }
    },
  },
};
