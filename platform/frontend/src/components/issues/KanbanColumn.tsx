"use client";

import { useDroppable } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { Issue, IssueStatus } from "@/types";
import { KanbanCard } from "./KanbanCard";
import { cn, statusLabel } from "@/lib/utils";

const columnBadgeStyles: Record<IssueStatus, string> = {
  BACKLOG: "bg-[color-mix(in_srgb,var(--text-tertiary)_15%,transparent)] text-text-secondary",
  TODO: "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400",
  IN_PROGRESS:
    "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400",
  REVIEW: "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400",
  DONE: "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400",
};

interface KanbanColumnProps {
  status: IssueStatus;
  issues: Issue[];
  workspaceId: string;
  projectId: string;
}

export function KanbanColumn({ status, issues, workspaceId, projectId }: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({
    id: status,
    data: { status, type: "column" },
  });

  return (
    <div className="flex flex-col w-72 flex-shrink-0">
      <div className="flex items-center gap-2 mb-3 px-1">
        <h3 className="text-sm font-semibold text-text-primary">{statusLabel(status)}</h3>
        <span
          className={cn(
            "text-xs font-medium px-1.5 py-0.5 rounded-full",
            columnBadgeStyles[status]
          )}
        >
          {issues.length}
        </span>
      </div>

      <div
        ref={setNodeRef}
        className={cn(
          "flex flex-col gap-2 flex-1 p-2 rounded-xl min-h-[120px] transition-colors",
          isOver ? "bg-accent/5 ring-1 ring-inset ring-accent/30" : "bg-bg-surface-hover"
        )}
      >
        <SortableContext items={issues.map((i) => i.id)} strategy={verticalListSortingStrategy}>
          {issues.map((issue) => (
            <KanbanCard
              key={issue.id}
              issue={issue}
              workspaceId={workspaceId}
              projectId={projectId}
            />
          ))}
        </SortableContext>

        {issues.length === 0 && (
          <div className="flex items-center justify-center py-6">
            <p className="text-xs text-text-tertiary">No issues</p>
          </div>
        )}
      </div>
    </div>
  );
}
