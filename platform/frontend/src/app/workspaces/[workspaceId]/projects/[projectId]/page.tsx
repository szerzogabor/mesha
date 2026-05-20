"use client";

import { use, useState, useCallback } from "react";
import { useIssues, useCreateIssue } from "@/hooks/useIssues";
import { IssueCard } from "@/components/issues/IssueCard";
import { IssueFilters } from "@/components/issues/IssueFilters";
import { CreateIssueModal } from "@/components/issues/CreateIssueModal";
import { Pagination } from "@/components/ui/Pagination";
import { Spinner } from "@/components/ui/Spinner";
import { IssueStatus, IssuePriority } from "@/types";

export default function ProjectPage({
  params,
}: {
  params: Promise<{ workspaceId: string; projectId: string }>;
}) {
  const { workspaceId, projectId } = use(params);

  const [status, setStatus] = useState<IssueStatus | undefined>();
  const [priority, setPriority] = useState<IssuePriority | undefined>();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);

  const { data, isLoading, error } = useIssues(projectId, {
    status,
    priority,
    search: search || undefined,
    page,
    size: 25,
  });

  const createIssue = useCreateIssue(projectId);

  const handleFilterChange = useCallback(() => setPage(0), []);

  const handleStatusChange = (s: IssueStatus | undefined) => {
    setStatus(s);
    handleFilterChange();
  };
  const handlePriorityChange = (p: IssuePriority | undefined) => {
    setPriority(p);
    handleFilterChange();
  };
  const handleSearchChange = (s: string) => {
    setSearch(s);
    handleFilterChange();
  };

  return (
    <div className="h-full flex flex-col">
      <div className="px-6 py-4 bg-bg-surface border-b border-border-default">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Issues</h2>
            {data && (
              <p className="text-sm text-text-tertiary">{data.totalElements} total</p>
            )}
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="px-4 py-2 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover transition-colors"
          >
            + New Issue
          </button>
        </div>

        <IssueFilters
          status={status}
          priority={priority}
          search={search}
          onStatusChange={handleStatusChange}
          onPriorityChange={handlePriorityChange}
          onSearchChange={handleSearchChange}
        />
      </div>

      <div className="flex-1 overflow-y-auto">
        {isLoading && (
          <div className="flex items-center justify-center py-16">
            <Spinner size="lg" className="text-accent" />
          </div>
        )}

        {error && (
          <div className="p-6">
            <div className="p-4 bg-destructive-muted text-destructive rounded-lg text-sm">
              {error instanceof Error ? error.message : "Failed to load issues"}
            </div>
          </div>
        )}

        {data && data.content.length === 0 && (
          <div className="flex flex-col items-center justify-center py-24 text-text-tertiary">
            <p className="text-lg mb-1">No issues found</p>
            <p className="text-sm">
              {search || status || priority
                ? "Try clearing your filters."
                : 'Create your first issue with "+ New Issue".'}
            </p>
          </div>
        )}

        {data && data.content.length > 0 && (
          <div className="bg-bg-surface mx-6 mt-4 rounded-xl border border-border-default divide-y divide-border-default overflow-hidden">
            {data.content.map((issue) => (
              <IssueCard
                key={issue.id}
                issue={issue}
                workspaceId={workspaceId}
                projectId={projectId}
              />
            ))}
          </div>
        )}

        {data && (
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            onPageChange={setPage}
          />
        )}
      </div>

      <CreateIssueModal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        onSubmit={async (formData) => {
          await createIssue.mutateAsync(formData);
        }}
      />
    </div>
  );
}
