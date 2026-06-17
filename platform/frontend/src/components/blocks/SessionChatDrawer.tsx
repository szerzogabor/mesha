"use client";

import { useEffect, useRef, useState } from "react";
import { AIExecutionState, BlocksMessage, BlocksSession, LinkedPullRequest } from "@/types";
import { useBlocksMessages } from "@/hooks/useBlocksMessages";
import { useSendBlocksMessage } from "@/hooks/useBlocksSessions";
import { formatRelativeTime } from "@/lib/utils";

const TERMINAL_STATES: AIExecutionState[] = ["DONE", "FAILED", "CANCELED"];

function prStateLabel(pr: LinkedPullRequest): string {
  if (pr.state === "open") return pr.draft ? "Draft" : "Open";
  if (pr.mergedAt) return "Merged";
  if (pr.state === "closed") return "Closed";
  return "PR";
}

function prStateBadgeClass(pr: LinkedPullRequest): string {
  if (pr.state === "open") {
    if (pr.draft) return "bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300";
    return "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400";
  }
  if (pr.mergedAt) return "bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400";
  if (pr.state === "closed") return "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400";
  return "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400";
}

function stateBadgeClass(state: AIExecutionState): string {
  if (state === "DONE") return "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400";
  if (state === "FAILED") return "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400";
  if (state === "CANCELED") return "bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400";
  return "bg-accent/10 text-accent";
}

function stateLabel(state: AIExecutionState): string {
  switch (state) {
    case "CREATED": return "Starting";
    case "PLANNING": return "Planning";
    case "EXECUTING": return "Coding";
    case "WAITING_REVIEW": return "Awaiting review";
    case "PR_OPENED": return "PR opened";
    case "DONE": return "Done";
    case "FAILED": return "Failed";
    case "CANCELED": return "Canceled";
  }
}

const URL_PATTERN = /(https?:\/\/[^\s<>"]+)/g;

function renderWithLinks(text: string, linkClass: string) {
  const parts = text.split(URL_PATTERN);
  return parts.map((part, i) =>
    /^https?:\/\//.test(part) ? (
      <a
        key={i}
        href={part}
        target="_blank"
        rel="noopener noreferrer"
        className={linkClass}
        onClick={(e) => e.stopPropagation()}
      >
        {part}
      </a>
    ) : (
      part
    )
  );
}

function MessageBubble({ msg }: { msg: BlocksMessage }) {
  const isUser = msg.role === "USER";
  const isSystem = msg.role === "SYSTEM";

  if (isSystem) {
    return (
      <div className="flex justify-center py-1">
        <span className="text-xs text-text-tertiary italic">
          {renderWithLinks(msg.message, "underline opacity-80 hover:opacity-100 break-all")}
        </span>
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
        <p className="leading-relaxed whitespace-pre-wrap">
          {renderWithLinks(
            msg.message,
            isUser
              ? "underline opacity-80 hover:opacity-100 break-all"
              : "text-accent underline hover:opacity-80 break-all"
          )}
        </p>
        <p className={`mt-1 text-[10px] opacity-60 ${isUser ? "text-right" : ""}`}>
          {new Date(msg.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false })}
        </p>
      </div>
    </div>
  );
}

interface Props {
  session: BlocksSession;
  sessionIndex: number;
  projectId: string;
  issueId: string;
  onClose: () => void;
}

export function SessionChatDrawer({ session, sessionIndex, projectId, issueId, onClose }: Props) {
  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const isTerminal = TERMINAL_STATES.includes(session.executionState);

  const { data: messages = [], isLoading } = useBlocksMessages(
    projectId,
    issueId,
    session.id,
    session.executionState
  );
  const sendMessage = useSendBlocksMessage(projectId, issueId);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => { document.body.style.overflow = prev; };
  }, []);

  const handleSend = async () => {
    const content = input.trim();
    if (!content || sendMessage.isPending) return;
    setInput("");
    await sendMessage.mutateAsync({ sessionId: session.id, content });
  };

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/30 z-40 touch-none"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Drawer */}
      <div className="fixed right-0 top-0 h-full w-full max-w-md z-50 flex flex-col bg-bg-surface border-l border-border-default shadow-2xl animate-slide-in-right">
        {/* Header */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-border-default flex-shrink-0">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-text-primary">
                AI Session #{sessionIndex}
              </span>
              <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${stateBadgeClass(session.executionState)}`}>
                {!isTerminal && <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse" />}
                {stateLabel(session.executionState)}
              </span>
            </div>
            <p className="text-xs text-text-tertiary mt-0.5">
              Started {formatRelativeTime(session.createdAt)}
            </p>
          </div>
          <button
            onClick={onClose}
            className="flex-shrink-0 rounded-lg p-1.5 text-text-tertiary hover:bg-bg-surface-hover hover:text-text-primary transition-colors"
            aria-label="Close"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Links bar */}
        {(session.sessionUrl || session.linkedPullRequests?.length || session.prUrl) && (
          <div className="flex flex-col gap-2 px-4 py-2 border-b border-border-default flex-shrink-0">
            {session.sessionUrl && (
              <a
                href={session.sessionUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 rounded-lg border border-border-default px-2.5 py-1 text-xs text-text-secondary hover:bg-bg-surface-hover transition-colors self-start"
              >
                <svg className="h-3 w-3 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                Blocks Dashboard
                <svg className="h-2.5 w-2.5 opacity-50" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                </svg>
              </a>
            )}
            {(session.linkedPullRequests?.length ? session.linkedPullRequests : session.prUrl ? [null] : []).map((pr, idx) => {
              const prUrl = pr?.htmlUrl ?? session.prUrl!;
              return (
                <a
                  key={pr?.id ?? prUrl ?? idx}
                  href={prUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-start gap-2.5 rounded-lg border border-border-default bg-bg-surface px-3 py-2.5 text-xs hover:bg-bg-surface-hover transition-colors group"
                >
                  {pr ? (
                    <>
                      <span className={`mt-0.5 shrink-0 inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium ${prStateBadgeClass(pr)}`}>
                        {prStateLabel(pr)}
                      </span>
                      <span className="flex-1 min-w-0">
                        <span className="block font-medium text-text-primary truncate">
                          {pr.title ?? pr.sourceBranch ?? `Pull Request${pr.githubPrNumber ? ` #${pr.githubPrNumber}` : ""}`}
                        </span>
                        <span className="flex items-center gap-2 mt-0.5 text-text-tertiary">
                          {pr.githubPrNumber && <span>#{pr.githubPrNumber}</span>}
                          {pr.sourceBranch && (
                            <span className="truncate max-w-[150px]">{pr.sourceBranch}</span>
                          )}
                        </span>
                      </span>
                    </>
                  ) : (
                    <>
                      <svg className="h-3.5 w-3.5 text-accent flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                      </svg>
                      <span className="flex-1 text-text-primary truncate">
                        {session.prNumber ? `PR #${session.prNumber}` : "Pull Request"}
                      </span>
                    </>
                  )}
                  <svg
                    className="h-3 w-3 text-text-tertiary shrink-0 mt-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
                    fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                  </svg>
                </a>
              );
            })}
          </div>
        )}

        {/* Messages */}
        <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
          {isLoading && (
            <div className="space-y-2 pt-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-3 bg-border-default rounded animate-pulse" style={{ width: `${60 + i * 10}%` }} />
              ))}
            </div>
          )}
          {!isLoading && messages.length === 0 && (
            <p className="text-xs text-text-tertiary text-center py-8">No messages yet.</p>
          )}
          {!isLoading && messages.map((msg) => (
            <MessageBubble key={msg.id} msg={msg} />
          ))}
          {session.errorMessage && isTerminal && (
            <div className={`rounded-lg px-3 py-2 text-xs ${
              session.executionState === "FAILED"
                ? "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400"
                : "bg-bg-surface-hover text-text-tertiary"
            }`}>
              {session.errorMessage}
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input */}
        {!isTerminal && (
          <div className="border-t border-border-default px-4 py-3 flex-shrink-0">
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
                className="flex-1 resize-none rounded-lg border border-input-border bg-input-bg px-3 py-2 text-sm text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-2 focus:ring-accent"
              />
              <button
                onClick={handleSend}
                disabled={!input.trim() || sendMessage.isPending}
                className="flex-shrink-0 rounded-lg bg-accent px-3 py-2 text-sm font-medium text-white hover:bg-accent-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {sendMessage.isPending ? "…" : "Send"}
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
