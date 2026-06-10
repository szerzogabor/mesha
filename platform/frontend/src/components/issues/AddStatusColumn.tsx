"use client";

import { useState } from "react";
import { useCreateProjectStatus } from "@/hooks/useProjectStatuses";

const PRESET_COLORS = [
  "#94a3b8", "#3b82f6", "#f59e0b", "#8b5cf6", "#22c55e",
  "#ef4444", "#f97316", "#06b6d4", "#ec4899", "#84cc16",
];

interface AddStatusColumnProps {
  projectId: string;
}

export function AddStatusColumn({ projectId }: AddStatusColumnProps) {
  const [expanded, setExpanded] = useState(false);
  const [name, setName] = useState("");
  const [color, setColor] = useState("#6366f1");
  const createStatus = useCreateProjectStatus(projectId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    await createStatus.mutateAsync({ name: name.trim(), color });
    setName("");
    setColor("#6366f1");
    setExpanded(false);
  };

  if (!expanded) {
    return (
      <div className="flex flex-col w-72 flex-shrink-0">
        <button
          onClick={() => setExpanded(true)}
          className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-text-tertiary hover:text-text-primary hover:bg-bg-surface-hover transition-colors border-2 border-dashed border-border-default"
        >
          <span className="text-lg leading-none">+</span>
          <span>Add status</span>
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col w-72 flex-shrink-0">
      <form onSubmit={handleSubmit} className="p-3 bg-bg-surface border border-border-default rounded-xl">
        <p className="text-xs font-semibold text-text-secondary mb-2 uppercase tracking-wide">New Status</p>
        <input
          autoFocus
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Status name..."
          className="w-full border border-input-border rounded-lg px-3 py-1.5 text-sm bg-input-bg text-text-primary placeholder:text-text-placeholder focus:outline-none focus:ring-2 focus:ring-accent mb-2"
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
        <div className="flex items-center gap-2">
          <button
            type="submit"
            disabled={!name.trim() || createStatus.isPending}
            className="flex-1 px-3 py-1.5 bg-accent text-white rounded-lg text-sm hover:bg-accent-hover disabled:opacity-50 transition-colors"
          >
            {createStatus.isPending ? "Creating..." : "Create"}
          </button>
          <button
            type="button"
            onClick={() => { setExpanded(false); setName(""); }}
            className="px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary rounded-lg hover:bg-bg-surface-hover transition-colors"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
