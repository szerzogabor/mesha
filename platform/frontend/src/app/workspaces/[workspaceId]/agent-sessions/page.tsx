"use client";

import { use, useState } from "react";
import { useAgentSessions } from "@/hooks/useAgentSessions";
import { AgentSessionDrawer } from "@/components/agent-sessions/AgentSessionDrawer";
import { Spinner } from "@/components/ui/Spinner";
import { ConnectorAgentSession, ConnectorAgentSessionStatus } from "@/types";
import { formatRelativeTime } from "@/lib/utils";

function stateBadgeClass(status: ConnectorAgentSessionStatus): string {
  if (status === "COMPLETED") return "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400";
  if (status === "FAILED") return "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400";
  if (status === "CANCELLED") return "bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400";
  if (status === "WAITING_FOR_USER") return "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400";
  return "bg-accent/10 text-accent";
}

const TERMINAL_STATES: ConnectorAgentSessionStatus[] = ["COMPLETED", "FAILED", "CANCELLED"];

function SessionCard({ session, onClick }: { session: ConnectorAgentSession; onClick: () => void }) {
  const isTerminal = TERMINAL_STATES.includes(session.status);
  return (
    <button
      onClick={onClick}
      className="w-full text-left bg-bg-surface border border-border-default rounded-xl p-4 space-y-2 hover:bg-bg-surface-hover transition-colors"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-text-primary truncate">
            {session.issueIdentifier ?? "Agent Session"}
          </p>
          {session.issueTitle && (
            <p className="text-xs text-text-secondary truncate mt-0.5">{session.issueTitle}</p>
          )}
        </div>
        <span className={`shrink-0 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${stateBadgeClass(session.status)}`}>
          {!isTerminal && <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse" />}
          {session.status.replace(/_/g, " ")}
        </span>
      </div>
      {session.prUrl && (
        <p className="text-xs text-accent truncate">
          {session.prTitle ?? `PR #${session.prNumber ?? ""}`}
        </p>
      )}
      <p className="text-xs text-text-tertiary">Created {formatRelativeTime(session.createdAt)}</p>
    </button>
  );
}

export default function AgentSessionsPage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  use(params);
  const { data: sessions = [], isLoading, isError } = useAgentSessions();
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);

  const selectedSession = sessions.find((s) => s.id === selectedSessionId) ?? null;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-6 max-w-4xl mx-auto">
        <div className="rounded-lg border border-red-200 bg-red-50 dark:bg-red-900/20 dark:border-red-800 p-6 text-center">
          <p className="text-sm font-medium text-red-700 dark:text-red-300 mb-1">
            Failed to load Agent Sessions
          </p>
          <p className="text-xs text-red-600 dark:text-red-400">
            There was an error fetching your agent sessions. Please refresh the page.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text-primary">Agent Sessions</h1>
        <p className="text-text-muted mt-1 text-sm">
          {sessions.length} session{sessions.length !== 1 ? "s" : ""}
        </p>
      </div>

      {sessions.length === 0 ? (
        <div className="bg-bg-surface border border-border-default rounded-xl p-8 text-center">
          <p className="text-sm text-text-secondary mb-1">No agent sessions yet</p>
          <p className="text-xs text-text-tertiary">
            Sessions appear here once a connector-backed agent is assigned to a ticket.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {sessions.map((session) => (
            <SessionCard
              key={session.id}
              session={session}
              onClick={() => setSelectedSessionId(session.id)}
            />
          ))}
        </div>
      )}

      {selectedSession && (
        <AgentSessionDrawer session={selectedSession} onClose={() => setSelectedSessionId(null)} />
      )}
    </div>
  );
}
