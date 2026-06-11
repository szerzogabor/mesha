"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { IssueLink, IssueLinkType } from "@/types";

export function useIssueLinks(issueId: string) {
  return useQuery({
    queryKey: ["issue-links", issueId],
    queryFn: () => apiClient.get<IssueLink[]>(`/api/issues/${issueId}/links`),
    enabled: !!issueId,
  });
}

export function useCreateIssueLink(issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { targetIssueId: string; linkType: IssueLinkType }) =>
      apiClient.post<IssueLink>(`/api/issues/${issueId}/links`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["issue-links", issueId] });
    },
  });
}

export function useDeleteIssueLink(issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (linkId: string) =>
      apiClient.delete(`/api/issues/${issueId}/links/${linkId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["issue-links", issueId] });
    },
  });
}
