"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { Issue, IssuePriority, LinkedPullRequest } from "@/types";
import { StatusBadge } from "./StatusBadge";
import { PrioritySelector } from "./PrioritySelector";
import { LabelSelector } from "./LabelSelector";
import { AssigneeSelector } from "./AssigneeSelector";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { formatRelativeTime, cn } from "@/lib/utils";
import { useUpdateIssueInProject } from "@/hooks/useIssues";
import { useLabels } from "@/hooks/useLabels";
import { useWorkspaceMembers } from "@/hooks/useWorkspaceMembers";

type SortField = "title" | "status" | "priority" | "updatedAt";
type SortDir = "asc" | "desc";

const PRIORITY_ORDER: Record<IssuePriority, number> = {
  URGENT: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
};

const STATUS_ORDER: Record<string, number> = {
  BACKLOG: 0,
  TODO: 1,
  IN_PROGRESS: 2,
  REVIEW: 3,
  DONE: 4,
};

interface ListViewProps {
  issues: Issue[];
  totalPages: number;
  page: number;
  isLoading: boolean;
  error: Error | null;
  workspaceId: string;
  projectId: string;
  onPageChange: (page: number) => void;
}

function getPrStateColor(pr: LinkedPullRequest): string {
  if (pr.mergedAt) return "text-purple-500";
  if (pr.state === "closed") return "text-red-500";
  return "text-green-500";
}

function getPrLabel(pr: LinkedPullRequest): string {
  const num = pr.githubPrNumber ? `#${pr.githubPrNumber}` : "PR";
  if (pr.mergedAt) return `${num} merged`;
  if (pr.state === "closed") return `${num} closed`;
  return `${num} open`;
}

export function ListView({
  issues,
  totalPages,
  page,
  isLoading,
  error,
  workspaceId,
  projectId,
  onPageChange,
}: ListViewProps) {
  const [sortField, setSortField] = useState<SortField>("updatedAt");
  const [sortDir, setSortDir] = useState<SortDir>("desc");

  const { mutate: updateIssue } = useUpdateIssueInProject(projectId);
  const { data: allLabels = [] } = useLabels(workspaceId);
  const { data: members = [] } = useWorkspaceMembers(workspaceId);

  const sorted = useMemo(() => {
    return [...issues].sort((a, b) => {
      let cmp = 0;
      switch (sortField) {
        case "title":
          cmp = a.title.localeCompare(b.title);
          break;
        case "status":
          cmp = (STATUS_ORDER[a.status] ?? 999) - (STATUS_ORDER[b.status] ?? 999);
          break;
        case "priority":
          cmp = PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority];
          break;
        case "updatedAt":
          cmp = a.updatedAt.localeCompare(b.updatedAt);
          break;
      }
      return sortDir === "asc" ? cmp : -cmp;
    });
  }, [issues, sortField, sortDir]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDir("asc");
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <div className="p-4 bg-destructive-muted text-destructive rounded-lg text-sm">
          {error instanceof Error ? error.message : "Failed to load issues"}
        </div>
      </div>
    );
  }

  if (issues.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-text-tertiary">
        <p className="text-lg mb-1">No issues found</p>
        <p className="text-sm">Try clearing your filters or create a new issue.</p>
      </div>
    );
  }

  const SortButton = ({ field, label }: { field: SortField; label: string }) => (
    <button
      onClick={() => handleSort(field)}
      className={cn(
        "flex items-center gap-1 text-xs font-medium transition-colors",
        sortField === field
          ? "text-accent"
          : "text-text-tertiary hover:text-text-secondary"
      )}
    >
      {label}
      {sortField === field && (
        <span aria-hidden="true">{sortDir === "asc" ? "↑" : "↓"}</span>
      )}
    </button>
  );

  return (
    <div>
      {/* Header row */}
      <div className="hidden md:grid grid-cols-[80px_1fr_130px_110px_110px_36px_90px_100px] gap-4 px-4 py-2 mx-6 mt-4 border-b border-border-default">
        <span className="text-xs font-medium text-text-tertiary">ID</span>
        <SortButton field="title" label="Title" />
        <SortButton field="status" label="Status" />
        <SortButton field="priority" label="Priority" />
        <span className="text-xs font-medium text-text-tertiary">Labels</span>
        <span className="text-xs font-medium text-text-tertiary">Who</span>
        <SortButton field="updatedAt" label="Updated" />
        <span className="text-xs font-medium text-text-tertiary">PR</span>
      </div>

      <div
        className={cn(
          "bg-bg-surface mx-6 rounded-xl border border-border-default",
          "divide-y divide-border-default overflow-hidden",
          "mt-4 md:mt-0 md:rounded-t-none md:border-t-0"
        )}
      >
        {sorted.map((issue) => (
          <div
            key={issue.id}
            className={cn(
              "relative flex flex-col gap-2 px-4 py-3",
              "md:grid md:grid-cols-[80px_1fr_130px_110px_110px_36px_90px_100px] md:items-center md:gap-4",
              "hover:bg-bg-surface-hover transition-colors"
            )}
          >
            {/* Navigate link covers the non-interactive parts */}
            <Link
              href={`/workspaces/${workspaceId}/projects/${projectId}/issues/${issue.id}`}
              className="absolute inset-0"
              tabIndex={-1}
              aria-hidden="true"
            />

            {/* ID */}
            <span className="relative text-xs font-mono text-text-tertiary whitespace-nowrap pointer-events-none">
              {issue.identifier ?? ""}
            </span>

            {/* Title */}
            <Link
              href={`/workspaces/${workspaceId}/projects/${projectId}/issues/${issue.id}`}
              className="relative text-sm font-medium text-text-primary truncate hover:underline"
            >
              {issue.title}
            </Link>

            {/* Status */}
            <div className="relative pointer-events-none">
              <StatusBadge status={issue.status} />
            </div>

            {/* Priority — interactive */}
            <div className="relative">
              <PrioritySelector
                priority={issue.priority}
                onUpdate={(priority) =>
                  updateIssue({ issueId: issue.id, data: { priority } })
                }
              />
            </div>

            {/* Labels — interactive */}
            <div className="relative">
              <LabelSelector
                selectedLabels={issue.labels}
                allLabels={allLabels}
                onUpdate={(labelIds) =>
                  updateIssue({ issueId: issue.id, data: { labelIds } })
                }
              />
            </div>

            {/* Assignee — interactive compact */}
            <div className="relative">
              <AssigneeSelector
                assignee={issue.assignee}
                members={members}
                compact
                onAssign={(userId) =>
                  updateIssue({
                    issueId: issue.id,
                    data: userId ? { assigneeId: userId } : { clearAssignee: true },
                  })
                }
              />
            </div>

            {/* Updated */}
            <span className="relative text-xs text-text-tertiary whitespace-nowrap pointer-events-none">
              {formatRelativeTime(issue.updatedAt)}
            </span>

            {/* PR */}
            <span className="relative text-xs text-text-tertiary">
              {issue.lastPullRequest ? (
                <a
                  href={issue.lastPullRequest.htmlUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  onClick={(e) => e.stopPropagation()}
                  className={`font-medium hover:underline ${getPrStateColor(issue.lastPullRequest)}`}
                >
                  {getPrLabel(issue.lastPullRequest)}
                </a>
              ) : (
                "—"
              )}
            </span>
          </div>
        ))}
      </div>

      <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} />
    </div>
  );
}
