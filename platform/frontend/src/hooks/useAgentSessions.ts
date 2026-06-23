"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { ConnectorAgentSession, ConnectorAgentSessionMessage, ConnectorAgentSessionStatus } from "@/types";

const TERMINAL_STATES: ConnectorAgentSessionStatus[] = ["COMPLETED", "FAILED", "CANCELLED"];

export function useAgentSessions() {
  return useQuery({
    queryKey: ["agent-sessions"],
    queryFn: () => apiClient.get<ConnectorAgentSession[]>("/api/agent-sessions"),
    refetchInterval: (query) => {
      const sessions = query.state.data;
      if (!sessions?.length) return 10000;
      const hasActive = sessions.some((s) => !TERMINAL_STATES.includes(s.status));
      return hasActive ? 5000 : 10000;
    },
  });
}

export function useAgentSession(sessionId: string | undefined) {
  return useQuery({
    queryKey: ["agent-session", sessionId],
    queryFn: () => apiClient.get<ConnectorAgentSession>(`/api/agent-sessions/${sessionId}`),
    enabled: !!sessionId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (!status) return false;
      return TERMINAL_STATES.includes(status) ? false : 5000;
    },
  });
}

export function useAgentSessionMessages(sessionId: string | undefined, status: ConnectorAgentSessionStatus | undefined) {
  const isTerminal = status ? TERMINAL_STATES.includes(status) : false;

  return useQuery({
    queryKey: ["agent-session-messages", sessionId],
    queryFn: () => apiClient.get<ConnectorAgentSessionMessage[]>(`/api/agent-sessions/${sessionId}/messages`),
    enabled: !!sessionId,
    refetchInterval: isTerminal ? false : 5000,
  });
}

export function useCreateConnectorSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ issueId, instructions }: { issueId: string; instructions?: string }) =>
      apiClient.post<ConnectorAgentSession>("/api/agent-sessions", { issueId, instructions }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["agent-sessions"] });
    },
  });
}

export function useEnqueueAgentSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      apiClient.post<ConnectorAgentSession>(`/api/agent-sessions/${sessionId}/enqueue`, {}),
    onSuccess: (_data, sessionId) => {
      qc.invalidateQueries({ queryKey: ["agent-sessions"] });
      qc.invalidateQueries({ queryKey: ["agent-session", sessionId] });
    },
  });
}

export function useCancelAgentSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) =>
      apiClient.post<ConnectorAgentSession>(`/api/agent-sessions/${sessionId}/cancel`, {}),
    onSuccess: (_data, sessionId) => {
      qc.invalidateQueries({ queryKey: ["agent-sessions"] });
      qc.invalidateQueries({ queryKey: ["agent-session", sessionId] });
    },
  });
}

export function useSendAgentSessionMessage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ sessionId, content }: { sessionId: string; content: string }) =>
      apiClient.post<ConnectorAgentSessionMessage>(`/api/agent-sessions/${sessionId}/messages`, { content }),
    onSuccess: (_data, { sessionId }) => {
      qc.invalidateQueries({ queryKey: ["agent-session-messages", sessionId] });
      qc.invalidateQueries({ queryKey: ["agent-session", sessionId] });
      qc.invalidateQueries({ queryKey: ["agent-sessions"] });
    },
  });
}
