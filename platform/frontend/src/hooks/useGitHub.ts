"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { logger } from "@/lib/logger";
import {
  AvailableRepository,
  GitHubInstallation,
  GitHubRepository,
  GitHubPullRequest,
} from "@/types";

export function useGitHubInstallations(workspaceId: string) {
  return useQuery({
    queryKey: ["github", "installations", workspaceId],
    queryFn: async () => {
      logger.github.installationsFetchStarted(workspaceId);
      try {
        const result = await apiClient.get<GitHubInstallation[]>(
          `/api/workspaces/${workspaceId}/github/installations`
        );
        logger.github.installationsFetched(workspaceId, result?.length ?? 0);
        return result;
      } catch (error) {
        logger.github.installationsFetchFailed(workspaceId, error);
        throw error;
      }
    },
    enabled: !!workspaceId,
    // Always treat as stale so window-focus refetch picks up uninstall events promptly
    staleTime: 0,
  });
}

export function useRegisterInstallation(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (installationId: number) => {
      logger.github.registrationStarted(workspaceId, installationId);
      const result = await apiClient.post<GitHubInstallation>(
        `/api/workspaces/${workspaceId}/github/installations/${installationId}`,
        {}
      );
      logger.github.registrationSucceeded(workspaceId, installationId);
      return result;
    },
    onError: (error, installationId) => {
      logger.github.registrationFailed(workspaceId, installationId, error);
    },
    onSuccess: () => {
      logger.github.queryInvalidated("github.installations", { workspaceId });
      qc.invalidateQueries({ queryKey: ["github", "installations", workspaceId] });
      logger.github.queryInvalidated("github.repositories", { workspaceId });
      qc.invalidateQueries({ queryKey: ["github", "repositories", workspaceId] });
    },
  });
}

export function useRefreshInstallation(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (installationId: string) => {
      const result = await apiClient.post<GitHubInstallation>(
        `/api/workspaces/${workspaceId}/github/installations/${installationId}/refresh`,
        {}
      );
      return result;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["github", "installations", workspaceId] });
      qc.invalidateQueries({ queryKey: ["github", "repositories", workspaceId] });
    },
  });
}

export function useGitHubRepositories(workspaceId: string) {
  return useQuery({
    queryKey: ["github", "repositories", workspaceId],
    queryFn: async () => {
      const result = await apiClient.get<GitHubRepository[]>(
        `/api/workspaces/${workspaceId}/github/repositories`
      );
      logger.github.repositoriesFetched(workspaceId, result?.length ?? 0);
      return result;
    },
    enabled: !!workspaceId,
  });
}

export function useInstallationRepositories(workspaceId: string, installationId: number | null) {
  return useQuery({
    queryKey: ["github", "installation-repos", workspaceId, installationId],
    queryFn: async () => {
      const result = await apiClient.get<AvailableRepository[]>(
        `/api/workspaces/${workspaceId}/github/installations/${installationId}/repositories`
      );
      logger.github.repositoriesFetched(workspaceId, result?.length ?? 0);
      return result;
    },
    enabled: !!workspaceId && installationId !== null,
  });
}

export function useConnectRepository(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (data: { installationId: number; githubRepoId: number }) => {
      logger.github.repositoryConnectStarted(workspaceId, {
        installationId: data.installationId,
        githubRepoId: data.githubRepoId,
      });
      const result = await apiClient.post<GitHubRepository>(
        `/api/workspaces/${workspaceId}/github/repositories`,
        data
      );
      logger.github.repositoryConnected(workspaceId, result.id);
      return result;
    },
    onError: (error) => {
      logger.github.repositoryConnectFailed(workspaceId, error);
    },
    onSuccess: () => {
      logger.github.queryInvalidated("github.repositories", { workspaceId });
      qc.invalidateQueries({ queryKey: ["github", "repositories", workspaceId] });
    },
  });
}

export function useDisconnectRepository(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (repositoryId: string) => {
      const result = await apiClient.delete(
        `/api/workspaces/${workspaceId}/github/repositories/${repositoryId}`
      );
      logger.github.repositoryDisconnected(workspaceId, repositoryId);
      return result;
    },
    onError: (error, repositoryId) => {
      logger.github.repositoryDisconnectFailed(workspaceId, repositoryId, error);
    },
    onSuccess: () => {
      logger.github.queryInvalidated("github.repositories", { workspaceId });
      qc.invalidateQueries({ queryKey: ["github", "repositories", workspaceId] });
    },
  });
}

export type PullRequestStatus = "open" | "merged" | "closed";

export function useGitHubPullRequests(
  workspaceId: string,
  repositoryId: string,
  status?: PullRequestStatus
) {
  return useQuery({
    queryKey: ["github", "pull-requests", repositoryId, status ?? "all"],
    queryFn: async () => {
      const url = status
        ? `/api/workspaces/${workspaceId}/github/repositories/${repositoryId}/pull-requests?status=${status}`
        : `/api/workspaces/${workspaceId}/github/repositories/${repositoryId}/pull-requests`;
      const result = await apiClient.get<GitHubPullRequest[]>(url);
      logger.github.pullRequestsFetched(repositoryId, result?.length ?? 0);
      return result;
    },
    enabled: !!repositoryId,
  });
}

export function useSyncPullRequests(workspaceId: string, repositoryId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      logger.github.pullRequestsSyncStarted(repositoryId);
      const result = await apiClient.post<GitHubPullRequest[]>(
        `/api/workspaces/${workspaceId}/github/repositories/${repositoryId}/pull-requests/sync`,
        {}
      );
      logger.github.pullRequestsSynced(repositoryId, result?.length ?? 0);
      return result;
    },
    onSuccess: () => {
      logger.github.queryInvalidated("github.pull-requests", { repositoryId });
      qc.invalidateQueries({ queryKey: ["github", "pull-requests", repositoryId] });
    },
  });
}
