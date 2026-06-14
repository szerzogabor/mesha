"use client";

import { Modal } from "./Modal";

interface Props {
  message: string | null;
  onClose: () => void;
}

export function RuleViolationDialog({ message, onClose }: Props) {
  return (
    <Modal open={!!message} onClose={onClose} title="Action Blocked by Rule" className="max-w-md">
      <div className="space-y-4">
        <div className="flex gap-3">
          <div className="flex-shrink-0 flex h-8 w-8 items-center justify-center rounded-full bg-amber-100 dark:bg-amber-900/30">
            <svg className="h-4 w-4 text-amber-600 dark:text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
          </div>
          <p className="text-sm text-text-secondary leading-relaxed">{message}</p>
        </div>
        <div className="flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium bg-accent text-white rounded-lg hover:bg-accent-hover transition-colors"
          >
            Got it
          </button>
        </div>
      </div>
    </Modal>
  );
}
