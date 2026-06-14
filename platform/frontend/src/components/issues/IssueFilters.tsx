"use client";

import { IssueStatus, IssuePriority, ProjectStatus, Label } from "@/types";
import { statusLabel } from "@/lib/utils";
import { useLabels } from "@/hooks/useLabels";

const PRIORITIES: IssuePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

const inputClass =
  "border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

interface IssueFiltersProps {
  status?: IssueStatus;
  priority?: IssuePriority;
  search: string;
  projectStatuses?: ProjectStatus[];
  workspaceId: string;
  selectedLabelIds?: string[];
  onStatusChange: (s: IssueStatus | undefined) => void;
  onPriorityChange: (p: IssuePriority | undefined) => void;
  onSearchChange: (s: string) => void;
  onLabelIdsChange: (ids: string[]) => void;
  hideStatusFilter?: boolean;
}

export function IssueFilters({
  status,
  priority,
  search,
  projectStatuses,
  workspaceId,
  selectedLabelIds = [],
  onStatusChange,
  onPriorityChange,
  onSearchChange,
  onLabelIdsChange,
  hideStatusFilter = false,
}: IssueFiltersProps) {
  const { data: labels = [] } = useLabels(workspaceId);

  function toggleLabel(id: string) {
    if (selectedLabelIds.includes(id)) {
      onLabelIdsChange(selectedLabelIds.filter((l) => l !== id));
    } else {
      onLabelIdsChange([...selectedLabelIds, id]);
    }
  }

  return (
    <div className="flex items-center gap-3 flex-wrap">
      <input
        type="search"
        placeholder="Search issues..."
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        className={`${inputClass} w-48`}
      />

      {!hideStatusFilter && (
        <select
          value={status ?? ""}
          onChange={(e) => onStatusChange(e.target.value || undefined)}
          className={inputClass}
        >
          <option value="">All statuses</option>
          {(projectStatuses ?? []).map((s) => (
            <option key={s.id} value={s.name}>
              {statusLabel(s.name)}
            </option>
          ))}
        </select>
      )}

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

      {labels.length > 0 && (
        <LabelFilter
          labels={labels}
          selectedIds={selectedLabelIds}
          onToggle={toggleLabel}
          onClear={() => onLabelIdsChange([])}
        />
      )}
    </div>
  );
}

function LabelFilter({
  labels,
  selectedIds,
  onToggle,
  onClear,
}: {
  labels: Label[];
  selectedIds: string[];
  onToggle: (id: string) => void;
  onClear: () => void;
}) {
  return (
    <div className="relative group">
      <button
        className={`${inputClass} flex items-center gap-1.5 cursor-pointer ${
          selectedIds.length > 0 ? "ring-2 ring-accent border-accent" : ""
        }`}
      >
        <span>Labels</span>
        {selectedIds.length > 0 && (
          <span className="bg-accent text-white text-xs rounded-full w-4 h-4 flex items-center justify-center leading-none">
            {selectedIds.length}
          </span>
        )}
        <svg
          width="12"
          height="12"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="ml-0.5"
        >
          <polyline points="6 9 12 15 18 9" />
        </svg>
      </button>

      <div className="absolute top-full left-0 mt-1 z-20 hidden group-focus-within:block group-hover:block min-w-[180px] bg-bg-surface border border-border-default rounded-lg shadow-lg py-1">
        {selectedIds.length > 0 && (
          <button
            onClick={onClear}
            className="w-full px-3 py-1.5 text-xs text-left text-text-tertiary hover:bg-bg-surface-hover transition-colors border-b border-border-default mb-1"
          >
            Clear filter
          </button>
        )}
        {labels.map((label) => {
          const selected = selectedIds.includes(label.id);
          return (
            <button
              key={label.id}
              onClick={() => onToggle(label.id)}
              className="w-full px-3 py-1.5 text-sm text-left text-text-primary hover:bg-bg-surface-hover transition-colors flex items-center gap-2"
            >
              <span
                className="w-3 h-3 rounded-full flex-shrink-0"
                style={{ backgroundColor: label.color }}
              />
              <span className="flex-1">{label.name}</span>
              {selected && (
                <svg
                  width="12"
                  height="12"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="3"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="text-accent flex-shrink-0"
                >
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
