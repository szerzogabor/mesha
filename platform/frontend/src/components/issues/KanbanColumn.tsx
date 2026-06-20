"use client";

import { useDroppable } from "@dnd-kit/core";
import { useSortable, SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Issue, ProjectStatus } from "@/types";
import { KanbanCard } from "./KanbanCard";
import { cn, statusLabel } from "@/lib/utils";

interface KanbanColumnProps {
  status: ProjectStatus;
  issues: Issue[];
  allStatuses: ProjectStatus[];
  workspaceId: string;
  projectId: string;
  onMoveStatus: (issueId: string, status: string) => void;
  onCreateIssue?: () => void;
}

export function KanbanColumn({ status, issues, allStatuses, workspaceId, projectId, onMoveStatus, onCreateIssue }: KanbanColumnProps) {
  const { setNodeRef: setDropRef, isOver } = useDroppable({
    id: status.name,
    data: { status: status.name, type: "column" },
  });

  const {
    attributes,
    listeners,
    setNodeRef: setSortRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: `col-${status.id}`,
    data: { type: "column-header", statusId: status.id },
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div ref={setSortRef} style={style} className="flex flex-col w-72 flex-shrink-0 min-h-0">
      <div className="flex items-center gap-2 mb-3 px-1 flex-shrink-0">
        <button
          {...attributes}
          {...listeners}
          className="cursor-grab active:cursor-grabbing text-text-tertiary hover:text-text-secondary p-0.5 rounded transition-colors"
          aria-label={`Drag ${statusLabel(status.name)} column`}
        >
          <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor">
            <circle cx="4" cy="3" r="1" />
            <circle cx="8" cy="3" r="1" />
            <circle cx="4" cy="6" r="1" />
            <circle cx="8" cy="6" r="1" />
            <circle cx="4" cy="9" r="1" />
            <circle cx="8" cy="9" r="1" />
          </svg>
        </button>
        <span
          className="w-2 h-2 rounded-full flex-shrink-0"
          style={{ backgroundColor: status.color }}
        />
        <h3 className="text-sm font-semibold text-text-primary">{statusLabel(status.name)}</h3>
        <span
          className="text-xs font-medium px-1.5 py-0.5 rounded-full"
          style={{
            backgroundColor: `${status.color}25`,
            color: status.color,
          }}
        >
          {issues.length}
        </span>
      </div>

      <div
        ref={setDropRef}
        className={cn(
          "flex flex-col gap-2 flex-1 p-2 rounded-xl min-h-[120px] overflow-y-auto touch-pan-y transition-colors",
          isOver ? "bg-accent/5 ring-1 ring-inset ring-accent/30" : "bg-bg-surface-hover"
        )}
      >
        <SortableContext items={issues.map((i) => i.id)} strategy={verticalListSortingStrategy}>
          {issues.map((issue) => (
            <KanbanCard
              key={issue.id}
              issue={issue}
              allStatuses={allStatuses}
              workspaceId={workspaceId}
              projectId={projectId}
              onMoveStatus={onMoveStatus}
            />
          ))}
        </SortableContext>

        {issues.length === 0 && (
          <div className="flex items-center justify-center py-6">
            <p className="text-xs text-text-tertiary">No issues</p>
          </div>
        )}
      </div>

      {onCreateIssue && (
        <button
          onClick={onCreateIssue}
          className="mt-1.5 w-full flex items-center gap-1.5 px-2 py-1.5 text-xs text-text-tertiary hover:text-text-secondary hover:bg-bg-surface-hover rounded-lg transition-colors flex-shrink-0"
        >
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round">
            <line x1="6" y1="1" x2="6" y2="11" />
            <line x1="1" y1="6" x2="11" y2="6" />
          </svg>
          Add issue
        </button>
      )}
    </div>
  );
}
