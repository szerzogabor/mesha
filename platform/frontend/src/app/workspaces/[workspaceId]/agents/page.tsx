"use client";

import { use, useState } from "react";
import {
  useAgentDefinitions,
  useCreateAgentDefinition,
  useUpdateAgentDefinition,
  useDeleteAgentDefinition,
} from "@/hooks/useAgentDefinitions";
import { Spinner } from "@/components/ui/Spinner";
import { AgentDefinition } from "@/types";

const inputClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

const selectClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

function AgentCard({
  agent,
  onEdit,
  onDelete,
  isDeleting,
}: {
  agent: AgentDefinition;
  onEdit: () => void;
  onDelete: () => void;
  isDeleting: boolean;
}) {
  const [confirmDelete, setConfirmDelete] = useState(false);
  const startupCommands = (agent.providerParameters?.startupCommands as string[]) ?? [];

  return (
    <div className="bg-bg-surface border border-border-default rounded-xl p-5 space-y-3">
      <div className="flex items-start gap-3">
        <div className="h-10 w-10 rounded-full bg-accent/10 flex items-center justify-center text-accent font-bold text-lg shrink-0">
          {agent.title.charAt(0).toUpperCase()}
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-semibold text-text-primary truncate">
            {agent.title}
          </h3>
          <p className="text-xs font-mono text-text-tertiary">{agent.name}</p>
        </div>
        <span
          className={`shrink-0 inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${
            agent.active
              ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
              : "bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400"
          }`}
        >
          {agent.active ? "ACTIVE" : "INACTIVE"}
        </span>
      </div>

      <div className="space-y-1">
        <p className="text-xs text-text-tertiary uppercase tracking-wide font-semibold">
          Provider
        </p>
        <p className="text-sm text-text-secondary">
          {agent.providerType === "BLOCKS" ? "Blocks Agent" : agent.providerType}
          {agent.blocksAgentName && (
            <span className="ml-2 inline-flex items-center rounded-md bg-bg-surface-hover px-2 py-0.5 text-xs font-mono text-text-secondary">
              {agent.blocksAgentName}
            </span>
          )}
        </p>
      </div>

      {agent.description && (
        <p className="text-sm text-text-secondary">{agent.description}</p>
      )}

      {startupCommands.length > 0 && (
        <div className="space-y-1">
          <p className="text-xs text-text-tertiary uppercase tracking-wide font-semibold">
            Startup Commands
          </p>
          <div className="flex flex-wrap gap-1">
            {startupCommands.map((cmd, i) => (
              <span
                key={i}
                className="inline-flex items-center rounded-md bg-bg-surface-hover px-2 py-0.5 text-xs font-mono text-text-secondary"
              >
                {cmd}
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="flex items-center gap-2 pt-1 border-t border-border-default">
        <button
          onClick={onEdit}
          className="px-3 py-1.5 text-xs font-medium border border-border-default text-text-secondary rounded-lg hover:bg-bg-surface-hover transition-colors"
        >
          Edit
        </button>
        {confirmDelete ? (
          <>
            <button
              onClick={onDelete}
              disabled={isDeleting}
              className="px-3 py-1.5 text-xs font-medium bg-destructive text-white rounded-lg hover:bg-destructive/90 transition-colors disabled:opacity-50"
            >
              {isDeleting ? "Deleting..." : "Confirm"}
            </button>
            <button
              onClick={() => setConfirmDelete(false)}
              className="px-3 py-1.5 text-xs font-medium border border-border-default text-text-secondary rounded-lg hover:bg-bg-surface-hover transition-colors"
            >
              Cancel
            </button>
          </>
        ) : (
          <button
            onClick={() => setConfirmDelete(true)}
            className="px-3 py-1.5 text-xs font-medium border border-destructive/50 text-destructive rounded-lg hover:bg-destructive/10 transition-colors"
          >
            Delete
          </button>
        )}
      </div>
    </div>
  );
}

function AgentForm({
  initial,
  onSubmit,
  onCancel,
  isPending,
}: {
  initial?: AgentDefinition;
  onSubmit: (data: {
    title: string;
    name: string;
    description?: string;
    providerType: string;
    systemPrompt: string;
    providerParameters: Record<string, unknown>;
    blocksAgentName?: string;
    active: boolean;
  }) => Promise<void>;
  onCancel: () => void;
  isPending: boolean;
}) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [providerType, setProviderType] = useState<string>(initial?.providerType ?? "BLOCKS");
  const [systemPrompt, setSystemPrompt] = useState(initial?.systemPrompt ?? "");
  const [startupCommands, setStartupCommands] = useState(
    ((initial?.providerParameters?.startupCommands as string[]) ?? []).join("\n")
  );
  const [blocksAgentName, setBlocksAgentName] = useState(initial?.blocksAgentName ?? "");
  const [active, setActive] = useState(initial?.active ?? true);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const commands = startupCommands
        .split("\n")
        .map((c) => c.trim())
        .filter(Boolean);
      await onSubmit({
        title: title.trim(),
        name: name.trim(),
        description: description.trim() || undefined,
        providerType,
        systemPrompt: systemPrompt.trim(),
        providerParameters: commands.length > 0 ? { startupCommands: commands } : {},
        blocksAgentName: blocksAgentName.trim() || undefined,
        active,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save agent");
    }
  };

  const isValid = title.trim() && name.trim() && systemPrompt.trim();

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-bg-surface border border-border-default rounded-xl p-6 space-y-6"
    >
      <h2 className="text-lg font-semibold text-text-primary">
        {initial ? "Edit Agent" : "Create Agent"}
      </h2>

      <div className="space-y-4">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          General
        </p>
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1">
            Title
          </label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Senior Backend Developer"
            maxLength={150}
            required
            className={inputClass}
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1">
            Name
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) =>
              setName(
                e.target.value
                  .toLowerCase()
                  .replace(/[^a-z0-9-]/g, "-")
                  .replace(/-+/g, "-")
              )
            }
            placeholder="e.g. senior-backend-dev"
            maxLength={100}
            required
            className={inputClass}
          />
          <p className="text-xs text-text-tertiary mt-1">
            Unique identifier. Lowercase kebab-case only.
          </p>
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1">
            Description
          </label>
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Short explanation of what this agent does"
            className={inputClass}
          />
        </div>
      </div>

      <div className="space-y-4">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          Provider
        </p>
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1">
            Provider Type
          </label>
          <select
            value={providerType}
            onChange={(e) => setProviderType(e.target.value)}
            className={selectClass}
          >
            <option value="BLOCKS">Blocks</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1">
            Blocks Agent Name
          </label>
          <input
            type="text"
            value={blocksAgentName}
            onChange={(e) => setBlocksAgentName(e.target.value)}
            placeholder="e.g. claude, codex"
            maxLength={100}
            className={inputClass}
          />
          <p className="text-xs text-text-tertiary mt-1">
            The Blocks agent to use for sessions. Leave blank to use the workspace default.
          </p>
        </div>
      </div>

      <div className="space-y-4">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          System Prompt
        </p>
        <textarea
          value={systemPrompt}
          onChange={(e) => setSystemPrompt(e.target.value)}
          placeholder="You are a senior software engineer..."
          rows={6}
          required
          className={inputClass + " resize-none"}
        />
      </div>

      <div className="space-y-4">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          Provider Settings
        </p>
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1">
            Startup Commands
          </label>
          <textarea
            value={startupCommands}
            onChange={(e) => setStartupCommands(e.target.value)}
            placeholder={"/sonnet\n/ultrathink"}
            rows={3}
            className={inputClass + " resize-none font-mono"}
          />
          <p className="text-xs text-text-tertiary mt-1">
            One command per line. These will be sent when the agent starts a session.
          </p>
        </div>
      </div>

      <div className="space-y-4">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          Status
        </p>
        <label className="flex items-center gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
            className="h-4 w-4 rounded border-border-default text-accent focus:ring-accent"
          />
          <span className="text-sm text-text-primary">Active</span>
        </label>
        <p className="text-xs text-text-tertiary">
          Inactive agents cannot be assigned to issues.
        </p>
      </div>

      {error && (
        <p className="text-xs text-destructive">{error}</p>
      )}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={!isValid || isPending}
          className="px-4 py-2 bg-accent text-white text-sm rounded-lg hover:bg-accent/90 disabled:opacity-50 transition-colors"
        >
          {isPending ? "Saving..." : initial ? "Update" : "Create"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm text-text-secondary rounded-lg hover:bg-bg-surface-hover transition-colors"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}

export default function AgentsPage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = use(params);
  const { data: agents = [], isLoading, isError } = useAgentDefinitions(workspaceId);
  const createAgent = useCreateAgentDefinition(workspaceId);
  const updateAgent = useUpdateAgentDefinition(workspaceId);
  const deleteAgent = useDeleteAgentDefinition(workspaceId);

  const [showForm, setShowForm] = useState(false);
  const [editingAgent, setEditingAgent] = useState<AgentDefinition | null>(null);

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
            Failed to load AI Agents
          </p>
          <p className="text-xs text-red-600 dark:text-red-400">
            There was an error fetching your agent configurations. Please refresh the page.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-text-primary">AI Agents</h1>
          <p className="text-text-muted mt-1 text-sm">
            {agents.length} agent{agents.length !== 1 ? "s" : ""} registered
          </p>
        </div>
        {!showForm && !editingAgent && (
          <button
            onClick={() => setShowForm(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent/90 transition-colors"
          >
            + Create Agent
          </button>
        )}
      </div>

      {(showForm || editingAgent) && (
        <div className="mb-6">
          <AgentForm
            initial={editingAgent ?? undefined}
            isPending={createAgent.isPending || updateAgent.isPending}
            onCancel={() => {
              setShowForm(false);
              setEditingAgent(null);
            }}
            onSubmit={async (data) => {
              if (editingAgent) {
                await updateAgent.mutateAsync({ agentId: editingAgent.id, ...data });
                setEditingAgent(null);
              } else {
                await createAgent.mutateAsync(data);
                setShowForm(false);
              }
            }}
          />
        </div>
      )}

      {agents.length === 0 && !showForm ? (
        <div className="bg-bg-surface border border-border-default rounded-xl p-8 text-center">
          <div className="mx-auto h-12 w-12 rounded-full bg-accent/10 flex items-center justify-center mb-3">
            <svg
              className="h-6 w-6 text-accent"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456z"
              />
            </svg>
          </div>
          <p className="text-sm text-text-secondary mb-1">No agents configured yet</p>
          <p className="text-xs text-text-tertiary mb-4">
            Create reusable AI agent configurations to assign to your tickets.
          </p>
          <button
            onClick={() => setShowForm(true)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent/90 transition-colors"
          >
            + Create Agent
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {agents.map((agent) => (
            <AgentCard
              key={agent.id}
              agent={agent}
              onEdit={() => {
                setEditingAgent(agent);
                setShowForm(false);
              }}
              onDelete={() => deleteAgent.mutate(agent.id)}
              isDeleting={deleteAgent.isPending}
            />
          ))}
        </div>
      )}
    </div>
  );
}
