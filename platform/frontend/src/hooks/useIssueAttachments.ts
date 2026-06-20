"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { IssueAttachment } from "@/types";

function attachmentsKey(projectId: string, issueId: string) {
  return ["issue-attachments", projectId, issueId] as const;
}

export function useIssueAttachments(projectId: string, issueId: string) {
  return useQuery({
    queryKey: attachmentsKey(projectId, issueId),
    queryFn: () =>
      apiClient.get<IssueAttachment[]>(
        `/api/projects/${projectId}/issues/${issueId}/attachments`
      ),
    enabled: Boolean(projectId && issueId),
  });
}

export function useUploadAttachment(projectId: string, issueId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      return apiClient.upload<IssueAttachment>(
        `/api/projects/${projectId}/issues/${issueId}/attachments`,
        formData
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: attachmentsKey(projectId, issueId) });
    },
  });
}

export function useDeleteAttachment(projectId: string, issueId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (attachmentId: string) =>
      apiClient.delete(
        `/api/projects/${projectId}/issues/${issueId}/attachments/${attachmentId}`
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: attachmentsKey(projectId, issueId) });
    },
  });
}
