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
} from "@/hooks/useGitHub";
import { RepositoryCard } from "@/components/github/RepositoryCard";
import { Spinner } from "@/components/ui/Spinner";
import { logger } from "@/lib/logger";

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
  const { data: installations = [], isLoading: loadingInstallations } =
    useGitHubInstallations(workspaceId);
  const { data: repositories = [], isLoading: loadingRepos } =
    useGitHubRepositories(workspaceId);
  const registerInstallation = useRegisterInstallation(workspaceId);
  const disconnectRepo = useDisconnectRepository(workspaceId);

  const [showConnectForm, setShowConnectForm] = useState(false);

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
        <h2 className="text-lg font-semibold text-text-primary mb-3">
          App Installations
        </h2>
        {installations.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border-subtle p-6 text-center">
            <p className="text-text-muted text-sm mb-3">
              No GitHub App installations found.
            </p>
            <a
              href={`https://github.com/apps/${process.env.NEXT_PUBLIC_GITHUB_APP_NAME ?? "mesha-github-app"}/installations/new?state=${workspaceId}`}
              className="inline-flex items-center gap-1.5 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent/90 transition-colors"
            >
              Install GitHub App
            </a>
          </div>
        ) : (
          <ul className="space-y-2">
            {installations.map((inst) => (
              <li
                key={inst.id}
                className="flex items-center gap-3 bg-bg-surface border border-border-subtle rounded-lg px-4 py-3"
              >
                {inst.accountAvatarUrl && (
                  <img
                    src={inst.accountAvatarUrl}
                    alt={inst.accountLogin}
                    className="w-7 h-7 rounded-full"
                  />
                )}
                <div>
                  <p className="font-medium text-text-primary text-sm">
                    {inst.accountLogin}
                  </p>
                  <p className="text-xs text-text-muted">
                    {inst.accountType} · {inst.status}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Repositories */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg font-semibold text-text-primary">
            Connected Repositories
          </h2>
          {installations.length > 0 && (
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
            installations={installations}
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
