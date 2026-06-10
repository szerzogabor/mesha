"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { ProjectStatus } from "@/types";

export function useProjectStatuses(projectId: string) {
  return useQuery({
    queryKey: ["project-statuses", projectId],
    queryFn: () => apiClient.get<ProjectStatus[]>(`/api/projects/${projectId}/statuses`),
    enabled: !!projectId,
  });
}

export function useCreateProjectStatus(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; color?: string }) =>
      apiClient.post<ProjectStatus>(`/api/projects/${projectId}/statuses`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["project-statuses", projectId] });
    },
  });
}

export function useUpdateProjectStatus(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ statusId, data }: { statusId: string; data: { name?: string; color?: string } }) =>
      apiClient.patch<ProjectStatus>(`/api/projects/${projectId}/statuses/${statusId}`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["project-statuses", projectId] });
    },
  });
}

export function useDeleteProjectStatus(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (statusId: string) =>
      apiClient.delete(`/api/projects/${projectId}/statuses/${statusId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["project-statuses", projectId] });
    },
  });
}

export function useReorderProjectStatuses(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (statusIds: string[]) =>
      apiClient.put<ProjectStatus[]>(`/api/projects/${projectId}/statuses/reorder`, { statusIds }),
    onSuccess: (data) => {
      qc.setQueryData(["project-statuses", projectId], data);
    },
  });
}
