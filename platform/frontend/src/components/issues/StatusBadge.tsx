import { Badge } from "@/components/ui/Badge";
import { IssueStatus } from "@/types";
import { statusLabel } from "@/lib/utils";
import { cn } from "@/lib/utils";

const defaultStatusStyles: Record<string, string> = {
  BACKLOG: "bg-[color-mix(in_srgb,var(--text-tertiary)_15%,transparent)] text-text-secondary",
  TODO: "bg-[color-mix(in_srgb,#3b82f6_15%,transparent)] text-blue-500",
  IN_PROGRESS: "bg-[color-mix(in_srgb,#f59e0b_15%,transparent)] text-yellow-600 dark:text-yellow-400",
  REVIEW: "bg-accent-muted text-accent-muted-text",
  DONE: "bg-success-muted text-success",
};

const fallbackStyle = "bg-[color-mix(in_srgb,var(--text-tertiary)_15%,transparent)] text-text-secondary";

interface StatusBadgeProps {
  status: IssueStatus;
  className?: string;
}

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const style = defaultStatusStyles[status] ?? fallbackStyle;
  return (
    <Badge className={cn(style, className)}>
      {statusLabel(status)}
    </Badge>
  );
}
