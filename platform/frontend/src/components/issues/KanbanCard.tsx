"use client";

import { useState } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import Link from "next/link";
import { Issue, LinkedPullRequest, ProjectStatus } from "@/types";
import { formatRelativeTime, cn } from "@/lib/utils";
import { PrioritySelector } from "./PrioritySelector";
import { LabelSelector } from "./LabelSelector";
import { AssigneeSelector } from "./AssigneeSelector";
import { MoveStatusSheet } from "./MoveStatusSheet";
import { useUpdateIssueInProject } from "@/hooks/useIssues";
import { useLabels } from "@/hooks/useLabels";
import { useWorkspaceMembers } from "@/hooks/useWorkspaceMembers";

interface KanbanCardProps {
  issue: Issue;
  workspaceId: string;
  projectId: string;
  /** All board statuses — enables the mobile tap-to-move picker. */
  allStatuses?: ProjectStatus[];
  /** Status-change handler (carries rule-violation handling from the page). */
  onMoveStatus?: (issueId: string, status: string) => void;
  overlay?: boolean;
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

export function KanbanCard({ issue, workspaceId, projectId, allStatuses, onMoveStatus, overlay = false }: KanbanCardProps) {
  const [showMove, setShowMove] = useState(false);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: issue.id,
    data: { status: issue.status },
    disabled: overlay,
  });

  // No touchAction on the card itself — only the drag handle gets touch-action:none
  // so the column can scroll freely on mobile when touching the card body.
  const style = overlay
    ? undefined
    : { transform: CSS.Transform.toString(transform), transition };

  const { mutate: updateIssue } = useUpdateIssueInProject(projectId);
  const { data: allLabels = [] } = useLabels(workspaceId);
  const { data: members = [] } = useWorkspaceMembers(workspaceId);

  return (
    <div
      ref={overlay ? undefined : setNodeRef}
      style={style}
      className={cn(
        "relative bg-bg-surface border border-border-default rounded-lg p-3",
        "select-none hover:border-accent/40 hover:shadow-sm transition-all",
        isDragging && !overlay && "opacity-40",
        overlay && "shadow-xl ring-1 ring-accent/20 rotate-1"
      )}
    >
      {/* Drag handle — touch-action:none is scoped here so the card body stays scrollable */}
      {!overlay && (
        <div
          {...attributes}
          {...listeners}
          style={{ touchAction: "none" }}
          className="absolute top-2 right-2 z-10 cursor-grab active:cursor-grabbing p-1 text-text-tertiary hover:text-text-secondary rounded transition-colors"
          aria-label={`Drag ${issue.title}`}
        >
          <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor">
            <circle cx="4" cy="3" r="1" />
            <circle cx="8" cy="3" r="1" />
            <circle cx="4" cy="6" r="1" />
            <circle cx="8" cy="6" r="1" />
            <circle cx="4" cy="9" r="1" />
            <circle cx="8" cy="9" r="1" />
          </svg>
        </div>
      )}

      <Link
        href={`/workspaces/${workspaceId}/projects/${projectId}/issues/${issue.id}`}
        className="block pr-5"
        onClick={(e) => isDragging && e.preventDefault()}
        draggable={false}
      >
        {issue.identifier && (
          <span className="text-xs font-mono text-text-tertiary mb-1 block">
            {issue.identifier}
          </span>
        )}
        <p className="text-sm font-medium text-text-primary mb-2.5 line-clamp-2 leading-snug">
          {issue.title}
        </p>
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2 flex-wrap min-w-0">
            <PrioritySelector
              priority={issue.priority}
              onUpdate={(priority) => updateIssue({ issueId: issue.id, data: { priority } })}
            />
            <LabelSelector
              selectedLabels={issue.labels}
              allLabels={allLabels}
              onUpdate={(labelIds) => updateIssue({ issueId: issue.id, data: { labelIds } })}
            />
          </div>
          <div onPointerDown={(e) => e.stopPropagation()}>
            <AssigneeSelector
              assignee={issue.assignee}
              members={members}
              compact
              onSelect={(sel) =>
                updateIssue({
                  issueId: issue.id,
                  data: sel.type === "human" ? { assigneeId: sel.userId } : { clearAssignee: true },
                })
              }
            />
          </div>
        </div>
        <div className="flex items-center justify-between mt-2">
          <p className="text-xs text-text-tertiary">{formatRelativeTime(issue.updatedAt)}</p>
          {issue.lastPullRequest && (
            <a
              href={issue.lastPullRequest.htmlUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={(e) => e.stopPropagation()}
              className={cn("text-xs font-medium hover:underline", getPrStateColor(issue.lastPullRequest))}
              draggable={false}
            >
              {getPrLabel(issue.lastPullRequest)}
            </a>
          )}
        </div>
      </Link>

      {/* Mobile tap-to-move — drag-and-drop stays the desktop interaction */}
      {!overlay && allStatuses && onMoveStatus && (
        <button
          type="button"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            setShowMove(true);
          }}
          onPointerDown={(e) => e.stopPropagation()}
          className="md:hidden mt-2.5 w-full flex items-center justify-center gap-1.5 px-2 py-2 text-xs font-medium text-text-secondary bg-bg-surface-hover rounded-lg active:bg-bg-app transition-colors min-h-[40px]"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <polyline points="5 9 2 12 5 15" />
            <polyline points="9 5 12 2 15 5" />
            <polyline points="15 19 12 22 9 19" />
            <polyline points="19 9 22 12 19 15" />
            <line x1="2" y1="12" x2="22" y2="12" />
            <line x1="12" y1="2" x2="12" y2="22" />
          </svg>
          Move
        </button>
      )}

      {allStatuses && onMoveStatus && (
        <MoveStatusSheet
          open={showMove}
          onClose={() => setShowMove(false)}
          statuses={allStatuses}
          currentStatus={issue.status}
          issueTitle={issue.title}
          onSelect={(status) => onMoveStatus(issue.id, status)}
        />
      )}
    </div>
  );
}
