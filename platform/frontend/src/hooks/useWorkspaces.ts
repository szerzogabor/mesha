"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { Workspace } from "@/types";

export function useWorkspaces() {
  return useQuery({
    queryKey: ["workspaces"],
    queryFn: () => apiClient.get<Workspace[]>("/api/workspaces"),
  });
}

export function useCreateWorkspace() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { name: string; slug: string }) =>
      apiClient.post<Workspace>("/api/workspaces", data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["workspaces"] }),
  });
}
