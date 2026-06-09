"use client";

import { useBlocksSessions } from "@/hooks/useBlocksSessions";

interface Props {
  projectId: string;
  issueId: string;
}

export function ResourcesPanel({ projectId, issueId }: Props) {
  const { data: sessions = [], isLoading } = useBlocksSessions(projectId, issueId);

  const prLinks = sessions
    .map((s) => {
      const url = s.prUrl ?? s.linkedPullRequest?.htmlUrl;
      if (!url) return null;
      const prNumber = s.prNumber ?? s.linkedPullRequest?.githubPrNumber;
      const branchName = s.branchName ?? s.linkedPullRequest?.sourceBranch;
      return { url, prNumber, branchName };
    })
    .filter((x): x is NonNullable<typeof x> => x !== null)
    .filter((x, i, arr) => arr.findIndex((y) => y.url === x.url) === i);

  if (isLoading || prLinks.length === 0) return null;

  return (
    <div className="bg-bg-surface rounded-xl border border-border-default p-4 space-y-3">
      <p className="text-xs font-semibold text-text-tertiary uppercase tracking-wide">
        Resources
      </p>
      <ul className="space-y-2">
        {prLinks.map((pr) => (
          <li key={pr.url}>
            <a
              href={pr.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-surface px-3 py-2 text-xs text-text-primary hover:bg-bg-surface-hover transition-colors"
            >
              <svg className="h-3.5 w-3.5 text-accent flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
              </svg>
              <span className="flex-1 truncate font-medium">
                {pr.branchName
                  ? `${pr.branchName}${pr.prNumber ? ` #${pr.prNumber}` : ""}`
                  : pr.prNumber
                    ? `Pull Request #${pr.prNumber}`
                    : "Pull Request"}
              </span>
              <svg className="h-3 w-3 text-text-tertiary flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
              </svg>
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
