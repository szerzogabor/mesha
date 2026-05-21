"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import {
  DndContext,
  DragEndEvent,
  DragOverEvent,
  DragStartEvent,
  DragOverlay,
  PointerSensor,
  KeyboardSensor,
  pointerWithin,
  rectIntersection,
  useSensor,
  useSensors,
  CollisionDetection,
} from "@dnd-kit/core";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import { Issue, IssueStatus } from "@/types";
import { KanbanColumn } from "./KanbanColumn";
import { KanbanCard } from "./KanbanCard";
import { Spinner } from "@/components/ui/Spinner";

const STATUSES: IssueStatus[] = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE"];

// pointerWithin correctly targets whichever column/card is under the cursor,
// which reliably hits empty columns. rectIntersection covers the edge case
// where the pointer is in a gap between columns.
const kanbanCollision: CollisionDetection = (args) => {
  const pointerCollisions = pointerWithin(args);
  if (pointerCollisions.length > 0) return pointerCollisions;
  return rectIntersection(args);
};

// Resolve the target IssueStatus from a drag-over or drag-end event.
// Column droppable IDs equal the status string; card droppable IDs are UUIDs.
function resolveTargetStatus(
  overId: string,
  localIssuesRef: React.RefObject<Issue[]>
): IssueStatus | undefined {
  if (STATUSES.includes(overId as IssueStatus)) {
    return overId as IssueStatus;
  }
  return localIssuesRef.current?.find((i) => i.id === overId)?.status;
}

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

  // Keep a ref so memoized callbacks always read the latest localIssues
  // without needing it in their dependency arrays.
  const localIssuesRef = useRef(localIssues);
  localIssuesRef.current = localIssues;

  useEffect(() => {
    setLocalIssues(issues);
  }, [issues]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragStart = useCallback(({ active }: DragStartEvent) => {
    setActiveIssue(localIssuesRef.current.find((i) => i.id === active.id) ?? null);
  }, []);

  const handleDragOver = useCallback(({ active, over }: DragOverEvent) => {
    if (!over) return;
    const targetStatus = resolveTargetStatus(String(over.id), localIssuesRef);
    if (!targetStatus) return;

    const activeId = String(active.id);
    setLocalIssues((prev) =>
      prev.map((issue) =>
        issue.id === activeId && issue.status !== targetStatus
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

      const targetStatus = resolveTargetStatus(String(over.id), localIssuesRef);
      if (!targetStatus) {
        setLocalIssues(issues);
        return;
      }

      const activeId = String(active.id);
      const originalIssue = issues.find((i) => i.id === activeId);
      if (!originalIssue) return;

      if (originalIssue.status !== targetStatus) {
        onUpdateStatus(activeId, targetStatus);
      } else {
        // Target column is same as origin — revert the optimistic move
        setLocalIssues(issues);
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
      collisionDetection={kanbanCollision}
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
