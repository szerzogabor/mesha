"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useIssue, useUpdateIssue } from "@/hooks/useIssues";
import { useComments, useCreateComment } from "@/hooks/useComments";
import { useActivity } from "@/hooks/useActivity";
import { CommentThread } from "@/components/comments/CommentThread";
import { ActivityFeed } from "@/components/activity/ActivityFeed";
import { Spinner } from "@/components/ui/Spinner";
import { AISessionsPanel } from "@/components/blocks/AISessionsPanel";
import { ResourcesPanel } from "@/components/blocks/ResourcesPanel";
import { SessionsActivityList } from "@/components/blocks/SessionsActivityList";
import { SessionChatDrawer } from "@/components/blocks/SessionChatDrawer";
import { IssueStatus, IssuePriority, BlocksSession } from "@/types";
import { formatRelativeTime } from "@/lib/utils";

const STATUSES: IssueStatus[] = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE"];
const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const selectClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

export default function IssueDetailPage({
  params,
}: {
  params: Promise<{ workspaceId: string; projectId: string; issueId: string }>;
}) {
  const { workspaceId, projectId, issueId } = use(params);

  const { data: issue, isLoading } = useIssue(projectId, issueId);
  const { data: comments = [] } = useComments(issueId);
  const { data: activity = [] } = useActivity(projectId, issueId);

  const updateIssue = useUpdateIssue(projectId, issueId);
  const createComment = useCreateComment(issueId);

  const [editingTitle, setEditingTitle] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editingDesc, setEditingDesc] = useState(false);
  const [editDesc, setEditDesc] = useState("");
  const [activeTab, setActiveTab] = useState<"comments" | "activity">("comments");
  const [selectedSession, setSelectedSession] = useState<{ session: BlocksSession; index: number } | null>(null);

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
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s.replace("_", " ")}
                  </option>
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

            {issue.labels.length > 0 && (
              <div>
                <label className="block text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-1">
                  Labels
                </label>
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
              </div>
            )}
          </div>

          <ResourcesPanel
            projectId={projectId}
            issueId={issueId}
          />

          <AISessionsPanel
            workspaceId={workspaceId}
            projectId={projectId}
            issueId={issueId}
          />

          <div className="bg-bg-surface rounded-xl border border-border-default p-4 text-xs text-text-tertiary space-y-1">
            <p><span className="text-text-secondary font-medium">Created:</span> {new Date(issue.createdAt).toLocaleString()}</p>
            <p><span className="text-text-secondary font-medium">Updated:</span> {new Date(issue.updatedAt).toLocaleString()}</p>
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
