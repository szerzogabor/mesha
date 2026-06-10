"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useRef } from "react";
import { apiClient } from "@/lib/api-client";
import { logger } from "@/lib/logger";
import { BlocksSession, AIExecutionState } from "@/types";

const TERMINAL_STATES: AIExecutionState[] = ["DONE", "FAILED", "CANCELED"];

export function useBlocksSessions(projectId: string, issueId: string) {
  return useQuery({
    queryKey: ["blocks-sessions", issueId],
    queryFn: () =>
      apiClient.get<BlocksSession[]>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions`
      ),
    enabled: !!projectId && !!issueId,
    refetchInterval: (query) => {
      const sessions = query.state.data;
      if (!sessions?.length) return false;
      const hasActive = sessions.some((s) => !TERMINAL_STATES.includes(s.executionState));
      return hasActive ? 5000 : false;
    },
  });
}

export function useActiveBlocksSession(projectId: string, issueId: string, enabled = true) {
  const prevStateRef = useRef<AIExecutionState | undefined>(undefined);
  const qc = useQueryClient();

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

      if (nextState && TERMINAL_STATES.includes(nextState)) {
        logger.ai.pollingActive(issueId, session.id, nextState);
      }

      // Propagate the latest session data (including sessionUrl) into the sessions list cache
      if (session) {
        qc.setQueryData<BlocksSession[]>(["blocks-sessions", issueId], (old) => {
          if (!old) return old;
          return old.map((s) => (s.id === session.id ? session : s));
        });
      }

      return session;
    },
    enabled: enabled && !!projectId && !!issueId,
    retry: false,
    refetchInterval: (query) => {
      const state = query.state.data?.executionState;
      if (!state) return false;
      if (!TERMINAL_STATES.includes(state)) {
        logger.ai.pollingActive(issueId, query.state.data?.id ?? "", state);
      }
      return TERMINAL_STATES.includes(state) ? false : 5000;
    },
  });
}

export function useAssignToBlocks(projectId: string, issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (instructions?: string) => {
      const session = await apiClient.post<BlocksSession>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions`,
        { instructions: instructions?.trim() || undefined }
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

export function useSendBlocksMessage(projectId: string, issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ sessionId, content }: { sessionId: string; content: string }) => {
      return apiClient.post(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions/${sessionId}/messages`,
        { content }
      );
    },
    onSuccess: (_data, { sessionId }) => {
      qc.invalidateQueries({ queryKey: ["blocks-messages", sessionId] });
    },
  });
}
