"use client";

import { useState } from "react";
import { Modal } from "@/components/ui/Modal";
import { logger } from "@/lib/logger";

const inputClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

interface CreateProjectModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: { name: string; description?: string; key?: string }) => Promise<void>;
}

function deriveKey(name: string): string {
  const words = name.trim().split(/\s+/);
  const key = words.map((w) => w[0]?.toUpperCase() ?? "").join("").slice(0, 5);
  return key || "PROJ";
}

export function CreateProjectModal({ open, onClose, onSubmit }: CreateProjectModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [key, setKey] = useState("");
  const [keyTouched, setKeyTouched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const derivedKey = keyTouched ? key : deriveKey(name);

  const handleNameChange = (value: string) => {
    setName(value);
    if (!keyTouched) {
      setKey(deriveKey(value));
    }
  };

  const handleKeyChange = (value: string) => {
    setKey(value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 10));
    setKeyTouched(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setLoading(true);
    setError(null);
    try {
      await onSubmit({
        name: name.trim(),
        description: description || undefined,
        key: derivedKey || undefined,
      });
      setName("");
      setDescription("");
      setKey("");
      setKeyTouched(false);
      onClose();
    } catch (err) {
      logger.error("Failed to create project", err instanceof Error ? err : undefined);
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
            onChange={(e) => handleNameChange(e.target.value)}
            placeholder="Project name"
            className={inputClass}
            required
            autoFocus
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-text-secondary mb-1">
            Key
            <span className="ml-1 text-xs text-text-tertiary font-normal">(auto-generated from name)</span>
          </label>
          <input
            type="text"
            value={derivedKey}
            onChange={(e) => handleKeyChange(e.target.value)}
            placeholder="e.g. MESH"
            maxLength={10}
            className={`${inputClass} font-mono uppercase`}
          />
          <p className="text-xs text-text-tertiary mt-1">2–10 uppercase letters/digits. Used as issue prefix (e.g. {derivedKey || "PROJ"}-1)</p>
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
