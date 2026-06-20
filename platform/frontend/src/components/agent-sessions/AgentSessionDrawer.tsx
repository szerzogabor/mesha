"use client";

import { useEffect, useRef, useState } from "react";
import { ConnectorAgentSession, ConnectorAgentSessionMessage, ConnectorAgentSessionStatus } from "@/types";
import { useAgentSessionMessages, useCancelAgentSession, useSendAgentSessionMessage } from "@/hooks/useAgentSessions";
import { formatRelativeTime } from "@/lib/utils";

const TERMINAL_STATES: ConnectorAgentSessionStatus[] = ["COMPLETED", "FAILED", "CANCELLED"];

function stateBadgeClass(status: ConnectorAgentSessionStatus): string {
  if (status === "COMPLETED") return "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400";
  if (status === "FAILED") return "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400";
  if (status === "CANCELLED") return "bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400";
  if (status === "WAITING_FOR_USER") return "bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400";
  return "bg-accent/10 text-accent";
}

function stateLabel(status: ConnectorAgentSessionStatus): string {
  switch (status) {
    case "CREATED": return "Created";
    case "QUEUED": return "Queued";
    case "CLAIMED": return "Claimed";
    case "PREPARING": return "Preparing";
    case "RUNNING": return "Running";
    case "WAITING_FOR_USER": return "Waiting for you";
    case "COMPLETED": return "Completed";
    case "FAILED": return "Failed";
    case "CANCELLED": return "Cancelled";
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

function MessageBubble({ msg }: { msg: ConnectorAgentSessionMessage }) {
  const isUser = msg.role === "USER";

  return (
    <div className={`flex gap-2 ${isUser ? "flex-row-reverse" : "flex-row"}`}>
      <div className={`max-w-[85%] rounded-xl px-3 py-2 text-xs ${
        isUser
          ? "bg-accent text-white rounded-tr-sm"
          : "bg-bg-surface-hover text-text-secondary rounded-tl-sm"
      }`}>
        <p className="leading-relaxed whitespace-pre-wrap">
          {renderWithLinks(
            msg.content,
            isUser
              ? "underline opacity-80 hover:opacity-100 break-all"
              : "text-accent underline hover:opacity-80 break-all"
          )}
        </p>
        <p suppressHydrationWarning className={`mt-1 text-[10px] opacity-60 ${isUser ? "text-right" : ""}`}>
          {new Date(msg.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false })}
        </p>
      </div>
    </div>
  );
}

interface Props {
  session: ConnectorAgentSession;
  onClose: () => void;
}

export function AgentSessionDrawer({ session, onClose }: Props) {
  const [input, setInput] = useState("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const isTerminal = TERMINAL_STATES.includes(session.status);

  const { data: messages = [], isLoading } = useAgentSessionMessages(session.id, session.status);
  const sendMessage = useSendAgentSessionMessage();
  const cancelSession = useCancelAgentSession();

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
    try {
      await sendMessage.mutateAsync({ sessionId: session.id, content });
    } catch {
      setInput(content);
    }
  };

  const canCancel = !isTerminal && session.status !== "CREATED";

  return (
    <>
      <div
        className="fixed inset-0 bg-black/30 z-40 touch-none"
        onClick={onClose}
        aria-hidden="true"
      />

      <div className="fixed right-0 top-0 h-full w-full max-w-md z-50 flex flex-col bg-bg-surface border-l border-border-default shadow-2xl animate-slide-in-right">
        {/* Header */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-border-default flex-shrink-0">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-text-primary truncate">
                {session.issueIdentifier ?? "Agent Session"}
              </span>
              <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${stateBadgeClass(session.status)}`}>
                {!isTerminal && <span className="h-1.5 w-1.5 rounded-full bg-current animate-pulse" />}
                {stateLabel(session.status)}
              </span>
            </div>
            {session.issueTitle && (
              <p className="text-xs text-text-secondary mt-0.5 truncate">{session.issueTitle}</p>
            )}
            <p className="text-xs text-text-tertiary mt-0.5">
              Created {formatRelativeTime(session.createdAt)}
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

        {/* PR link bar */}
        {session.prUrl && (
          <div className="flex flex-col gap-2 px-4 py-2 border-b border-border-default flex-shrink-0">
            <a
              href={session.prUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-start gap-2.5 rounded-lg border border-border-default bg-bg-surface px-3 py-2.5 text-xs hover:bg-bg-surface-hover transition-colors group"
            >
              <svg className="h-3.5 w-3.5 text-accent flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
              </svg>
              <span className="flex-1 min-w-0">
                <span className="block font-medium text-text-primary truncate">
                  {session.prTitle ?? `Pull Request${session.prNumber ? ` #${session.prNumber}` : ""}`}
                </span>
                {session.prNumber && (
                  <span className="text-text-tertiary">#{session.prNumber}</span>
                )}
              </span>
              <svg
                className="h-3 w-3 text-text-tertiary shrink-0 mt-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
                fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
              </svg>
            </a>
          </div>
        )}

        {/* Execution details */}
        {(session.branchName || session.workspacePath) && (
          <div className="px-4 py-2 border-b border-border-default flex-shrink-0 space-y-1">
            {session.branchName && (
              <p className="text-xs text-text-tertiary">
                Branch: <span className="font-mono text-text-secondary">{session.branchName}</span>
              </p>
            )}
            {session.workspacePath && (
              <p className="text-xs text-text-tertiary truncate">
                Workspace: <span className="font-mono text-text-secondary">{session.workspacePath}</span>
              </p>
            )}
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
              session.status === "FAILED"
                ? "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400"
                : "bg-bg-surface-hover text-text-tertiary"
            }`}>
              {session.errorMessage}
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Footer: cancel + input */}
        {!isTerminal && (
          <div className="border-t border-border-default px-4 py-3 flex-shrink-0 space-y-2">
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
            {canCancel && (
              <button
                onClick={() => cancelSession.mutate(session.id)}
                disabled={cancelSession.isPending}
                className="text-xs text-red-600 dark:text-red-400 hover:underline disabled:opacity-50"
              >
                {cancelSession.isPending ? "Cancelling…" : "Cancel session"}
              </button>
            )}
          </div>
        )}
      </div>
    </>
  );
}
