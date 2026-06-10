"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { Project } from "@/types";

export function useProjects(workspaceId: string) {
  return useQuery({
    queryKey: ["projects", workspaceId],
    queryFn: () => apiClient.get<Project[]>(`/api/workspaces/${workspaceId}/projects`),
    enabled: !!workspaceId,
  });
}

export function useCreateProject(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; description?: string; key?: string }) =>
      apiClient.post<Project>(`/api/workspaces/${workspaceId}/projects`, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["projects", workspaceId] }),
  });
}

export function useDeleteProject(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (projectId: string) =>
      apiClient.delete(`/api/workspaces/${workspaceId}/projects/${projectId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["projects", workspaceId] }),
  });
}
