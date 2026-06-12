"use client";

import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import Link from "next/link";
import { Issue, LinkedPullRequest } from "@/types";
import { Badge } from "@/components/ui/Badge";
import { formatRelativeTime, cn } from "@/lib/utils";
import { PrioritySelector } from "./PrioritySelector";
import { LabelSelector } from "./LabelSelector";
import { AssigneeSelector } from "./AssigneeSelector";
import { useUpdateIssueInProject } from "@/hooks/useIssues";
import { useLabels } from "@/hooks/useLabels";
import { useWorkspaceMembers } from "@/hooks/useWorkspaceMembers";

interface KanbanCardProps {
  issue: Issue;
  workspaceId: string;
  projectId: string;
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

export function KanbanCard({ issue, workspaceId, projectId, overlay = false }: KanbanCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: issue.id,
    data: { status: issue.status },
    disabled: overlay,
  });

  const style = overlay
    ? undefined
    : { transform: CSS.Transform.toString(transform), transition, touchAction: "none" };

  const { mutate: updateIssue } = useUpdateIssueInProject(projectId);
  const { data: allLabels = [] } = useLabels(workspaceId);
  const { data: members = [] } = useWorkspaceMembers(workspaceId);

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
              onAssign={(userId) =>
                updateIssue({
                  issueId: issue.id,
                  data: userId ? { assigneeId: userId } : { clearAssignee: true },
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
    </div>
  );
}
