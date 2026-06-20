"use client";

import { BottomSheet } from "@/components/ui/BottomSheet";
import { ProjectStatus } from "@/types";
import { statusLabel } from "@/lib/utils";

interface MoveStatusSheetProps {
  open: boolean;
  onClose: () => void;
  statuses: ProjectStatus[];
  currentStatus: string;
  issueTitle: string;
  onSelect: (status: string) => void;
}

const CheckIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

/**
 * Phone-friendly status picker used by the Kanban board: tap an issue's
 * "Move" control, choose a destination status, done. This replaces drag-and-drop
 * on touch devices (desktop keeps drag-and-drop intact).
 */
export function MoveStatusSheet({
  open,
  onClose,
  statuses,
  currentStatus,
  issueTitle,
  onSelect,
}: MoveStatusSheetProps) {
  return (
    <BottomSheet open={open} onClose={onClose} title="Move to…">
      <p className="px-2 pb-2 text-xs text-text-tertiary truncate">{issueTitle}</p>
      <ul className="flex flex-col">
        {statuses.map((status) => {
          const isCurrent = status.name === currentStatus;
          return (
            <li key={status.id}>
              <button
                type="button"
                disabled={isCurrent}
                onClick={() => {
                  onSelect(status.name);
                  onClose();
                }}
                className="w-full flex items-center gap-3 px-3 py-3.5 rounded-lg text-left hover:bg-bg-surface-hover transition-colors disabled:opacity-100 min-h-[48px]"
              >
                <span
                  className="h-2.5 w-2.5 rounded-full shrink-0"
                  style={{ backgroundColor: status.color }}
                />
                <span className="flex-1 text-sm font-medium text-text-primary">
                  {statusLabel(status.name)}
                </span>
                {isCurrent && (
                  <span className="text-accent">
                    <CheckIcon />
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ul>
    </BottomSheet>
  );
}
