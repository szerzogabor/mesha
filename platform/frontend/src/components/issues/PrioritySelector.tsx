"use client";

import { useRef, useState, useEffect } from "react";
import { IssuePriority } from "@/types";
import { priorityLabel, cn } from "@/lib/utils";

const PRIORITIES: IssuePriority[] = ["URGENT", "HIGH", "MEDIUM", "LOW"];

const priorityIcons: Record<IssuePriority, string> = {
  LOW: "↓",
  MEDIUM: "→",
  HIGH: "↑",
  URGENT: "⚡",
};

const priorityStyles: Record<IssuePriority, string> = {
  LOW: "text-gray-400",
  MEDIUM: "text-blue-500",
  HIGH: "text-orange-500",
  URGENT: "text-red-600 font-semibold",
};

interface PrioritySelectorProps {
  priority: IssuePriority;
  onUpdate: (priority: IssuePriority) => void;
  disabled?: boolean;
}

export function PrioritySelector({ priority, onUpdate, disabled = false }: PrioritySelectorProps) {
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
    <div
      ref={ref}
      className="relative inline-flex"
      onPointerDown={(e) => e.stopPropagation()}
    >
      <button
        type="button"
        disabled={disabled}
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          setOpen((v) => !v);
        }}
        className={cn(
          "inline-flex items-center gap-1 text-xs rounded px-1 py-0.5",
          "hover:bg-bg-surface-hover transition-colors",
          "disabled:opacity-50 disabled:cursor-not-allowed",
          priorityStyles[priority]
        )}
        title="Change priority"
      >
        <span>{priorityIcons[priority]}</span>
        {priorityLabel(priority)}
      </button>

      {open && (
        <div className="absolute left-0 top-full mt-1 z-50 w-36 rounded-lg border border-border-default bg-bg-surface shadow-lg py-1">
          {PRIORITIES.map((p) => (
            <button
              key={p}
              type="button"
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                onUpdate(p);
                setOpen(false);
              }}
              className={cn(
                "w-full flex items-center gap-2 px-3 py-2 text-xs hover:bg-bg-surface-hover transition-colors",
                priorityStyles[p]
              )}
            >
              <span>{priorityIcons[p]}</span>
              <span>{priorityLabel(p)}</span>
              {priority === p && (
                <span className="ml-auto text-accent text-xs">✓</span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
