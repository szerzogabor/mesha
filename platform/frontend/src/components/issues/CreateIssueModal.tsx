"use client";

import { useState } from "react";
import * as Sentry from "@sentry/nextjs";
import { Modal } from "@/components/ui/Modal";
import { IssueStatus, IssuePriority } from "@/types";

interface CreateIssueModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: {
    title: string;
    description?: string;
    status: IssueStatus;
    priority: IssuePriority;
  }) => Promise<void>;
}

const labelClass = "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1";
const inputClass =
  "w-full border dark:border-gray-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:focus:ring-gray-500 bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100";

export function CreateIssueModal({ open, onClose, onSubmit }: CreateIssueModalProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState<IssueStatus>("BACKLOG");
  const [priority, setPriority] = useState<IssuePriority>("MEDIUM");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setLoading(true);
    setError(null);
    try {
      await onSubmit({ title: title.trim(), description: description || undefined, status, priority });
      setTitle("");
      setDescription("");
      setStatus("BACKLOG");
      setPriority("MEDIUM");
      onClose();
    } catch (err) {
      Sentry.captureException(err);
      setError(err instanceof Error ? err.message : "Failed to create issue");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Create Issue">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className={labelClass}>
            Title <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Issue title"
            className={inputClass}
            required
          />
        </div>

        <div>
          <label className={labelClass}>Description</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Add a description..."
            rows={3}
            className={`${inputClass} resize-none`}
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Status</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as IssueStatus)}
              className={inputClass}
            >
              <option value="BACKLOG">Backlog</option>
              <option value="TODO">Todo</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="REVIEW">Review</option>
              <option value="DONE">Done</option>
            </select>
          </div>
          <div>
            <label className={labelClass}>Priority</label>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value as IssuePriority)}
              className={inputClass}
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </select>
          </div>
        </div>

        {error && (
          <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm text-gray-700 dark:text-gray-300 border dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={loading || !title.trim()}
            className="px-4 py-2 text-sm bg-indigo-600 dark:bg-gray-700 text-white rounded-lg hover:bg-indigo-700 dark:hover:bg-gray-600 disabled:opacity-50 transition-colors"
          >
            {loading ? "Creating..." : "Create Issue"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
