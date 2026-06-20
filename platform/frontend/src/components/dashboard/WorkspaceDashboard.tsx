"use client";

import Link from "next/link";
import { useConnectorAgents } from "@/hooks/useConnectorAgents";
import { useAgentSessions } from "@/hooks/useAgentSessions";
import { useProjects } from "@/hooks/useProjects";
import { Spinner } from "@/components/ui/Spinner";
import { InstallAppCard } from "@/components/pwa/InstallAppCard";
import { ConnectorAgentSession, ConnectorAgentSessionStatus } from "@/types";
import { formatRelativeTime } from "@/lib/utils";

const TERMINAL_STATES: ConnectorAgentSessionStatus[] = [
  "COMPLETED",
  "FAILED",
  "CANCELLED",
];

function StatCard({
  label,
  value,
  href,
  accent,
}: {
  label: string;
  value: number | string;
  href: string;
  accent?: boolean;
}) {
  return (
    <Link
      href={href}
      className="bg-bg-surface border border-border-default rounded-xl p-4 flex flex-col gap-1 hover:border-accent/40 transition-colors active:scale-[0.98]"
    >
      <span className="text-2xl font-bold tabular-nums text-text-primary">
        <span className={accent ? "text-accent" : undefined}>{value}</span>
      </span>
      <span className="text-xs text-text-tertiary leading-tight">{label}</span>
    </Link>
  );
}

function SessionRow({
  session,
  workspaceId,
}: {
  session: ConnectorAgentSession;
  workspaceId: string;
}) {
  const isTerminal = TERMINAL_STATES.includes(session.status);
  return (
    <Link
      href={`/workspaces/${workspaceId}/agent-sessions`}
      className="flex items-center gap-3 px-4 py-3 hover:bg-bg-surface-hover transition-colors"
    >
      <span
        className={`h-2 w-2 rounded-full shrink-0 ${
          isTerminal ? "bg-text-tertiary" : "bg-accent animate-pulse"
        }`}
      />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-text-primary truncate">
          {session.issueIdentifier ?? session.issueTitle ?? "Agent Session"}
        </p>
        <p className="text-xs text-text-tertiary truncate">
          {session.status.replace(/_/g, " ")} · {formatRelativeTime(session.updatedAt)}
        </p>
      </div>
      {session.prNumber && (
        <span className="text-xs font-medium text-accent shrink-0">
          #{session.prNumber}
        </span>
      )}
    </Link>
  );
}

/**
 * Responsive workspace home dashboard. Reuses existing connector-agent,
 * agent-session, and project hooks — no new endpoints — to surface online
 * agents, running sessions, agent-opened pull requests, and recent activity.
 */
export function WorkspaceDashboard({ workspaceId }: { workspaceId: string }) {
  const { data: agents = [], isLoading: agentsLoading } = useConnectorAgents();
  const { data: sessions = [], isLoading: sessionsLoading } = useAgentSessions();
  const { data: projects = [], isLoading: projectsLoading } = useProjects(workspaceId);

  if (agentsLoading || sessionsLoading || projectsLoading) {
    return (
      <div className="flex items-center justify-center py-32">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  const onlineAgents = agents.filter((a) => a.status === "ONLINE").length;
  const runningSessions = sessions.filter(
    (s) => !TERMINAL_STATES.includes(s.status)
  );
  const openPrs = sessions.filter((s) => s.prUrl && !s.completedAt);
  const recentSessions = [...sessions]
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .slice(0, 6);

  return (
    <div className="max-w-4xl mx-auto px-4 md:px-6 py-5 md:py-8 space-y-6">
      <div>
        <h1 className="text-xl md:text-2xl font-semibold text-text-primary">
          Dashboard
        </h1>
        <p className="text-sm text-text-tertiary mt-0.5">
          Overview of your agents, sessions, and work.
        </p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <StatCard
          label="Online agents"
          value={onlineAgents}
          accent={onlineAgents > 0}
          href={`/workspaces/${workspaceId}/connector-agents`}
        />
        <StatCard
          label="Running sessions"
          value={runningSessions.length}
          accent={runningSessions.length > 0}
          href={`/workspaces/${workspaceId}/agent-sessions`}
        />
        <StatCard
          label="Open pull requests"
          value={openPrs.length}
          href={`/workspaces/${workspaceId}/github`}
        />
        <StatCard
          label="Projects"
          value={projects.length}
          href={`/workspaces/${workspaceId}/projects`}
        />
      </div>

      <InstallAppCard />

      <section className="bg-bg-surface border border-border-default rounded-xl overflow-hidden">
        <header className="flex items-center justify-between px-4 py-3 border-b border-border-default">
          <h2 className="text-sm font-semibold text-text-primary">
            Recent sessions
          </h2>
          <Link
            href={`/workspaces/${workspaceId}/agent-sessions`}
            className="text-xs text-accent hover:underline"
          >
            View all
          </Link>
        </header>
        {recentSessions.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-text-tertiary">
            No sessions yet. Assign an issue to an agent to get started.
          </p>
        ) : (
          <ul className="divide-y divide-border-default">
            {recentSessions.map((session) => (
              <li key={session.id}>
                <SessionRow session={session} workspaceId={workspaceId} />
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-sm font-semibold text-text-primary">Projects</h2>
          <Link
            href={`/workspaces/${workspaceId}/projects`}
            className="text-xs text-accent hover:underline"
          >
            View all
          </Link>
        </div>
        {projects.length === 0 ? (
          <p className="text-sm text-text-tertiary">
            No projects yet. Create one from the sidebar.
          </p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {projects.slice(0, 4).map((project) => (
              <Link
                key={project.id}
                href={`/workspaces/${workspaceId}/projects/${project.id}`}
                className="bg-bg-surface border border-border-default rounded-xl p-4 hover:border-accent/40 transition-colors"
              >
                <p className="font-medium text-text-primary truncate">
                  {project.name}
                </p>
                {project.description && (
                  <p className="text-xs text-text-tertiary truncate mt-0.5">
                    {project.description}
                  </p>
                )}
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
