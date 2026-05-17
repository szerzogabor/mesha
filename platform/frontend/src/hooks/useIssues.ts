"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { Issue, IssueStatus, IssuePriority, PagedResponse } from "@/types";

interface IssueFilters {
  status?: IssueStatus;
  priority?: IssuePriority;
  assigneeId?: string;
  search?: string;
  page?: number;
  size?: number;
}

function buildQuery(filters: IssueFilters): string {
  const params = new URLSearchParams();
  if (filters.status) params.set("status", filters.status);
  if (filters.priority) params.set("priority", filters.priority);
  if (filters.assigneeId) params.set("assigneeId", filters.assigneeId);
  if (filters.search) params.set("search", filters.search);
  if (filters.page !== undefined) params.set("page", String(filters.page));
  if (filters.size !== undefined) params.set("size", String(filters.size));
  const q = params.toString();
  return q ? `?${q}` : "";
}

export function useIssues(projectId: string, filters: IssueFilters = {}) {
  return useQuery({
    queryKey: ["issues", projectId, filters],
    queryFn: () =>
      apiClient.get<PagedResponse<Issue>>(
        `/api/projects/${projectId}/issues${buildQuery(filters)}`
      ),
    enabled: !!projectId,
  });
}

export function useIssue(projectId: string, issueId: string) {
  return useQuery({
    queryKey: ["issue", issueId],
    queryFn: () => apiClient.get<Issue>(`/api/projects/${projectId}/issues/${issueId}`),
    enabled: !!projectId && !!issueId,
  });
}

export function useCreateIssue(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      title: string;
      description?: string;
      status?: IssueStatus;
      priority?: IssuePriority;
      assigneeId?: string;
      labelIds?: string[];
    }) => apiClient.post<Issue>(`/api/projects/${projectId}/issues`, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["issues", projectId] }),
  });
}

export function useUpdateIssue(projectId: string, issueId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      title?: string;
      description?: string;
      status?: IssueStatus;
      priority?: IssuePriority;
      assigneeId?: string;
      clearAssignee?: boolean;
      labelIds?: string[];
    }) => apiClient.patch<Issue>(`/api/projects/${projectId}/issues/${issueId}`, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["issue", issueId] });
      qc.invalidateQueries({ queryKey: ["issues", projectId] });
    },
  });
}

export function useDeleteIssue(projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (issueId: string) =>
      apiClient.delete(`/api/projects/${projectId}/issues/${issueId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["issues", projectId] }),
  });
}
