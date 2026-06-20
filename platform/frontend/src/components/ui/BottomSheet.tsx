"use client";

import { useEffect } from "react";
import { cn } from "@/lib/utils";

interface BottomSheetProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  className?: string;
}

/**
 * Mobile-first bottom sheet — an Android-style action surface that slides up
 * from the bottom edge, respecting the safe-area inset. Used for compact,
 * touch-friendly choices (e.g. "Move to…" status pickers, action menus).
 */
export function BottomSheet({
  open,
  onClose,
  title,
  children,
  className,
}: BottomSheetProps) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    if (open) document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  useEffect(() => {
    if (!open) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [open]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center sm:justify-center">
      <div
        className="absolute inset-0 bg-black/50 backdrop-blur-sm touch-none"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cn(
          "relative z-10 w-full sm:max-w-md bg-bg-surface border border-border-default shadow-2xl",
          "rounded-t-2xl sm:rounded-2xl max-h-[85vh] overflow-y-auto",
          "animate-slide-up pb-safe",
          className
        )}
      >
        {/* Drag affordance */}
        <div className="sm:hidden flex justify-center pt-2.5 pb-1">
          <span className="h-1 w-10 rounded-full bg-border-strong" />
        </div>
        {title && (
          <div className="px-4 pt-2 pb-3 sm:pt-4">
            <h2 className="text-base font-semibold text-text-primary">{title}</h2>
          </div>
        )}
        <div className="px-2 pb-2">{children}</div>
      </div>
    </div>
  );
}
