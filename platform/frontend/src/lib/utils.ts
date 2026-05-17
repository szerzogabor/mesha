import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";
import { IssueStatus, IssuePriority } from "@/types";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function statusLabel(status: IssueStatus): string {
  const map: Record<IssueStatus, string> = {
    BACKLOG: "Backlog",
    TODO: "Todo",
    IN_PROGRESS: "In Progress",
    REVIEW: "Review",
    DONE: "Done",
  };
  return map[status] ?? status;
}

export function priorityLabel(priority: IssuePriority): string {
  const map: Record<IssuePriority, string> = {
    LOW: "Low",
    MEDIUM: "Medium",
    HIGH: "High",
    URGENT: "Urgent",
  };
  return map[priority] ?? priority;
}

export function formatRelativeTime(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return date.toLocaleDateString();
}
