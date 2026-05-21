"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { AIDraft, Issue, IssueStatus, IssuePriority } from "@/types";

export function useGenerateDraft(projectId: string) {
  return useMutation({
    mutationFn: (prompt: string) =>
      apiClient.post<AIDraft>(`/api/projects/${projectId}/ai-drafts`, { prompt }),
  });
}

export function useApproveDraft(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      draftId,
      title,
      description,
      status,
      priority,
    }: {
      draftId: string;
      title: string;
      description?: string;
      status?: IssueStatus;
      priority?: IssuePriority;
    }) =>
      apiClient.post<Issue>(`/api/projects/${projectId}/ai-drafts/${draftId}/approve`, {
        title,
        description,
        status,
        priority,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["issues", projectId] });
      qc.invalidateQueries({ queryKey: ["issues-all", projectId] });
    },
  });
}

export function useRejectDraft(projectId: string) {
  return useMutation({
    mutationFn: (draftId: string) =>
      apiClient.delete(`/api/projects/${projectId}/ai-drafts/${draftId}`),
  });
}

export function useRegenerateDraft(projectId: string) {
  return useMutation({
    mutationFn: ({ draftId, prompt }: { draftId: string; prompt?: string }) =>
      apiClient.post<AIDraft>(
        `/api/projects/${projectId}/ai-drafts/${draftId}/regenerate`,
        prompt ? { prompt } : {}
      ),
  });
}
