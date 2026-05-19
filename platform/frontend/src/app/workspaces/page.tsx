"use client";

import { useState } from "react";
import Link from "next/link";
import { Show, UserButton } from "@clerk/nextjs";
import { useWorkspaces, useCreateWorkspace } from "@/hooks/useWorkspaces";
import { Spinner } from "@/components/ui/Spinner";
import { Modal } from "@/components/ui/Modal";
import * as Sentry from "@sentry/nextjs";

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
      Sentry.captureException(err);
      setCreateError(err instanceof Error ? err.message : "Failed to create workspace");
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Spinner size="lg" className="text-indigo-600" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b px-8 py-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">Mesha</h1>
        <div className="flex items-center gap-3">
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700"
        >
          New Workspace
        </button>
        <Show when="signed-in">
          <UserButton />
        </Show>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-8 py-12">
        <h2 className="text-2xl font-semibold text-gray-900 mb-6">Your Workspaces</h2>

        {error && (
          <div className="p-4 bg-red-50 text-red-700 rounded-lg text-sm mb-6">
            {error instanceof Error ? error.message : "Failed to load workspaces"}
          </div>
        )}

        {workspaces && workspaces.length === 0 && (
          <div className="text-center py-16 text-gray-400">
            <p className="text-lg mb-2">No workspaces yet</p>
            <p className="text-sm">Create your first workspace to get started.</p>
          </div>
        )}

        <div className="space-y-3">
          {workspaces?.map((ws) => (
            <Link
              key={ws.id}
              href={`/workspaces/${ws.id}`}
              className="block p-4 bg-white rounded-xl border hover:border-indigo-300 hover:shadow-sm transition-all"
            >
              <p className="font-medium text-gray-900">{ws.name}</p>
              <p className="text-sm text-gray-400 mt-0.5">{ws.slug}</p>
            </Link>
          ))}
        </div>
      </main>

      <Modal open={showCreate} onClose={() => setShowCreate(false)} title="Create Workspace">
        <form onSubmit={handleCreate} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="My Workspace"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Slug</label>
            <input
              type="text"
              value={slug}
              onChange={(e) => setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, "-"))}
              placeholder="my-workspace"
              pattern="^[a-z0-9-]{2,50}$"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              required
            />
            <p className="text-xs text-gray-400 mt-1">Lowercase letters, numbers, and hyphens only</p>
          </div>
          {createError && <p className="text-sm text-red-600">{createError}</p>}
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={() => setShowCreate(false)}
              className="px-4 py-2 text-sm text-gray-700 border rounded-lg hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createWorkspace.isPending}
              className="px-4 py-2 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50"
            >
              {createWorkspace.isPending ? "Creating..." : "Create"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
