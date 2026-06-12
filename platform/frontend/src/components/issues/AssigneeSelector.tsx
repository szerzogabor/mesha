"use client";

import { useRef, useState, useEffect } from "react";
import { UserSummary } from "@/types";
import { WorkspaceMember } from "@/hooks/useWorkspaceMembers";

interface AssigneeSelectorProps {
  assignee?: UserSummary;
  members: WorkspaceMember[];
  onAssign: (userId: string | null) => void;
  disabled?: boolean;
}

function initials(member: WorkspaceMember | UserSummary): string {
  const name = "name" in member ? member.name : undefined;
  const email = member.email;
  return ((name || email)[0] ?? "?").toUpperCase();
}

function displayName(member: WorkspaceMember | UserSummary): string {
  return ("name" in member && member.name ? member.name : undefined) || member.email;
}

function PlaceholderAvatar() {
  return (
    <div
      className="h-6 w-6 rounded-full border-2 border-dashed border-border-default flex items-center justify-center text-text-tertiary hover:border-accent hover:text-accent transition-colors"
      title="Unassigned — click to assign"
    >
      <svg className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
        <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
      </svg>
    </div>
  );
}

export function AssigneeSelector({
  assignee,
  members,
  onAssign,
  disabled = false,
}: AssigneeSelectorProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        disabled={disabled}
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 hover:opacity-80 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {assignee ? (
          <>
            <div className="h-6 w-6 rounded-full bg-accent-muted flex items-center justify-center text-xs font-medium text-accent-muted-text">
              {initials(assignee)}
            </div>
            <span className="text-sm text-text-primary">{displayName(assignee)}</span>
          </>
        ) : (
          <>
            <PlaceholderAvatar />
            <span className="text-sm text-text-tertiary">Unassigned</span>
          </>
        )}
      </button>

      {open && (
        <div className="absolute left-0 top-full mt-1 z-50 w-56 rounded-lg border border-border-default bg-bg-surface shadow-lg py-1">
          <button
            type="button"
            onClick={() => { onAssign(null); setOpen(false); }}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-secondary hover:bg-bg-surface-hover transition-colors"
          >
            <div className="h-6 w-6 rounded-full border-2 border-dashed border-border-default flex items-center justify-center text-text-tertiary flex-shrink-0">
              <svg className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
              </svg>
            </div>
            <span>No assignee</span>
            {!assignee && <span className="ml-auto text-accent text-xs">✓</span>}
          </button>

          {members.length > 0 && (
            <div className="border-t border-border-default my-1" />
          )}

          {members.map((member) => {
            const isSelected = assignee?.id === member.userId;
            return (
              <button
                key={member.id}
                type="button"
                onClick={() => { onAssign(member.userId); setOpen(false); }}
                className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-primary hover:bg-bg-surface-hover transition-colors"
              >
                <div className="h-6 w-6 rounded-full bg-accent-muted flex items-center justify-center text-xs font-medium text-accent-muted-text flex-shrink-0">
                  {initials(member)}
                </div>
                <span className="truncate">{displayName(member)}</span>
                {isSelected && <span className="ml-auto text-accent text-xs flex-shrink-0">✓</span>}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
