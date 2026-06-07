"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { BlocksMessage, AIExecutionState } from "@/types";

export function useBlocksMessages(
  projectId: string,
  issueId: string,
  sessionId: string | undefined,
  sessionState: AIExecutionState | undefined,
  enabled = true
) {
  const terminalStates: AIExecutionState[] = ["DONE", "FAILED", "CANCELED"];
  const isTerminal = sessionState ? terminalStates.includes(sessionState) : false;

  return useQuery({
    queryKey: ["blocks-messages", sessionId],
    queryFn: () =>
      apiClient.get<BlocksMessage[]>(
        `/api/projects/${projectId}/issues/${issueId}/blocks-sessions/${sessionId}/messages`
      ),
    enabled: enabled && !!projectId && !!issueId && !!sessionId,
    refetchInterval: isTerminal ? false : 5000,
  });
}
