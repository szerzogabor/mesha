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
    if (!navigator.clipboard) {
      console.warn("Clipboard API not available");
      return;
    }
    navigator.clipboard.writeText(text)
      .then(() => {
        setCopiedIndex(type);
        setTimeout(() => setCopiedIndex(null), 2000);
      })
      .catch((err) => {
        console.error("Failed to copy text: ", err);
      });
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

          <div className="rounded-lg bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 p-4 space-y-2">
            <h4 className="text-sm font-medium text-amber-700 dark:text-amber-300">Important: Setup Flow</h4>
            <div className="text-xs text-amber-600 dark:text-amber-400 space-y-2">
              <p><strong>Step 1: Initial Login</strong></p>
              <p className="ml-2">Run: <code className="font-mono">mesha-connector login --token=&lt;your-clerk-jwt&gt;</code></p>
              <p className="text-[11px] ml-2 italic">Use your Clerk JWT from the web app, not the token above</p>

              <p className="mt-2"><strong>Step 2: Register Agent</strong></p>
              <p className="ml-2">Run: <code className="font-mono">mesha-connector register --executor-type=docker</code></p>

              <p className="mt-2"><strong>Step 3: Poll Sessions</strong></p>
              <p className="ml-2">Run: <code className="font-mono">mesha-connector poll</code></p>

              <div className="mt-4 pt-2 border-t border-amber-200/50 dark:border-amber-800/50">
                <p><strong>Direct API Calls (Advanced)</strong></p>
                <p className="ml-2 mt-1">Use the access token above: <code className="font-mono">curl -H &quot;Authorization: Bearer &lt;access-token&gt;&quot; https://mesha-api.onrender.com/api/agents</code></p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
