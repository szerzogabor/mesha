"use client";

import { useState } from "react";
import Link from "next/link";
import { BlocksActivityFeed } from "./BlocksActivityFeed";
import { useActiveBlocksSession, useAssignToBlocks, useCancelBlocksSession } from "@/hooks/useBlocksSessions";
import { useBlocksConfig } from "@/hooks/useBlocksConfig";
import { RuleViolationDialog } from "@/components/ui/RuleViolationDialog";
import { extractApiErrorMessage, isRuleViolationError } from "@/lib/error-utils";

interface Props {
  workspaceId: string;
  projectId: string;
  issueId: string;
  hasActiveSession: boolean;
}

export function AssignToBlocksPanel({ workspaceId, projectId, issueId, hasActiveSession }: Props) {
  const { data: activeSession, isLoading } = useActiveBlocksSession(projectId, issueId, hasActiveSession);
  const { data: blocksConfig, isLoading: configLoading } = useBlocksConfig(workspaceId);
  const assignMutation = useAssignToBlocks(projectId, issueId);
  const cancelMutation = useCancelBlocksSession(projectId, issueId);
  const [ruleViolation, setRuleViolation] = useState<string | null>(null);

  const isConnected = !!blocksConfig;

  if (isLoading && hasActiveSession) {
    return (
      <div className="bg-bg-surface rounded-xl border border-border-default p-4">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide mb-3">
          Blocks Activity
        </p>
        <div className="h-4 bg-border-default rounded animate-pulse w-2/3" />
      </div>
    );
  }

  if (activeSession) {
    return (
      <div className="bg-bg-surface rounded-xl border border-border-default p-4">
        <BlocksActivityFeed
          session={activeSession}
          projectId={projectId}
          issueId={issueId}
          onCancel={() => cancelMutation.mutate(activeSession.id)}
          cancelPending={cancelMutation.isPending}
        />
      </div>
    );
  }

  if (configLoading) {
    return null;
  }

  if (!isConnected) {
    return (
      <div className="bg-bg-surface rounded-xl border border-border-default p-4 space-y-2">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          Blocks AI
        </p>
        <p className="text-xs text-text-tertiary">
          Blocks is not connected for this workspace.{" "}
          <Link
            href={`/workspaces/${workspaceId}/blocks`}
            className="text-accent hover:underline"
          >
            Set it up in Integrations.
          </Link>
        </p>
      </div>
    );
  }

  return (
    <div className="bg-bg-surface rounded-xl border border-border-default p-4 space-y-3">
      <div>
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          Blocks AI
        </p>
        <p className="text-xs text-text-tertiary mt-1">
          Delegate implementation to Blocks AI. It will plan, write code, and open a pull request for review.
        </p>
      </div>
      <button
        onClick={() => {
          assignMutation.mutate(undefined, {
            onError: (err) => {
              if (isRuleViolationError(err)) {
                setRuleViolation(extractApiErrorMessage(err));
              }
            },
          });
        }}
        disabled={assignMutation.isPending}
        className="w-full flex items-center justify-center gap-2 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-white hover:bg-accent-hover transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {assignMutation.isPending ? (
          <>
            <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            Assigning…
          </>
        ) : (
          <>
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            Assign to Blocks AI
          </>
        )}
      </button>
      {assignMutation.isError && !isRuleViolationError(assignMutation.error) && (
        <p className="text-xs text-red-500">
          {(assignMutation.error as Error)?.message ?? "Failed to assign to Blocks"}
        </p>
      )}
      <RuleViolationDialog message={ruleViolation} onClose={() => setRuleViolation(null)} />
    </div>
  );
}
