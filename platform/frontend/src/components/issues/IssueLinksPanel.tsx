"use client";

import { useState } from "react";
import Link from "next/link";
import { useIssueLinks, useCreateIssueLink, useDeleteIssueLink } from "@/hooks/useIssueLinks";
import { useAllIssues } from "@/hooks/useIssues";
import { IssueLink, IssueLinkType } from "@/types";

const LINK_TYPE_LABELS: Record<IssueLinkType, string> = {
  DEPENDS_ON: "depends on",
  BLOCKS: "blocks",
  DUPLICATE_OF: "duplicate of",
  PARENT_OF: "parent of",
  CHILD_OF: "child of",
};

const LINK_TYPE_OPTIONS: { value: IssueLinkType; label: string }[] = [
  { value: "DEPENDS_ON", label: "Depends on" },
  { value: "BLOCKS", label: "Blocks" },
  { value: "DUPLICATE_OF", label: "Duplicate of" },
  { value: "PARENT_OF", label: "Parent of" },
  { value: "CHILD_OF", label: "Child of (sub-ticket)" },
];

interface Props {
  issueId: string;
  projectId: string;
  workspaceId: string;
}

export function IssueLinksPanel({ issueId, projectId, workspaceId }: Props) {
  const { data: links = [], isLoading } = useIssueLinks(issueId);
  const createLink = useCreateIssueLink(issueId);
  const deleteLink = useDeleteIssueLink(issueId);

  const [showAdd, setShowAdd] = useState(false);
  const [search, setSearch] = useState("");
  const [selectedLinkType, setSelectedLinkType] = useState<IssueLinkType>("DEPENDS_ON");
  const [error, setError] = useState<string | null>(null);

  const { data: issueSearchResult } = useAllIssues(
    projectId,
    { search: search.trim() || undefined },
    { enabled: search.trim().length > 0 }
  );
  const searchResults = (issueSearchResult?.content ?? []).filter((i) => i.id !== issueId);

  async function handleAdd(targetIssueId: string) {
    setError(null);
    try {
      await createLink.mutateAsync({ targetIssueId, linkType: selectedLinkType });
      setShowAdd(false);
      setSearch("");
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Failed to create link";
      setError(msg.includes("409") || msg.includes("CONFLICT") ? "Link already exists" : msg);
    }
  }

  function labelFor(link: IssueLink) {
    const isSource = link.sourceIssue.id === issueId;
    return isSource
      ? LINK_TYPE_LABELS[link.linkType]
      : invertedLabel(link.linkType);
  }

  function linkedIssue(link: IssueLink) {
    return link.sourceIssue.id === issueId ? link.targetIssue : link.sourceIssue;
  }

  return (
    <div className="bg-bg-surface rounded-xl border border-border-default p-4 space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
          Linked Issues {links.length > 0 && `(${links.length})`}
        </p>
        <button
          onClick={() => { setShowAdd((v) => !v); setError(null); setSearch(""); }}
          className="text-xs text-text-tertiary hover:text-text-primary transition-colors"
        >
          {showAdd ? "Cancel" : "+ Add"}
        </button>
      </div>

      {isLoading && (
        <p className="text-xs text-text-tertiary">Loading…</p>
      )}

      {links.length > 0 && (
        <ul className="space-y-1.5">
          {links.map((link) => {
            const issue = linkedIssue(link);
            return (
              <li key={link.id} className="flex items-start gap-2 group">
                <div className="flex-1 min-w-0">
                  <span className="text-[10px] text-text-tertiary capitalize block">
                    {labelFor(link)}
                  </span>
                  {issue.projectId ? (
                    <Link
                      href={`/workspaces/${workspaceId}/projects/${issue.projectId}/issues/${issue.id}`}
                      className="text-xs text-text-primary truncate block hover:text-accent transition-colors"
                    >
                      {issue.identifier && (
                        <span className="font-mono text-text-tertiary mr-1">{issue.identifier}</span>
                      )}
                      {issue.title}
                    </Link>
                  ) : (
                    <span className="text-xs text-text-primary truncate block">
                      {issue.identifier && (
                        <span className="font-mono text-text-tertiary mr-1">{issue.identifier}</span>
                      )}
                      {issue.title}
                    </span>
                  )}
                </div>
                <button
                  onClick={() => deleteLink.mutate(link.id)}
                  disabled={deleteLink.isPending}
                  className="opacity-0 group-hover:opacity-100 text-text-tertiary hover:text-destructive transition-all text-xs shrink-0 mt-0.5"
                  title="Remove link"
                >
                  ×
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {!isLoading && links.length === 0 && !showAdd && (
        <p className="text-xs text-text-tertiary">No linked issues.</p>
      )}

      {showAdd && (
        <div className="space-y-2 pt-1 border-t border-border-default">
          <select
            value={selectedLinkType}
            onChange={(e) => setSelectedLinkType(e.target.value as IssueLinkType)}
            className="w-full border border-input-border rounded-lg px-2 py-1.5 text-xs bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent"
          >
            {LINK_TYPE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>

          <input
            type="text"
            placeholder="Search issues by title…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full border border-input-border rounded-lg px-2 py-1.5 text-xs bg-input-bg text-text-primary focus:outline-none focus:ring-2 focus:ring-accent"
          />

          {error && (
            <p className="text-xs text-destructive">{error}</p>
          )}

          {search.trim().length > 0 && searchResults.length === 0 && (
            <p className="text-xs text-text-tertiary">No issues found.</p>
          )}

          {searchResults.length > 0 && (
            <ul className="space-y-1 max-h-40 overflow-y-auto">
              {searchResults.map((issue) => (
                <li key={issue.id}>
                  <button
                    onClick={() => handleAdd(issue.id)}
                    disabled={createLink.isPending}
                    className="w-full text-left px-2 py-1.5 rounded-lg text-xs hover:bg-bg-surface-hover transition-colors disabled:opacity-50"
                  >
                    {issue.identifier && (
                      <span className="font-mono text-text-tertiary mr-1">{issue.identifier}</span>
                    )}
                    <span className="text-text-primary">{issue.title}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function invertedLabel(linkType: IssueLinkType): string {
  switch (linkType) {
    case "DEPENDS_ON": return "is depended on by";
    case "BLOCKS": return "is blocked by";
    case "DUPLICATE_OF": return "has duplicate";
    case "PARENT_OF": return "is sub-ticket of";
    case "CHILD_OF": return "is parent of";
  }
}
