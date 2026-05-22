"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";
import { apiClient } from "@/lib/api-client";
import { logger } from "@/lib/logger";
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
  const prevStateRef = useRef<AIExecutionState | undefined>(undefined);

  return useQuery({
    queryKey: ["blocks-session-active", issueId],
    queryFn: async () => {
      const session = await apiClient.get<BlocksSession>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions/active`
      );

      const prevState = prevStateRef.current;
      const nextState = session?.executionState;

      if (nextState && prevState !== nextState) {
        if (prevState === undefined) {
          logger.ai.sessionStateChange(session.id, "none", nextState);
        } else {
          logger.ai.sessionStateChange(session.id, prevState, nextState);
        }
        prevStateRef.current = nextState;
      }

      const terminalStates: AIExecutionState[] = ["DONE", "FAILED", "CANCELED"];
      if (nextState && terminalStates.includes(nextState)) {
        logger.ai.pollingActive(issueId, session.id, nextState);
      }

      return session;
    },
    enabled: enabled && !!projectId && !!issueId,
    retry: false,
    refetchInterval: (query) => {
      const state = query.state.data?.executionState;
      if (!state) return false;
      const terminalStates: AIExecutionState[] = ["DONE", "FAILED", "CANCELED"];
      if (!terminalStates.includes(state)) {
        logger.ai.pollingActive(issueId, query.state.data?.id ?? "", state);
      }
      return terminalStates.includes(state) ? false : 5000;
    },
  });
}

export function useAssignToBlocks(projectId: string, issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const session = await apiClient.post<BlocksSession>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions`,
        {}
      );
      logger.ai.sessionStarted(issueId, session.id);
      return session;
    },
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
    mutationFn: async (sessionId: string) => {
      const result = await apiClient.post<BlocksSession>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions/${sessionId}/cancel`,
        {}
      );
      logger.ai.sessionCanceled(issueId, sessionId);
      return result;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["blocks-sessions", issueId] });
      qc.invalidateQueries({ queryKey: ["blocks-session-active", issueId] });
      qc.invalidateQueries({ queryKey: ["issue", issueId] });
    },
  });
}
