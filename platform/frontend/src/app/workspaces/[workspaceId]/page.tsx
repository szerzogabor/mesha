"use client";

import { use } from "react";
import { WorkspaceDashboard } from "@/components/dashboard/WorkspaceDashboard";

export default function WorkspacePage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = use(params);
  return <WorkspaceDashboard workspaceId={workspaceId} />;
}
