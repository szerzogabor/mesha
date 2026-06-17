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
import {
  SortableContext,
  horizontalListSortingStrategy,
  sortableKeyboardCoordinates,
  arrayMove,
} from "@dnd-kit/sortable";
import { Issue, ProjectStatus } from "@/types";
import { KanbanColumn } from "./KanbanColumn";
import { KanbanCard } from "./KanbanCard";
import { AddStatusColumn } from "./AddStatusColumn";
import { Spinner } from "@/components/ui/Spinner";
import { logger } from "@/lib/logger";

// pointerWithin correctly targets whichever column/card is under the cursor,
// which reliably hits empty columns. rectIntersection covers the edge case
// where the pointer is in a gap between columns.
const kanbanCollision: CollisionDetection = (args) => {
  const pointerCollisions = pointerWithin(args);
  if (pointerCollisions.length > 0) return pointerCollisions;
  return rectIntersection(args);
};

// Resolve the target status name from a drag-over or drag-end event.
// Column droppable IDs equal the status name string; card droppable IDs are UUIDs.
function resolveTargetStatus(
  overId: string,
  statuses: ProjectStatus[],
  localIssuesRef: React.RefObject<Issue[]>
): string | undefined {
  if (statuses.some((s) => s.name === overId)) return overId;
  return localIssuesRef.current?.find((i) => i.id === overId)?.status;
}

interface KanbanViewProps {
  issues: Issue[];
  statuses: ProjectStatus[];
  isLoading: boolean;
  error: Error | null;
  workspaceId: string;
  projectId: string;
  onUpdateStatus: (issueId: string, status: string) => void;
  onReorderStatuses: (statusIds: string[]) => void;
  onCreateIssueForStatus?: (status: string) => void;
}

export function KanbanView({
  issues,
  statuses: initialStatuses,
  isLoading,
  error,
  workspaceId,
  projectId,
  onUpdateStatus,
  onReorderStatuses,
  onCreateIssueForStatus,
}: KanbanViewProps) {
  const [localIssues, setLocalIssues] = useState<Issue[]>(issues);
  const [localStatuses, setLocalStatuses] = useState<ProjectStatus[]>(initialStatuses);
  const [activeIssue, setActiveIssue] = useState<Issue | null>(null);
  const [draggingColumn, setDraggingColumn] = useState(false);

  const localIssuesRef = useRef(localIssues);
  localIssuesRef.current = localIssues;

  const localStatusesRef = useRef(localStatuses);
  localStatusesRef.current = localStatuses;

  useEffect(() => {
    setLocalIssues(issues);
  }, [issues]);

  useEffect(() => {
    setLocalStatuses(initialStatuses);
  }, [initialStatuses]);

  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
    // Delay-based activation lets quick swipes scroll the board while a
    // 250ms hold initiates a drag, preventing conflicts with horizontal scroll.
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragStart = useCallback(({ active }: DragStartEvent) => {
    if (String(active.id).startsWith("col-")) {
      setDraggingColumn(true);
      return;
    }
    const issue = localIssuesRef.current.find((i) => i.id === active.id) ?? null;
    setActiveIssue(issue);
    if (issue) {
      logger.kanban.dragStarted(issue.id, issue.status);
    }
  }, []);

  const handleDragOver = useCallback(({ active, over }: DragOverEvent) => {
    if (!over) return;

    // Column reordering — handled in handleDragEnd
    if (String(active.id).startsWith("col-")) return;

    const targetStatus = resolveTargetStatus(String(over.id), localStatusesRef.current, localIssuesRef);
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

      // Handle column reorder
      if (activeId.startsWith("col-")) {
        setDraggingColumn(false);
        if (!over || active.id === over.id) return;

        const oldIndex = localStatusesRef.current.findIndex((s) => `col-${s.id}` === activeId);
        const newIndex = localStatusesRef.current.findIndex((s) => `col-${s.id}` === String(over.id));
        if (oldIndex === -1 || newIndex === -1) return;

        const reordered = arrayMove(localStatusesRef.current, oldIndex, newIndex);
        setLocalStatuses(reordered);
        onReorderStatuses(reordered.map((s) => s.id));
        return;
      }

      const originalIssue = issues.find((i) => i.id === activeId);
      setActiveIssue(null);

      if (!over) {
        logger.kanban.dragCanceled(activeId);
        setLocalIssues(issues);
        return;
      }

      const targetStatus = resolveTargetStatus(String(over.id), localStatusesRef.current, localIssuesRef);
      if (!targetStatus) {
        setLocalIssues(issues);
        return;
      }

      if (!originalIssue) return;

      logger.kanban.dragEnded(activeId, originalIssue.status, targetStatus);

      if (originalIssue.status !== targetStatus) {
        onUpdateStatus(activeId, targetStatus);
      } else {
        setLocalIssues(issues);
      }
    },
    [issues, onUpdateStatus, onReorderStatuses]
  );

  const handleDragCancel = useCallback(() => {
    if (draggingColumn) {
      setDraggingColumn(false);
      setLocalStatuses(initialStatuses);
      return;
    }
    logger.kanban.dragCanceled(activeIssue?.id);
    setActiveIssue(null);
    setLocalIssues(issues);
  }, [activeIssue, issues, draggingColumn, initialStatuses]);

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

  const columnIds = localStatuses.map((s) => `col-${s.id}`);

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
        <SortableContext items={columnIds} strategy={horizontalListSortingStrategy}>
          {localStatuses.map((status) => (
            <KanbanColumn
              key={status.id}
              status={status}
              issues={localIssues.filter((i) => i.status === status.name)}
              workspaceId={workspaceId}
              projectId={projectId}
              onCreateIssue={onCreateIssueForStatus ? () => onCreateIssueForStatus(status.name) : undefined}
            />
          ))}
        </SortableContext>

        <AddStatusColumn projectId={projectId} />
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
