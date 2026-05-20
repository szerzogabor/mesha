import { Badge } from "@/components/ui/Badge";
import { IssueStatus } from "@/types";
import { statusLabel } from "@/lib/utils";
import { cn } from "@/lib/utils";

const statusStyles: Record<IssueStatus, string> = {
  BACKLOG: "bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400",
  TODO: "bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-400",
  IN_PROGRESS: "bg-yellow-100 text-yellow-700 dark:bg-yellow-900/40 dark:text-yellow-400",
  REVIEW: "bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-400",
  DONE: "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400",
};

interface StatusBadgeProps {
  status: IssueStatus;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  return (
    <Badge className={cn(statusStyles[status], className)}>
      {statusLabel(status)}
    </Badge>
  );
}
