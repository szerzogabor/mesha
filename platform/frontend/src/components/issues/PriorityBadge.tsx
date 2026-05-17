import { IssuePriority } from "@/types";
import { priorityLabel } from "@/lib/utils";
import { cn } from "@/lib/utils";

const priorityStyles: Record<IssuePriority, string> = {
  LOW: "text-gray-400",
  MEDIUM: "text-blue-500",
  HIGH: "text-orange-500",
  URGENT: "text-red-600 font-semibold",
};

const priorityIcons: Record<IssuePriority, string> = {
  LOW: "↓",
  MEDIUM: "→",
  HIGH: "↑",
  URGENT: "⚡",
};

interface PriorityBadgeProps {
  priority: IssuePriority;
  className?: string;
}

export function PriorityBadge({ priority, className }: PriorityBadgeProps) {
  return (
    <span className={cn("inline-flex items-center gap-1 text-xs", priorityStyles[priority], className)}>
      <span>{priorityIcons[priority]}</span>
      {priorityLabel(priority)}
    </span>
  );
}
