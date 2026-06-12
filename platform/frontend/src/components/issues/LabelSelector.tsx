"use client";

import { useRef, useState, useEffect } from "react";
import { Label } from "@/types";
import { Badge } from "@/components/ui/Badge";

interface LabelSelectorProps {
  selectedLabels: Label[];
  allLabels: Label[];
  onUpdate: (labelIds: string[]) => void;
  disabled?: boolean;
}

export function LabelSelector({
  selectedLabels,
  allLabels,
  onUpdate,
  disabled = false,
}: LabelSelectorProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const selectedIds = new Set(selectedLabels.map((l) => l.id));

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const handleToggle = (labelId: string) => {
    const next = selectedIds.has(labelId)
      ? [...selectedIds].filter((id) => id !== labelId)
      : [...selectedIds, labelId];
    onUpdate(next);
  };

  return (
    <div
      ref={ref}
      className="relative inline-flex items-center"
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
        className="inline-flex items-center gap-1 flex-wrap hover:opacity-80 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
        title="Change labels"
      >
        {selectedLabels.length > 0 ? (
          <>
            {selectedLabels.slice(0, 2).map((label) => (
              <Badge
                key={label.id}
                style={{ backgroundColor: label.color + "22", color: label.color }}
              >
                {label.name}
              </Badge>
            ))}
            {selectedLabels.length > 2 && (
              <span className="text-xs text-text-tertiary">
                +{selectedLabels.length - 2}
              </span>
            )}
          </>
        ) : (
          <span className="text-xs text-text-tertiary italic hover:text-text-secondary">
            No labels
          </span>
        )}
      </button>

      {open && (
        <div className="absolute left-0 top-full mt-1 z-50 w-48 rounded-lg border border-border-default bg-bg-surface shadow-lg py-1">
          {allLabels.length === 0 ? (
            <p className="px-3 py-2 text-xs text-text-tertiary">No labels available</p>
          ) : (
            allLabels.map((label) => {
              const selected = selectedIds.has(label.id);
              return (
                <button
                  key={label.id}
                  type="button"
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    handleToggle(label.id);
                  }}
                  className="w-full flex items-center gap-2 px-3 py-2 text-xs hover:bg-bg-surface-hover transition-colors"
                >
                  <span
                    className="w-3 h-3 rounded-full flex-shrink-0"
                    style={{ backgroundColor: label.color }}
                  />
                  <span className="text-text-primary truncate">{label.name}</span>
                  {selected && (
                    <span className="ml-auto text-accent text-xs flex-shrink-0">✓</span>
                  )}
                </button>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
