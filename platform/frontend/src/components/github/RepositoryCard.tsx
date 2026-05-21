"use client";

import { GitHubRepository } from "@/types";
import Link from "next/link";
import { cn } from "@/lib/utils";

interface RepositoryCardProps {
  repo: GitHubRepository;
  workspaceId: string;
  onDisconnect: (repoId: string) => void;
  disconnecting: boolean;
}

export function RepositoryCard({
  repo,
  workspaceId,
  onDisconnect,
  disconnecting,
}: RepositoryCardProps) {
  return (
    <div className="bg-bg-surface border border-border-subtle rounded-lg p-4 flex items-start justify-between gap-4">
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2 mb-1">
          <Link
            href={`/workspaces/${workspaceId}/github/repositories/${repo.id}`}
            className="font-medium text-text-primary hover:underline truncate"
          >
            {repo.fullName}
          </Link>
          <span
            className={cn(
              "text-xs px-1.5 py-0.5 rounded font-medium shrink-0",
              repo.isPrivate
                ? "bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400"
                : "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400"
            )}
          >
            {repo.isPrivate ? "Private" : "Public"}
          </span>
        </div>
        {repo.description && (
          <p className="text-sm text-text-muted truncate mb-2">{repo.description}</p>
        )}
        <div className="flex items-center gap-3 text-xs text-text-muted">
          <span>Default: {repo.defaultBranch}</span>
          {repo.lastSyncedAt && (
            <span>
              Synced {new Date(repo.lastSyncedAt).toLocaleDateString()}
            </span>
          )}
        </div>
      </div>
      <button
        onClick={() => onDisconnect(repo.id)}
        disabled={disconnecting}
        className="shrink-0 text-xs text-red-500 hover:text-red-700 disabled:opacity-50 transition-colors"
      >
        Disconnect
      </button>
    </div>
  );
}
