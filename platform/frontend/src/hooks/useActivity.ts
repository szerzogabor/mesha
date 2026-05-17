"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { ActivityEvent } from "@/types";

export function useActivity(projectId: string, issueId: string) {
  return useQuery({
    queryKey: ["activity", issueId],
    queryFn: () =>
      apiClient.get<ActivityEvent[]>(
        `/api/projects/${projectId}/issues/${issueId}/activity`
      ),
    enabled: !!projectId && !!issueId,
  });
}
