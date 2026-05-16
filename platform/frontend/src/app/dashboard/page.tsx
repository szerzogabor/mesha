"use client";

import { useQuery } from "@tanstack/react-query";
import { useAuthenticatedApi } from "@/hooks/useAuthenticatedApi";
import { useWorkspaceStore } from "@/store/workspace";
import { useEffect } from "react";
import Link from "next/link";
import type { Workspace } from "@/types";

export default function DashboardPage() {
  const api = useAuthenticatedApi();
  const { currentWorkspace, setCurrentWorkspace } = useWorkspaceStore();

  const { data: workspaces, isLoading } = useQuery<Workspace[]>({
    queryKey: ["workspaces"],
    queryFn: () => api.get<Workspace[]>("/api/workspaces"),
  });

  useEffect(() => {
    if (workspaces && workspaces.length > 0 && !currentWorkspace) {
      setCurrentWorkspace(workspaces[0]);
    }
  }, [workspaces, currentWorkspace, setCurrentWorkspace]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full py-32 text-gray-400">
        Loading...
      </div>
    );
  }

  if (!workspaces || workspaces.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full py-32">
        <p className="text-gray-500 mb-6">You don&apos;t belong to any workspace yet.</p>
        <Link
          href="/onboarding"
          className="px-5 py-2.5 bg-black text-white rounded-lg text-sm font-medium hover:bg-gray-800 transition-colors"
        >
          Create a workspace
        </Link>
      </div>
    );
  }

  return (
    <main className="max-w-4xl mx-auto p-8">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-2xl font-bold">Workspaces</h1>
        <Link
          href="/onboarding"
          className="px-4 py-2 bg-black text-white rounded-lg text-sm font-medium hover:bg-gray-800 transition-colors"
        >
          + New workspace
        </Link>
      </div>
      <div className="grid gap-3">
        {workspaces.map((ws) => (
          <button
            key={ws.id}
            onClick={() => setCurrentWorkspace(ws)}
            className={`w-full text-left p-4 rounded-xl border transition-all ${
              currentWorkspace?.id === ws.id
                ? "border-black bg-white shadow-sm"
                : "border-gray-200 bg-white hover:border-gray-400"
            }`}
          >
            <p className="font-semibold">{ws.name}</p>
            <p className="text-sm text-gray-400 mt-0.5">{ws.slug}</p>
          </button>
        ))}
      </div>
    </main>
  );
}
