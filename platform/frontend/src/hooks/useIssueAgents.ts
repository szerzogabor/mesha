"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { IssueAgentAssignment } from "@/types";

export function useIssueAgents(projectId: string, issueId: string) {
  return useQuery({
    queryKey: ["issueAgents", issueId],
    queryFn: () =>
      apiClient.get<IssueAgentAssignment[]>(
        `/api/projects/${projectId}/issues/${issueId}/agents`
      ),
    enabled: !!projectId && !!issueId,
  });
}

export function useAssignAgent(projectId: string, issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (agentDefinitionId: string) =>
      apiClient.post<IssueAgentAssignment>(
        `/api/projects/${projectId}/issues/${issueId}/agents`,
        { agentDefinitionId }
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["issueAgents", issueId] });
    },
  });
}

export function useUnassignAgent(projectId: string, issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (agentDefinitionId: string) =>
      apiClient.delete(
        `/api/projects/${projectId}/issues/${issueId}/agents/${agentDefinitionId}`
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["issueAgents", issueId] });
    },
  });
}
