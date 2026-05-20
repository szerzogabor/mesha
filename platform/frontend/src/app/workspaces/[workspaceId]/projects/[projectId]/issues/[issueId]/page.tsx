"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useIssue, useUpdateIssue } from "@/hooks/useIssues";
import { useComments, useCreateComment } from "@/hooks/useComments";
import { useActivity } from "@/hooks/useActivity";
import { StatusBadge } from "@/components/issues/StatusBadge";
import { PriorityBadge } from "@/components/issues/PriorityBadge";
import { CommentThread } from "@/components/comments/CommentThread";
import { ActivityFeed } from "@/components/activity/ActivityFeed";
import { Spinner } from "@/components/ui/Spinner";
import { IssueStatus, IssuePriority } from "@/types";
import { formatRelativeTime } from "@/lib/utils";

const STATUSES: IssueStatus[] = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE"];
const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const selectClass =
  "w-full border dark:border-gray-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100";

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

  if (isLoading || !issue) {
    return (
      <div className="flex items-center justify-center py-32">
        <Spinner size="lg" className="text-indigo-600" />
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto">
      <div className="px-6 py-4 bg-white dark:bg-gray-900 border-b dark:border-gray-800 flex items-center gap-3">
        <Link
          href={`/workspaces/${workspaceId}/projects/${projectId}`}
          className="text-sm text-gray-400 hover:text-gray-700 dark:text-gray-500 dark:hover:text-gray-300 transition-colors"
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
                  className="flex-1 text-2xl font-bold border-b-2 border-indigo-500 outline-none py-1 bg-transparent text-gray-900 dark:text-gray-100"
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
                  className="px-3 py-1 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                >
                  Save
                </button>
                <button
                  onClick={() => setEditingTitle(false)}
                  className="px-3 py-1 text-sm border dark:border-gray-700 rounded-lg dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <h1
                className="text-2xl font-bold text-gray-900 dark:text-gray-100 cursor-pointer hover:opacity-70 transition-opacity"
                onClick={() => {
                  setEditTitle(issue.title);
                  setEditingTitle(true);
                }}
                title="Click to edit"
              >
                {issue.title}
              </h1>
            )}
            <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">
              Created {formatRelativeTime(issue.createdAt)} · Updated {formatRelativeTime(issue.updatedAt)}
            </p>
          </div>

          {/* Description */}
          <div className="bg-white dark:bg-gray-900 rounded-xl border dark:border-gray-800 p-4">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">Description</h3>
              {!editingDesc && (
                <button
                  onClick={() => {
                    setEditDesc(issue.description || "");
                    setEditingDesc(true);
                  }}
                  className="text-xs text-gray-400 hover:text-gray-700 dark:text-gray-500 dark:hover:text-gray-300 transition-colors"
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
                  className="w-full border dark:border-gray-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
                />
                <div className="flex gap-2">
                  <button
                    onClick={async () => {
                      await updateIssue.mutateAsync({ description: editDesc });
                      setEditingDesc(false);
                    }}
                    className="px-3 py-1.5 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
                  >
                    Save
                  </button>
                  <button
                    onClick={() => setEditingDesc(false)}
                    className="px-3 py-1.5 text-sm border dark:border-gray-700 rounded-lg dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <p
                className="text-sm text-gray-600 dark:text-gray-400 whitespace-pre-wrap cursor-pointer min-h-[40px]"
                onClick={() => {
                  setEditDesc(issue.description || "");
                  setEditingDesc(true);
                }}
              >
                {issue.description || (
                  <span className="text-gray-400 dark:text-gray-600 italic">No description. Click to add one.</span>
                )}
              </p>
            )}
          </div>

          {/* Comments + Activity tabs */}
          <div className="bg-white dark:bg-gray-900 rounded-xl border dark:border-gray-800 p-4">
            <div className="flex gap-4 mb-4 border-b dark:border-gray-800">
              {(["comments", "activity"] as const).map((tab) => (
                <button
                  key={tab}
                  onClick={() => setActiveTab(tab)}
                  className={`pb-2 text-sm font-medium capitalize transition-colors ${
                    activeTab === tab
                      ? "border-b-2 border-indigo-600 text-indigo-600 dark:text-indigo-400 dark:border-indigo-400"
                      : "text-gray-400 hover:text-gray-700 dark:text-gray-500 dark:hover:text-gray-300"
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

            {activeTab === "activity" && <ActivityFeed events={activity} />}
          </div>
        </div>

        {/* Sidebar: metadata */}
        <div className="space-y-4">
          <div className="bg-white dark:bg-gray-900 rounded-xl border dark:border-gray-800 p-4 space-y-4">
            <div>
              <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1">
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
              <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1">
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
              <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1">
                Assignee
              </label>
              {issue.assignee ? (
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="h-6 w-6 rounded-full bg-indigo-100 dark:bg-indigo-900/40 flex items-center justify-center text-xs font-medium text-indigo-700 dark:text-indigo-400">
                      {(issue.assignee.name || issue.assignee.email)[0]?.toUpperCase()}
                    </div>
                    <span className="text-sm text-gray-700 dark:text-gray-300">
                      {issue.assignee.name || issue.assignee.email}
                    </span>
                  </div>
                  <button
                    onClick={() => updateIssue.mutateAsync({ clearAssignee: true })}
                    className="text-xs text-gray-400 hover:text-red-500 dark:text-gray-500 dark:hover:text-red-400 transition-colors"
                  >
                    ×
                  </button>
                </div>
              ) : (
                <p className="text-sm text-gray-400 dark:text-gray-500">Unassigned</p>
              )}
            </div>

            {issue.labels.length > 0 && (
              <div>
                <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1">
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

          <div className="bg-white dark:bg-gray-900 rounded-xl border dark:border-gray-800 p-4 text-xs text-gray-400 dark:text-gray-500 space-y-1">
            <p><span className="text-gray-500 dark:text-gray-400 font-medium">Created:</span> {new Date(issue.createdAt).toLocaleString()}</p>
            <p><span className="text-gray-500 dark:text-gray-400 font-medium">Updated:</span> {new Date(issue.updatedAt).toLocaleString()}</p>
          </div>
        </div>
      </div>
    </div>
  );
}
