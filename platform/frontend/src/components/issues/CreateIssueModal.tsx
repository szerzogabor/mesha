"use client";

import { useState, useEffect } from "react";
import { Modal } from "@/components/ui/Modal";
import { logger } from "@/lib/logger";
import { IssueStatus, IssuePriority, ProjectStatus } from "@/types";
import { useLabels } from "@/hooks/useLabels";
import { statusLabel } from "@/lib/utils";

const inputClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

interface CreateIssueModalProps {
  open: boolean;
  onClose: () => void;
  workspaceId: string;
  projectStatuses?: ProjectStatus[];
  onSubmit: (data: {
    title: string;
    description?: string;
    status: IssueStatus;
    priority: IssuePriority;
    labelIds?: string[];
  }) => Promise<void>;
}

export function CreateIssueModal({ open, onClose, workspaceId, projectStatuses, onSubmit }: CreateIssueModalProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState<IssueStatus>("");
  const [priority, setPriority] = useState<IssuePriority>("MEDIUM");

  useEffect(() => {
    if (projectStatuses && projectStatuses.length > 0 && !status) {
      setStatus(projectStatuses[0].name);
    }
  }, [projectStatuses]);

  const [selectedLabelIds, setSelectedLabelIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data: labels = [] } = useLabels(workspaceId);

  const toggleLabel = (labelId: string) => {
    setSelectedLabelIds((prev) =>
      prev.includes(labelId) ? prev.filter((id) => id !== labelId) : [...prev, labelId]
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setLoading(true);
    setError(null);
    try {
      await onSubmit({
        title: title.trim(),
        description: description || undefined,
        status,
        priority,
        labelIds: selectedLabelIds.length > 0 ? selectedLabelIds : undefined,
      });
      setTitle("");
      setDescription("");
      setStatus(projectStatuses?.[0]?.name ?? "");
      setPriority("MEDIUM");
      setSelectedLabelIds([]);
      onClose();
    } catch (err) {
      logger.error("Failed to create issue", err instanceof Error ? err : undefined);
      setError(err instanceof Error ? err.message : "Failed to create issue");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="Create Issue">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-text-secondary mb-1">
            Title <span className="text-destructive">*</span>
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
          <label className="block text-sm font-medium text-text-secondary mb-1">
            Description
          </label>
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
            <label className="block text-sm font-medium text-text-secondary mb-1">Status</label>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              className={inputClass}
            >
              {(projectStatuses ?? []).map((s) => (
                <option key={s.id} value={s.name}>{statusLabel(s.name)}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1">Priority</label>
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

        {labels.length > 0 && (
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-2">Labels</label>
            <div className="flex flex-wrap gap-2">
              {labels.map((label) => {
                const selected = selectedLabelIds.includes(label.id);
                return (
                  <button
                    key={label.id}
                    type="button"
                    onClick={() => toggleLabel(label.id)}
                    className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium transition-all border"
                    style={{
                      backgroundColor: selected ? label.color + "33" : "transparent",
                      color: label.color,
                      borderColor: selected ? label.color : label.color + "55",
                    }}
                  >
                    {selected && <span className="mr-1">✓</span>}
                    {label.name}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {error && (
          <p className="text-sm text-destructive">{error}</p>
        )}

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
            disabled={loading || !title.trim()}
            className="px-4 py-2 text-sm bg-accent text-white rounded-lg hover:bg-accent-hover disabled:opacity-50 transition-colors"
          >
            {loading ? "Creating..." : "Create Issue"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
