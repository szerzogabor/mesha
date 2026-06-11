"use client";

import { use, useState } from "react";
import { useProjectStatuses, useCreateProjectStatus, useUpdateProjectStatus, useDeleteProjectStatus, useReorderProjectStatuses } from "@/hooks/useProjectStatuses";
import { ProjectStatus } from "@/types";
import { statusLabel } from "@/lib/utils";
import AutomationRulesSection from "@/components/automation/AutomationRulesSection";
import Link from "next/link";
import {
  DndContext,
  DragEndEvent,
  MouseSensor,
  TouchSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
  closestCenter,
} from "@dnd-kit/core";
import {
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
  arrayMove,
  sortableKeyboardCoordinates,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";

const PRESET_COLORS = [
  "#94a3b8", "#3b82f6", "#f59e0b", "#8b5cf6", "#22c55e",
  "#ef4444", "#f97316", "#06b6d4", "#ec4899", "#84cc16",
];

interface StatusRowProps {
  status: ProjectStatus;
  projectId: string;
  onDeleted: () => void;
}

function StatusRow({ status, projectId, onDeleted }: StatusRowProps) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(status.name);
  const [color, setColor] = useState(status.color);
  const updateStatus = useUpdateProjectStatus(projectId);
  const deleteStatus = useDeleteProjectStatus(projectId);

  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: status.id,
  });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  };

  const handleSave = async () => {
    await updateStatus.mutateAsync({ statusId: status.id, data: { name, color } });
    setEditing(false);
  };

  const handleDelete = async () => {
    if (!confirm(`Delete status "${statusLabel(status.name)}"? Issues with this status will remain but appear in no column.`)) return;
    await deleteStatus.mutateAsync(status.id);
    onDeleted();
  };

  if (editing) {
    return (
      <div ref={setNodeRef} style={style} className="p-3 bg-bg-surface border border-border-default rounded-lg">
        <input
          autoFocus
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="w-full border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent mb-2"
        />
        <div className="flex flex-wrap gap-1.5 mb-3">
          {PRESET_COLORS.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setColor(c)}
              className="w-5 h-5 rounded-full border-2 transition-transform hover:scale-110"
              style={{
                backgroundColor: c,
                borderColor: color === c ? "white" : "transparent",
                boxShadow: color === c ? `0 0 0 2px ${c}` : undefined,
              }}
            />
          ))}
        </div>
        <div className="flex gap-2">
          <button
            onClick={handleSave}
            disabled={!name.trim() || updateStatus.isPending}
            className="px-3 py-1.5 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover disabled:opacity-50 transition-colors"
          >
            {updateStatus.isPending ? "Saving..." : "Save"}
          </button>
          <button
            onClick={() => { setEditing(false); setName(status.name); setColor(status.color); }}
            className="px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary rounded-lg hover:bg-bg-surface-hover transition-colors"
          >
            Cancel
          </button>
        </div>
      </div>
    );
  }

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="flex items-center gap-3 p-3 bg-bg-surface border border-border-default rounded-lg group"
    >
      <button
        {...attributes}
        {...listeners}
        className="cursor-grab active:cursor-grabbing text-text-tertiary hover:text-text-secondary p-0.5"
        aria-label="Drag to reorder"
      >
        <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor">
          <circle cx="4" cy="3" r="1" />
          <circle cx="8" cy="3" r="1" />
          <circle cx="4" cy="6" r="1" />
          <circle cx="8" cy="6" r="1" />
          <circle cx="4" cy="9" r="1" />
          <circle cx="8" cy="9" r="1" />
        </svg>
      </button>

      <span
        className="w-3 h-3 rounded-full flex-shrink-0"
        style={{ backgroundColor: status.color }}
      />

      <span className="flex-1 text-sm font-medium text-text-primary">
        {statusLabel(status.name)}
      </span>

      <span className="text-xs text-text-tertiary font-mono">{status.name}</span>

      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={() => setEditing(true)}
          className="p-1.5 text-text-tertiary hover:text-text-primary rounded hover:bg-bg-surface-hover transition-colors"
          title="Edit"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
          </svg>
        </button>
        <button
          onClick={handleDelete}
          disabled={deleteStatus.isPending}
          className="p-1.5 text-text-tertiary hover:text-destructive rounded hover:bg-bg-surface-hover transition-colors"
          title="Delete"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="3 6 5 6 21 6" />
            <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
            <path d="M10 11v6M14 11v6" />
            <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
          </svg>
        </button>
      </div>
    </div>
  );
}

interface CreateStatusFormProps {
  projectId: string;
  onCreated: () => void;
}

function CreateStatusForm({ projectId, onCreated }: CreateStatusFormProps) {
  const [name, setName] = useState("");
  const [color, setColor] = useState("#6366f1");
  const createStatus = useCreateProjectStatus(projectId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    await createStatus.mutateAsync({ name: name.trim(), color });
    setName("");
    setColor("#6366f1");
    onCreated();
  };

  return (
    <form onSubmit={handleSubmit} className="p-4 bg-bg-surface border border-border-default rounded-lg">
      <h3 className="text-sm font-semibold text-text-primary mb-3">Create New Status</h3>
      <div className="flex gap-3 items-start flex-wrap">
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Status name (e.g. IN_REVIEW)"
          className="flex-1 min-w-48 border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent"
        />
        <div className="flex flex-wrap gap-1.5">
          {PRESET_COLORS.map((c) => (
            <button
              key={c}
              type="button"
              onClick={() => setColor(c)}
              className="w-6 h-6 rounded-full border-2 transition-transform hover:scale-110"
              style={{
                backgroundColor: c,
                borderColor: color === c ? "white" : "transparent",
                boxShadow: color === c ? `0 0 0 2px ${c}` : undefined,
              }}
            />
          ))}
        </div>
        <button
          type="submit"
          disabled={!name.trim() || createStatus.isPending}
          className="px-4 py-1.5 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover disabled:opacity-50 transition-colors"
        >
          {createStatus.isPending ? "Creating..." : "Create"}
        </button>
      </div>
    </form>
  );
}

export default function ProjectSettingsPage({
  params,
}: {
  params: Promise<{ workspaceId: string; projectId: string }>;
}) {
  const { workspaceId, projectId } = use(params);
  const statusesQuery = useProjectStatuses(projectId);
  const reorderStatuses = useReorderProjectStatuses(projectId);
  const [localStatuses, setLocalStatuses] = useState<ProjectStatus[]>([]);

  // Sync local state with fetched statuses
  if (statusesQuery.data && localStatuses.length === 0) {
    setLocalStatuses(statusesQuery.data);
  }

  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    if (!over || active.id === over.id) return;
    const statuses = statusesQuery.data ?? [];
    const oldIndex = statuses.findIndex((s) => s.id === active.id);
    const newIndex = statuses.findIndex((s) => s.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;
    const reordered = arrayMove(statuses, oldIndex, newIndex);
    setLocalStatuses(reordered);
    reorderStatuses.mutate(reordered.map((s) => s.id));
  };

  const statuses = statusesQuery.data ?? [];

  return (
    <div className="h-full overflow-y-auto">
      <div className="px-6 py-4 bg-bg-surface border-b border-border-default">
        <div className="flex items-center gap-3">
          <Link
            href={`/workspaces/${workspaceId}/projects/${projectId}`}
            className="text-text-tertiary hover:text-text-primary transition-colors"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </Link>
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Project Settings</h2>
            <p className="text-sm text-text-tertiary">Manage statuses, workflow and automations</p>
          </div>
        </div>
      </div>

      <div className="p-6 max-w-2xl">
        <div className="mb-6">
          <h3 className="text-base font-semibold text-text-primary mb-1">Statuses</h3>
          <p className="text-sm text-text-tertiary mb-4">
            Define the workflow stages for issues in this project. Drag to reorder columns on the board.
          </p>

          {statusesQuery.isLoading ? (
            <div className="text-sm text-text-tertiary">Loading statuses...</div>
          ) : (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext items={statuses.map((s) => s.id)} strategy={verticalListSortingStrategy}>
                <div className="flex flex-col gap-2 mb-4">
                  {statuses.map((status) => (
                    <StatusRow
                      key={status.id}
                      status={status}
                      projectId={projectId}
                      onDeleted={() => statusesQuery.refetch()}
                    />
                  ))}
                </div>
              </SortableContext>
            </DndContext>
          )}

          <CreateStatusForm
            projectId={projectId}
            onCreated={() => statusesQuery.refetch()}
          />
        </div>

        <AutomationRulesSection workspaceId={workspaceId} projectId={projectId} />
      </div>
    </div>
  );
}
