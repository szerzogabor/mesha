import * as Sentry from "@sentry/nextjs";

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
  },

  info(message: string, context?: LogContext) {
    consoleLog("info", message, context);
    Sentry.logger.info(message, toAttrs(context));
  },

  warn(message: string, context?: LogContext) {
    consoleLog("warn", message, context);
    Sentry.logger.warn(message, toAttrs(context));
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
  },

  api: {
    request(method: string, path: string, context?: LogContext) {
      consoleLog("debug", `API → ${method} ${path}`, context);
      Sentry.logger.debug(`API request: ${method} ${path}`, {
        method, path, ...toAttrs(context),
      });
    },
    response(method: string, path: string, status: number, durationMs?: number) {
      consoleLog("debug", `API ← ${method} ${path} ${status}`, { durationMs });
      Sentry.logger.debug(`API response: ${method} ${path}`, { method, path, status, durationMs });
    },
    failure(path: string, status: number, message: string, method?: string) {
      consoleLog("error", `API failure: ${method ?? "?"} ${path} → ${status}`, { message });
      Sentry.logger.error(`API failure: ${path}`, { path, status, message, method });
    },
    authFailure(path: string, status: number, method?: string) {
      consoleLog("warn", `API auth failure: ${method ?? "?"} ${path} → ${status}`);
      Sentry.logger.warn(`Auth failure: ${path}`, { path, status, method });
    },
  },

  auth: {
    syncStarted(userId: string) {
      consoleLog("info", "Auth sync started", { userId });
      Sentry.logger.info("Auth sync started", { userId });
    },
    syncCompleted(userId: string) {
      consoleLog("info", "Auth sync completed", { userId });
      Sentry.logger.info("Auth sync completed", { userId });
    },
    syncSkipped(reason: string, context?: LogContext) {
      consoleLog("warn", `Auth sync skipped: ${reason}`, context);
      Sentry.logger.warn(`Auth sync skipped: ${reason}`, toAttrs(context));
    },
    syncFailed(error: unknown, context?: LogContext) {
      consoleLog("error", "Auth sync failed", context);
      Sentry.logger.error("Auth sync failed", toAttrs(context));
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "auth-sync" }, extra: redact(context) });
    },
    stateChange(from: string, to: string, context?: LogContext) {
      consoleLog("debug", `Auth state: ${from} → ${to}`, context);
      Sentry.logger.debug(`Auth state change: ${from} → ${to}`, { from, to, ...toAttrs(context) });
    },
    failure(reason: string, context?: LogContext) {
      consoleLog("warn", `Auth failure: ${reason}`, context);
      Sentry.logger.warn(`Auth failure: ${reason}`, toAttrs(context));
    },
  },

  github: {
    installationsFetchStarted(workspaceId: string) {
      consoleLog("debug", "GitHub installations fetch started", { workspaceId });
      Sentry.logger.debug("GitHub installations fetch started", { workspaceId });
    },
    installationsFetched(workspaceId: string, count: number) {
      consoleLog("info", `GitHub installations fetched: ${count}`, { workspaceId, count });
      Sentry.logger.info("GitHub installations fetched", { workspaceId, count });
    },
    installationsFetchFailed(workspaceId: string, error: unknown) {
      consoleLog("error", "GitHub installations fetch failed", { workspaceId });
      Sentry.logger.error("GitHub installations fetch failed", { workspaceId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "github-installations", workspaceId } });
    },
    registrationStarted(workspaceId: string, installationId: number) {
      consoleLog("info", "GitHub installation registration started", { workspaceId, installationId });
      Sentry.logger.info("GitHub installation registration started", { workspaceId, installationId });
    },
    registrationSucceeded(workspaceId: string, installationId: number) {
      consoleLog("info", "GitHub installation registered", { workspaceId, installationId });
      Sentry.logger.info("GitHub installation registered", { workspaceId, installationId });
    },
    registrationFailed(workspaceId: string, installationId: number, error: unknown) {
      consoleLog("error", "GitHub installation registration failed", { workspaceId, installationId });
      Sentry.logger.error("GitHub installation registration failed", { workspaceId, installationId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "github-register", workspaceId } });
    },
    repositoriesFetched(workspaceId: string, count: number) {
      consoleLog("debug", `GitHub repositories fetched: ${count}`, { workspaceId, count });
      Sentry.logger.debug("GitHub repositories fetched", { workspaceId, count });
    },
    repositoryConnectStarted(workspaceId: string, context?: LogContext) {
      consoleLog("info", "GitHub repository connect started", { workspaceId, ...context });
      Sentry.logger.info("GitHub repository connect started", { workspaceId, ...toAttrs(context) });
    },
    repositoryConnected(workspaceId: string, repoId: string) {
      consoleLog("info", "GitHub repository connected", { workspaceId, repoId });
      Sentry.logger.info("GitHub repository connected", { workspaceId, repoId });
    },
    repositoryConnectFailed(workspaceId: string, error: unknown) {
      consoleLog("error", "GitHub repository connect failed", { workspaceId });
      Sentry.logger.error("GitHub repository connect failed", { workspaceId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "github-connect", workspaceId } });
    },
    repositoryDisconnected(workspaceId: string, repositoryId: string) {
      consoleLog("info", "GitHub repository disconnected", { workspaceId, repositoryId });
      Sentry.logger.info("GitHub repository disconnected", { workspaceId, repositoryId });
    },
    repositoryDisconnectFailed(workspaceId: string, repositoryId: string, error: unknown) {
      consoleLog("error", "GitHub repository disconnect failed", { workspaceId, repositoryId });
      Sentry.logger.error("GitHub repository disconnect failed", { workspaceId, repositoryId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "github-disconnect", workspaceId } });
    },
    pullRequestsFetched(repositoryId: string, count: number) {
      consoleLog("debug", `GitHub pull requests fetched: ${count}`, { repositoryId, count });
      Sentry.logger.debug("GitHub pull requests fetched", { repositoryId, count });
    },
    pullRequestsSyncStarted(repositoryId: string) {
      consoleLog("info", "GitHub pull requests sync started", { repositoryId });
      Sentry.logger.info("GitHub pull requests sync started", { repositoryId });
    },
    pullRequestsSynced(repositoryId: string, count: number) {
      consoleLog("info", `GitHub pull requests synced: ${count}`, { repositoryId, count });
      Sentry.logger.info("GitHub pull requests synced", { repositoryId, count });
    },
    queryInvalidated(queryKey: string, context?: LogContext) {
      consoleLog("debug", `GitHub query invalidated: ${queryKey}`, context);
      Sentry.logger.debug(`GitHub query invalidated: ${queryKey}`, toAttrs(context));
    },
    uiStateChange(from: string, to: string, context?: LogContext) {
      consoleLog("debug", `GitHub UI state: ${from} → ${to}`, context);
      Sentry.logger.debug(`GitHub UI state change`, { from, to, ...toAttrs(context) });
    },
    redirectHandled(url: string) {
      consoleLog("info", "GitHub redirect handled", { url });
      Sentry.logger.info("GitHub redirect handled", { url });
    },
  },

  kanban: {
    dragStarted(issueId: string, status: string) {
      consoleLog("debug", "Kanban drag started", { issueId, status });
      Sentry.logger.debug("Kanban drag started", { issueId, status });
    },
    dragEnded(issueId: string, fromStatus: string, toStatus: string) {
      consoleLog("debug", `Kanban drag ended: ${fromStatus} → ${toStatus}`, { issueId });
      Sentry.logger.debug("Kanban drag ended", { issueId, fromStatus, toStatus });
    },
    dragCanceled(issueId?: string) {
      consoleLog("debug", "Kanban drag canceled", { issueId });
      Sentry.logger.debug("Kanban drag canceled", { issueId });
    },
    optimisticUpdate(issueId: string, newStatus: string) {
      consoleLog("debug", `Kanban optimistic update: ${issueId} → ${newStatus}`, { issueId, newStatus });
      Sentry.logger.debug("Kanban optimistic status update", { issueId, newStatus });
    },
    statusUpdateFailed(issueId: string, error: unknown) {
      consoleLog("error", "Kanban status update failed", { issueId });
      Sentry.logger.error("Kanban status update failed", { issueId });
      if (error instanceof Error) Sentry.captureException(error, { tags: { source: "kanban-update", issueId } });
    },
  },

  routing: {
    change(from: string, to: string) {
      consoleLog("info", `Route change: ${from} → ${to}`);
      Sentry.logger.info("Route change", { from, to });
    },
    error(path: string, error: unknown) {
      consoleLog("error", `Routing error: ${path}`);
      Sentry.logger.error(`Routing error: ${path}`, { path });
      if (error instanceof Error) Sentry.captureException(error, { tags: { path } });
    },
  },

  websocket: {
    connected(context?: LogContext) {
      consoleLog("info", "WebSocket connected", context);
      Sentry.logger.info("WebSocket connected", toAttrs(context));
    },
    disconnected(context?: LogContext) {
      consoleLog("warn", "WebSocket disconnected", context);
      Sentry.logger.warn("WebSocket disconnected", toAttrs(context));
    },
    event(eventType: string, context?: LogContext) {
      consoleLog("debug", `WebSocket event: ${eventType}`, context);
      Sentry.logger.debug(`WebSocket event: ${eventType}`, toAttrs(context));
    },
    failure(event: string, context?: LogContext) {
      consoleLog("error", `WebSocket failure: ${event}`, context);
      Sentry.logger.error(`WebSocket failure: ${event}`, toAttrs(context));
    },
  },

  hydration: {
    error(component: string, error: unknown) {
      consoleLog("error", `Hydration error: ${component}`);
      Sentry.logger.error(`Hydration error: ${component}`, { component });
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { component, type: "hydration" } });
      }
    },
  },

  ai: {
    sessionStarted(issueId: string, sessionId: string) {
      consoleLog("info", "AI session started", { issueId, sessionId });
      Sentry.logger.info("AI session started", { issueId, sessionId });
    },
    sessionStateChange(sessionId: string, from: string, to: string) {
      consoleLog("info", `AI session state: ${from} → ${to}`, { sessionId });
      Sentry.logger.info("AI session state change", { sessionId, from, to });
    },
    sessionCanceled(issueId: string, sessionId: string) {
      consoleLog("info", "AI session canceled", { issueId, sessionId });
      Sentry.logger.info("AI session canceled", { issueId, sessionId });
    },
    pollingActive(issueId: string, sessionId: string, state: string) {
      consoleLog("debug", `AI session polling: ${state}`, { issueId, sessionId, state });
      Sentry.logger.debug("AI session polling active", { issueId, sessionId, state });
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
    },
  },

  pr: {
    renderStarted(prId: string) {
      consoleLog("debug", `PR render started: ${prId}`, { prId });
      Sentry.logger.debug(`PR render started: ${prId}`, { prId });
    },
    renderComplete(prId: string) {
      consoleLog("debug", `PR render complete: ${prId}`, { prId });
      Sentry.logger.debug(`PR render complete: ${prId}`, { prId });
    },
    renderFailure(prId: string, error: unknown) {
      consoleLog("error", `PR render failure: ${prId}`);
      Sentry.logger.error(`PR render failure: ${prId}`, { prId });
      if (error instanceof Error) {
        Sentry.captureException(error, { tags: { prId, type: "pr-render" } });
      }
    },
  },

  mobile: {
    renderIssue(component: string, context?: LogContext) {
      consoleLog("warn", `Mobile render issue: ${component}`, context);
      Sentry.logger.warn(`Mobile render issue: ${component}`, toAttrs(context));
    },
    interaction(action: string, context?: LogContext) {
      consoleLog("debug", `Mobile interaction: ${action}`, context);
      Sentry.logger.debug(`Mobile interaction: ${action}`, toAttrs(context));
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
    },
  },
};
