"use client";

import { useState, useEffect } from "react";
import { Modal } from "@/components/ui/Modal";
import { logger } from "@/lib/logger";
import { IssueStatus, IssuePriority, ProjectStatus, IssueLinkType } from "@/types";
import { useLabels } from "@/hooks/useLabels";
import { statusLabel } from "@/lib/utils";
import { useAllIssues } from "@/hooks/useIssues";
import { apiClient } from "@/lib/api-client";

const inputClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

const smallInputClass =
  "w-full border border-input-border rounded-lg px-2 py-1.5 text-xs bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

const LINK_TYPE_OPTIONS: { value: IssueLinkType; label: string }[] = [
  { value: "DEPENDS_ON", label: "Depends on" },
  { value: "BLOCKS", label: "Blocks" },
  { value: "DUPLICATE_OF", label: "Duplicate of" },
  { value: "PARENT_OF", label: "Parent of" },
  { value: "CHILD_OF", label: "Child of (sub-ticket)" },
];

const LINK_TYPE_LABELS: Record<IssueLinkType, string> = {
  DEPENDS_ON: "depends on",
  BLOCKS: "blocks",
  DUPLICATE_OF: "duplicate of",
  PARENT_OF: "parent of",
  CHILD_OF: "child of",
};

interface PendingLink {
  targetIssueId: string;
  targetIdentifier?: string;
  targetTitle: string;
  linkType: IssueLinkType;
}

interface CreateIssueModalProps {
  open: boolean;
  onClose: () => void;
  workspaceId: string;
  projectId: string;
  projectStatuses?: ProjectStatus[];
  defaultStatus?: string;
  onSubmit: (data: {
    title: string;
    description?: string;
    status: IssueStatus;
    priority: IssuePriority;
    labelIds?: string[];
  }) => Promise<{ id: string }>;
}

export function CreateIssueModal({ open, onClose, workspaceId, projectId, projectStatuses, defaultStatus, onSubmit }: CreateIssueModalProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [status, setStatus] = useState<IssueStatus>("");

  useEffect(() => {
    if (open && defaultStatus) {
      setStatus(defaultStatus as IssueStatus);
    }
  }, [open, defaultStatus]);

  const [priority, setPriority] = useState<IssuePriority>("MEDIUM");

  useEffect(() => {
    if (projectStatuses && projectStatuses.length > 0 && !status) {
      setStatus(projectStatuses[0].name);
    }
  }, [projectStatuses]);

  const [selectedLabelIds, setSelectedLabelIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [pendingLinks, setPendingLinks] = useState<PendingLink[]>([]);
  const [showLinkAdd, setShowLinkAdd] = useState(false);
  const [linkSearch, setLinkSearch] = useState("");
  const [selectedLinkType, setSelectedLinkType] = useState<IssueLinkType>("DEPENDS_ON");

  const { data: labels = [] } = useLabels(workspaceId);

  const { data: issueSearchResult } = useAllIssues(
    projectId,
    { search: linkSearch.trim() || undefined },
    { enabled: linkSearch.trim().length > 0 }
  );
  const searchResults = (issueSearchResult?.content ?? []).filter(
    (i) => !pendingLinks.some((l) => l.targetIssueId === i.id)
  );

  const toggleLabel = (labelId: string) => {
    setSelectedLabelIds((prev) =>
      prev.includes(labelId) ? prev.filter((id) => id !== labelId) : [...prev, labelId]
    );
  };

  function addPendingLink(issue: { id: string; identifier?: string; title: string }) {
    setPendingLinks((prev) => [
      ...prev,
      {
        targetIssueId: issue.id,
        targetIdentifier: issue.identifier,
        targetTitle: issue.title,
        linkType: selectedLinkType,
      },
    ]);
    setLinkSearch("");
    setShowLinkAdd(false);
  }

  function removePendingLink(targetIssueId: string) {
    setPendingLinks((prev) => prev.filter((l) => l.targetIssueId !== targetIssueId));
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const newIssue = await onSubmit({
        title: title.trim(),
        description: description || undefined,
        status,
        priority,
        labelIds: selectedLabelIds.length > 0 ? selectedLabelIds : undefined,
      });

      if (pendingLinks.length > 0) {
        await Promise.all(
          pendingLinks.map((link) =>
            apiClient.post(`/api/issues/${newIssue.id}/links`, {
              targetIssueId: link.targetIssueId,
              linkType: link.linkType,
            })
          )
        );
      }

      setTitle("");
      setDescription("");
      setStatus(projectStatuses?.[0]?.name ?? "");
      setPriority("MEDIUM");
      setSelectedLabelIds([]);
      setPendingLinks([]);
      setShowLinkAdd(false);
      setLinkSearch("");
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

        <div>
          <div className="flex items-center justify-between mb-2">
            <label className="block text-sm font-medium text-text-secondary">
              Links{pendingLinks.length > 0 && ` (${pendingLinks.length})`}
            </label>
            <button
              type="button"
              onClick={() => { setShowLinkAdd((v) => !v); setLinkSearch(""); }}
              className="text-xs text-text-tertiary hover:text-text-primary transition-colors"
            >
              {showLinkAdd ? "Cancel" : "+ Add"}
            </button>
          </div>

          {pendingLinks.length > 0 && (
            <ul className="space-y-1.5 mb-2">
              {pendingLinks.map((link) => (
                <li key={link.targetIssueId} className="flex items-start gap-2 group">
                  <div className="flex-1 min-w-0">
                    <span className="text-[10px] text-text-tertiary capitalize block">
                      {LINK_TYPE_LABELS[link.linkType]}
                    </span>
                    <span className="text-xs text-text-primary truncate block">
                      {link.targetIdentifier && (
                        <span className="font-mono text-text-tertiary mr-1">{link.targetIdentifier}</span>
                      )}
                      {link.targetTitle}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => removePendingLink(link.targetIssueId)}
                    className="opacity-0 group-hover:opacity-100 text-text-tertiary hover:text-destructive transition-all text-xs shrink-0 mt-0.5"
                    title="Remove link"
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
          )}

          {showLinkAdd && (
            <div className="space-y-2 pt-2 border-t border-border-default">
              <select
                value={selectedLinkType}
                onChange={(e) => setSelectedLinkType(e.target.value as IssueLinkType)}
                className={smallInputClass}
              >
                {LINK_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>

              <input
                type="text"
                placeholder="Search issues by title…"
                value={linkSearch}
                onChange={(e) => setLinkSearch(e.target.value)}
                className={smallInputClass}
              />

              {linkSearch.trim().length > 0 && searchResults.length === 0 && (
                <p className="text-xs text-text-tertiary">No issues found.</p>
              )}

              {searchResults.length > 0 && (
                <ul className="space-y-1 max-h-32 overflow-y-auto">
                  {searchResults.map((issue) => (
                    <li key={issue.id}>
                      <button
                        type="button"
                        onClick={() => addPendingLink(issue)}
                        className="w-full text-left px-2 py-1.5 rounded-lg text-xs hover:bg-bg-surface-hover transition-colors"
                      >
                        {issue.identifier && (
                          <span className="font-mono text-text-tertiary mr-1">{issue.identifier}</span>
                        )}
                        <span className="text-text-primary">{issue.title}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

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
