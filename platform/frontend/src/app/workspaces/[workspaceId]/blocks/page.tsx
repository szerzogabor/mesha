"use client";

import { use, useState } from "react";
import { useBlocksConfig, useSaveBlocksConfig, useDisconnectBlocks } from "@/hooks/useBlocksConfig";
import { Spinner } from "@/components/ui/Spinner";

export default function BlocksPage({
  params,
}: {
  params: Promise<{ workspaceId: string }>;
}) {
  const { workspaceId } = use(params);

  const { data: config, isLoading, isError } = useBlocksConfig(workspaceId);
  const saveConfig = useSaveBlocksConfig(workspaceId);
  const disconnectBlocks = useDisconnectBlocks(workspaceId);

  const [apiKey, setApiKey] = useState("");
  const [showConnectForm, setShowConnectForm] = useState(false);

  const isConnected = !!config;

  const handleConnect = async (e: React.FormEvent) => {
    e.preventDefault();
    await saveConfig.mutateAsync(apiKey);
    setApiKey("");
    setShowConnectForm(false);
  };

  const handleDisconnect = async () => {
    if (!confirm("Are you sure you want to disconnect Blocks from this workspace? The 'Assign to Blocks' button will stop appearing on issues.")) return;
    await disconnectBlocks.mutateAsync();
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Spinner size="lg" className="text-accent" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-6 max-w-3xl mx-auto">
        <div className="rounded-lg border border-red-200 bg-red-50 dark:bg-red-900/20 dark:border-red-800 p-6 text-center">
          <p className="text-sm font-medium text-red-700 dark:text-red-300 mb-1">
            Failed to load Blocks configuration
          </p>
          <p className="text-xs text-red-600 dark:text-red-400">
            There was an error fetching your Blocks settings. Please refresh the page.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text-primary">Blocks Integration</h1>
        <p className="text-text-muted mt-1">
          Connect your workspace to Blocks AI to delegate issues for automated implementation.
        </p>
      </div>

      {/* Connection Status */}
      <section className="mb-8">
        <h2 className="text-lg font-semibold text-text-primary mb-3">Connection Status</h2>
        <div className="bg-bg-surface border border-border-subtle rounded-lg px-4 py-4">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div className="flex items-center gap-3">
              <div className={`w-3 h-3 rounded-full ${isConnected ? "bg-green-500" : "bg-border-subtle"}`} />
              <div>
                {isConnected ? (
                  <>
                    <p className="text-sm font-medium text-text-primary">Connected</p>
                    <p className="text-xs text-text-muted mt-0.5">
                      Connected {new Date(config.connectedAt).toLocaleDateString()}
                      {" · "}Last updated {new Date(config.updatedAt).toLocaleString()}
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-sm font-medium text-text-primary">Not connected</p>
                    <p className="text-xs text-text-muted mt-0.5">
                      Connect Blocks to enable AI-powered issue delegation.
                    </p>
                  </>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              {isConnected ? (
                <>
                  <button
                    onClick={() => setShowConnectForm((v) => !v)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border-subtle text-text-secondary rounded-lg hover:bg-bg-subtle transition-colors"
                  >
                    Update API Key
                  </button>
                  <button
                    onClick={handleDisconnect}
                    disabled={disconnectBlocks.isPending}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-red-200 text-red-600 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors disabled:opacity-50"
                  >
                    {disconnectBlocks.isPending ? <Spinner size="sm" /> : null}
                    {disconnectBlocks.isPending ? "Disconnecting…" : "Disconnect"}
                  </button>
                </>
              ) : (
                <button
                  onClick={() => setShowConnectForm((v) => !v)}
                  className="inline-flex items-center gap-1.5 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent/90 transition-colors"
                >
                  Connect Blocks
                </button>
              )}
            </div>
          </div>

          {disconnectBlocks.isError && (
            <div className="mt-3 p-2.5 bg-red-50 border border-red-200 rounded-lg dark:bg-red-900/20 dark:border-red-800">
              <p className="text-xs text-red-700 dark:text-red-300">
                Failed to disconnect. Please try again.
              </p>
            </div>
          )}
        </div>

        {/* Connect / Update form */}
        {showConnectForm && (
          <form
            onSubmit={handleConnect}
            className="mt-3 bg-bg-surface border border-border-subtle rounded-lg p-4 space-y-3"
          >
            <div>
              <label className="block text-xs font-medium text-text-secondary mb-1">
                Blocks API Key
              </label>
              <input
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="blocks_…"
                required
                className="w-full text-sm border border-border-input rounded-lg px-3 py-2 bg-bg-input text-text-primary focus:outline-none focus:ring-2 focus:ring-accent"
              />
              <p className="text-xs text-text-muted mt-1">
                Find your API key in the{" "}
                <a
                  href="https://blocks.team"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-accent hover:underline"
                >
                  Blocks workspace settings
                </a>
                .
              </p>
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={saveConfig.isPending || !apiKey.trim()}
                className="px-4 py-2 bg-accent text-white text-sm rounded-lg hover:bg-accent/90 disabled:opacity-50 transition-colors"
              >
                {saveConfig.isPending ? "Saving…" : isConnected ? "Update" : "Connect"}
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowConnectForm(false);
                  setApiKey("");
                }}
                className="px-4 py-2 text-sm text-text-secondary rounded-lg hover:bg-bg-subtle transition-colors"
              >
                Cancel
              </button>
            </div>
            {saveConfig.isError && (
              <p className="text-xs text-red-500">
                {(saveConfig.error as Error)?.message ?? "Failed to save configuration."}
              </p>
            )}
          </form>
        )}
      </section>

      {/* About Blocks */}
      <section>
        <h2 className="text-lg font-semibold text-text-primary mb-3">About Blocks</h2>
        <div className="bg-bg-surface border border-border-subtle rounded-lg p-4 space-y-3">
          <p className="text-sm text-text-secondary">
            Blocks is an AI coding agent that can pick up issues, plan implementations, write code, and open pull requests automatically.
          </p>
          <ul className="text-sm text-text-secondary space-y-1 list-disc list-inside">
            <li>Delegate issues directly from the issue detail page</li>
            <li>Track real-time execution progress (Planning → Executing → PR Opened)</li>
            <li>Review and merge AI-generated pull requests</li>
          </ul>
          <a
            href="https://blocks.team"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 text-sm text-accent hover:underline"
          >
            Create a Blocks account
            <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
            </svg>
          </a>
        </div>
      </section>
    </div>
  );
}
