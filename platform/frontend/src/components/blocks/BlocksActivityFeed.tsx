"use client";

import { AIExecutionState, BlocksMessage, BlocksSession } from "@/types";
import { formatRelativeTime } from "@/lib/utils";
import { useBlocksMessages } from "@/hooks/useBlocksMessages";

function formatTime(iso: string) {
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false });
}

function StateIcon({ state }: { state: AIExecutionState }) {
  if (state === "DONE") {
    return (
      <div className="h-5 w-5 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center flex-shrink-0">
        <svg className="h-3 w-3 text-green-600 dark:text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
        </svg>
      </div>
    );
  }
  if (state === "FAILED") {
    return (
      <div className="h-5 w-5 rounded-full bg-red-100 dark:bg-red-900/30 flex items-center justify-center flex-shrink-0">
        <svg className="h-3 w-3 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </div>
    );
  }
  if (state === "CANCELED") {
    return (
      <div className="h-5 w-5 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center flex-shrink-0">
        <svg className="h-3 w-3 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636" />
        </svg>
      </div>
    );
  }
  return (
    <div className="h-5 w-5 rounded-full bg-accent/20 flex items-center justify-center flex-shrink-0">
      <div className="h-2 w-2 rounded-full bg-accent animate-pulse" />
    </div>
  );
}

function MessageRow({ msg, isLast }: { msg: BlocksMessage; isLast: boolean }) {
  return (
    <div className={`flex gap-2.5 ${!isLast ? "pb-2" : ""}`}>
      <div className="flex flex-col items-center mt-0.5">
        <div className="h-1.5 w-1.5 rounded-full bg-border-default flex-shrink-0 mt-1" />
        {!isLast && <div className="w-px flex-1 bg-border-default mt-1" />}
      </div>
      <div className="flex-1 min-w-0 pb-1">
        <span className="text-xs text-text-tertiary tabular-nums mr-2">{formatTime(msg.createdAt)}</span>
        <span className="text-xs text-text-secondary">{msg.message}</span>
      </div>
    </div>
  );
}

interface Props {
  session: BlocksSession;
  projectId: string;
  issueId: string;
  onCancel?: () => void;
  cancelPending?: boolean;
}

export function BlocksActivityFeed({ session, projectId, issueId, onCancel, cancelPending }: Props) {
  const { executionState } = session;
  const isTerminal = ["DONE", "FAILED", "CANCELED"].includes(executionState);

  const { data: messages, isLoading, isError } = useBlocksMessages(
    projectId,
    issueId,
    session.id,
    executionState
  );

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          Blocks Activity
        </p>
        {!isTerminal && onCancel && (
          <button
            onClick={onCancel}
            disabled={cancelPending}
            className="text-xs text-text-tertiary hover:text-destructive transition-colors disabled:opacity-50"
          >
            {cancelPending ? "Canceling…" : "Cancel"}
          </button>
        )}
      </div>

      <div className="flex items-center gap-2">
        <StateIcon state={executionState} />
        <span className={`text-sm font-medium ${
          executionState === "FAILED" ? "text-red-600 dark:text-red-400" :
          executionState === "CANCELED" ? "text-text-tertiary" :
          executionState === "DONE" ? "text-green-700 dark:text-green-400" :
          "text-text-primary"
        }`}>
          {executionState === "CREATED" && "Initializing…"}
          {executionState === "PLANNING" && "Planning implementation…"}
          {executionState === "EXECUTING" && "Writing code…"}
          {executionState === "WAITING_REVIEW" && "Awaiting review"}
          {executionState === "PR_OPENED" && "Pull request opened"}
          {executionState === "DONE" && "Completed"}
          {executionState === "FAILED" && "Failed"}
          {executionState === "CANCELED" && "Canceled"}
        </span>
      </div>

      {isLoading && (
        <div className="space-y-2">
          {[1, 2].map((i) => (
            <div key={i} className="flex gap-2.5">
              <div className="h-1.5 w-1.5 rounded-full bg-border-default mt-2 flex-shrink-0" />
              <div className="h-3 bg-border-default rounded animate-pulse flex-1" />
            </div>
          ))}
        </div>
      )}

      {isError && (
        <p className="text-xs text-text-tertiary">Unable to load activity messages.</p>
      )}

      {!isLoading && !isError && messages && messages.length > 0 && (
        <div className="max-h-48 overflow-y-auto space-y-0 pr-1">
          {messages.map((msg, idx) => (
            <MessageRow key={msg.id} msg={msg} isLast={idx === messages.length - 1} />
          ))}
        </div>
      )}

      {!isLoading && !isError && messages && messages.length === 0 && (
        <p className="text-xs text-text-tertiary">No activity yet.</p>
      )}

      {session.errorMessage && (executionState === "FAILED" || executionState === "CANCELED") && (
        <div className={`rounded-lg px-3 py-2 text-xs ${
          executionState === "FAILED"
            ? "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400"
            : "bg-bg-surface text-text-tertiary"
        }`}>
          {session.errorMessage}
        </div>
      )}

      {session.sessionUrl && (
        <a
          href={session.sessionUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-surface px-3 py-2 text-sm text-text-primary hover:bg-bg-surface-hover transition-colors"
        >
          <svg className="h-4 w-4 text-accent flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
          <span className="flex-1 truncate">View in Blocks Dashboard</span>
          <svg className="h-3.5 w-3.5 text-text-tertiary flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
          </svg>
        </a>
      )}

      {session.prUrl && (
        <a
          href={session.prUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-surface px-3 py-2 text-sm text-text-primary hover:bg-bg-surface-hover transition-colors"
        >
          <svg className="h-4 w-4 text-accent flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
          </svg>
          <span className="flex-1 truncate">
            {session.branchName ?? "View Pull Request"}
            {session.prNumber && ` #${session.prNumber}`}
          </span>
          <svg className="h-3.5 w-3.5 text-text-tertiary flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
          </svg>
        </a>
      )}

      <div className="text-xs text-text-tertiary space-y-0.5">
        <p>Started {formatRelativeTime(session.createdAt)} · Updated {formatRelativeTime(session.updatedAt)}</p>
        {session.providerSessionId && (
          <p className="font-mono truncate" title={session.providerSessionId}>
            ID: {session.providerSessionId}
          </p>
        )}
      </div>
    </div>
  );
}
