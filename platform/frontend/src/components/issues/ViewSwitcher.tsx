import { cn } from "@/lib/utils";

export type ViewMode = "list" | "kanban";

interface ViewSwitcherProps {
  view: ViewMode;
  onViewChange: (view: ViewMode) => void;
}

export function ViewSwitcher({ view, onViewChange }: ViewSwitcherProps) {
  return (
    <div className="flex items-center gap-0.5 bg-bg-surface-hover rounded-lg p-1 border border-border-default">
      <button
        onClick={() => onViewChange("list")}
        className={cn(
          "flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-all",
          view === "list"
            ? "bg-bg-surface text-text-primary shadow-sm"
            : "text-text-tertiary hover:text-text-secondary"
        )}
        aria-label="List view"
        aria-pressed={view === "list"}
      >
        <ListIcon className="w-4 h-4" />
        <span className="hidden sm:inline">List</span>
      </button>
      <button
        onClick={() => onViewChange("kanban")}
        className={cn(
          "flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium transition-all",
          view === "kanban"
            ? "bg-bg-surface text-text-primary shadow-sm"
            : "text-text-tertiary hover:text-text-secondary"
        )}
        aria-label="Board view"
        aria-pressed={view === "kanban"}
      >
        <KanbanIcon className="w-4 h-4" />
        <span className="hidden sm:inline">Board</span>
      </button>
    </div>
  );
}

function ListIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
      <rect x="1" y="2" width="14" height="2.5" rx="1.25" />
      <rect x="1" y="6.75" width="14" height="2.5" rx="1.25" />
      <rect x="1" y="11.5" width="14" height="2.5" rx="1.25" />
    </svg>
  );
}

function KanbanIcon({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
      <rect x="1" y="1" width="4" height="10" rx="1" />
      <rect x="6" y="1" width="4" height="7" rx="1" />
      <rect x="11" y="1" width="4" height="13" rx="1" />
    </svg>
  );
}
