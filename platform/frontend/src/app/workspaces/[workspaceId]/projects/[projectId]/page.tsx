"use client";

import { use, useState, useCallback, useEffect } from "react";
import {
  useIssues,
  useCreateIssue,
  useAllIssues,
  useUpdateIssueInProject,
} from "@/hooks/useIssues";
import { IssueFilters } from "@/components/issues/IssueFilters";
import { CreateIssueModal } from "@/components/issues/CreateIssueModal";
import { AIDraftModal } from "@/components/issues/AIDraftModal";
import { ViewSwitcher, ViewMode } from "@/components/issues/ViewSwitcher";
import { ListView } from "@/components/issues/ListView";
import { KanbanView } from "@/components/issues/KanbanView";
import { IssueStatus, IssuePriority } from "@/types";

const VIEW_STORAGE_KEY = "mesha-view-mode";

export default function ProjectPage({
  params,
}: {
  params: Promise<{ workspaceId: string; projectId: string }>;
}) {
  const { workspaceId, projectId } = use(params);

  const [view, setView] = useState<ViewMode>("list");
  const [status, setStatus] = useState<IssueStatus | undefined>();
  const [priority, setPriority] = useState<IssuePriority | undefined>();
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [showAIDraft, setShowAIDraft] = useState(false);

  useEffect(() => {
    const saved = localStorage.getItem(VIEW_STORAGE_KEY);
    if (saved === "list" || saved === "kanban") setView(saved);
  }, []);

  const handleViewChange = (v: ViewMode) => {
    setView(v);
    localStorage.setItem(VIEW_STORAGE_KEY, v);
  };

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

  const listQuery = useIssues(projectId, {
    status,
    priority,
    search: search || undefined,
    page,
    size: 25,
  });

  const kanbanQuery = useAllIssues(
    projectId,
    { priority, search: search || undefined },
    { enabled: view === "kanban" }
  );

  const createIssue = useCreateIssue(projectId);
  const updateIssue = useUpdateIssueInProject(projectId);

  const totalElements =
    view === "list"
      ? listQuery.data?.totalElements ?? 0
      : kanbanQuery.data?.totalElements ?? 0;

  return (
    <div className="h-full flex flex-col">
      <div className="px-6 py-4 bg-bg-surface border-b border-border-default flex-shrink-0">
        <div className="flex items-center justify-between mb-3 gap-3 flex-wrap">
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Issues</h2>
            <p className="text-sm text-text-tertiary">{totalElements} total</p>
          </div>
          <div className="flex items-center gap-3">
            <ViewSwitcher view={view} onViewChange={handleViewChange} />
            <button
              onClick={() => setShowAIDraft(true)}
              className="px-4 py-2 bg-bg-surface border border-border-default text-text-primary rounded-lg text-sm hover:bg-bg-surface-hover transition-colors flex items-center gap-1.5"
            >
              ✦ Generate with AI
            </button>
            <button
              onClick={() => setShowCreate(true)}
              className="px-4 py-2 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover transition-colors"
            >
              + New Issue
            </button>
          </div>
        </div>

        <IssueFilters
          status={status}
          priority={priority}
          search={search}
          onStatusChange={handleStatusChange}
          onPriorityChange={handlePriorityChange}
          onSearchChange={handleSearchChange}
          hideStatusFilter={view === "kanban"}
        />
      </div>

      <div
        className={
          view === "kanban"
            ? "flex-1 overflow-hidden flex flex-col min-h-0"
            : "flex-1 overflow-y-auto"
        }
      >
        {view === "list" ? (
          <ListView
            issues={listQuery.data?.content ?? []}
            totalPages={listQuery.data?.totalPages ?? 0}
            page={page}
            isLoading={listQuery.isLoading}
            error={listQuery.error}
            workspaceId={workspaceId}
            projectId={projectId}
            onPageChange={setPage}
          />
        ) : (
          <KanbanView
            issues={kanbanQuery.data?.content ?? []}
            isLoading={kanbanQuery.isLoading}
            error={kanbanQuery.error}
            workspaceId={workspaceId}
            projectId={projectId}
            onUpdateStatus={(issueId, newStatus) =>
              updateIssue.mutate({ issueId, data: { status: newStatus } })
            }
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

      <AIDraftModal
        open={showAIDraft}
        projectId={projectId}
        onClose={() => setShowAIDraft(false)}
      />
    </div>
  );
}
