"use client";

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-center gap-2 py-4">
      <button
        onClick={() => onPageChange(page - 1)}
        disabled={page === 0}
        className="px-3 py-1 rounded border border-border-default text-sm text-text-secondary disabled:opacity-40 hover:bg-bg-surface-hover transition-colors"
      >
        Prev
      </button>
      <span className="text-sm text-text-secondary">
        {page + 1} / {totalPages}
      </span>
      <button
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        className="px-3 py-1 rounded border border-border-default text-sm text-text-secondary disabled:opacity-40 hover:bg-bg-surface-hover transition-colors"
      >
        Next
      </button>
    </div>
  );
}
