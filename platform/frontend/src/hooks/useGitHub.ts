"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import {
  GitHubInstallation,
  GitHubRepository,
  GitHubPullRequest,
} from "@/types";

export function useGitHubInstallations(workspaceId: string) {
  return useQuery({
    queryKey: ["github", "installations", workspaceId],
    queryFn: () =>
      apiClient.get<GitHubInstallation[]>(
        `/api/workspaces/${workspaceId}/github/installations`
      ),
    enabled: !!workspaceId,
  });
}

export function useRegisterInstallation(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (installationId: number) =>
      apiClient.post<GitHubInstallation>(
        `/api/workspaces/${workspaceId}/github/installations/${installationId}`,
        {}
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["github", "installations", workspaceId] }),
  });
}

export function useGitHubRepositories(workspaceId: string) {
  return useQuery({
    queryKey: ["github", "repositories", workspaceId],
    queryFn: () =>
      apiClient.get<GitHubRepository[]>(
        `/api/workspaces/${workspaceId}/github/repositories`
      ),
    enabled: !!workspaceId,
  });
}

export function useConnectRepository(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { installationId: number; githubRepoId: number }) =>
      apiClient.post<GitHubRepository>(
        `/api/workspaces/${workspaceId}/github/repositories`,
        data
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["github", "repositories", workspaceId] }),
  });
}

export function useDisconnectRepository(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (repositoryId: string) =>
      apiClient.delete(
        `/api/workspaces/${workspaceId}/github/repositories/${repositoryId}`
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["github", "repositories", workspaceId] }),
  });
}

export function useGitHubPullRequests(workspaceId: string, repositoryId: string) {
  return useQuery({
    queryKey: ["github", "pull-requests", repositoryId],
    queryFn: () =>
      apiClient.get<GitHubPullRequest[]>(
        `/api/workspaces/${workspaceId}/github/repositories/${repositoryId}/pull-requests`
      ),
    enabled: !!repositoryId,
  });
}

export function useSyncPullRequests(workspaceId: string, repositoryId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiClient.post<GitHubPullRequest[]>(
        `/api/workspaces/${workspaceId}/github/repositories/${repositoryId}/pull-requests/sync`,
        {}
      ),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["github", "pull-requests", repositoryId] }),
  });
}
