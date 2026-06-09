"use client";

import { useBlocksSessions } from "@/hooks/useBlocksSessions";
import { LinkedPullRequest } from "@/types";

interface Props {
  projectId: string;
  issueId: string;
}

interface ResourceEntry {
  key: string;
  url?: string;
  pr?: LinkedPullRequest;
  branchName?: string;
}

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

function CIIcon({ status }: { status: string }) {
  if (status === "success") {
    return (
      <svg className="h-3 w-3 text-green-600" viewBox="0 0 20 20" fill="currentColor">
        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
      </svg>
    );
  }
  if (status === "failure") {
    return (
      <svg className="h-3 w-3 text-red-600" viewBox="0 0 20 20" fill="currentColor">
        <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
      </svg>
    );
  }
  return (
    <svg className="h-3 w-3 text-yellow-500 animate-spin" viewBox="0 0 24 24" fill="none">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  );
}

export function ResourcesPanel({ projectId, issueId }: Props) {
  const { data: sessions = [], isLoading } = useBlocksSessions(projectId, issueId);

  const seenUrls = new Set<string>();
  const seenBranches = new Set<string>();
  const entries: ResourceEntry[] = [];

  for (const s of sessions) {
    const url = s.linkedPullRequest?.htmlUrl ?? s.prUrl;
    if (url) {
      if (seenUrls.has(url)) continue;
      seenUrls.add(url);
      const pr: LinkedPullRequest = s.linkedPullRequest ?? {
        id: s.id,
        htmlUrl: url,
        githubPrNumber: s.prNumber ?? undefined,
        sourceBranch: s.branchName ?? undefined,
      };
      if (pr.sourceBranch) seenBranches.add(pr.sourceBranch);
      entries.push({ key: url, url, pr });
    } else if (s.branchName && !seenBranches.has(s.branchName)) {
      seenBranches.add(s.branchName);
      entries.push({ key: s.branchName, branchName: s.branchName });
    }
  }

  if (isLoading || entries.length === 0) return null;

  return (
    <div className="bg-bg-surface rounded-xl border border-border-default p-4 space-y-3">
      <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
        Resources
      </p>
      <ul className="space-y-2">
        {entries.map((entry) => {
          if (entry.url && entry.pr) {
            const pr = entry.pr;
            return (
              <li key={entry.key}>
                <a
                  href={entry.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex items-start gap-2.5 rounded-lg border border-border-default bg-bg-surface px-3 py-2.5 text-xs hover:bg-bg-surface-hover transition-colors group"
                >
                  <span
                    className={`mt-0.5 shrink-0 inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium ${prStateBadgeClass(pr)}`}
                  >
                    {prStateLabel(pr)}
                  </span>
                  <span className="flex-1 min-w-0">
                    <span className="block font-medium text-text-primary truncate">
                      {pr.title
                        ? pr.title
                        : pr.sourceBranch
                          ? pr.sourceBranch
                          : `Pull Request${pr.githubPrNumber ? ` #${pr.githubPrNumber}` : ""}`}
                    </span>
                    <span className="flex items-center gap-2 mt-0.5 text-text-tertiary">
                      {pr.githubPrNumber && <span>#{pr.githubPrNumber}</span>}
                      {pr.sourceBranch && (
                        <span className="truncate max-w-[120px]">{pr.sourceBranch}</span>
                      )}
                      {pr.checksStatus && (
                        <span className="flex items-center gap-0.5">
                          <CIIcon status={pr.checksStatus} />
                          <span>{pr.checksStatus}</span>
                        </span>
                      )}
                    </span>
                  </span>
                  <svg
                    className="h-3 w-3 text-text-tertiary shrink-0 mt-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                  </svg>
                </a>
              </li>
            );
          }

          // Branch-only entry (PR exists but URL not yet synced)
          return (
            <li key={entry.key}>
              <div className="flex items-start gap-2.5 rounded-lg border border-border-default bg-bg-surface px-3 py-2.5 text-xs">
                <span className="mt-0.5 shrink-0 inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                  Branch
                </span>
                <span className="flex-1 min-w-0">
                  <span className="block font-medium text-text-primary truncate">
                    {entry.branchName}
                  </span>
                  <span className="text-text-tertiary">PR not yet synced</span>
                </span>
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
