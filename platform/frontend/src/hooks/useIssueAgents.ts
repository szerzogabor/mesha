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
    onMutate: async () => {
      // Cancel any in-flight refetches so a stale GET from a preceding unassign
      // invalidation cannot overwrite the setQueryData we apply in onSuccess.
      await qc.cancelQueries({ queryKey: ["issueAgents", issueId] });
    },
    onSuccess: (data) => {
      qc.setQueryData<IssueAgentAssignment[]>(["issueAgents", issueId], (old) => {
        const existing = old ?? [];
        const withoutDup = existing.filter((a) => a.agentDefinitionId !== data.agentDefinitionId);
        return [data, ...withoutDup];
      });
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
    onSuccess: (_, agentDefinitionId) => {
      qc.setQueryData<IssueAgentAssignment[]>(["issueAgents", issueId], (old) =>
        (old ?? []).filter((a) => a.agentDefinitionId !== agentDefinitionId)
      );
      qc.invalidateQueries({ queryKey: ["issueAgents", issueId] });
    },
  });
}
