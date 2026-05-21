"use client";

import { AIExecutionState, BlocksSession } from "@/types";
import { formatRelativeTime } from "@/lib/utils";

const ORDERED_STATES: AIExecutionState[] = [
  "CREATED",
  "PLANNING",
  "EXECUTING",
  "WAITING_REVIEW",
  "PR_OPENED",
  "DONE",
];

const STATE_LABELS: Record<AIExecutionState, string> = {
  CREATED: "Session Created",
  PLANNING: "AI Planning",
  EXECUTING: "Implementing",
  WAITING_REVIEW: "Waiting for Review",
  PR_OPENED: "Pull Request Opened",
  DONE: "Done",
  FAILED: "Failed",
  CANCELED: "Canceled",
};

const STATE_DESCRIPTIONS: Record<AIExecutionState, string> = {
  CREATED: "Blocks session initialized",
  PLANNING: "AI is analyzing the issue and planning implementation",
  EXECUTING: "AI is writing code and making changes",
  WAITING_REVIEW: "Implementation complete, awaiting human review",
  PR_OPENED: "Pull request created and ready to review",
  DONE: "Workflow completed successfully",
  FAILED: "Workflow encountered an error",
  CANCELED: "Session was canceled",
};

function StateIcon({ state, isCurrent, isPast }: { state: AIExecutionState; isCurrent: boolean; isPast: boolean }) {
  if (state === "FAILED") {
    return (
      <div className="h-7 w-7 rounded-full bg-red-100 dark:bg-red-900/30 flex items-center justify-center">
        <svg className="h-4 w-4 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </div>
    );
  }
  if (state === "CANCELED") {
    return (
      <div className="h-7 w-7 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
        <svg className="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
        </svg>
      </div>
    );
  }
  if (isPast) {
    return (
      <div className="h-7 w-7 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center">
        <svg className="h-4 w-4 text-green-600 dark:text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
        </svg>
      </div>
    );
  }
  if (isCurrent) {
    return (
      <div className="h-7 w-7 rounded-full bg-accent/20 flex items-center justify-center">
        <div className="h-3 w-3 rounded-full bg-accent animate-pulse" />
      </div>
    );
  }
  return (
    <div className="h-7 w-7 rounded-full border-2 border-border-default bg-bg-surface" />
  );
}

interface Props {
  session: BlocksSession;
  onCancel?: () => void;
  cancelPending?: boolean;
}

export function AIExecutionTimeline({ session, onCancel, cancelPending }: Props) {
  const { executionState } = session;
  const isTerminal = ["DONE", "FAILED", "CANCELED"].includes(executionState);
  const isFailed = executionState === "FAILED";
  const isCanceled = executionState === "CANCELED";

  const currentIndex = ORDERED_STATES.indexOf(executionState as AIExecutionState);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          AI Execution
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

      {(isFailed || isCanceled) && (
        <div className={`rounded-lg px-3 py-2 text-xs ${isFailed ? "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400" : "bg-bg-surface text-text-tertiary"}`}>
          <p className="font-medium">{STATE_LABELS[executionState]}</p>
          {session.errorMessage && (
            <p className="mt-1 opacity-80">{session.errorMessage}</p>
          )}
          <p className="mt-1 opacity-60">{formatRelativeTime(session.updatedAt)}</p>
        </div>
      )}

      {!isFailed && !isCanceled && (
        <div className="relative">
          {ORDERED_STATES.map((state, idx) => {
            const isPast = currentIndex > idx;
            const isCurrent = currentIndex === idx;
            return (
              <div key={state} className="flex gap-3">
                <div className="flex flex-col items-center">
                  <StateIcon state={state} isCurrent={isCurrent} isPast={isPast} />
                  {idx < ORDERED_STATES.length - 1 && (
                    <div className={`w-0.5 flex-1 my-1 min-h-[16px] ${isPast ? "bg-green-300 dark:bg-green-700" : "bg-border-default"}`} />
                  )}
                </div>
                <div className="pb-3 flex-1 min-w-0">
                  <p className={`text-sm font-medium ${isCurrent ? "text-text-primary" : isPast ? "text-text-secondary" : "text-text-tertiary"}`}>
                    {STATE_LABELS[state]}
                  </p>
                  {isCurrent && (
                    <p className="text-xs text-text-tertiary mt-0.5">{STATE_DESCRIPTIONS[state]}</p>
                  )}
                  {isCurrent && state === "PR_OPENED" && session.prUrl && (
                    <a
                      href={session.prUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="mt-1 inline-flex items-center gap-1 text-xs text-accent hover:underline"
                    >
                      View PR #{session.prNumber}
                      <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                      </svg>
                    </a>
                  )}
                </div>
              </div>
            );
          })}
        </div>
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

      <p className="text-xs text-text-tertiary">
        Started {formatRelativeTime(session.createdAt)} · Updated {formatRelativeTime(session.updatedAt)}
      </p>
    </div>
  );
}
