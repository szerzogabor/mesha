"use client";

import { useState } from "react";
import * as Sentry from "@sentry/nextjs";
import { Modal } from "@/components/ui/Modal";
import { AIDraft, IssuePriority, IssueStatus } from "@/types";
import {
  useGenerateDraft,
  useApproveDraft,
  useRejectDraft,
  useRegenerateDraft,
} from "@/hooks/useAIDraft";

const inputClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

const textareaClass = `${inputClass} resize-none`;

type Step = "prompt" | "review";

interface AIDraftModalProps {
  open: boolean;
  projectId: string;
  onClose: () => void;
}

function parseSuggestedLabels(raw?: string): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) return parsed.map(String);
  } catch {}
  return [];
}

function buildFullDescription(draft: AIDraft): string {
  const parts: string[] = [];
  if (draft.generatedDescription) parts.push(draft.generatedDescription);
  if (draft.acceptanceCriteria) parts.push(`\n**Acceptance Criteria**\n${draft.acceptanceCriteria}`);
  if (draft.implementationNotes) parts.push(`\n**Implementation Notes**\n${draft.implementationNotes}`);
  if (draft.scopeNotes) parts.push(`\n**In Scope**\n${draft.scopeNotes}`);
  if (draft.outOfScopeNotes) parts.push(`\n**Out of Scope**\n${draft.outOfScopeNotes}`);
  return parts.join("\n");
}

export function AIDraftModal({ open, projectId, onClose }: AIDraftModalProps) {
  const [step, setStep] = useState<Step>("prompt");
  const [prompt, setPrompt] = useState("");
  const [draft, setDraft] = useState<AIDraft | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Editable draft fields
  const [editTitle, setEditTitle] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editStatus, setEditStatus] = useState<IssueStatus>("BACKLOG");
  const [editPriority, setEditPriority] = useState<IssuePriority>("MEDIUM");

  const generate = useGenerateDraft(projectId);
  const approve = useApproveDraft(projectId);
  const reject = useRejectDraft(projectId);
  const regenerate = useRegenerateDraft(projectId);

  const loading = generate.isPending || approve.isPending || reject.isPending || regenerate.isPending;

  function resetAndClose() {
    setStep("prompt");
    setPrompt("");
    setDraft(null);
    setError(null);
    onClose();
  }

  function populateEditFields(d: AIDraft) {
    setEditTitle(d.generatedTitle ?? "");
    setEditDescription(buildFullDescription(d));
    const priority = (d.prioritySuggestion as IssuePriority) ?? "MEDIUM";
    setEditPriority(["LOW", "MEDIUM", "HIGH", "URGENT"].includes(priority) ? priority : "MEDIUM");
    setEditStatus("BACKLOG");
  }

  async function handleGenerate(e: React.FormEvent) {
    e.preventDefault();
    if (!prompt.trim()) return;
    setError(null);
    try {
      const result = await generate.mutateAsync(prompt.trim());
      if (result.status === "FAILED") {
        setError(result.errorMessage ?? "AI generation failed. Please try again.");
        return;
      }
      setDraft(result);
      populateEditFields(result);
      setStep("review");
    } catch (err) {
      Sentry.captureException(err);
      setError(err instanceof Error ? err.message : "Generation failed");
    }
  }

  async function handleApprove() {
    if (!draft || !editTitle.trim()) return;
    setError(null);
    try {
      await approve.mutateAsync({
        draftId: draft.id,
        title: editTitle.trim(),
        description: editDescription || undefined,
        status: editStatus,
        priority: editPriority,
      });
      resetAndClose();
    } catch (err) {
      Sentry.captureException(err);
      setError(err instanceof Error ? err.message : "Failed to create issue");
    }
  }

  async function handleReject() {
    if (!draft) return;
    setError(null);
    try {
      await reject.mutateAsync(draft.id);
      resetAndClose();
    } catch (err) {
      Sentry.captureException(err);
      setError(err instanceof Error ? err.message : "Failed to reject draft");
    }
  }

  async function handleRegenerate() {
    if (!draft) return;
    setError(null);
    try {
      const result = await regenerate.mutateAsync({ draftId: draft.id, prompt: prompt.trim() || undefined });
      if (result.status === "FAILED") {
        setError(result.errorMessage ?? "Regeneration failed. Please try again.");
        return;
      }
      setDraft(result);
      populateEditFields(result);
    } catch (err) {
      Sentry.captureException(err);
      setError(err instanceof Error ? err.message : "Regeneration failed");
    }
  }

  const suggestedLabels = parseSuggestedLabels(draft?.suggestedLabels);

  return (
    <Modal
      open={open}
      onClose={resetAndClose}
      title="Generate Issue with AI"
      className="max-w-2xl"
    >
      {step === "prompt" ? (
        <form onSubmit={handleGenerate} className="space-y-4">
          <p className="text-sm text-text-secondary">
            Describe the feature, bug, or task you want to create a ticket for.
          </p>
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1">
              Your request <span className="text-destructive">*</span>
            </label>
            <textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="e.g. Add a password reset flow for users who have forgotten their credentials..."
              rows={5}
              className={textareaClass}
              required
              minLength={10}
            />
            <p className="text-xs text-text-tertiary mt-1">Minimum 10 characters</p>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={resetAndClose}
              className="px-4 py-2 text-sm text-text-secondary border border-border-default rounded-lg hover:bg-bg-surface-hover transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading || prompt.trim().length < 10}
              className="px-4 py-2 text-sm bg-accent text-white rounded-lg hover:bg-accent-hover disabled:opacity-50 transition-colors flex items-center gap-2"
            >
              {loading ? (
                <>
                  <span className="inline-block w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Generating...
                </>
              ) : (
                "Generate Draft"
              )}
            </button>
          </div>
        </form>
      ) : (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm text-text-secondary">
              Review and edit the AI-generated draft before creating the issue.
            </p>
            <span className="text-xs bg-accent/10 text-accent px-2 py-0.5 rounded-full font-medium">AI Draft</span>
          </div>

          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1">
              Title <span className="text-destructive">*</span>
            </label>
            <input
              type="text"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              className={inputClass}
              maxLength={500}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1">Description</label>
            <textarea
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
              rows={8}
              className={textareaClass}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-text-secondary mb-1">Status</label>
              <select
                value={editStatus}
                onChange={(e) => setEditStatus(e.target.value as IssueStatus)}
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
              <label className="block text-sm font-medium text-text-secondary mb-1">
                Priority <span className="text-xs text-text-tertiary">(AI suggested)</span>
              </label>
              <select
                value={editPriority}
                onChange={(e) => setEditPriority(e.target.value as IssuePriority)}
                className={inputClass}
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="URGENT">Urgent</option>
              </select>
            </div>
          </div>

          {suggestedLabels.length > 0 && (
            <div>
              <p className="text-xs font-medium text-text-secondary mb-1">Suggested labels</p>
              <div className="flex flex-wrap gap-1">
                {suggestedLabels.map((label) => (
                  <span
                    key={label}
                    className="text-xs px-2 py-0.5 rounded-full bg-bg-surface-hover text-text-secondary border border-border-default"
                  >
                    {label}
                  </span>
                ))}
              </div>
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex flex-wrap justify-between gap-3 pt-2">
            <div className="flex gap-2">
              <button
                type="button"
                onClick={handleReject}
                disabled={loading}
                className="px-4 py-2 text-sm text-destructive border border-destructive/30 rounded-lg hover:bg-destructive/5 disabled:opacity-50 transition-colors"
              >
                Reject
              </button>
              <button
                type="button"
                onClick={handleRegenerate}
                disabled={loading}
                className="px-4 py-2 text-sm text-text-secondary border border-border-default rounded-lg hover:bg-bg-surface-hover disabled:opacity-50 transition-colors flex items-center gap-2"
              >
                {regenerate.isPending ? (
                  <>
                    <span className="inline-block w-3 h-3 border-2 border-current/30 border-t-current rounded-full animate-spin" />
                    Regenerating...
                  </>
                ) : (
                  "Regenerate"
                )}
              </button>
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => { setStep("prompt"); setError(null); }}
                disabled={loading}
                className="px-4 py-2 text-sm text-text-secondary border border-border-default rounded-lg hover:bg-bg-surface-hover disabled:opacity-50 transition-colors"
              >
                Back
              </button>
              <button
                type="button"
                onClick={handleApprove}
                disabled={loading || !editTitle.trim()}
                className="px-4 py-2 text-sm bg-accent text-white rounded-lg hover:bg-accent-hover disabled:opacity-50 transition-colors flex items-center gap-2"
              >
                {approve.isPending ? (
                  <>
                    <span className="inline-block w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    Creating...
                  </>
                ) : (
                  "Approve & Create Issue"
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}
