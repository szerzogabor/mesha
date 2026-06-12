"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface WorkspaceMember {
  id: string;
  userId: string;
  email: string;
  name?: string;
  role: string;
}

export function useWorkspaceMembers(workspaceId: string) {
  return useQuery({
    queryKey: ["workspace-members", workspaceId],
    queryFn: () =>
      apiClient.get<WorkspaceMember[]>(`/api/workspaces/${workspaceId}/members`),
    enabled: !!workspaceId,
  });
}
