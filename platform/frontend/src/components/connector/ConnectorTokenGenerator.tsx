"use client";

import { useState } from "react";
import { useConnectorAuthLogin } from "@/hooks/useConnectorAuthLogin";

export function ConnectorTokenGenerator() {
  const { mutate: generateToken, isPending, data: tokenData, error } = useConnectorAuthLogin();
  const [copiedIndex, setCopiedIndex] = useState<"access" | "refresh" | null>(null);

  const handleGenerateToken = () => {
    generateToken();
  };

  const copyToClipboard = (text: string, type: "access" | "refresh") => {
    navigator.clipboard.writeText(text);
    setCopiedIndex(type);
    setTimeout(() => setCopiedIndex(null), 2000);
  };

  const formatExpiryTime = (seconds: number) => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  };

  return (
    <div className="rounded-lg border border-border-default bg-bg-surface p-6 space-y-4">
      <div className="space-y-2">
        <h3 className="text-lg font-semibold text-text-primary">Connector Access Token</h3>
        <p className="text-sm text-text-tertiary">
          Generate a token to authenticate your Mesha connector CLI with the backend.
        </p>
      </div>

      <button
        onClick={handleGenerateToken}
        disabled={isPending}
        className="inline-flex items-center px-4 py-2 rounded-lg bg-accent hover:bg-accent/90 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium text-sm transition-colors"
      >
        {isPending ? "Generating..." : "Generate Token"}
      </button>

      {error && (
        <div className="rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-4">
          <p className="text-sm text-red-700 dark:text-red-300">
            Failed to generate token. Please try again.
          </p>
        </div>
      )}

      {tokenData && (
        <div className="space-y-4 pt-4 border-t border-border-default">
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium text-text-primary">Access Token</label>
              <span className="text-xs text-text-tertiary">
                Expires in {formatExpiryTime(tokenData.expiresIn)}
              </span>
            </div>
            <div className="flex gap-2">
              <code className="flex-1 px-3 py-2 rounded-lg bg-bg-surface-hover text-text-secondary text-xs font-mono overflow-x-auto break-all">
                {tokenData.accessToken}
              </code>
              <button
                onClick={() => copyToClipboard(tokenData.accessToken, "access")}
                className="px-3 py-2 rounded-lg bg-bg-surface-hover hover:bg-bg-surface-hover/80 text-text-secondary hover:text-text-primary text-sm font-medium transition-colors"
              >
                {copiedIndex === "access" ? "✓" : "Copy"}
              </button>
            </div>
            <p className="text-xs text-text-tertiary">
              Use this with: <code className="font-mono">mesha-connector login --token=&lt;token&gt;</code>
            </p>
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium text-text-primary">Refresh Token</label>
            <div className="flex gap-2">
              <code className="flex-1 px-3 py-2 rounded-lg bg-bg-surface-hover text-text-secondary text-xs font-mono overflow-x-auto break-all">
                {tokenData.refreshToken}
              </code>
              <button
                onClick={() => copyToClipboard(tokenData.refreshToken, "refresh")}
                className="px-3 py-2 rounded-lg bg-bg-surface-hover hover:bg-bg-surface-hover/80 text-text-secondary hover:text-text-primary text-sm font-medium transition-colors"
              >
                {copiedIndex === "refresh" ? "✓" : "Copy"}
              </button>
            </div>
            <p className="text-xs text-text-tertiary">
              Kept locally for automatic token refresh (30 days)
            </p>
          </div>

          <div className="rounded-lg bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 p-4 space-y-2">
            <h4 className="text-sm font-medium text-blue-700 dark:text-blue-300">Quick Start</h4>
            <ol className="text-xs text-blue-600 dark:text-blue-400 space-y-1 list-decimal list-inside">
              <li>Copy the access token above</li>
              <li>Run: <code className="font-mono">mesha-connector login --token=&lt;token&gt;</code></li>
              <li>Register: <code className="font-mono">mesha-connector register --executor-type=docker</code></li>
              <li>Start polling: <code className="font-mono">mesha-connector poll</code></li>
            </ol>
          </div>
        </div>
      )}
    </div>
  );
}
