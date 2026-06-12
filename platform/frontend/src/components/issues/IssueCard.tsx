import Link from "next/link";
import { Issue, LinkedPullRequest } from "@/types";
import { StatusBadge } from "./StatusBadge";
import { PriorityBadge } from "./PriorityBadge";
import { formatRelativeTime } from "@/lib/utils";
import { Badge } from "@/components/ui/Badge";

interface IssueCardProps {
  issue: Issue;
  workspaceId: string;
  projectId: string;
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

export function IssueCard({ issue, workspaceId, projectId }: IssueCardProps) {
  return (
    <Link
      href={`/workspaces/${workspaceId}/projects/${projectId}/issues/${issue.id}`}
      className="block px-4 py-3 hover:bg-bg-surface-hover border-b border-border-default last:border-0 transition-colors"
    >
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 min-w-0">
            {issue.identifier && (
              <span className="shrink-0 text-xs font-mono text-text-tertiary bg-bg-surface-hover px-1.5 py-0.5 rounded">
                {issue.identifier}
              </span>
            )}
            <p className="text-sm font-medium text-text-primary truncate">{issue.title}</p>
          </div>
          <div className="flex items-center gap-2 mt-1 flex-wrap">
            <StatusBadge status={issue.status} />
            <PriorityBadge priority={issue.priority} />
            {issue.assignee ? (
              <span className="inline-flex items-center gap-1">
                <span className="h-4 w-4 rounded-full bg-accent-muted flex items-center justify-center text-[10px] font-medium text-accent-muted-text">
                  {(issue.assignee.name || issue.assignee.email)[0]?.toUpperCase()}
                </span>
                <span className="text-xs text-text-tertiary">
                  {issue.assignee.name || issue.assignee.email}
                </span>
              </span>
            ) : (
              <span
                className="h-4 w-4 rounded-full border border-dashed border-border-default inline-flex items-center justify-center text-text-tertiary"
                title="Unassigned"
              >
                <svg className="h-2.5 w-2.5" viewBox="0 0 20 20" fill="currentColor">
                  <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
                </svg>
              </span>
            )}
            {issue.labels.map((label) => (
              <Badge
                key={label.id}
                style={{ backgroundColor: label.color + "22", color: label.color }}
              >
                {label.name}
              </Badge>
            ))}
            {issue.lastPullRequest && (
              <a
                href={issue.lastPullRequest.htmlUrl}
                target="_blank"
                rel="noopener noreferrer"
                onClick={(e) => e.stopPropagation()}
                className={`text-xs font-medium hover:underline ${getPrStateColor(issue.lastPullRequest)}`}
              >
                {getPrLabel(issue.lastPullRequest)}
              </a>
            )}
          </div>
        </div>
        <span className="text-xs text-text-tertiary whitespace-nowrap">
          {formatRelativeTime(issue.updatedAt)}
        </span>
      </div>
    </Link>
  );
}
