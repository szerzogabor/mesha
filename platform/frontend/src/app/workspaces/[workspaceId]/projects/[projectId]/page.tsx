"use client";

import { use, useState, useCallback, useEffect, useRef } from "react";
import {
  useIssues,
  useCreateIssue,
  useAllIssues,
  useUpdateIssueInProject,
} from "@/hooks/useIssues";
import { useProjectStatuses, useReorderProjectStatuses } from "@/hooks/useProjectStatuses";
import { useIssueEvents } from "@/hooks/useIssueEvents";
import { IssueFilters } from "@/components/issues/IssueFilters";
import { CreateIssueModal } from "@/components/issues/CreateIssueModal";
import { AIDraftModal } from "@/components/issues/AIDraftModal";
import { ViewSwitcher, ViewMode } from "@/components/issues/ViewSwitcher";
import { ListView } from "@/components/issues/ListView";
import { KanbanView } from "@/components/issues/KanbanView";
import { IssueStatus, IssuePriority } from "@/types";
import Link from "next/link";
import { RuleViolationDialog } from "@/components/ui/RuleViolationDialog";
import { extractApiErrorMessage, isRuleViolationError } from "@/lib/error-utils";

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
  const [labelIds, setLabelIds] = useState<string[]>([]);
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [createWithStatus, setCreateWithStatus] = useState<string | undefined>(undefined);
  const [showAIDraft, setShowAIDraft] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [ruleViolation, setRuleViolation] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    }
    if (showDropdown) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [showDropdown]);

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
  const handleLabelIdsChange = (ids: string[]) => {
    setLabelIds(ids);
    handleFilterChange();
  };

  useIssueEvents(projectId);

  const listQuery = useIssues(projectId, {
    status,
    priority,
    search: search || undefined,
    labelIds: labelIds.length ? labelIds : undefined,
    page,
    size: 25,
  });

  const kanbanQuery = useAllIssues(
    projectId,
    { priority, search: search || undefined, labelIds: labelIds.length ? labelIds : undefined },
    { enabled: view === "kanban" }
  );

  const createIssue = useCreateIssue(projectId);
  const updateIssue = useUpdateIssueInProject(projectId);
  const statusesQuery = useProjectStatuses(projectId);
  const reorderStatuses = useReorderProjectStatuses(projectId);

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
            <Link
              href={`/workspaces/${workspaceId}/projects/${projectId}/settings`}
              className="p-2 text-text-tertiary hover:text-text-primary hover:bg-bg-surface-hover rounded-lg transition-colors"
              title="Project settings"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="3" />
                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
              </svg>
            </Link>
            <ViewSwitcher view={view} onViewChange={handleViewChange} />
            <div ref={dropdownRef} className="relative flex items-center">
              <button
                onClick={() => setShowCreate(true)}
                className="px-4 py-2 bg-accent text-white rounded-l-lg text-sm hover:bg-accent-hover transition-colors"
              >
                + New Issue
              </button>
              <button
                onClick={() => setShowDropdown((v) => !v)}
                className="px-2 py-2 bg-accent text-white rounded-r-lg text-sm hover:bg-accent-hover transition-colors border-l border-white/20"
                aria-label="More issue creation options"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </button>
              {showDropdown && (
                <div className="absolute right-0 top-full mt-1 bg-bg-surface border border-border-default rounded-lg shadow-lg z-10 w-48 py-1">
                  <button
                    onClick={() => {
                      setShowDropdown(false);
                      setShowAIDraft(true);
                    }}
                    className="w-full px-4 py-2 text-sm text-left text-text-primary hover:bg-bg-surface-hover transition-colors flex items-center gap-2"
                  >
                    <span>✦</span> Generate with AI
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        <IssueFilters
          status={status}
          priority={priority}
          search={search}
          projectStatuses={statusesQuery.data}
          workspaceId={workspaceId}
          selectedLabelIds={labelIds}
          onStatusChange={handleStatusChange}
          onPriorityChange={handlePriorityChange}
          onSearchChange={handleSearchChange}
          onLabelIdsChange={handleLabelIdsChange}
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
            statuses={statusesQuery.data ?? []}
            isLoading={kanbanQuery.isLoading || statusesQuery.isLoading}
            error={kanbanQuery.error}
            workspaceId={workspaceId}
            projectId={projectId}
            onUpdateStatus={(issueId, newStatus) =>
              updateIssue.mutate(
                { issueId, data: { status: newStatus } },
                {
                  onError: (err) => {
                    if (isRuleViolationError(err)) {
                      setRuleViolation(extractApiErrorMessage(err));
                    }
                  },
                }
              )
            }
            onReorderStatuses={(statusIds) => reorderStatuses.mutate(statusIds)}
            onCreateIssueForStatus={(s) => {
              setCreateWithStatus(s);
              setShowCreate(true);
            }}
          />
        )}
      </div>

      <CreateIssueModal
        open={showCreate}
        onClose={() => {
          setShowCreate(false);
          setCreateWithStatus(undefined);
        }}
        workspaceId={workspaceId}
        projectId={projectId}
        projectStatuses={statusesQuery.data}
        defaultStatus={createWithStatus}
        onSubmit={async (formData) => {
          return createIssue.mutateAsync(formData);
        }}
      />

      <AIDraftModal
        open={showAIDraft}
        projectId={projectId}
        onClose={() => setShowAIDraft(false)}
      />

      <RuleViolationDialog message={ruleViolation} onClose={() => setRuleViolation(null)} />
    </div>
  );
}
