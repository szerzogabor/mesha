"use client";

import { use } from "react";
import Link from "next/link";
import StatusesSection from "@/components/settings/StatusesSection";

export default function ProjectStatusesPage({
  params,
}: {
  params: Promise<{ workspaceId: string; projectId: string }>;
}) {
  const { workspaceId, projectId } = use(params);

  return (
    <div className="h-full overflow-y-auto">
      <div className="px-6 py-4 bg-bg-surface border-b border-border-default">
        <div className="flex items-center gap-3">
          <Link
            href={`/workspaces/${workspaceId}/projects/${projectId}`}
            className="text-text-tertiary hover:text-text-primary transition-colors"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </Link>
          <div>
            <h2 className="text-lg font-semibold text-text-primary">Ticket Statuses</h2>
            <p className="text-sm text-text-tertiary">Manage workflow stages for this project</p>
          </div>
        </div>
      </div>

      <div className="p-6 max-w-2xl mx-auto">
        <StatusesSection projectId={projectId} />
      </div>
    </div>
  );
}
