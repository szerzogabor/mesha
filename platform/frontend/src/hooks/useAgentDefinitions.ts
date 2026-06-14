"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { AgentDefinition } from "@/types";

export function useAgentDefinitions(workspaceId: string) {
  return useQuery({
    queryKey: ["agentDefinitions", workspaceId],
    queryFn: () =>
      apiClient.get<AgentDefinition[]>(`/api/workspaces/${workspaceId}/agents`),
    enabled: !!workspaceId,
  });
}

export function useActiveAgentDefinitions(workspaceId: string) {
  return useQuery({
    queryKey: ["agentDefinitions", workspaceId, "active"],
    queryFn: () =>
      apiClient.get<AgentDefinition[]>(`/api/workspaces/${workspaceId}/agents/active`),
    enabled: !!workspaceId,
  });
}

export function useAgentDefinition(workspaceId: string, agentId: string) {
  return useQuery({
    queryKey: ["agentDefinition", agentId],
    queryFn: () =>
      apiClient.get<AgentDefinition>(`/api/workspaces/${workspaceId}/agents/${agentId}`),
    enabled: !!workspaceId && !!agentId,
  });
}

export function useCreateAgentDefinition(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      title: string;
      name: string;
      description?: string;
      providerType: string;
      systemPrompt: string;
      providerParameters?: Record<string, unknown>;
      active?: boolean;
    }) =>
      apiClient.post<AgentDefinition>(`/api/workspaces/${workspaceId}/agents`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["agentDefinitions", workspaceId] });
    },
  });
}

export function useUpdateAgentDefinition(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      agentId,
      ...data
    }: {
      agentId: string;
      title?: string;
      name?: string;
      description?: string;
      providerType?: string;
      systemPrompt?: string;
      providerParameters?: Record<string, unknown>;
      active?: boolean;
    }) =>
      apiClient.put<AgentDefinition>(`/api/workspaces/${workspaceId}/agents/${agentId}`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["agentDefinitions", workspaceId] });
    },
  });
}

export function useDeleteAgentDefinition(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (agentId: string) =>
      apiClient.delete(`/api/workspaces/${workspaceId}/agents/${agentId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["agentDefinitions", workspaceId] });
    },
  });
}
