"use client";

import { GitHubPullRequest } from "@/types";
import { cn } from "@/lib/utils";

interface PullRequestRowProps {
  pr: GitHubPullRequest;
}

export function PullRequestRow({ pr }: PullRequestRowProps) {
  const stateColor =
    pr.state === "open"
      ? pr.draft
        ? "bg-gray-200 text-gray-700 dark:bg-gray-700 dark:text-gray-300"
        : "bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400"
      : pr.mergedAt
      ? "bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400"
      : "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400";

  const stateLabel =
    pr.state === "open" ? (pr.draft ? "Draft" : "Open") : pr.mergedAt ? "Merged" : "Closed";

  return (
    <div className="flex items-start gap-3 py-3 border-b border-border-subtle last:border-0">
      <span
        className={cn(
          "mt-0.5 shrink-0 text-xs px-2 py-0.5 rounded-full font-medium",
          stateColor
        )}
      >
        {stateLabel}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <a
            href={pr.htmlUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="font-medium text-text-primary hover:underline"
          >
            {pr.title}
          </a>
          <span className="text-xs text-text-muted shrink-0">#{pr.githubPrNumber}</span>
        </div>
        <div className="flex items-center gap-3 text-xs text-text-muted mt-1">
          <span>
            {pr.sourceBranch} → {pr.targetBranch}
          </span>
          {pr.authorLogin && <span>by {pr.authorLogin}</span>}
          {pr.commitsCount > 0 && <span>{pr.commitsCount} commits</span>}
          {pr.checksStatus && (
            <span
              className={cn(
                "font-medium",
                pr.checksStatus === "success"
                  ? "text-green-600"
                  : pr.checksStatus === "failure"
                  ? "text-red-600"
                  : "text-yellow-600"
              )}
            >
              CI: {pr.checksStatus}
            </span>
          )}
        </div>
      </div>
      <span className="shrink-0 text-xs text-text-muted">
        {new Date(pr.updatedAt).toLocaleDateString()}
      </span>
    </div>
  );
}
