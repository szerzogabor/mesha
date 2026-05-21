"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { BlocksSession, AIExecutionState } from "@/types";

export function useBlocksSessions(projectId: string, issueId: string) {
  return useQuery({
    queryKey: ["blocks-sessions", issueId],
    queryFn: () =>
      apiClient.get<BlocksSession[]>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions`
      ),
    enabled: !!projectId && !!issueId,
  });
}

export function useActiveBlocksSession(projectId: string, issueId: string, enabled = true) {
  return useQuery({
    queryKey: ["blocks-session-active", issueId],
    queryFn: () =>
      apiClient.get<BlocksSession>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions/active`
      ),
    enabled: enabled && !!projectId && !!issueId,
    retry: false,
    refetchInterval: (query) => {
      const state = query.state.data?.executionState;
      if (!state) return false;
      const terminalStates: AIExecutionState[] = ["DONE", "FAILED", "CANCELED"];
      return terminalStates.includes(state) ? false : 5000;
    },
  });
}

export function useAssignToBlocks(projectId: string, issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiClient.post<BlocksSession>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions`,
        {}
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["blocks-sessions", issueId] });
      qc.invalidateQueries({ queryKey: ["blocks-session-active", issueId] });
      qc.invalidateQueries({ queryKey: ["issue", issueId] });
    },
  });
}

export function useCancelBlocksSession(projectId: string, issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      apiClient.post<BlocksSession>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions/${sessionId}/cancel`,
        {}
      ),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["blocks-sessions", issueId] });
      qc.invalidateQueries({ queryKey: ["blocks-session-active", issueId] });
      qc.invalidateQueries({ queryKey: ["issue", issueId] });
    },
  });
}
