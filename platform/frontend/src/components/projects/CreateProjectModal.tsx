"use client";

import { useState } from "react";
import * as Sentry from "@sentry/nextjs";
import { Modal } from "@/components/ui/Modal";

interface CreateProjectModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: { name: string; description?: string }) => Promise<void>;
}

const labelClass = "block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1";
const inputClass =
  "w-full border dark:border-gray-700 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:focus:ring-gray-500 bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100";

export function CreateProjectModal({ open, onClose, onSubmit }: CreateProjectModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setLoading(true);
    setError(null);
    try {
      await onSubmit({ name: name.trim(), description: description || undefined });
      setName("");
      setDescription("");
      onClose();
    } catch (err) {
      Sentry.captureException(err);
      setError(err instanceof Error ? err.message : "Failed to create project");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Create Project">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className={labelClass}>
            Name <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Project name"
            className={inputClass}
            required
            autoFocus
          />
        </div>

        <div>
          <label className={labelClass}>Description</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional description"
            rows={2}
            className={`${inputClass} resize-none`}
          />
        </div>

        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

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
            disabled={loading || !name.trim()}
            className="px-4 py-2 text-sm bg-indigo-600 dark:bg-gray-700 text-white rounded-lg hover:bg-indigo-700 dark:hover:bg-gray-600 disabled:opacity-50 transition-colors"
          >
            {loading ? "Creating..." : "Create Project"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
