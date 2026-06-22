"use client";

import { use } from "react";
import { useConnectorAgents } from "@/hooks/useConnectorAgents";
import { ConnectorTokenGenerator } from "@/components/connector/ConnectorTokenGenerator";
import { Spinner } from "@/components/ui/Spinner";
import { ConnectorAgent } from "@/types";

function StatusBadge({ status }: { status: ConnectorAgent["status"] }) {
  return (
    <span
      className={`shrink-0 inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${
        status === "ONLINE"
          ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
          : "bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400"
      }`}
    >
      <span
        className={`h-1.5 w-1.5 rounded-full ${
          status === "ONLINE" ? "bg-green-500" : "bg-gray-400"
        }`}
      />
      {status}
    </span>
  );
}

function AgentCard({ agent }: { agent: ConnectorAgent }) {
  return (
    <div className="bg-bg-surface border border-border-default rounded-xl p-5 space-y-3">
      <div className="flex items-start gap-3">
        <div className="h-10 w-10 rounded-full bg-accent/10 flex items-center justify-center text-accent font-bold text-lg shrink-0">
          {agent.hostname.charAt(0).toUpperCase()}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-semibold text-text-primary truncate">
            {agent.hostname}
          </h3>
          <p className="text-xs font-mono text-text-tertiary">{agent.executorType}</p>
        </div>
        <StatusBadge status={agent.status} />
      </div>

      <div className="space-y-1">
        <p className="text-xs text-text-tertiary uppercase tracking-wide font-semibold">
          Connector Version
        </p>
        <p className="text-sm text-text-secondary font-mono">{agent.connectorVersion}</p>
      </div>

      {agent.capabilities.length > 0 && (
        <div className="space-y-1">
          <p className="text-xs text-text-tertiary uppercase tracking-wide font-semibold">
            Capabilities
          </p>
          <div className="flex flex-wrap gap-1">
            {agent.capabilities.map((capability) => (
              <span
                key={capability}
                className="inline-flex items-center rounded-md bg-bg-surface-hover px-2 py-0.5 text-xs font-mono text-text-secondary"
              >
                {capability}
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="pt-1 border-t border-border-default text-xs text-text-tertiary" suppressHydrationWarning>
        Last seen: {agent.lastSeenAt ? new Date(agent.lastSeenAt).toLocaleString() : "Never"}
      </div>
    </div>
  );
}

export default function ConnectorAgentsPage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  use(params);
  const { data: agents = [], isLoading, isError } = useConnectorAgents();

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
            Failed to load Connector Agents
          </p>
          <p className="text-xs text-red-600 dark:text-red-400">
            There was an error fetching your registered agents. Please refresh the page.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-4xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-text-primary">Connector Agents</h1>
        <p className="text-text-muted mt-1 text-sm">
          {agents.length} agent{agents.length !== 1 ? "s" : ""} registered
        </p>
      </div>

      <ConnectorTokenGenerator />

      {agents.length === 0 ? (
        <div className="bg-bg-surface border border-border-default rounded-xl p-8 text-center">
          <p className="text-sm text-text-secondary mb-1">No connector agents registered yet</p>
          <p className="text-xs text-text-tertiary">
            Run <code className="font-mono">mesha-connector register --executor-type=&lt;type&gt;</code>{" "}
            from a machine to register it as an agent.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {agents.map((agent) => (
            <AgentCard key={agent.id} agent={agent} />
          ))}
        </div>
      )}
    </div>
  );
}
