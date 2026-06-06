"use client";

import { use } from "react";
import { useRouter, usePathname, useSearchParams } from "next/navigation";
import {
  useGitHubPullRequests,
  useSyncPullRequests,
  PullRequestStatus,
} from "@/hooks/useGitHub";
import { PullRequestRow } from "@/components/github/PullRequestRow";
import { Spinner } from "@/components/ui/Spinner";
import Link from "next/link";

const STATUS_OPTIONS: { value: PullRequestStatus | "all"; label: string }[] = [
  { value: "all", label: "All" },
  { value: "open", label: "Open" },
  { value: "merged", label: "Merged" },
  { value: "closed", label: "Closed" },
];

export default function RepositoryDetailPage({
  params,
}: {
  params: Promise<{ workspaceId: string; repositoryId: string }>;
}) {
  const { workspaceId, repositoryId } = use(params);
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const rawStatus = searchParams.get("status");
  // "all" means no filter; null (no param) defaults to "open"
  const activeTab: PullRequestStatus | "all" =
    rawStatus === "all" ? "all"
    : rawStatus === "merged" ? "merged"
    : rawStatus === "closed" ? "closed"
    : "open";

  const filterStatus: PullRequestStatus | undefined =
    activeTab === "all" ? undefined : activeTab;

  const { data: pullRequests = [], isLoading } = useGitHubPullRequests(
    workspaceId,
    repositoryId,
    filterStatus
  );
  const sync = useSyncPullRequests(workspaceId, repositoryId);

  function handleStatusChange(value: PullRequestStatus | "all") {
    const params = new URLSearchParams(searchParams.toString());
    params.set("status", value);
    router.push(`${pathname}?${params.toString()}`);
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

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

      <div className="flex items-center justify-between mb-4 mt-2">
        <h1 className="text-2xl font-bold text-text-primary">Pull Requests</h1>
        <button
          onClick={() => sync.mutate()}
          disabled={sync.isPending}
          className="text-sm px-3 py-1.5 bg-accent text-white rounded-lg hover:bg-accent/90 disabled:opacity-50 transition-colors"
        >
          {sync.isPending ? "Syncing…" : "Sync from GitHub"}
        </button>
      </div>

      <div className="flex gap-1 mb-4 bg-bg-subtle rounded-lg p-1 w-fit">
        {STATUS_OPTIONS.map(({ value, label }) => (
          <button
            key={value}
            onClick={() => handleStatusChange(value)}
            className={`px-3 py-1.5 text-sm rounded-md font-medium transition-colors ${
              activeTab === value
                ? "bg-bg-surface text-text-primary shadow-sm"
                : "text-text-muted hover:text-text-primary"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {pullRequests.length === 0 ? (
        <div className="rounded-lg border border-dashed border-border-subtle p-8 text-center">
          <p className="text-text-muted text-sm mb-2">
            No {activeTab !== "all" ? activeTab : ""} pull requests found.
          </p>
          {activeTab === "all" && (
            <p className="text-text-muted text-xs">
              Click &quot;Sync from GitHub&quot; to fetch pull requests.
            </p>
          )}
        </div>
      ) : (
        <div className="bg-bg-surface border border-border-subtle rounded-lg divide-y divide-border-subtle">
          <section className="px-4 py-2">
            <h3 className="text-xs font-semibold text-text-muted uppercase tracking-wider mb-1">
              {activeTab === "all"
                ? `All (${pullRequests.length})`
                : `${STATUS_OPTIONS.find((s) => s.value === activeTab)?.label} (${pullRequests.length})`}
            </h3>
            {pullRequests.map((pr) => (
              <PullRequestRow key={pr.id} pr={pr} />
            ))}
          </section>
        </div>
      )}
    </div>
  );
}
