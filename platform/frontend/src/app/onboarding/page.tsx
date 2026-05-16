"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { useAuthenticatedApi } from "@/hooks/useAuthenticatedApi";
import { useWorkspaceStore } from "@/store/workspace";
import { useRouter } from "next/navigation";
import type { Workspace } from "@/types";

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^a-z0-9-]/g, "")
    .replace(/-+/g, "-")
    .slice(0, 50);
}

export default function OnboardingPage() {
  const api = useAuthenticatedApi();
  const router = useRouter();
  const { setCurrentWorkspace } = useWorkspaceStore();

  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [slugTouched, setSlugTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { mutate: createWorkspace, isPending } = useMutation({
    mutationFn: () =>
      api.post<Workspace>("/api/workspaces", { name, slug }),
    onSuccess: (ws) => {
      setCurrentWorkspace(ws);
      router.push("/dashboard");
    },
    onError: (err: Error) => {
      setError(err.message);
    },
  });

  function handleNameChange(value: string) {
    setName(value);
    if (!slugTouched) {
      setSlug(slugify(value));
    }
  }

  function handleSlugChange(value: string) {
    setSlugTouched(true);
    setSlug(slugify(value));
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    createWorkspace();
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-gray-50 p-8">
      <div className="w-full max-w-md bg-white rounded-2xl border border-gray-200 p-8 shadow-sm">
        <h1 className="text-2xl font-bold mb-1">Create your workspace</h1>
        <p className="text-gray-500 text-sm mb-8">
          A workspace is a shared space for your team to manage projects.
        </p>

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-1.5">
              Workspace name
            </label>
            <input
              id="name"
              type="text"
              value={name}
              onChange={(e) => handleNameChange(e.target.value)}
              placeholder="Acme Inc."
              required
              className="w-full px-3.5 py-2.5 rounded-lg border border-gray-300 text-sm focus:outline-none focus:ring-2 focus:ring-black focus:border-transparent"
            />
          </div>

          <div>
            <label htmlFor="slug" className="block text-sm font-medium text-gray-700 mb-1.5">
              URL slug
            </label>
            <div className="flex items-center rounded-lg border border-gray-300 overflow-hidden focus-within:ring-2 focus-within:ring-black focus-within:border-transparent">
              <span className="px-3 py-2.5 text-sm text-gray-400 bg-gray-50 border-r border-gray-300 select-none">
                mesha.app/
              </span>
              <input
                id="slug"
                type="text"
                value={slug}
                onChange={(e) => handleSlugChange(e.target.value)}
                placeholder="acme"
                required
                pattern="^[a-z0-9-]{2,50}$"
                title="2–50 lowercase letters, digits, or hyphens"
                className="flex-1 px-3 py-2.5 text-sm focus:outline-none"
              />
            </div>
            <p className="mt-1 text-xs text-gray-400">
              Lowercase letters, digits, and hyphens only.
            </p>
          </div>

          {error && (
            <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{error}</p>
          )}

          <button
            type="submit"
            disabled={isPending || !name || !slug}
            className="w-full py-2.5 bg-black text-white rounded-lg text-sm font-medium hover:bg-gray-800 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isPending ? "Creating..." : "Create workspace"}
          </button>
        </form>
      </div>
    </main>
  );
}
