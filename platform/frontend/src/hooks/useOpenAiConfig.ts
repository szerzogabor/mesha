"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { OpenAiConfig, OpenAiAuthMode } from "@/types";

const QUERY_KEY = ["openai-config"];

export function useOpenAiConfig() {
  return useQuery({
    queryKey: QUERY_KEY,
    queryFn: async () => {
      try {
        return await apiClient.get<OpenAiConfig>("/api/me/openai/config");
      } catch (err: unknown) {
        if (err instanceof Response && err.status === 404) return null;
        const anyErr = err as { status?: number };
        if (anyErr?.status === 404) return null;
        throw err;
      }
    },
    retry: false,
  });
}

export interface SaveOpenAiConfigInput {
  authMode: OpenAiAuthMode;
  apiKey?: string;
  accessToken?: string;
  refreshToken?: string;
  accountId?: string;
  model?: string;
}

export function useSaveOpenAiConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: SaveOpenAiConfigInput) =>
      apiClient.put<OpenAiConfig>("/api/me/openai/config", input),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}

export function useDisconnectOpenAi() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => apiClient.delete("/api/me/openai/config"),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });
}
