"use client";

import { IssueStatus, IssuePriority } from "@/types";

const STATUSES: IssueStatus[] = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE"];
const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const inputClass =
  "border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

interface IssueFiltersProps {
  status?: IssueStatus;
  priority?: IssuePriority;
  search: string;
  onStatusChange: (s: IssueStatus | undefined) => void;
  onPriorityChange: (p: IssuePriority | undefined) => void;
  onSearchChange: (s: string) => void;
}

export function IssueFilters({
  status,
  priority,
  search,
  onStatusChange,
  onPriorityChange,
  onSearchChange,
}: IssueFiltersProps) {
  return (
    <div className="flex items-center gap-3 flex-wrap">
      <input
        type="search"
        placeholder="Search issues..."
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        className={`${inputClass} w-48`}
      />

      <select
        value={status ?? ""}
        onChange={(e) => onStatusChange((e.target.value as IssueStatus) || undefined)}
        className={inputClass}
      >
        <option value="">All statuses</option>
        {STATUSES.map((s) => (
          <option key={s} value={s}>
            {s.replace("_", " ")}
          </option>
        ))}
      </select>

      <select
        value={priority ?? ""}
        onChange={(e) => onPriorityChange((e.target.value as IssuePriority) || undefined)}
        className={inputClass}
      >
        <option value="">All priorities</option>
        {PRIORITIES.map((p) => (
          <option key={p} value={p}>
            {p}
          </option>
        ))}
      </select>
    </div>
  );
}
