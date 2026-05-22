"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import {
  DndContext,
  DragEndEvent,
  DragOverEvent,
  DragStartEvent,
  DragOverlay,
  MouseSensor,
  TouchSensor,
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
import { logger } from "@/lib/logger";

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
    useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
    // Delay-based activation lets quick swipes scroll the board while a
    // 250ms hold initiates a drag, preventing conflicts with horizontal scroll.
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragStart = useCallback(({ active }: DragStartEvent) => {
    const issue = localIssuesRef.current.find((i) => i.id === active.id) ?? null;
    setActiveIssue(issue);
    if (issue) {
      logger.kanban.dragStarted(issue.id, issue.status);
    }
  }, []);

  const handleDragOver = useCallback(({ active, over }: DragOverEvent) => {
    if (!over) return;
    const targetStatus = resolveTargetStatus(String(over.id), localIssuesRef);
    if (!targetStatus) return;

    const activeId = String(active.id);
    setLocalIssues((prev) =>
      prev.map((issue) => {
        if (issue.id === activeId && issue.status !== targetStatus) {
          logger.kanban.optimisticUpdate(issue.id, targetStatus);
          return { ...issue, status: targetStatus };
        }
        return issue;
      })
    );
  }, []);

  const handleDragEnd = useCallback(
    ({ active, over }: DragEndEvent) => {
      const activeId = String(active.id);
      const originalIssue = issues.find((i) => i.id === activeId);

      setActiveIssue(null);

      if (!over) {
        logger.kanban.dragCanceled(activeId);
        setLocalIssues(issues);
        return;
      }

      const targetStatus = resolveTargetStatus(String(over.id), localIssuesRef);
      if (!targetStatus) {
        setLocalIssues(issues);
        return;
      }

      if (!originalIssue) return;

      logger.kanban.dragEnded(activeId, originalIssue.status, targetStatus);

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
    logger.kanban.dragCanceled(activeIssue?.id);
    setActiveIssue(null);
    setLocalIssues(issues);
  }, [activeIssue, issues]);

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
