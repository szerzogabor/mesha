"use client";

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

function SessionEntry({
  session,
  index,
  onSelect,
}: {
  session: BlocksSession;
  index: number;
  onSelect: () => void;
}) {
  const isTerminal = ["DONE", "FAILED", "CANCELED"].includes(session.executionState);

  return (
    <li className="flex items-start gap-3 pl-8 relative">
      <div className="absolute left-1 top-0.5 h-5 w-5 rounded-full bg-accent/20 flex items-center justify-center text-[10px] font-bold text-accent">
        AI
      </div>
      <div className="flex-1 min-w-0">
        <button
          className="w-full text-left flex items-center gap-2 rounded-lg hover:bg-bg-surface-hover px-2 py-1 -mx-2 transition-colors group"
          onClick={onSelect}
        >
          <span className="text-sm text-text-secondary font-medium">
            AI Session #{index}
          </span>
          <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${stateBadgeClass(session.executionState)}`}>
            {!isTerminal && <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse" />}
            {stateLabel(session.executionState)}
          </span>
          {(session.sessionUrl || session.prUrl) && (
            <span className="ml-auto text-xs text-text-tertiary group-hover:text-accent transition-colors flex-shrink-0">
              Open chat →
            </span>
          )}
          {!session.sessionUrl && !session.prUrl && (
            <span className="ml-auto text-xs text-text-tertiary group-hover:text-accent transition-colors flex-shrink-0">
              View →
            </span>
          )}
        </button>
        <p className="text-xs text-text-tertiary mt-0.5 pl-2">{formatRelativeTime(session.createdAt)}</p>
      </div>
    </li>
  );
}

interface Props {
  projectId: string;
  issueId: string;
  onSelectSession: (session: BlocksSession, index: number) => void;
}

export function SessionsActivityList({ projectId, issueId, onSelectSession }: Props) {
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
            <SessionEntry
              key={session.id}
              session={session}
              index={i + 1}
              onSelect={() => onSelectSession(session, i + 1)}
            />
          ))}
        </ul>
      </div>
    </div>
  );
}
