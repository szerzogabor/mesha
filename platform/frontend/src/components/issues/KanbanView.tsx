"use client";

import { useState, useEffect, useCallback } from "react";
import {
  DndContext,
  DragEndEvent,
  DragOverEvent,
  DragStartEvent,
  DragOverlay,
  PointerSensor,
  KeyboardSensor,
  closestCorners,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import { Issue, IssueStatus } from "@/types";
import { KanbanColumn } from "./KanbanColumn";
import { KanbanCard } from "./KanbanCard";
import { Spinner } from "@/components/ui/Spinner";

const STATUSES: IssueStatus[] = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE"];

interface KanbanViewProps {
  issues: Issue[];
  isLoading: boolean;
  error: Error | null;
  workspaceId: string;
  projectId: string;
  onUpdateStatus: (issueId: string, status: IssueStatus) => void;
}

export function KanbanView({
  issues,
  isLoading,
  error,
  workspaceId,
  projectId,
  onUpdateStatus,
}: KanbanViewProps) {
  const [localIssues, setLocalIssues] = useState<Issue[]>(issues);
  const [activeIssue, setActiveIssue] = useState<Issue | null>(null);

  useEffect(() => {
    setLocalIssues(issues);
  }, [issues]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragStart = useCallback(
    ({ active }: DragStartEvent) => {
      setActiveIssue(localIssues.find((i) => i.id === active.id) ?? null);
    },
    [localIssues]
  );

  const handleDragOver = useCallback(({ active, over }: DragOverEvent) => {
    if (!over) return;
    const targetStatus = over.data.current?.status as IssueStatus | undefined;
    if (!targetStatus) return;

    setLocalIssues((prev) =>
      prev.map((issue) =>
        issue.id === active.id && issue.status !== targetStatus
          ? { ...issue, status: targetStatus }
          : issue
      )
    );
  }, []);

  const handleDragEnd = useCallback(
    ({ active, over }: DragEndEvent) => {
      setActiveIssue(null);
      if (!over) {
        setLocalIssues(issues);
        return;
      }

      const targetStatus = over.data.current?.status as IssueStatus | undefined;
      if (!targetStatus) {
        setLocalIssues(issues);
        return;
      }

      const originalIssue = issues.find((i) => i.id === active.id);
      if (!originalIssue) return;

      if (originalIssue.status !== targetStatus) {
        onUpdateStatus(String(active.id), targetStatus);
      }
    },
    [issues, onUpdateStatus]
  );

  const handleDragCancel = useCallback(() => {
    setActiveIssue(null);
    setLocalIssues(issues);
  }, [issues]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6">
        <div className="p-4 bg-destructive-muted text-destructive rounded-lg text-sm">
          {error instanceof Error ? error.message : "Failed to load issues"}
        </div>
      </div>
    );
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCorners}
      onDragStart={handleDragStart}
      onDragOver={handleDragOver}
      onDragEnd={handleDragEnd}
      onDragCancel={handleDragCancel}
    >
      <div className="flex gap-4 px-6 py-4 overflow-x-auto flex-1 pb-6 min-h-0">
        {STATUSES.map((status) => (
          <KanbanColumn
            key={status}
            status={status}
            issues={localIssues.filter((i) => i.status === status)}
            workspaceId={workspaceId}
            projectId={projectId}
          />
        ))}
      </div>

      <DragOverlay>
        {activeIssue ? (
          <KanbanCard
            issue={activeIssue}
            workspaceId={workspaceId}
            projectId={projectId}
            overlay
          />
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
