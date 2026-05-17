"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { Comment } from "@/types";

export function useComments(issueId: string) {
  return useQuery({
    queryKey: ["comments", issueId],
    queryFn: () => apiClient.get<Comment[]>(`/api/issues/${issueId}/comments`),
    enabled: !!issueId,
  });
}

export function useCreateComment(issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { body: string; parentId?: string }) =>
      apiClient.post<Comment>(`/api/issues/${issueId}/comments`, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["comments", issueId] }),
  });
}

export function useDeleteComment(issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (commentId: string) =>
      apiClient.delete(`/api/issues/${issueId}/comments/${commentId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["comments", issueId] }),
  });
}
