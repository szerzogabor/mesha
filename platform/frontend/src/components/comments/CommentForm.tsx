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
        className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
      />
      <div className="flex justify-end gap-2">
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            className="px-3 py-1.5 text-sm text-gray-600 border rounded-lg hover:bg-gray-50"
          >
            Cancel
          </button>
        )}
        <button
          type="submit"
          disabled={loading || !body.trim()}
          className="px-3 py-1.5 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50"
        >
          {loading ? "Posting..." : "Comment"}
        </button>
      </div>
    </form>
  );
}
