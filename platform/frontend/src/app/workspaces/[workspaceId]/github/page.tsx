"use client";

import { use, useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  useGitHubInstallations,
  useGitHubRepositories,
  useRegisterInstallation,
  useInstallationRepositories,
  useConnectRepository,
  useDisconnectRepository,
  useRefreshInstallation,
} from "@/hooks/useGitHub";
import { RepositoryCard } from "@/components/github/RepositoryCard";
import { Spinner } from "@/components/ui/Spinner";
import { logger } from "@/lib/logger";
import Image from "next/image";
import { GitHubInstallation } from "@/types";

function InstallationStatusBadge({ status }: { status: string }) {
  if (status === "active") {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400">
        <span className="w-1.5 h-1.5 rounded-full bg-green-500" />
        Active
      </span>
    );
  }
  if (status === "suspended") {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400">
        <span className="w-1.5 h-1.5 rounded-full bg-yellow-500" />
        Suspended
      </span>
    );
  }
  if (status === "deleted") {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400">
        <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
        Uninstalled
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-bg-subtle text-text-muted">
      {status}
    </span>
  );
}

function InstallationCard({
  inst,
  workspaceId,
}: {
  inst: GitHubInstallation;
  workspaceId: string;
}) {
  const refresh = useRefreshInstallation(workspaceId);
  const isDeleted = inst.status === "deleted";

  return (
    <li className="bg-bg-surface border border-border-subtle rounded-lg px-4 py-3">
      <div className="flex items-start gap-3">
        {inst.accountAvatarUrl && (
          <Image
            src={inst.accountAvatarUrl}
            alt={inst.accountLogin}
            width={32}
            height={32}
            className="rounded-full shrink-0 mt-0.5"
          />
        )}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <p className="font-medium text-text-primary text-sm truncate">
              {inst.accountLogin}
            </p>
            <InstallationStatusBadge status={inst.status} />
          </div>
          <p className="text-xs text-text-muted mt-0.5">{inst.accountType}</p>
          {inst.lastRefreshAt && (
            <p className="text-xs text-text-muted mt-0.5">
              Last refreshed {new Date(inst.lastRefreshAt).toLocaleString()}
            </p>
          )}
        </div>

        {/* Action buttons */}
        <div className="flex items-center gap-2 shrink-0">
          {isDeleted ? (
            <a
              href={`https://github.com/apps/${process.env.NEXT_PUBLIC_GITHUB_APP_NAME ?? "mesha-github-app"}/installations/new?state=${workspaceId}`}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-accent text-white rounded-lg hover:bg-accent/90 transition-colors"
            >
              Reconnect GitHub App
            </a>
          ) : (
            <>
              {inst.manageUrl && (
                <a
                  href={inst.manageUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border-subtle text-text-secondary rounded-lg hover:bg-bg-subtle transition-colors"
                >
                  Manage Installation
                  <svg
                    className="w-3 h-3"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"
                    />
                  </svg>
                </a>
              )}
              <button
                onClick={() => refresh.mutate(inst.id)}
                disabled={refresh.isPending}
                title="Refresh repositories and installation state from GitHub"
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border-subtle text-text-secondary rounded-lg hover:bg-bg-subtle transition-colors disabled:opacity-50"
              >
                {refresh.isPending ? (
                  <Spinner size="sm" />
                ) : (
                  <svg
                    className="w-3 h-3"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
                    />
                  </svg>
                )}
                {refresh.isPending ? "Refreshing…" : "Refresh Repositories"}
              </button>
            </>
          )}
        </div>
      </div>

      {/* Suspended warning */}
      {inst.status === "suspended" && (
        <div className="mt-3 flex items-start gap-2 p-2.5 bg-yellow-50 border border-yellow-200 rounded-lg dark:bg-yellow-900/20 dark:border-yellow-800">
          <svg
            className="w-4 h-4 text-yellow-600 dark:text-yellow-400 shrink-0 mt-0.5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
          <p className="text-xs text-yellow-700 dark:text-yellow-300">
            This installation is suspended. Visit GitHub to restore access, then click{" "}
            <strong>Refresh Repositories</strong>.
          </p>
        </div>
      )}

      {/* Uninstalled warning with reconnect CTA */}
      {inst.status === "deleted" && (
        <div className="mt-3 flex items-start gap-2 p-2.5 bg-red-50 border border-red-200 rounded-lg dark:bg-red-900/20 dark:border-red-800">
          <svg
            className="w-4 h-4 text-red-600 dark:text-red-400 shrink-0 mt-0.5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
          <p className="text-xs text-red-700 dark:text-red-300">
            The GitHub App was uninstalled from <strong>{inst.accountLogin}</strong>. Click{" "}
            <strong>Reconnect GitHub App</strong> to reinstall it and restore repository access.
          </p>
        </div>
      )}

      {/* Refresh error */}
      {refresh.isError && (
        <div className="mt-3 p-2.5 bg-red-50 border border-red-200 rounded-lg dark:bg-red-900/20 dark:border-red-800">
          <p className="text-xs text-red-700 dark:text-red-300">
            Failed to refresh installation. Please try again or check GitHub for issues.
          </p>
        </div>
      )}
    </li>
  );
}

function ConnectRepositoryForm({
  workspaceId,
  installations,
  onCancel,
  onConnected,
}: {
  workspaceId: string;
  installations: { id: string; installationId: number; accountLogin: string }[];
  onCancel: () => void;
  onConnected: () => void;
}) {
  const [selectedInstallationId, setSelectedInstallationId] = useState<number | null>(
    installations.length === 1 ? installations[0].installationId : null
  );
  const [selectedRepoId, setSelectedRepoId] = useState<number | null>(null);

  const { data: availableRepos = [], isLoading: loadingRepos } = useInstallationRepositories(
    workspaceId,
    selectedInstallationId
  );
  const connectRepo = useConnectRepository(workspaceId);

  const handleInstallationChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedInstallationId(e.target.value ? Number(e.target.value) : null);
    setSelectedRepoId(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedInstallationId || !selectedRepoId) return;
    logger.github.uiStateChange("idle", "connecting", { workspaceId });
    await connectRepo.mutateAsync({
      installationId: selectedInstallationId,
      githubRepoId: selectedRepoId,
    });
    logger.github.uiStateChange("connecting", "idle", { workspaceId });
    onConnected();
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-4 bg-bg-surface border border-border-subtle rounded-lg p-4 space-y-3"
    >
      {installations.length > 1 && (
        <div>
          <label className="block text-xs font-medium text-text-secondary mb-1">
            Installation
          </label>
          <select
            className="w-full text-sm border border-border-input rounded-lg px-3 py-2 bg-bg-input text-text-primary"
            value={selectedInstallationId ?? ""}
            onChange={handleInstallationChange}
            required
          >
            <option value="">Select installation…</option>
            {installations.map((i) => (
              <option key={i.id} value={i.installationId}>
                {i.accountLogin} ({i.installationId})
              </option>
            ))}
          </select>
        </div>
      )}

      <div>
        <label className="block text-xs font-medium text-text-secondary mb-1">
          Repository
        </label>
        {loadingRepos ? (
          <div className="flex items-center gap-2 text-sm text-text-muted py-2">
            <Spinner size="sm" />
            Loading repositories…
          </div>
        ) : (
          <select
            className="w-full text-sm border border-border-input rounded-lg px-3 py-2 bg-bg-input text-text-primary"
            value={selectedRepoId ?? ""}
            onChange={(e) => setSelectedRepoId(e.target.value ? Number(e.target.value) : null)}
            required
            disabled={!selectedInstallationId}
          >
            <option value="">
              {!selectedInstallationId
                ? "Select an installation first…"
                : availableRepos.length === 0
                ? "No repositories found"
                : "Select repository…"}
            </option>
            {availableRepos.map((repo) => (
              <option key={repo.id} value={repo.id}>
                {repo.fullName}
                {repo.isPrivate ? " (private)" : ""}
              </option>
            ))}
          </select>
        )}
      </div>

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={connectRepo.isPending || !selectedInstallationId || !selectedRepoId}
          className="px-4 py-2 bg-accent text-white text-sm rounded-lg hover:bg-accent/90 disabled:opacity-50 transition-colors"
        >
          {connectRepo.isPending ? "Connecting…" : "Connect"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm text-text-secondary rounded-lg hover:bg-bg-subtle transition-colors"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}

export default function GitHubPage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = use(params);
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const {
    data: installations = [],
    isLoading: loadingInstallations,
    isError: installationsError,
  } = useGitHubInstallations(workspaceId);
  const {
    data: repositories = [],
    isLoading: loadingRepos,
    isError: reposError,
  } = useGitHubRepositories(workspaceId);
  const registerInstallation = useRegisterInstallation(workspaceId);
  const disconnectRepo = useDisconnectRepository(workspaceId);

  const [showConnectForm, setShowConnectForm] = useState(false);

  const visibleInstallations = installations.filter((i) => i.status === "active");

  useEffect(() => {
    const installationIdParam = searchParams.get("installation_id");
    if (!installationIdParam) return;

    const installationId = Number(installationIdParam);
    if (!Number.isFinite(installationId)) {
      logger.github.uiStateChange("oauth_redirect", "invalid_installation_id", {
        workspaceId,
        installationIdParam,
      });
      return;
    }

    const setupAction = searchParams.get("setup_action");
    logger.github.uiStateChange("oauth_redirect", "registering_installation", {
      workspaceId,
      installationId,
      setupAction,
    });

    registerInstallation
      .mutateAsync(installationId)
      .then(() => {
        const paramsWithoutInstallation = new URLSearchParams(searchParams.toString());
        paramsWithoutInstallation.delete("installation_id");
        paramsWithoutInstallation.delete("setup_action");
        paramsWithoutInstallation.delete("state");
        const nextQuery = paramsWithoutInstallation.toString();
        router.replace(nextQuery ? `${pathname}?${nextQuery}` : pathname);
        logger.github.uiStateChange("registering_installation", "registered", {
          workspaceId,
          installationId,
        });
      })
      .catch((error) => {
        logger.github.registrationFailed(workspaceId, installationId, error);
      });
  }, [pathname, registerInstallation, router, searchParams, workspaceId]);

  if (loadingInstallations || loadingRepos) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  if (installationsError || reposError) {
    return (
      <div className="p-6 max-w-3xl mx-auto">
        <div className="rounded-lg border border-red-200 bg-red-50 dark:bg-red-900/20 dark:border-red-800 p-6 text-center">
          <svg
            className="w-8 h-8 text-red-500 mx-auto mb-3"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={1.5}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
          <p className="text-sm font-medium text-red-700 dark:text-red-300 mb-1">
            Failed to load GitHub data
          </p>
          <p className="text-xs text-red-600 dark:text-red-400">
            There was an error communicating with GitHub. Please refresh the page or try again later.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text-primary">GitHub Integration</h1>
        <p className="text-text-muted mt-1">
          Connect repositories to sync pull requests and code activity.
        </p>
      </div>

      {/* Installations */}
      <section className="mb-8">
        <h2 className="text-lg font-semibold text-text-primary mb-3">App Installations</h2>
        {visibleInstallations.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border-subtle p-6 text-center">
            <p className="text-text-muted text-sm mb-3">No GitHub App installations found.</p>
            <a
              href={`https://github.com/apps/${process.env.NEXT_PUBLIC_GITHUB_APP_NAME ?? "mesha-github-app"}/installations/new?state=${workspaceId}`}
              className="inline-flex items-center gap-1.5 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent/90 transition-colors"
            >
              Install GitHub App
            </a>
          </div>
        ) : (
          <ul className="space-y-2">
            {visibleInstallations.map((inst) => (
              <InstallationCard key={inst.id} inst={inst} workspaceId={workspaceId} />
            ))}
          </ul>
        )}
      </section>

      {/* Repositories */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg font-semibold text-text-primary">Connected Repositories</h2>
          {visibleInstallations.length > 0 && (
            <button
              onClick={() => setShowConnectForm((v) => !v)}
              className="text-sm px-3 py-1.5 bg-accent text-white rounded-lg hover:bg-accent/90 transition-colors"
            >
              Connect Repository
            </button>
          )}
        </div>

        {showConnectForm && (
          <ConnectRepositoryForm
            workspaceId={workspaceId}
            installations={visibleInstallations}
            onCancel={() => setShowConnectForm(false)}
            onConnected={() => setShowConnectForm(false)}
          />
        )}

        {repositories.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border-subtle p-6 text-center">
            <p className="text-text-muted text-sm">No repositories connected yet.</p>
          </div>
        ) : (
          <ul className="space-y-2">
            {repositories.map((repo) => (
              <li key={repo.id}>
                <RepositoryCard
                  repo={repo}
                  workspaceId={workspaceId}
                  onDisconnect={(id) => disconnectRepo.mutate(id)}
                  disconnecting={disconnectRepo.isPending}
                />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
