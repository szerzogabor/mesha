"use client";

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import Link from "next/link";
import { Issue } from "@/types";
import { PriorityBadge } from "./PriorityBadge";
import { Badge } from "@/components/ui/Badge";
import { formatRelativeTime, cn } from "@/lib/utils";

interface KanbanCardProps {
  issue: Issue;
  workspaceId: string;
  projectId: string;
  overlay?: boolean;
}

export function KanbanCard({ issue, workspaceId, projectId, overlay = false }: KanbanCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: issue.id,
    data: { status: issue.status },
    disabled: overlay,
  });

  const style = overlay
    ? undefined
    : { transform: CSS.Transform.toString(transform), transition, touchAction: "none" };

  return (
    <div
      ref={overlay ? undefined : setNodeRef}
      style={style}
      {...(overlay ? {} : { ...attributes, ...listeners })}
      className={cn(
        "bg-bg-surface border border-border-default rounded-lg p-3",
        "cursor-grab active:cursor-grabbing select-none",
        "hover:border-accent/40 hover:shadow-sm transition-all",
        isDragging && !overlay && "opacity-40",
        overlay && "shadow-xl ring-1 ring-accent/20 rotate-1"
      )}
    >
      <Link
        href={`/workspaces/${workspaceId}/projects/${projectId}/issues/${issue.id}`}
        className="block"
        onClick={(e) => isDragging && e.preventDefault()}
        draggable={false}
      >
        <p className="text-sm font-medium text-text-primary mb-2.5 line-clamp-2 leading-snug">
          {issue.title}
        </p>
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 flex-wrap min-w-0">
            <PriorityBadge priority={issue.priority} />
            {issue.labels.slice(0, 2).map((label) => (
              <Badge
                key={label.id}
                style={{ backgroundColor: label.color + "22", color: label.color }}
              >
                {label.name}
              </Badge>
            ))}
          </div>
          {issue.assignee && (
            <div
              className="w-5 h-5 rounded-full bg-accent-muted flex items-center justify-center text-xs font-medium text-accent-muted-text flex-shrink-0"
              title={issue.assignee.name || issue.assignee.email}
            >
              {(issue.assignee.name || issue.assignee.email)[0]?.toUpperCase()}
            </div>
          )}
        </div>
        <p className="text-xs text-text-tertiary mt-2">{formatRelativeTime(issue.updatedAt)}</p>
      </Link>
    </div>
  );
}
