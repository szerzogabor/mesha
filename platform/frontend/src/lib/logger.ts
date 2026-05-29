import * as Sentry from "@sentry/nextjs";
import { SeverityNumber } from "@opentelemetry/api-logs";
import { getOtelLogger, otelConfig } from "@/lib/otel";

export type LogLevel = "debug" | "info" | "warn" | "error";

export interface LogContext {
  [key: string]: unknown;
}

const isDev = process.env.NODE_ENV === "development";

const SENSITIVE_KEYS = new Set([
  "token", "accessToken", "refreshToken", "password", "secret",
  "authorization", "cookie", "sessionId", "apiKey", "privateKey",
]);

function redact(context?: LogContext): LogContext | undefined {
  if (!context) return undefined;
  const out: LogContext = {};
  for (const [k, v] of Object.entries(context)) {
    out[k] = SENSITIVE_KEYS.has(k) ? "[REDACTED]" : v;
  }
  return out;
}

function toAttrs(
  context?: LogContext
): Record<string, string | number | boolean> | undefined {
  const safe = redact(context);
  if (!safe) return undefined;
  const attrs: Record<string, string | number | boolean> = {};
  for (const [k, v] of Object.entries(safe)) {
    if (typeof v === "string" || typeof v === "number" || typeof v === "boolean") {
      attrs[k] = v;
    } else if (v !== null && v !== undefined) {
      attrs[k] = String(v);
    }
  }
  return Object.keys(attrs).length > 0 ? attrs : undefined;
}

function toOtelAttrs(context?: LogContext): Record<string, string | number | boolean> {
  return toAttrs(context) ?? {};
}

const SEVERITY_MAP: Record<LogLevel, SeverityNumber> = {
  debug: SeverityNumber.DEBUG,
  info: SeverityNumber.INFO,
  warn: SeverityNumber.WARN,
  error: SeverityNumber.ERROR,
};

function emitOtelLog(
  level: LogLevel,
  message: string,
  context?: LogContext
): void {
  if (!otelConfig.enabled) return;
  try {
    const otelLogger = getOtelLogger();
    otelLogger.emit({
      severityNumber: SEVERITY_MAP[level],
      severityText: level.toUpperCase(),
      body: message,
      attributes: {
        "service.name": otelConfig.serviceName,
        "deployment.environment": otelConfig.environment,
        ...toOtelAttrs(context),
      },
    });
  } catch {
    // OTel not initialised yet — silently skip
  }
}

function consoleLog(level: LogLevel, message: string, context?: LogContext) {
  if (!isDev && (level === "debug" || level === "info")) return;
  const safe = redact(context);
  const prefix = `[mesha/${level.toUpperCase()}]`;
  if (safe && Object.keys(safe).length > 0) {
    // eslint-disable-next-line no-console
    console[level === "debug" ? "debug" : level === "info" ? "info" : level === "warn" ? "warn" : "error"](
      prefix, message, safe
    );
  } else {
    // eslint-disable-next-line no-console
    console[level === "debug" ? "debug" : level === "info" ? "info" : level === "warn" ? "warn" : "error"](
      prefix, message
    );
  }
}

export const logger = {
  debug(message: string, context?: LogContext) {
    consoleLog("debug", message, context);
    if (isDev) Sentry.logger.debug(message, toAttrs(context));
    emitOtelLog("debug", message, context);
  },

  info(message: string, context?: LogContext) {
    consoleLog("info", message, context);
    Sentry.logger.info(message, toAttrs(context));
    emitOtelLog("info", message, context);
  },

  warn(message: string, context?: LogContext) {
    consoleLog("warn", message, context);
    Sentry.logger.warn(message, toAttrs(context));
    emitOtelLog("warn", message, context);
  },

  error(message: string, error?: unknown, context?: LogContext) {
    consoleLog("error", message, context);
    Sentry.logger.error(message, {
      ...toAttrs(context),
      ...(error instanceof Error ? { errorMessage: error.message } : {}),
    });
    if (error instanceof Error) {
      Sentry.captureException(error, { extra: redact(context) });
    }
    emitOtelLog("error", message, {
      ...context,
      ...(error instanceof Error ? { errorMessage: error.message, errorStack: error.stack } : {}),
    });
  },

  api: {
    request(method: string, path: string, context?: LogContext) {
      consoleLog("debug", `API → ${method} ${path}`, context);
      Sentry.logger.debug(`API request: ${method} ${path}`, {
        method, path, ...toAttrs(context),
      });
      emitOtelLog("debug", `API request: ${method} ${path}`, { method, path, ...context });
    },
    response(method: string, path: string, status: number, durationMs?: number) {
      consoleLog("debug", `API ← ${method} ${path} ${status}`, { durationMs });
      Sentry.logger.debug(`API response: ${method} ${path}`, { method, path, status, durationMs });
      emitOtelLog("debug", `API response: ${method} ${path}`, { method, path, status, durationMs });
    },
    failure(path: string, status: number, message: string, method?: string) {
      consoleLog("error", `API failure: ${method ?? "?"} ${path} → ${status}`, { message });
      Sentry.logger.error(`API failure: ${path}`, { path, status, message, method });
      emitOtelLog("error", `API failure: ${path}`, { path, status, message, method });
    },
    authFailure(path: string, status: number, method?: string) {
      consoleLog("warn", `API auth failure: ${method ?? "?"} ${path} → ${status}`);
      Sentry.logger.warn(`Auth failure: ${path}`, { path, status, method });
      emitOtelLog("warn", `API auth failure: ${path}`, { path, status, method });
    },
  },

  auth: {
    syncStarted(userId: string) {
      consoleLog("info", "Auth sync started", { userId });
      Sentry.logger.info("Auth sync started", { userId });
      emitOtelLog("info", "Auth sync started", { userId });
    },
    syncCompleted(userId: string) {
      consoleLog("info", "Auth sync completed", { userId });
      Sentry.logger.info("Auth sync completed", { userId });
      emitOtelLog("info", "Auth sync completed", { userId });
    },
    syncSkipped(reason: string, context?: LogContext) {
      consoleLog("warn", `Auth sync skipped: ${reason}`, context);
      Sentry.logger.warn(`Auth sync skipped: ${reason}`, toAttrs(context));
      emitOtelLog("warn", `Auth sync skipped: ${reason}`, context);
    },
    syncFailed(error: unknown, context?: LogContext) {
      consoleLog("error", "Auth sync failed", context);
      Sentry.logger.error("Auth sync failed", toAttrs(context));
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "auth-sync" }, extra: redact(context) });
      emitOtelLog("error", "Auth sync failed", {
        ...context,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
    stateChange(from: string, to: string, context?: LogContext) {
      consoleLog("debug", `Auth state: ${from} → ${to}`, context);
      Sentry.logger.debug(`Auth state change: ${from} → ${to}`, { from, to, ...toAttrs(context) });
      emitOtelLog("debug", `Auth state change: ${from} → ${to}`, { from, to, ...context });
    },
    failure(reason: string, context?: LogContext) {
      consoleLog("warn", `Auth failure: ${reason}`, context);
      Sentry.logger.warn(`Auth failure: ${reason}`, toAttrs(context));
      emitOtelLog("warn", `Auth failure: ${reason}`, context);
    },
  },

  github: {
    installationsFetchStarted(workspaceId: string) {
      consoleLog("debug", "GitHub installations fetch started", { workspaceId });
      Sentry.logger.debug("GitHub installations fetch started", { workspaceId });
      emitOtelLog("debug", "GitHub installations fetch started", { workspaceId });
    },
    installationsFetched(workspaceId: string, count: number) {
      consoleLog("info", `GitHub installations fetched: ${count}`, { workspaceId, count });
      Sentry.logger.info("GitHub installations fetched", { workspaceId, count });
      emitOtelLog("info", "GitHub installations fetched", { workspaceId, count });
    },
    installationsFetchFailed(workspaceId: string, error: unknown) {
      consoleLog("error", "GitHub installations fetch failed", { workspaceId });
      Sentry.logger.error("GitHub installations fetch failed", { workspaceId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "github-installations", workspaceId } });
      emitOtelLog("error", "GitHub installations fetch failed", {
        workspaceId,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
    registrationStarted(workspaceId: string, installationId: number) {
      consoleLog("info", "GitHub installation registration started", { workspaceId, installationId });
      Sentry.logger.info("GitHub installation registration started", { workspaceId, installationId });
      emitOtelLog("info", "GitHub installation registration started", { workspaceId, installationId });
    },
    registrationSucceeded(workspaceId: string, installationId: number) {
      consoleLog("info", "GitHub installation registered", { workspaceId, installationId });
      Sentry.logger.info("GitHub installation registered", { workspaceId, installationId });
      emitOtelLog("info", "GitHub installation registered", { workspaceId, installationId });
    },
    registrationFailed(workspaceId: string, installationId: number, error: unknown) {
      consoleLog("error", "GitHub installation registration failed", { workspaceId, installationId });
      Sentry.logger.error("GitHub installation registration failed", { workspaceId, installationId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "github-register", workspaceId } });
      emitOtelLog("error", "GitHub installation registration failed", {
        workspaceId, installationId,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
    repositoriesFetched(workspaceId: string, count: number) {
      consoleLog("debug", `GitHub repositories fetched: ${count}`, { workspaceId, count });
      Sentry.logger.debug("GitHub repositories fetched", { workspaceId, count });
      emitOtelLog("debug", "GitHub repositories fetched", { workspaceId, count });
    },
    repositoryConnectStarted(workspaceId: string, context?: LogContext) {
      consoleLog("info", "GitHub repository connect started", { workspaceId, ...context });
      Sentry.logger.info("GitHub repository connect started", { workspaceId, ...toAttrs(context) });
      emitOtelLog("info", "GitHub repository connect started", { workspaceId, ...context });
    },
    repositoryConnected(workspaceId: string, repoId: string) {
      consoleLog("info", "GitHub repository connected", { workspaceId, repoId });
      Sentry.logger.info("GitHub repository connected", { workspaceId, repoId });
      emitOtelLog("info", "GitHub repository connected", { workspaceId, repoId });
    },
    repositoryConnectFailed(workspaceId: string, error: unknown) {
      consoleLog("error", "GitHub repository connect failed", { workspaceId });
      Sentry.logger.error("GitHub repository connect failed", { workspaceId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "github-connect", workspaceId } });
      emitOtelLog("error", "GitHub repository connect failed", {
        workspaceId,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
    repositoryDisconnected(workspaceId: string, repositoryId: string) {
      consoleLog("info", "GitHub repository disconnected", { workspaceId, repositoryId });
      Sentry.logger.info("GitHub repository disconnected", { workspaceId, repositoryId });
      emitOtelLog("info", "GitHub repository disconnected", { workspaceId, repositoryId });
    },
    repositoryDisconnectFailed(workspaceId: string, repositoryId: string, error: unknown) {
      consoleLog("error", "GitHub repository disconnect failed", { workspaceId, repositoryId });
      Sentry.logger.error("GitHub repository disconnect failed", { workspaceId, repositoryId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "github-disconnect", workspaceId } });
      emitOtelLog("error", "GitHub repository disconnect failed", {
        workspaceId, repositoryId,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
    pullRequestsFetched(repositoryId: string, count: number) {
      consoleLog("debug", `GitHub pull requests fetched: ${count}`, { repositoryId, count });
      Sentry.logger.debug("GitHub pull requests fetched", { repositoryId, count });
      emitOtelLog("debug", "GitHub pull requests fetched", { repositoryId, count });
    },
    pullRequestsSyncStarted(repositoryId: string) {
      consoleLog("info", "GitHub pull requests sync started", { repositoryId });
      Sentry.logger.info("GitHub pull requests sync started", { repositoryId });
      emitOtelLog("info", "GitHub pull requests sync started", { repositoryId });
    },
    pullRequestsSynced(repositoryId: string, count: number) {
      consoleLog("info", `GitHub pull requests synced: ${count}`, { repositoryId, count });
      Sentry.logger.info("GitHub pull requests synced", { repositoryId, count });
      emitOtelLog("info", "GitHub pull requests synced", { repositoryId, count });
    },
    queryInvalidated(queryKey: string, context?: LogContext) {
      consoleLog("debug", `GitHub query invalidated: ${queryKey}`, context);
      Sentry.logger.debug(`GitHub query invalidated: ${queryKey}`, toAttrs(context));
      emitOtelLog("debug", `GitHub query invalidated: ${queryKey}`, context);
    },
    uiStateChange(from: string, to: string, context?: LogContext) {
      consoleLog("debug", `GitHub UI state: ${from} → ${to}`, context);
      Sentry.logger.debug(`GitHub UI state change`, { from, to, ...toAttrs(context) });
      emitOtelLog("debug", "GitHub UI state change", { from, to, ...context });
    },
    redirectHandled(url: string) {
      consoleLog("info", "GitHub redirect handled", { url });
      Sentry.logger.info("GitHub redirect handled", { url });
      emitOtelLog("info", "GitHub redirect handled", { url });
    },
  },

  kanban: {
    dragStarted(issueId: string, status: string) {
      consoleLog("debug", "Kanban drag started", { issueId, status });
      Sentry.logger.debug("Kanban drag started", { issueId, status });
      emitOtelLog("debug", "Kanban drag started", { issueId, status });
    },
    dragEnded(issueId: string, fromStatus: string, toStatus: string) {
      consoleLog("debug", `Kanban drag ended: ${fromStatus} → ${toStatus}`, { issueId });
      Sentry.logger.debug("Kanban drag ended", { issueId, fromStatus, toStatus });
      emitOtelLog("debug", "Kanban drag ended", { issueId, fromStatus, toStatus });
    },
    dragCanceled(issueId?: string) {
      consoleLog("debug", "Kanban drag canceled", { issueId });
      Sentry.logger.debug("Kanban drag canceled", { issueId });
      emitOtelLog("debug", "Kanban drag canceled", { issueId });
    },
    optimisticUpdate(issueId: string, newStatus: string) {
      consoleLog("debug", `Kanban optimistic update: ${issueId} → ${newStatus}`, { issueId, newStatus });
      Sentry.logger.debug("Kanban optimistic status update", { issueId, newStatus });
      emitOtelLog("debug", "Kanban optimistic status update", { issueId, newStatus });
    },
    statusUpdateFailed(issueId: string, error: unknown) {
      consoleLog("error", "Kanban status update failed", { issueId });
      Sentry.logger.error("Kanban status update failed", { issueId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "kanban-update", issueId } });
      emitOtelLog("error", "Kanban status update failed", {
        issueId,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
  },

  routing: {
    change(from: string, to: string) {
      consoleLog("info", `Route change: ${from} → ${to}`);
      Sentry.logger.info("Route change", { from, to });
      emitOtelLog("info", `Route change: ${from} → ${to}`, { from, to });
    },
    error(path: string, error: unknown) {
      consoleLog("error", `Routing error: ${path}`);
      Sentry.logger.error(`Routing error: ${path}`, { path });
      if (error instanceof Error) Sentry.captureException(error, { tags: { path } });
      emitOtelLog("error", `Routing error: ${path}`, {
        path,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
  },

  websocket: {
    connected(context?: LogContext) {
      consoleLog("info", "WebSocket connected", context);
      Sentry.logger.info("WebSocket connected", toAttrs(context));
      emitOtelLog("info", "WebSocket connected", context);
    },
    disconnected(context?: LogContext) {
      consoleLog("warn", "WebSocket disconnected", context);
      Sentry.logger.warn("WebSocket disconnected", toAttrs(context));
      emitOtelLog("warn", "WebSocket disconnected", context);
    },
    event(eventType: string, context?: LogContext) {
      consoleLog("debug", `WebSocket event: ${eventType}`, context);
      Sentry.logger.debug(`WebSocket event: ${eventType}`, toAttrs(context));
      emitOtelLog("debug", `WebSocket event: ${eventType}`, { eventType, ...context });
    },
    failure(event: string, context?: LogContext) {
      consoleLog("error", `WebSocket failure: ${event}`, context);
      Sentry.logger.error(`WebSocket failure: ${event}`, toAttrs(context));
      emitOtelLog("error", `WebSocket failure: ${event}`, { event, ...context });
    },
  },

  hydration: {
    error(component: string, error: unknown) {
      consoleLog("error", `Hydration error: ${component}`);
      Sentry.logger.error(`Hydration error: ${component}`, { component });
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { component, type: "hydration" } });
      }
      emitOtelLog("error", `Hydration error: ${component}`, {
        component,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
  },

  ai: {
    sessionStarted(issueId: string, sessionId: string) {
      consoleLog("info", "AI session started", { issueId, sessionId });
      Sentry.logger.info("AI session started", { issueId, sessionId });
      emitOtelLog("info", "AI session started", { issueId, sessionId });
    },
    sessionStateChange(sessionId: string, from: string, to: string) {
      consoleLog("info", `AI session state: ${from} → ${to}`, { sessionId });
      Sentry.logger.info("AI session state change", { sessionId, from, to });
      emitOtelLog("info", `AI session state change: ${from} → ${to}`, { sessionId, from, to });
    },
    sessionCanceled(issueId: string, sessionId: string) {
      consoleLog("info", "AI session canceled", { issueId, sessionId });
      Sentry.logger.info("AI session canceled", { issueId, sessionId });
      emitOtelLog("info", "AI session canceled", { issueId, sessionId });
    },
    pollingActive(issueId: string, sessionId: string, state: string) {
      consoleLog("debug", `AI session polling: ${state}`, { issueId, sessionId, state });
      Sentry.logger.debug("AI session polling active", { issueId, sessionId, state });
      emitOtelLog("debug", "AI session polling active", { issueId, sessionId, state });
    },
    workflowError(workflow: string, error: unknown, context?: LogContext) {
      consoleLog("error", `AI workflow error: ${workflow}`, context);
      Sentry.logger.error(`AI workflow error: ${workflow}`, {
        workflow,
        ...toAttrs(context),
      });
      if (error instanceof Error) {
        Sentry.captureException(error, {
          tags: { workflow, type: "ai-workflow" },
          extra: redact(context),
        });
      }
      emitOtelLog("error", `AI workflow error: ${workflow}`, {
        workflow,
        ...context,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
  },

  pr: {
    renderStarted(prId: string) {
      consoleLog("debug", `PR render started: ${prId}`, { prId });
      Sentry.logger.debug(`PR render started: ${prId}`, { prId });
      emitOtelLog("debug", `PR render started: ${prId}`, { prId });
    },
    renderComplete(prId: string) {
      consoleLog("debug", `PR render complete: ${prId}`, { prId });
      Sentry.logger.debug(`PR render complete: ${prId}`, { prId });
      emitOtelLog("debug", `PR render complete: ${prId}`, { prId });
    },
    renderFailure(prId: string, error: unknown) {
      consoleLog("error", `PR render failure: ${prId}`);
      Sentry.logger.error(`PR render failure: ${prId}`, { prId });
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { prId, type: "pr-render" } });
      }
      emitOtelLog("error", `PR render failure: ${prId}`, {
        prId,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
  },

  mobile: {
    renderIssue(component: string, context?: LogContext) {
      consoleLog("warn", `Mobile render issue: ${component}`, context);
      Sentry.logger.warn(`Mobile render issue: ${component}`, toAttrs(context));
      emitOtelLog("warn", `Mobile render issue: ${component}`, { component, ...context });
    },
    interaction(action: string, context?: LogContext) {
      consoleLog("debug", `Mobile interaction: ${action}`, context);
      Sentry.logger.debug(`Mobile interaction: ${action}`, toAttrs(context));
      emitOtelLog("debug", `Mobile interaction: ${action}`, { action, ...context });
    },
  },

  ui: {
    interactionFailure(action: string, error: unknown, context?: LogContext) {
      consoleLog("error", `UI interaction failure: ${action}`, context);
      Sentry.logger.error(`UI interaction failure: ${action}`, {
        action,
        ...toAttrs(context),
      });
      if (error instanceof Error) {
        Sentry.captureException(error, {
          tags: { action, type: "ui-interaction" },
          extra: redact(context),
        });
      }
      emitOtelLog("error", `UI interaction failure: ${action}`, {
        action,
        ...context,
        ...(error instanceof Error ? { errorMessage: error.message } : {}),
      });
    },
  },
};
