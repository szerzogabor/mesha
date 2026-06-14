"use client";

import { useRef, useState, useEffect } from "react";
import { UserSummary, AgentDefinition, IssueAgentAssignment } from "@/types";
import { WorkspaceMember } from "@/hooks/useWorkspaceMembers";

type AssigneeSelection =
  | { type: "none" }
  | { type: "human"; userId: string }
  | { type: "agent"; agentId: string };

interface AssigneeSelectorProps {
  assignee?: UserSummary;
  assignedAgent?: IssueAgentAssignment;
  members: WorkspaceMember[];
  activeAgents?: AgentDefinition[];
  onSelect: (selection: AssigneeSelection) => void;
  disabled?: boolean;
  compact?: boolean;
}

function initials(member: WorkspaceMember | UserSummary): string {
  const name = "name" in member ? member.name : undefined;
  const email = member.email;
  return ((name || email)[0] ?? "?").toUpperCase();
}

function displayName(member: WorkspaceMember | UserSummary): string {
  return ("name" in member && member.name ? member.name : undefined) || member.email;
}

function PlaceholderAvatar() {
  return (
    <div
      className="h-6 w-6 rounded-full border-2 border-dashed border-border-default flex items-center justify-center text-text-tertiary hover:border-accent hover:text-accent transition-colors"
      title="Unassigned — click to assign"
    >
      <svg className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
        <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
      </svg>
    </div>
  );
}

function AgentAvatar({ title }: { title: string }) {
  return (
    <div className="h-6 w-6 rounded-full bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center text-purple-600 dark:text-purple-400 text-xs font-bold shrink-0">
      {title.charAt(0).toUpperCase()}
    </div>
  );
}

export function AssigneeSelector({
  assignee,
  assignedAgent,
  members,
  activeAgents = [],
  onSelect,
  disabled = false,
  compact = false,
}: AssigneeSelectorProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    if (open) document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const availableAgents = activeAgents.filter(
    (a) => a.id !== assignedAgent?.agentDefinitionId
  );

  return (
    <div
      ref={ref}
      className="relative"
      onPointerDown={compact ? (e) => e.stopPropagation() : undefined}
    >
      <button
        type="button"
        disabled={disabled}
        onClick={(e) => {
          if (compact) {
            e.preventDefault();
            e.stopPropagation();
          }
          setOpen((v) => !v);
        }}
        className="flex items-center gap-2 hover:opacity-80 transition-opacity disabled:opacity-50 disabled:cursor-not-allowed"
        title={
          assignee
            ? displayName(assignee)
            : assignedAgent
              ? assignedAgent.agentTitle
              : "Unassigned — click to assign"
        }
      >
        {assignee ? (
          <>
            <div className="h-6 w-6 rounded-full bg-accent-muted flex items-center justify-center text-xs font-medium text-accent-muted-text">
              {initials(assignee)}
            </div>
            {!compact && <span className="text-sm text-text-primary">{displayName(assignee)}</span>}
          </>
        ) : assignedAgent ? (
          <>
            <AgentAvatar title={assignedAgent.agentTitle} />
            {!compact && (
              <span className="text-sm text-text-primary">{assignedAgent.agentTitle}</span>
            )}
          </>
        ) : (
          <>
            <PlaceholderAvatar />
            {!compact && <span className="text-sm text-text-tertiary">Unassigned</span>}
          </>
        )}
      </button>

      {open && (
        <div className="absolute left-0 top-full mt-1 z-50 w-64 rounded-lg border border-border-default bg-bg-surface shadow-lg py-1 max-h-72 overflow-y-auto">
          {/* No assignee */}
          <button
            type="button"
            onClick={() => { onSelect({ type: "none" }); setOpen(false); }}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-secondary hover:bg-bg-surface-hover transition-colors"
          >
            <div className="h-6 w-6 rounded-full border-2 border-dashed border-border-default flex items-center justify-center text-text-tertiary flex-shrink-0">
              <svg className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
              </svg>
            </div>
            <span>No assignee</span>
            {!assignee && !assignedAgent && <span className="ml-auto text-accent text-xs">✓</span>}
          </button>

          {/* People section */}
          {members.length > 0 && (
            <>
              <div className="border-t border-border-default my-1" />
              <p className="px-3 py-1 text-[10px] font-semibold text-text-tertiary uppercase tracking-wider">
                People
              </p>
              {members.map((member) => {
                const isSelected = assignee?.id === member.userId;
                return (
                  <button
                    key={member.id}
                    type="button"
                    onClick={() => { onSelect({ type: "human", userId: member.userId }); setOpen(false); }}
                    className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-primary hover:bg-bg-surface-hover transition-colors"
                  >
                    <div className="h-6 w-6 rounded-full bg-accent-muted flex items-center justify-center text-xs font-medium text-accent-muted-text flex-shrink-0">
                      {initials(member)}
                    </div>
                    <span className="truncate">{displayName(member)}</span>
                    {isSelected && <span className="ml-auto text-accent text-xs flex-shrink-0">✓</span>}
                  </button>
                );
              })}
            </>
          )}

          {/* AI Agents section */}
          {(availableAgents.length > 0 || assignedAgent) && (
            <>
              <div className="border-t border-border-default my-1" />
              <p className="px-3 py-1 text-[10px] font-semibold text-text-tertiary uppercase tracking-wider">
                AI Agents
              </p>
              {assignedAgent && (
                <button
                  type="button"
                  onClick={() => { onSelect({ type: "agent", agentId: assignedAgent.agentDefinitionId }); setOpen(false); }}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-primary hover:bg-bg-surface-hover transition-colors"
                >
                  <AgentAvatar title={assignedAgent.agentTitle} />
                  <div className="min-w-0 flex-1">
                    <span className="truncate block">{assignedAgent.agentTitle}</span>
                  </div>
                  <span className="ml-auto text-accent text-xs flex-shrink-0">✓</span>
                </button>
              )}
              {availableAgents.map((agent) => (
                <button
                  key={agent.id}
                  type="button"
                  onClick={() => { onSelect({ type: "agent", agentId: agent.id }); setOpen(false); }}
                  className="w-full flex items-center gap-2 px-3 py-2 text-sm text-text-primary hover:bg-bg-surface-hover transition-colors"
                >
                  <AgentAvatar title={agent.title} />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-text-primary truncate">{agent.title}</p>
                    <p className="text-[10px] text-text-tertiary font-mono">{agent.name}</p>
                  </div>
                </button>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  );
}
