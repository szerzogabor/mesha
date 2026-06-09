"use client";

import { useState } from "react";
import Link from "next/link";
import { BlocksSession, BlocksMessage, AIExecutionState } from "@/types";
import { useBlocksSessions, useAssignToBlocks, useCancelBlocksSession, useSendBlocksMessage } from "@/hooks/useBlocksSessions";
import { useBlocksMessages } from "@/hooks/useBlocksMessages";
import { useBlocksConfig } from "@/hooks/useBlocksConfig";

const TERMINAL_STATES: AIExecutionState[] = ["DONE", "FAILED", "CANCELED"];

function isTerminal(state: AIExecutionState) {
  return TERMINAL_STATES.includes(state);
}

function SessionStatusBadge({ state }: { state: AIExecutionState }) {
  if (state === "DONE") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">
        <svg className="h-2.5 w-2.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
        </svg>
        Done
      </span>
    );
  }
  if (state === "FAILED") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400">
        <svg className="h-2.5 w-2.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
        Failed
      </span>
    );
  }
  if (state === "CANCELED") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400">
        Canceled
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-accent/10 text-accent">
      <span className="h-1.5 w-1.5 rounded-full bg-accent animate-pulse" />
      {state === "CREATED" && "Starting"}
      {state === "PLANNING" && "Planning"}
      {state === "EXECUTING" && "Coding"}
      {state === "WAITING_REVIEW" && "Awaiting review"}
      {state === "PR_OPENED" && "PR opened"}
    </span>
  );
}

function MessageBubble({ msg }: { msg: BlocksMessage }) {
  const isUser = msg.role === "USER";
  const isSystem = msg.role === "SYSTEM";

  if (isSystem) {
    return (
      <div className="flex justify-center py-0.5">
        <span className="text-xs text-text-tertiary italic">{msg.message}</span>
      </div>
    );
  }

  return (
    <div className={`flex gap-2 ${isUser ? "flex-row-reverse" : "flex-row"}`}>
      <div className={`max-w-[85%] rounded-xl px-3 py-2 text-xs ${
        isUser
          ? "bg-accent text-white rounded-tr-sm"
          : "bg-bg-surface-hover text-text-secondary rounded-tl-sm"
      }`}>
        <p className="leading-relaxed">{msg.message}</p>
        <p className={`mt-1 text-[10px] opacity-60 ${isUser ? "text-right" : ""}`}>
          {new Date(msg.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false })}
        </p>
      </div>
    </div>
  );
}

function SessionConversation({
  session,
  projectId,
  issueId,
  onCancel,
  cancelPending,
}: {
  session: BlocksSession;
  projectId: string;
  issueId: string;
  onCancel: () => void;
  cancelPending: boolean;
}) {
  const [input, setInput] = useState("");
  const sendMessage = useSendBlocksMessage(projectId, issueId);
  const { executionState } = session;
  const terminal = isTerminal(executionState);

  const { data: messages = [], isLoading } = useBlocksMessages(
    projectId,
    issueId,
    session.id,
    executionState
  );

  const handleSend = async () => {
    const content = input.trim();
    if (!content) return;
    setInput("");
    await sendMessage.mutateAsync({ sessionId: session.id, content });
  };

  return (
    <div className="space-y-3 pt-2">
      {/* Messages */}
      {isLoading ? (
        <div className="space-y-2">
          {[1, 2].map((i) => (
            <div key={i} className="h-3 bg-border-default rounded animate-pulse w-3/4" />
          ))}
        </div>
      ) : (
        <div className="max-h-56 overflow-y-auto space-y-2 pr-1">
          {messages.map((msg) => (
            <MessageBubble key={msg.id} msg={msg} />
          ))}
          {messages.length === 0 && (
            <p className="text-xs text-text-tertiary text-center py-2">No messages yet.</p>
          )}
        </div>
      )}

      {/* PR / Branch links */}
      {session.prUrl && (
        <a
          href={session.prUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-surface px-3 py-2 text-xs text-text-primary hover:bg-bg-surface-hover transition-colors"
        >
          <svg className="h-3.5 w-3.5 text-accent flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
          </svg>
          <span className="flex-1 truncate">
            {session.branchName ?? "View Pull Request"}
            {session.prNumber && ` #${session.prNumber}`}
          </span>
          <svg className="h-3 w-3 text-text-tertiary flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
          </svg>
        </a>
      )}

      {session.sessionUrl && (
        <a
          href={session.sessionUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-surface px-3 py-2 text-xs text-text-primary hover:bg-bg-surface-hover transition-colors"
        >
          <svg className="h-3.5 w-3.5 text-accent flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
          <span className="flex-1 truncate">View in Blocks Dashboard</span>
        </a>
      )}

      {session.errorMessage && (executionState === "FAILED" || executionState === "CANCELED") && (
        <div className={`rounded-lg px-3 py-2 text-xs ${
          executionState === "FAILED"
            ? "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400"
            : "bg-bg-surface text-text-tertiary"
        }`}>
          {session.errorMessage}
        </div>
      )}

      {/* Follow-up message input for non-terminal sessions */}
      {!terminal && (
        <div className="flex gap-2 items-end">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Send a follow-up instruction…"
            rows={2}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            className="flex-1 resize-none rounded-lg border border-input-border bg-input-bg px-3 py-2 text-xs text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-2 focus:ring-accent"
          />
          <button
            onClick={handleSend}
            disabled={!input.trim() || sendMessage.isPending}
            className="flex-shrink-0 rounded-lg bg-accent px-3 py-2 text-xs font-medium text-white hover:bg-accent-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {sendMessage.isPending ? "…" : "Send"}
          </button>
        </div>
      )}

      {/* Cancel */}
      {!terminal && (
        <div className="flex justify-end">
          <button
            onClick={onCancel}
            disabled={cancelPending}
            className="text-xs text-text-tertiary hover:text-destructive transition-colors disabled:opacity-50"
          >
            {cancelPending ? "Canceling…" : "Cancel session"}
          </button>
        </div>
      )}
    </div>
  );
}

function SessionRow({
  session,
  index,
  projectId,
  issueId,
}: {
  session: BlocksSession;
  index: number;
  projectId: string;
  issueId: string;
}) {
  const [expanded, setExpanded] = useState(!isTerminal(session.executionState));
  const cancelMutation = useCancelBlocksSession(projectId, issueId);

  return (
    <div className="rounded-lg border border-border-default overflow-hidden">
      {/* Header row */}
      <button
        className="w-full flex items-center gap-2 px-3 py-2.5 bg-bg-surface hover:bg-bg-surface-hover transition-colors text-left"
        onClick={() => setExpanded((v) => !v)}
      >
        <span className="text-xs text-text-tertiary font-mono flex-shrink-0">#{index}</span>
        <span className="flex-1 text-xs text-text-secondary font-medium truncate">
          {session.provider === "blocks" ? "Blocks AI" : session.provider}
          {session.branchName && (
            <span className="ml-1.5 font-mono text-text-tertiary">{session.branchName}</span>
          )}
        </span>
        <SessionStatusBadge state={session.executionState} />
        <svg
          className={`h-3.5 w-3.5 text-text-tertiary transition-transform flex-shrink-0 ${expanded ? "rotate-180" : ""}`}
          fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* Expanded detail */}
      {expanded && (
        <div className="border-t border-border-default px-3 pb-3">
          <SessionConversation
            session={session}
            projectId={projectId}
            issueId={issueId}
            onCancel={() => cancelMutation.mutate(session.id)}
            cancelPending={cancelMutation.isPending}
          />
        </div>
      )}
    </div>
  );
}

interface Props {
  workspaceId: string;
  projectId: string;
  issueId: string;
}

export function AISessionsPanel({ workspaceId, projectId, issueId }: Props) {
  const { data: sessions = [], isLoading } = useBlocksSessions(projectId, issueId);
  const { data: blocksConfig, isLoading: configLoading } = useBlocksConfig(workspaceId);
  const assignMutation = useAssignToBlocks(projectId, issueId);
  const [showInstructions, setShowInstructions] = useState(false);
  const [instructions, setInstructions] = useState("");

  const isConnected = !!blocksConfig;

  const activeSessions = sessions.filter((s) => !isTerminal(s.executionState));
  const pastSessions = sessions.filter((s) => isTerminal(s.executionState));

  const handleStartSession = () => {
    assignMutation.mutate(instructions || undefined, {
      onSuccess: () => {
        setShowInstructions(false);
        setInstructions("");
      },
    });
  };

  return (
    <div className="bg-bg-surface rounded-xl border border-border-default p-4 space-y-3">
      {/* Header */}
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          AI Sessions
        </p>
        {sessions.length > 0 && (
          <span className="text-xs text-text-tertiary">
            {sessions.length} session{sessions.length !== 1 ? "s" : ""}
          </span>
        )}
      </div>

      {/* Not connected */}
      {!configLoading && !isConnected && (
        <p className="text-xs text-text-tertiary">
          Blocks is not connected.{" "}
          <Link href={`/workspaces/${workspaceId}/blocks`} className="text-accent hover:underline">
            Set it up in Integrations.
          </Link>
        </p>
      )}

      {/* Loading sessions */}
      {isLoading && (
        <div className="space-y-2">
          {[1, 2].map((i) => (
            <div key={i} className="h-8 bg-border-default rounded-lg animate-pulse" />
          ))}
        </div>
      )}

      {/* Active sessions */}
      {!isLoading && activeSessions.length > 0 && (
        <div className="space-y-2">
          {activeSessions.map((session) => (
            <SessionRow
              key={session.id}
              session={session}
              index={sessions.length - sessions.indexOf(session)}
              projectId={projectId}
              issueId={issueId}
            />
          ))}
        </div>
      )}

      {/* Past sessions */}
      {!isLoading && pastSessions.length > 0 && (
        <div className="space-y-2">
          {pastSessions.length > 0 && activeSessions.length > 0 && (
            <p className="text-xs text-text-tertiary font-medium">Past sessions</p>
          )}
          {pastSessions.map((session) => (
            <SessionRow
              key={session.id}
              session={session}
              index={sessions.length - sessions.indexOf(session)}
              projectId={projectId}
              issueId={issueId}
            />
          ))}
        </div>
      )}

      {/* Empty state */}
      {!isLoading && sessions.length === 0 && isConnected && (
        <p className="text-xs text-text-tertiary">
          No sessions yet. Start one to delegate implementation to an AI agent.
        </p>
      )}

      {/* Start new session */}
      {isConnected && (
        <div className="space-y-2">
          {showInstructions ? (
            <div className="space-y-2 rounded-lg border border-border-default bg-bg-surface p-3">
              <label className="block text-xs font-medium text-text-secondary">
                Additional instructions
                <span className="ml-1 font-normal text-text-tertiary">(optional)</span>
              </label>
              <textarea
                value={instructions}
                onChange={(e) => setInstructions(e.target.value)}
                placeholder="e.g. Use TypeScript strict mode, avoid modifying auth files, name the branch feature/my-feature…"
                rows={3}
                autoFocus
                className="w-full resize-none rounded-lg border border-input-border bg-input-bg px-3 py-2 text-xs text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-2 focus:ring-accent"
              />
              <div className="flex gap-2">
                <button
                  onClick={handleStartSession}
                  disabled={assignMutation.isPending}
                  className="flex-1 flex items-center justify-center gap-2 rounded-lg bg-accent px-3 py-2 text-xs font-medium text-white hover:bg-accent-hover transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
                >
                  {assignMutation.isPending ? (
                    <>
                      <svg className="h-3.5 w-3.5 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      Starting…
                    </>
                  ) : (
                    <>
                      <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                      Start Session
                    </>
                  )}
                </button>
                <button
                  onClick={() => { setShowInstructions(false); setInstructions(""); }}
                  disabled={assignMutation.isPending}
                  className="rounded-lg border border-border-default px-3 py-2 text-xs text-text-secondary hover:bg-bg-surface-hover transition-colors disabled:opacity-50"
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <button
              onClick={() => setShowInstructions(true)}
              disabled={assignMutation.isPending}
              className="w-full flex items-center justify-center gap-2 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-white hover:bg-accent-hover transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              {sessions.length === 0 ? "Start AI Session" : "Start New Session"}
            </button>
          )}
        </div>
      )}

      {assignMutation.isError && (
        <p className="text-xs text-red-500">
          {(assignMutation.error as Error)?.message ?? "Failed to start session"}
        </p>
      )}
    </div>
  );
}
