"use client";

import { use } from "react";
import {
  useGitHubPullRequests,
  useSyncPullRequests,
} from "@/hooks/useGitHub";
import { PullRequestRow } from "@/components/github/PullRequestRow";
import { Spinner } from "@/components/ui/Spinner";
import Link from "next/link";

export default function RepositoryDetailPage({
  params,
}: {
  params: Promise<{ workspaceId: string; repositoryId: string }>;
}) {
  const { workspaceId, repositoryId } = use(params);
  const { data: pullRequests = [], isLoading } = useGitHubPullRequests(
    workspaceId,
    repositoryId
  );
  const sync = useSyncPullRequests(workspaceId, repositoryId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  const open = pullRequests.filter((pr) => pr.state === "open" && !pr.draft);
  const drafts = pullRequests.filter((pr) => pr.draft);
  const closed = pullRequests.filter((pr) => pr.state !== "open");

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <div className="flex items-center gap-2 mb-1">
        <Link
          href={`/workspaces/${workspaceId}/github`}
          className="text-sm text-text-muted hover:text-text-primary transition-colors"
        >
          GitHub
        </Link>
        <span className="text-text-muted">/</span>
        <span className="text-sm text-text-primary font-medium">
          Pull Requests
        </span>
      </div>

      <div className="flex items-center justify-between mb-6 mt-2">
        <h1 className="text-2xl font-bold text-text-primary">Pull Requests</h1>
        <button
          onClick={() => sync.mutate()}
          disabled={sync.isPending}
          className="text-sm px-3 py-1.5 bg-accent text-white rounded-lg hover:bg-accent/90 disabled:opacity-50 transition-colors"
        >
          {sync.isPending ? "Syncing…" : "Sync from GitHub"}
        </button>
      </div>

      {pullRequests.length === 0 ? (
        <div className="rounded-lg border border-dashed border-border-subtle p-8 text-center">
          <p className="text-text-muted text-sm mb-2">No pull requests synced yet.</p>
          <p className="text-text-muted text-xs">
            Click &quot;Sync from GitHub&quot; to fetch pull requests.
          </p>
        </div>
      ) : (
        <div className="bg-bg-surface border border-border-subtle rounded-lg divide-y divide-border-subtle">
          {open.length > 0 && (
            <section className="px-4 py-2">
              <h3 className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-1">
                Open ({open.length})
              </h3>
              {open.map((pr) => (
                <PullRequestRow key={pr.id} pr={pr} />
              ))}
            </section>
          )}
          {drafts.length > 0 && (
            <section className="px-4 py-2">
              <h3 className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-1">
                Drafts ({drafts.length})
              </h3>
              {drafts.map((pr) => (
                <PullRequestRow key={pr.id} pr={pr} />
              ))}
            </section>
          )}
          {closed.length > 0 && (
            <section className="px-4 py-2">
              <h3 className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-1">
                Closed / Merged ({closed.length})
              </h3>
              {closed.map((pr) => (
                <PullRequestRow key={pr.id} pr={pr} />
              ))}
            </section>
          )}
        </div>
      )}
    </div>
  );
}
