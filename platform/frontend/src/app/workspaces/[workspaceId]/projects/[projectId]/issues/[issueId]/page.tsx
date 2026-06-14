"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useIssue, useUpdateIssue, useDeleteIssue } from "@/hooks/useIssues";
import { useProjectStatuses } from "@/hooks/useProjectStatuses";
import { useComments, useCreateComment } from "@/hooks/useComments";
import { useActivity } from "@/hooks/useActivity";
import { CommentThread } from "@/components/comments/CommentThread";
import { ActivityFeed } from "@/components/activity/ActivityFeed";
import { Spinner } from "@/components/ui/Spinner";
import { AISessionsPanel } from "@/components/blocks/AISessionsPanel";
import { ResourcesPanel } from "@/components/blocks/ResourcesPanel";
import { IssueLinksPanel } from "@/components/issues/IssueLinksPanel";
import { SessionsActivityList } from "@/components/blocks/SessionsActivityList";
import { SessionChatDrawer } from "@/components/blocks/SessionChatDrawer";
import { IssueStatus, IssuePriority, BlocksSession } from "@/types";
import { useLabels, useCreateLabel } from "@/hooks/useLabels";
import { useIssueEvents } from "@/hooks/useIssueEvents";
import { useWorkspaceMembers } from "@/hooks/useWorkspaceMembers";
import { AssigneeSelector } from "@/components/issues/AssigneeSelector";
import { useActiveAgentDefinitions } from "@/hooks/useAgentDefinitions";
import { useIssueAgents, useAssignAgent, useUnassignAgent } from "@/hooks/useIssueAgents";
import { formatRelativeTime, statusLabel } from "@/lib/utils";
import { RuleViolationDialog } from "@/components/ui/RuleViolationDialog";
import { extractApiErrorMessage, isRuleViolationError } from "@/lib/error-utils";
const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const LABEL_PRESET_COLORS = [
  "#94a3b8", "#3b82f6", "#f59e0b", "#8b5cf6", "#22c55e",
  "#ef4444", "#f97316", "#06b6d4", "#ec4899", "#84cc16",
];

const selectClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

export default function IssueDetailPage({
  params,
}: {
  params: Promise<{ workspaceId: string; projectId: string; issueId: string }>;
}) {
  const { workspaceId, projectId, issueId } = use(params);
  const router = useRouter();

  useIssueEvents(projectId);

  const { data: issue, isLoading } = useIssue(projectId, issueId);
  const { data: comments = [] } = useComments(issueId);
  const { data: activity = [] } = useActivity(projectId, issueId);
  const { data: projectStatuses = [] } = useProjectStatuses(projectId);

  const updateIssue = useUpdateIssue(projectId, issueId);
  const deleteIssue = useDeleteIssue(projectId);
  const createComment = useCreateComment(issueId);

  const { data: availableLabels = [] } = useLabels(workspaceId);
  const createLabel = useCreateLabel(workspaceId);
  const { data: workspaceMembers = [] } = useWorkspaceMembers(workspaceId);
  const { data: activeAgents = [] } = useActiveAgentDefinitions(workspaceId);
  const { data: agentAssignments = [] } = useIssueAgents(projectId, issueId);
  const assignAgent = useAssignAgent(projectId, issueId);
  const unassignAgent = useUnassignAgent(projectId, issueId);

  const [editingTitle, setEditingTitle] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editingDesc, setEditingDesc] = useState(false);
  const [editDesc, setEditDesc] = useState("");
  const [showLabelPicker, setShowLabelPicker] = useState(false);
  const [creatingLabel, setCreatingLabel] = useState(false);
  const [newLabelName, setNewLabelName] = useState("");
  const [newLabelColor, setNewLabelColor] = useState(LABEL_PRESET_COLORS[0]);
  const [newLabelError, setNewLabelError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"comments" | "activity">("comments");
  const [selectedSession, setSelectedSession] = useState<{ session: BlocksSession; index: number } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [ruleViolation, setRuleViolation] = useState<string | null>(null);

  const currentAgent = agentAssignments.length > 0 ? agentAssignments[0] : undefined;
  const assignedAgentDef = currentAgent
    ? activeAgents.find((a) => a.id === currentAgent.agentDefinitionId)
    : undefined;

  if (isLoading || !issue) {
    return (
      <div className="flex items-center justify-center py-32">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto">
      <div className="px-6 py-4 bg-bg-surface border-b border-border-default flex items-center gap-3">
        <Link
          href={`/workspaces/${workspaceId}/projects/${projectId}`}
          className="text-sm text-text-tertiary hover:text-text-primary transition-colors"
        >
          ← Back
        </Link>
      </div>

      <div className="max-w-5xl mx-auto px-6 py-6 grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Title */}
          <div>
            {editingTitle ? (
              <div className="flex items-start gap-2">
                <input
                  autoFocus
                  value={editTitle}
                  onChange={(e) => setEditTitle(e.target.value)}
                  className="flex-1 text-2xl font-bold border-b-2 border-accent outline-none py-1 bg-transparent text-text-primary"
                  onKeyDown={async (e) => {
                    if (e.key === "Enter") {
                      await updateIssue.mutateAsync({ title: editTitle });
                      setEditingTitle(false);
                    }
                    if (e.key === "Escape") setEditingTitle(false);
                  }}
                />
                <button
                  onClick={async () => {
                    await updateIssue.mutateAsync({ title: editTitle });
                    setEditingTitle(false);
                  }}
                  className="px-3 py-1 text-sm bg-accent text-white rounded-lg hover:bg-accent-hover transition-colors"
                >
                  Save
                </button>
                <button
                  onClick={() => setEditingTitle(false)}
                  className="px-3 py-1 text-sm border border-border-default rounded-lg text-text-secondary hover:bg-bg-surface-hover transition-colors"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-3">
                {issue.identifier && (
                  <span className="shrink-0 text-sm font-mono text-text-tertiary bg-bg-surface-hover border border-border-default px-2 py-1 rounded">
                    {issue.identifier}
                  </span>
                )}
                <h1
                  className="text-2xl font-bold text-text-primary cursor-pointer hover:opacity-70 transition-opacity"
                  onClick={() => {
                    setEditTitle(issue.title);
                    setEditingTitle(true);
                  }}
                  title="Click to edit"
                >
                  {issue.title}
                </h1>
              </div>
            )}
            <p className="text-xs text-text-tertiary mt-1">
              Created {formatRelativeTime(issue.createdAt)} · Updated {formatRelativeTime(issue.updatedAt)}
            </p>
          </div>

          {/* Description */}
          <div className="bg-bg-surface rounded-xl border border-border-default p-4">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-text-secondary">Description</h3>
              {!editingDesc && (
                <button
                  onClick={() => {
                    setEditDesc(issue.description || "");
                    setEditingDesc(true);
                  }}
                  className="text-xs text-text-tertiary hover:text-text-primary transition-colors"
                >
                  Edit
                </button>
              )}
            </div>
            {editingDesc ? (
              <div className="space-y-2">
                <textarea
                  autoFocus
                  value={editDesc}
                  onChange={(e) => setEditDesc(e.target.value)}
                  rows={6}
                  className="w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent resize-none"
                />
                <div className="flex gap-2">
                  <button
                    onClick={async () => {
                      await updateIssue.mutateAsync({ description: editDesc });
                      setEditingDesc(false);
                    }}
                    className="px-3 py-1.5 text-sm bg-accent text-white rounded-lg hover:bg-accent-hover transition-colors"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => setEditingDesc(false)}
                    className="px-3 py-1.5 text-sm border border-border-default rounded-lg text-text-secondary hover:bg-bg-surface-hover transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <p
                className="text-sm text-text-secondary whitespace-pre-wrap cursor-pointer min-h-[40px] hover:opacity-80 transition-opacity"
                onClick={() => {
                  setEditDesc(issue.description || "");
                  setEditingDesc(true);
                }}
              >
                {issue.description || (
                  <span className="text-text-tertiary italic">No description. Click to add one.</span>
                )}
              </p>
            )}
          </div>

          {/* Comments + Activity tabs */}
          <div className="bg-bg-surface rounded-xl border border-border-default p-4">
            <div className="flex gap-4 mb-4 border-b border-border-default">
              {(["comments", "activity"] as const).map((tab) => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`pb-2 text-sm font-medium capitalize transition-colors ${
                    activeTab === tab
                      ? "border-b-2 border-accent text-accent"
                      : "text-text-tertiary hover:text-text-secondary"
                  }`}
                >
                  {tab}
                  {tab === "comments" && ` (${comments.length})`}
                  {tab === "activity" && ` (${activity.length})`}
                </button>
              ))}
            </div>

            {activeTab === "comments" && (
              <CommentThread
                comments={comments}
                onAddComment={async (body, parentId) => {
                  await createComment.mutateAsync({ body, parentId });
                }}
              />
            )}

            {activeTab === "activity" && (
              <div className="space-y-6">
                <SessionsActivityList
                  projectId={projectId}
                  issueId={issueId}
                  onSelectSession={(session, index) => setSelectedSession({ session, index })}
                />
                <ActivityFeed events={activity} />
              </div>
            )}
          </div>
        </div>

        {/* Sidebar: metadata */}
        <div className="space-y-4">
          <div className="bg-bg-surface rounded-xl border border-border-default p-4 space-y-4">
            <div>
              <label className="block text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-1">
                Status
              </label>
              <select
                value={issue.status}
                onChange={async (e) => {
                  try {
                    await updateIssue.mutateAsync({ status: e.target.value as IssueStatus });
                  } catch (err) {
                    if (isRuleViolationError(err)) {
                      setRuleViolation(extractApiErrorMessage(err));
                    }
                  }
                }}
                className={selectClass}
              >
                {projectStatuses.map((s) => (
                  <option key={s.id} value={s.name}>{statusLabel(s.name)}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-1">
                Priority
              </label>
              <select
                value={issue.priority}
                onChange={async (e) => {
                  await updateIssue.mutateAsync({ priority: e.target.value as IssuePriority });
                }}
                className={selectClass}
              >
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-1">
                Assignee
              </label>
              <AssigneeSelector
                assignee={issue.assignee}
                assignedAgent={currentAgent}
                members={workspaceMembers}
                activeAgents={activeAgents}
                disabled={updateIssue.isPending || assignAgent.isPending || unassignAgent.isPending}
                onSelect={async (selection) => {
                  if (selection.type === "none") {
                    for (const a of agentAssignments) {
                      await unassignAgent.mutateAsync(a.agentDefinitionId);
                    }
                    updateIssue.mutate({ clearAssignee: true, clearAgentAssignee: true });
                  } else if (selection.type === "human") {
                    for (const a of agentAssignments) {
                      await unassignAgent.mutateAsync(a.agentDefinitionId);
                    }
                    updateIssue.mutate({ assigneeId: selection.userId, clearAgentAssignee: true });
                  } else if (selection.type === "agent") {
                    const agent = activeAgents.find((a) => a.id === selection.agentId);
                    const agentLlm = (agent?.providerParameters?.agentLlm as string) || "claude";
                    for (const a of agentAssignments) {
                      await unassignAgent.mutateAsync(a.agentDefinitionId);
                    }
                    await updateIssue.mutateAsync({ clearAssignee: true, agentType: "BLOCKS", agentLlm });
                    await assignAgent.mutateAsync(selection.agentId);
                  }
                }}
              />
            </div>

            <div className="relative">
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-semibold text-text-tertiary uppercase tracking-wide">
                  Labels
                </label>
                <button
                  onClick={() => {
                    setShowLabelPicker((v) => !v);
                    setCreatingLabel(false);
                    setNewLabelName("");
                    setNewLabelColor(LABEL_PRESET_COLORS[0]);
                    setNewLabelError(null);
                  }}
                  className="text-xs text-text-tertiary hover:text-text-primary transition-colors"
                >
                  {showLabelPicker ? "Done" : "Edit"}
                </button>
              </div>
              {showLabelPicker && (
                <>
                  {availableLabels.length > 0 && (
                    <div className="mb-2 flex flex-wrap gap-1.5">
                      {availableLabels.map((label) => {
                        const active = issue.labels.some((l) => l.id === label.id);
                        const updatedLabelIds = active
                          ? issue.labels.filter((l) => l.id !== label.id).map((l) => l.id)
                          : [...issue.labels.map((l) => l.id), label.id];
                        return (
                          <button
                            key={label.id}
                            type="button"
                            onClick={() => updateIssue.mutate({ labelIds: updatedLabelIds })}
                            className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium transition-all border"
                            style={{
                              backgroundColor: active ? label.color + "33" : "transparent",
                              color: label.color,
                              borderColor: active ? label.color : label.color + "55",
                            }}
                          >
                            {active && <span className="mr-1">✓</span>}
                            {label.name}
                          </button>
                        );
                      })}
                    </div>
                  )}
                  {creatingLabel ? (
                    <div className="mt-2 space-y-2">
                      <input
                        autoFocus
                        type="text"
                        value={newLabelName}
                        onChange={(e) => { setNewLabelName(e.target.value); setNewLabelError(null); }}
                        placeholder="Label name"
                        className="w-full border border-input-border rounded-lg px-2 py-1 text-xs bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent"
                        onKeyDown={(e) => { if (e.key === "Escape") { setCreatingLabel(false); setNewLabelName(""); setNewLabelError(null); } }}
                      />
                      <div className="flex flex-wrap gap-1">
                        {LABEL_PRESET_COLORS.map((c) => (
                          <button
                            key={c}
                            type="button"
                            onClick={() => setNewLabelColor(c)}
                            className="h-4 w-4 rounded-full transition-transform hover:scale-110"
                            style={{
                              backgroundColor: c,
                              outline: newLabelColor === c ? `2px solid ${c}` : "none",
                              outlineOffset: "2px",
                            }}
                          />
                        ))}
                      </div>
                      {newLabelError && (
                        <p className="text-xs text-destructive">{newLabelError}</p>
                      )}
                      <div className="flex gap-1.5">
                        <button
                          type="button"
                          disabled={!newLabelName.trim() || createLabel.isPending}
                          onClick={async () => {
                            if (!newLabelName.trim()) return;
                            try {
                              const created = await createLabel.mutateAsync({ name: newLabelName.trim(), color: newLabelColor });
                              await updateIssue.mutateAsync({ labelIds: [...issue.labels.map((l) => l.id), created.id] });
                              setCreatingLabel(false);
                              setNewLabelName("");
                              setNewLabelColor(LABEL_PRESET_COLORS[0]);
                              setNewLabelError(null);
                            } catch (err) {
                              setNewLabelError(err instanceof Error ? err.message : "Failed to create label");
                            }
                          }}
                          className="flex-1 px-2 py-1 text-xs bg-accent text-white rounded-lg hover:bg-accent-hover transition-colors disabled:opacity-50"
                        >
                          {createLabel.isPending ? "Creating…" : "Create & add"}
                        </button>
                        <button
                          type="button"
                          onClick={() => { setCreatingLabel(false); setNewLabelName(""); setNewLabelError(null); }}
                          className="flex-1 px-2 py-1 text-xs border border-border-default rounded-lg text-text-secondary hover:bg-bg-surface-hover transition-colors"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setCreatingLabel(true)}
                      className="mt-1 text-xs text-text-tertiary hover:text-text-primary transition-colors"
                    >
                      + Create new label
                    </button>
                  )}
                </>
              )}
              {issue.labels.length > 0 ? (
                <div className="flex flex-wrap gap-1 mt-1">
                  {issue.labels.map((label) => (
                    <span
                      key={label.id}
                      className="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                      style={{ backgroundColor: label.color + "22", color: label.color }}
                    >
                      {label.name}
                    </span>
                  ))}
                </div>
              ) : (
                !showLabelPicker && <p className="text-sm text-text-tertiary">No labels</p>
              )}
            </div>
          </div>

          <IssueLinksPanel
            issueId={issueId}
            projectId={projectId}
            workspaceId={workspaceId}
          />

          <AISessionsPanel
            workspaceId={workspaceId}
            projectId={projectId}
            issueId={issueId}
            agentLlm={issue.agentLlm}
            agentSystemPrompt={assignedAgentDef?.systemPrompt}
          />

          <ResourcesPanel
            projectId={projectId}
            issueId={issueId}
          />

          <div className="bg-bg-surface rounded-xl border border-border-default p-4 text-xs text-text-tertiary space-y-1">
            <p><span className="text-text-secondary font-medium">Created:</span> {new Date(issue.createdAt).toLocaleString()}</p>
            <p><span className="text-text-secondary font-medium">Updated:</span> {new Date(issue.updatedAt).toLocaleString()}</p>
          </div>

          <div className="bg-bg-surface rounded-xl border border-destructive/30 p-4">
            <h3 className="text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-3">Danger Zone</h3>
            {confirmDelete ? (
              <div className="space-y-2">
                <p className="text-xs text-text-secondary">Are you sure? This cannot be undone.</p>
                <div className="flex gap-2">
                  <button
                    onClick={async () => {
                      await deleteIssue.mutateAsync(issueId);
                      router.push(`/workspaces/${workspaceId}/projects/${projectId}`);
                    }}
                    disabled={deleteIssue.isPending}
                    className="flex-1 px-3 py-1.5 text-xs bg-destructive text-white rounded-lg hover:bg-destructive/90 transition-colors disabled:opacity-50"
                  >
                    {deleteIssue.isPending ? "Deleting…" : "Delete"}
                  </button>
                  <button
                    onClick={() => setConfirmDelete(false)}
                    className="flex-1 px-3 py-1.5 text-xs border border-border-default rounded-lg text-text-secondary hover:bg-bg-surface-hover transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <button
                onClick={() => setConfirmDelete(true)}
                className="w-full px-3 py-1.5 text-xs border border-destructive/50 text-destructive rounded-lg hover:bg-destructive/10 transition-colors"
              >
                Delete ticket
              </button>
            )}
          </div>
        </div>
      </div>

      {selectedSession && (
        <SessionChatDrawer
          session={selectedSession.session}
          sessionIndex={selectedSession.index}
          projectId={projectId}
          issueId={issueId}
          onClose={() => setSelectedSession(null)}
        />
      )}

      <RuleViolationDialog message={ruleViolation} onClose={() => setRuleViolation(null)} />
    </div>
  );
}
