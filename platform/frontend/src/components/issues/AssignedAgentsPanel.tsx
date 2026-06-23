"use client";

import { useState, useRef, useEffect } from "react";
import { useIssueAgents, useAssignAgent, useUnassignAgent } from "@/hooks/useIssueAgents";
import { useActiveAgentDefinitions } from "@/hooks/useAgentDefinitions";
import { AssignableAgent, IssueAgentAssignment } from "@/types";

interface AssignedAgentsPanelProps {
  workspaceId: string;
  projectId: string;
  issueId: string;
}

export function AssignedAgentsPanel({ workspaceId, projectId, issueId }: AssignedAgentsPanelProps) {
  const { data: assignments = [] } = useIssueAgents(projectId, issueId);
  const { data: activeAgents = [] } = useActiveAgentDefinitions(workspaceId);
  const assignAgent = useAssignAgent(projectId, issueId);
  const unassignAgent = useUnassignAgent(projectId, issueId);

  const [showPicker, setShowPicker] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setShowPicker(false);
      }
    }
    if (showPicker) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [showPicker]);

  const assignedIds = new Set(assignments.map((a: IssueAgentAssignment) => a.agentDefinitionId));
  const availableAgents = activeAgents.filter(
    (a: AssignableAgent) => a.providerType !== "CONNECTOR" && !assignedIds.has(a.id)
  );

  return (
    <div className="bg-bg-surface rounded-xl border border-border-default p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          Assigned Agents
        </h3>
      </div>

      {assignments.length > 0 ? (
        <div className="space-y-2 mb-3">
          {assignments.map((assignment: IssueAgentAssignment) => (
            <div
              key={assignment.id}
              className="flex items-center justify-between group"
            >
              <div className="flex items-center gap-2 min-w-0">
                <div className="h-6 w-6 rounded-full bg-accent/10 flex items-center justify-center text-accent text-xs font-bold shrink-0">
                  {assignment.agentTitle.charAt(0).toUpperCase()}
                </div>
                <span className="text-sm text-text-primary truncate">
                  {assignment.agentTitle}
                </span>
              </div>
              <button
                onClick={() => !unassignAgent.isPending && unassignAgent.mutate(assignment.agentDefinitionId)}
                disabled={unassignAgent.isPending}
                className="text-xs text-text-tertiary hover:text-destructive transition-colors opacity-0 group-hover:opacity-100 disabled:opacity-50"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-sm text-text-tertiary mb-3">No agents assigned</p>
      )}

      <div className="relative" ref={pickerRef}>
        <button
          onClick={() => setShowPicker((v) => !v)}
          className="text-xs text-text-tertiary hover:text-text-primary transition-colors"
        >
          + Assign Agent
        </button>

        {showPicker && (
          <div className="absolute left-0 top-full mt-1 z-10 w-64 bg-bg-surface border border-border-default rounded-lg shadow-lg py-1 max-h-48 overflow-y-auto">
            {availableAgents.length === 0 ? (
              <p className="px-3 py-2 text-xs text-text-tertiary">
                {activeAgents.length === 0
                  ? "No agents configured. Create one in Settings."
                  : "All active agents are already assigned."}
              </p>
            ) : (
              availableAgents.map((agent: AssignableAgent) => (
                <button
                  key={agent.id}
                  onClick={async () => {
                    await assignAgent.mutateAsync(agent.id);
                    setShowPicker(false);
                  }}
                  disabled={assignAgent.isPending}
                  className="w-full text-left px-3 py-2 hover:bg-bg-surface-hover transition-colors flex items-center gap-2"
                >
                  <div className="h-5 w-5 rounded-full bg-accent/10 flex items-center justify-center text-accent text-[10px] font-bold shrink-0">
                    {agent.title.charAt(0).toUpperCase()}
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm text-text-primary truncate">{agent.title}</p>
                    <p className="text-[10px] text-text-tertiary font-mono">{agent.name}</p>
                  </div>
                </button>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
