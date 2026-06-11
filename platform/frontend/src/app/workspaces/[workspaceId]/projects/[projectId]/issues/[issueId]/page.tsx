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
import { SessionsActivityList } from "@/components/blocks/SessionsActivityList";
import { SessionChatDrawer } from "@/components/blocks/SessionChatDrawer";
import { IssueStatus, IssuePriority, BlocksSession, AgentLlm } from "@/types";
import { useLabels } from "@/hooks/useLabels";
import { formatRelativeTime, statusLabel } from "@/lib/utils";
const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const selectClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

export default function IssueDetailPage({
  params,
}: {
  params: Promise<{ workspaceId: string; projectId: string; issueId: string }>;
}) {
  const { workspaceId, projectId, issueId } = use(params);
  const router = useRouter();

  const { data: issue, isLoading } = useIssue(projectId, issueId);
  const { data: comments = [] } = useComments(issueId);
  const { data: activity = [] } = useActivity(projectId, issueId);
  const { data: projectStatuses = [] } = useProjectStatuses(projectId);

  const updateIssue = useUpdateIssue(projectId, issueId);
  const deleteIssue = useDeleteIssue(projectId);
  const createComment = useCreateComment(issueId);

  const { data: availableLabels = [] } = useLabels(workspaceId);

  const [editingTitle, setEditingTitle] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editingDesc, setEditingDesc] = useState(false);
  const [editDesc, setEditDesc] = useState("");
  const [showLabelPicker, setShowLabelPicker] = useState(false);
  const [activeTab, setActiveTab] = useState<"comments" | "activity">("comments");
  const [selectedSession, setSelectedSession] = useState<{ session: BlocksSession; index: number } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [showAgentPicker, setShowAgentPicker] = useState(false);
  const [pendingAgentLlm, setPendingAgentLlm] = useState<AgentLlm>("claude");

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
                  await updateIssue.mutateAsync({ status: e.target.value as IssueStatus });
                }}
                className={selectClass}
              >
                {projectStatuses.length > 0 ? (
                  projectStatuses.map((s) => (
                    <option key={s.id} value={s.name}>{statusLabel(s.name)}</option>
                  ))
                ) : (
                  <>
                    <option value="BACKLOG">Backlog</option>
                    <option value="TODO">Todo</option>
                    <option value="IN_PROGRESS">In Progress</option>
                    <option value="REVIEW">Review</option>
                    <option value="DONE">Done</option>
                  </>
                )}
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
              {issue.assignee ? (
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="h-6 w-6 rounded-full bg-accent-muted flex items-center justify-center text-xs font-medium text-accent-muted-text">
                      {(issue.assignee.name || issue.assignee.email)[0]?.toUpperCase()}
                    </div>
                    <span className="text-sm text-text-primary">
                      {issue.assignee.name || issue.assignee.email}
                    </span>
                  </div>
                  <button
                    onClick={() => updateIssue.mutateAsync({ clearAssignee: true })}
                    className="text-xs text-text-tertiary hover:text-destructive transition-colors"
                  >
                    ×
                  </button>
                </div>
              ) : (
                <p className="text-sm text-text-tertiary">Unassigned</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-1">
                AI Agent
              </label>
              {issue.agentType === "BLOCKS" ? (
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="h-6 w-6 rounded-full bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center">
                      <svg className="h-3.5 w-3.5 text-purple-600 dark:text-purple-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                    </div>
                    <span className="text-sm text-text-primary">
                      Blocks <span className="text-text-tertiary">/{issue.agentLlm ?? "claude"}</span>
                    </span>
                  </div>
                  <button
                    onClick={() => updateIssue.mutate({ clearAgentAssignee: true })}
                    className="text-xs text-text-tertiary hover:text-destructive transition-colors"
                  >
                    ×
                  </button>
                </div>
              ) : showAgentPicker ? (
                <div className="space-y-2">
                  <select
                    value={pendingAgentLlm}
                    onChange={(e) => setPendingAgentLlm(e.target.value as AgentLlm)}
                    className={selectClass}
                  >
                    <option value="claude">/claude</option>
                    <option value="codex">/codex</option>
                  </select>
                  <div className="flex gap-2">
                    <button
                      onClick={async () => {
                        await updateIssue.mutateAsync({ agentType: "BLOCKS", agentLlm: pendingAgentLlm });
                        setShowAgentPicker(false);
                      }}
                      disabled={updateIssue.isPending}
                      className="flex-1 px-2 py-1 text-xs bg-accent text-white rounded-lg hover:bg-accent-hover transition-colors disabled:opacity-50"
                    >
                      Assign
                    </button>
                    <button
                      onClick={() => setShowAgentPicker(false)}
                      className="flex-1 px-2 py-1 text-xs border border-border-default rounded-lg text-text-secondary hover:bg-bg-surface-hover transition-colors"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => setShowAgentPicker(true)}
                  className="text-sm text-text-tertiary hover:text-text-primary transition-colors"
                >
                  + Assign Blocks AI
                </button>
              )}
            </div>

            <div className="relative">
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-semibold text-text-tertiary uppercase tracking-wide">
                  Labels
                </label>
                <button
                  onClick={() => setShowLabelPicker((v) => !v)}
                  className="text-xs text-text-tertiary hover:text-text-primary transition-colors"
                >
                  {showLabelPicker ? "Done" : "Edit"}
                </button>
              </div>
              {showLabelPicker && availableLabels.length > 0 && (
                <div className="mb-2 flex flex-wrap gap-1.5">
                  {availableLabels.map((label) => {
                    const active = issue.labels.some((l) => l.id === label.id);
                    const newLabelIds = active
                      ? issue.labels.filter((l) => l.id !== label.id).map((l) => l.id)
                      : [...issue.labels.map((l) => l.id), label.id];
                    return (
                      <button
                        key={label.id}
                        type="button"
                        onClick={() => updateIssue.mutate({ labelIds: newLabelIds })}
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
              {issue.labels.length > 0 ? (
                <div className="flex flex-wrap gap-1">
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
                <p className="text-sm text-text-tertiary">No labels</p>
              )}
            </div>
          </div>

          <AISessionsPanel
            workspaceId={workspaceId}
            projectId={projectId}
            issueId={issueId}
            agentLlm={issue.agentLlm}
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
    </div>
  );
}
