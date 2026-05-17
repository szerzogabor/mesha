import Link from "next/link";
import { Issue } from "@/types";
import { StatusBadge } from "./StatusBadge";
import { PriorityBadge } from "./PriorityBadge";
import { formatRelativeTime } from "@/lib/utils";
import { Badge } from "@/components/ui/Badge";

interface IssueCardProps {
  issue: Issue;
  workspaceId: string;
  projectId: string;
}

export function IssueCard({ issue, workspaceId, projectId }: IssueCardProps) {
  return (
    <Link
      href={`/workspaces/${workspaceId}/projects/${projectId}/issues/${issue.id}`}
      className="block px-4 py-3 hover:bg-gray-50 border-b border-gray-100 last:border-0 transition-colors"
    >
      <div className="flex items-start gap-3">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-gray-900 truncate">{issue.title}</p>
          <div className="flex items-center gap-2 mt-1 flex-wrap">
            <StatusBadge status={issue.status} />
            <PriorityBadge priority={issue.priority} />
            {issue.assignee && (
              <span className="text-xs text-gray-500">
                {issue.assignee.name || issue.assignee.email}
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
          </div>
        </div>
        <span className="text-xs text-gray-400 whitespace-nowrap">
          {formatRelativeTime(issue.updatedAt)}
        </span>
      </div>
    </Link>
  );
}
