"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { BlocksConfig } from "@/types";

export function useBlocksConfig(workspaceId: string) {
  return useQuery({
    queryKey: ["blocks-config", workspaceId],
    queryFn: async () => {
      try {
        return await apiClient.get<BlocksConfig>(
          `/api/workspaces/${workspaceId}/blocks/config`
        );
      } catch (err: unknown) {
        if (err instanceof Response && err.status === 404) return null;
        const anyErr = err as { status?: number };
        if (anyErr?.status === 404) return null;
        throw err;
      }
    },
    enabled: !!workspaceId,
    retry: false,
  });
}

export function useSaveBlocksConfig(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ apiKey, blocksWorkspaceId }: { apiKey: string; blocksWorkspaceId?: string }) =>
      apiClient.put<BlocksConfig>(
        `/api/workspaces/${workspaceId}/blocks/config`,
        { apiKey, blocksWorkspaceId: blocksWorkspaceId || null }
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["blocks-config", workspaceId] });
    },
  });
}

export function useDisconnectBlocks(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiClient.delete(`/api/workspaces/${workspaceId}/blocks/config`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["blocks-config", workspaceId] });
    },
  });
}
