"use client";

import { useState } from "react";
import * as Sentry from "@sentry/nextjs";
import { Modal } from "@/components/ui/Modal";

const inputClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

interface CreateProjectModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: { name: string; description?: string }) => Promise<void>;
}

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
          <label className="block text-sm font-medium text-text-secondary mb-1">
            Name <span className="text-destructive">*</span>
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
          <label className="block text-sm font-medium text-text-secondary mb-1">
            Description
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Optional description"
            rows={2}
            className={`${inputClass} resize-none`}
          />
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm text-text-secondary border border-border-default rounded-lg hover:bg-bg-surface-hover transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={loading || !name.trim()}
            className="px-4 py-2 text-sm bg-accent text-white rounded-lg hover:bg-accent-hover disabled:opacity-50 transition-colors"
          >
            {loading ? "Creating..." : "Create Project"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
