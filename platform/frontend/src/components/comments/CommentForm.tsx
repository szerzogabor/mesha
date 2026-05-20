"use client";

import { useState } from "react";

interface CommentFormProps {
  onSubmit: (body: string, parentId?: string) => Promise<void>;
  parentId?: string;
  placeholder?: string;
  onCancel?: () => void;
}

export function CommentForm({ onSubmit, parentId, placeholder = "Write a comment...", onCancel }: CommentFormProps) {
  const [body, setBody] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!body.trim()) return;
    setLoading(true);
    try {
      await onSubmit(body.trim(), parentId);
      setBody("");
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-2">
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder={placeholder}
        rows={3}
        className="w-full border dark:border-gray-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:focus:ring-gray-500 resize-none bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 dark:placeholder-gray-500"
      />
      <div className="flex justify-end gap-2">
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="px-3 py-1.5 text-sm text-gray-600 dark:text-gray-400 border dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
          >
            Cancel
          </button>
        )}
        <button
          type="submit"
          disabled={loading || !body.trim()}
          className="px-3 py-1.5 text-sm bg-indigo-600 dark:bg-gray-700 text-white rounded-lg hover:bg-indigo-700 dark:hover:bg-gray-600 disabled:opacity-50 transition-colors"
        >
          {loading ? "Posting..." : "Comment"}
        </button>
      </div>
    </form>
  );
}
