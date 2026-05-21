"use client";

import { useState, useMemo } from "react";
import Link from "next/link";
import { Issue, IssueStatus, IssuePriority } from "@/types";
import { StatusBadge } from "./StatusBadge";
import { PriorityBadge } from "./PriorityBadge";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { formatRelativeTime, cn } from "@/lib/utils";

type SortField = "title" | "status" | "priority" | "updatedAt";
type SortDir = "asc" | "desc";

const PRIORITY_ORDER: Record<IssuePriority, number> = {
  URGENT: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
};

const STATUS_ORDER: Record<IssueStatus, number> = {
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

  const sorted = useMemo(() => {
    return [...issues].sort((a, b) => {
      let cmp = 0;
      switch (sortField) {
        case "title":
          cmp = a.title.localeCompare(b.title);
          break;
        case "status":
          cmp = STATUS_ORDER[a.status] - STATUS_ORDER[b.status];
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
      <div className="hidden md:grid grid-cols-[1fr_130px_110px_90px] gap-4 px-4 py-2 mx-6 mt-4 border-b border-border-default">
        <SortButton field="title" label="Title" />
        <SortButton field="status" label="Status" />
        <SortButton field="priority" label="Priority" />
        <SortButton field="updatedAt" label="Updated" />
      </div>

      <div
        className={cn(
          "bg-bg-surface mx-6 rounded-xl border border-border-default",
          "divide-y divide-border-default overflow-hidden",
          "mt-4 md:mt-0 md:rounded-t-none md:border-t-0"
        )}
      >
        {sorted.map((issue) => (
          <Link
            key={issue.id}
            href={`/workspaces/${workspaceId}/projects/${projectId}/issues/${issue.id}`}
            className={cn(
              "flex flex-col gap-2 px-4 py-3 hover:bg-bg-surface-hover transition-colors",
              "md:grid md:grid-cols-[1fr_130px_110px_90px] md:items-center md:gap-4"
            )}
          >
            <p className="text-sm font-medium text-text-primary truncate">{issue.title}</p>
            <StatusBadge status={issue.status} />
            <PriorityBadge priority={issue.priority} />
            <span className="text-xs text-text-tertiary whitespace-nowrap">
              {formatRelativeTime(issue.updatedAt)}
            </span>
          </Link>
        ))}
      </div>

      <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} />
    </div>
  );
}
