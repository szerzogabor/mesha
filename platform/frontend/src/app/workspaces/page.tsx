"use client";

import { useState } from "react";
import Link from "next/link";
import { useWorkspaces, useCreateWorkspace } from "@/hooks/useWorkspaces";
import { Spinner } from "@/components/ui/Spinner";
import { Modal } from "@/components/ui/Modal";
import { logger } from "@/lib/logger";

const inputClass =
  "w-full border border-input-border rounded-lg px-3 py-2 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent";

export default function WorkspacesPage() {
  const { data: workspaces, isLoading, error } = useWorkspaces();
  const createWorkspace = useCreateWorkspace();
  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [createError, setCreateError] = useState<string | null>(null);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);
    try {
      await createWorkspace.mutateAsync({ name, slug });
      setName("");
      setSlug("");
      setShowCreate(false);
    } catch (err) {
      logger.error("Failed to create workspace", err instanceof Error ? err : undefined);
      setCreateError(err instanceof Error ? err.message : "Failed to create workspace");
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-bg-app">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg-app">
      <main className="max-w-2xl mx-auto px-8 py-12">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-semibold text-text-primary">Your Workspaces</h2>
          <button
            onClick={() => setShowCreate(true)}
            className="px-4 py-2 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover transition-colors"
          >
            New Workspace
          </button>
        </div>

        {error && (
          <div className="p-4 bg-destructive-muted text-destructive rounded-lg text-sm mb-6">
            {error instanceof Error ? error.message : "Failed to load workspaces"}
          </div>
        )}

        {workspaces && workspaces.length === 0 && (
          <div className="text-center py-16 text-text-tertiary">
            <p className="text-lg mb-2">No workspaces yet</p>
            <p className="text-sm">Create your first workspace to get started.</p>
          </div>
        )}

        <div className="space-y-3">
          {workspaces?.map((ws) => (
            <Link
              key={ws.id}
              href={`/workspaces/${ws.id}`}
              className="block p-4 bg-bg-surface rounded-xl border border-border-default hover:border-accent hover:shadow-sm transition-all"
            >
              <p className="font-medium text-text-primary">{ws.name}</p>
              <p className="text-sm text-text-tertiary mt-0.5">{ws.slug}</p>
            </Link>
          ))}
        </div>
      </main>

      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Create Workspace">
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1">Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="My Workspace"
              className={inputClass}
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-text-secondary mb-1">Slug</label>
            <input
              type="text"
              value={slug}
              onChange={(e) => setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, "-"))}
              placeholder="my-workspace"
              pattern="^[a-z0-9-]{2,50}$"
              className={inputClass}
              required
            />
            <p className="text-xs text-text-tertiary mt-1">Lowercase letters, numbers, and hyphens only</p>
          </div>
          {createError && <p className="text-sm text-destructive">{createError}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() => setShowCreate(false)}
              className="px-4 py-2 text-sm text-text-secondary border border-border-default rounded-lg hover:bg-bg-surface-hover transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createWorkspace.isPending}
              className="px-4 py-2 text-sm bg-accent text-white rounded-lg hover:bg-accent-hover disabled:opacity-50 transition-colors"
            >
              {createWorkspace.isPending ? "Creating..." : "Create"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
