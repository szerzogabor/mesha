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
        className="px-3 py-1 rounded border text-sm disabled:opacity-40 hover:bg-gray-50"
      >
        Prev
      </button>
      <span className="text-sm text-gray-600">
        {page + 1} / {totalPages}
      </span>
      <button
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        className="px-3 py-1 rounded border text-sm disabled:opacity-40 hover:bg-gray-50"
      >
        Next
      </button>
    </div>
  );
}
