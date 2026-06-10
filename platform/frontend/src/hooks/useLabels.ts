"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { Label } from "@/types";

export function useLabels(workspaceId: string) {
  return useQuery({
    queryKey: ["labels", workspaceId],
    queryFn: () => apiClient.get<Label[]>(`/api/workspaces/${workspaceId}/labels`),
    enabled: !!workspaceId,
  });
}

export function useCreateLabel(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; color?: string }) =>
      apiClient.post<Label>(`/api/workspaces/${workspaceId}/labels`, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["labels", workspaceId] }),
  });
}

export function useDeleteLabel(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (labelId: string) =>
      apiClient.delete(`/api/workspaces/${workspaceId}/labels/${labelId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["labels", workspaceId] }),
  });
}
