"use client";

import { useState } from "react";
import { AIExecutionState, BlocksSession } from "@/types";
import { useBlocksSessions } from "@/hooks/useBlocksSessions";
import { formatRelativeTime } from "@/lib/utils";

function stateBadgeClass(state: AIExecutionState): string {
  if (state === "DONE") return "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400";
  if (state === "FAILED") return "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400";
  if (state === "CANCELED") return "bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400";
  return "bg-accent/10 text-accent";
}

function stateLabel(state: AIExecutionState): string {
  switch (state) {
    case "CREATED": return "Starting";
    case "PLANNING": return "Planning";
    case "EXECUTING": return "Coding";
    case "WAITING_REVIEW": return "Awaiting review";
    case "PR_OPENED": return "PR opened";
    case "DONE": return "Done";
    case "FAILED": return "Failed";
    case "CANCELED": return "Canceled";
  }
}

function SessionEntry({ session, index }: { session: BlocksSession; index: number }) {
  const [expanded, setExpanded] = useState(false);
  const isTerminal = ["DONE", "FAILED", "CANCELED"].includes(session.executionState);

  return (
    <li className="flex items-start gap-3 pl-8 relative">
      <div className="absolute left-1 top-0.5 h-5 w-5 rounded-full bg-accent/20 flex items-center justify-center text-[10px] font-bold text-accent">
        AI
      </div>
      <div className="flex-1 min-w-0">
        <button
          className="w-full text-left flex items-center gap-2 group"
          onClick={() => setExpanded((v) => !v)}
        >
          <span className="text-sm text-text-secondary font-medium">
            AI Session #{index}
          </span>
          <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${stateBadgeClass(session.executionState)}`}>
            {!isTerminal && <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse" />}
            {stateLabel(session.executionState)}
          </span>
          <svg
            className={`h-3.5 w-3.5 text-text-tertiary transition-transform ml-auto flex-shrink-0 ${expanded ? "rotate-180" : ""}`}
            fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
          </svg>
        </button>
        <p className="text-xs text-text-tertiary mt-0.5">{formatRelativeTime(session.createdAt)}</p>

        {expanded && (
          <div className="mt-2 space-y-2 rounded-lg border border-border-default bg-bg-surface p-3">
            {session.branchName && (
              <p className="text-xs font-mono text-text-tertiary truncate">{session.branchName}</p>
            )}
            {session.sessionUrl && (
              <a
                href={session.sessionUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 text-xs text-accent hover:underline"
              >
                <svg className="h-3 w-3 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                View in Blocks Dashboard
                <svg className="h-2.5 w-2.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                </svg>
              </a>
            )}
            {session.prUrl && (
              <a
                href={session.prUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 text-xs text-accent hover:underline"
              >
                <svg className="h-3 w-3 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                </svg>
                {session.branchName ?? "View Pull Request"}
                {session.prNumber && ` #${session.prNumber}`}
                <svg className="h-2.5 w-2.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                </svg>
              </a>
            )}
            {session.errorMessage && (
              <p className={`text-xs ${session.executionState === "FAILED" ? "text-red-600 dark:text-red-400" : "text-text-tertiary"}`}>
                {session.errorMessage}
              </p>
            )}
          </div>
        )}
      </div>
    </li>
  );
}

interface Props {
  projectId: string;
  issueId: string;
}

export function SessionsActivityList({ projectId, issueId }: Props) {
  const { data: sessions = [], isLoading } = useBlocksSessions(projectId, issueId);

  if (isLoading || sessions.length === 0) return null;

  const sorted = [...sessions].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
  );

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-text-primary">AI Sessions</h3>
      <div className="relative">
        <div className="absolute left-3 top-0 bottom-0 w-px bg-border-default" />
        <ul className="space-y-4">
          {sorted.map((session, i) => (
            <SessionEntry key={session.id} session={session} index={i + 1} />
          ))}
        </ul>
      </div>
    </div>
  );
}
