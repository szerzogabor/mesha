"use client";

import { useState } from "react";
import { useConnectorAuthLogin } from "@/hooks/useConnectorAuthLogin";

export function ConnectorTokenGenerator() {
  const { mutate: generateToken, isPending, data: tokenData, error } = useConnectorAuthLogin();
  const [copied, setCopied] = useState(false);

  const handleGenerateToken = () => {
    generateToken();
  };

  const copyToClipboard = (text: string) => {
    if (!navigator.clipboard) {
      console.warn("Clipboard API not available");
      return;
    }
    navigator.clipboard.writeText(text)
      .then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      })
      .catch((err) => {
        console.error("Failed to copy text: ", err);
      });
  };

  const formatExpiryTime = (seconds: number) => {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    if (days > 0) {
      return `${days}d ${hours}h`;
    }
    return `${hours}h`;
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
                onClick={() => copyToClipboard(tokenData.accessToken)}
                className="px-3 py-2 rounded-lg bg-bg-surface-hover hover:bg-bg-surface-hover/80 text-text-secondary hover:text-text-primary text-sm font-medium transition-colors"
              >
                {copied ? "✓" : "Copy"}
              </button>
            </div>
            <p className="text-xs text-text-tertiary">
              Use this with: <code className="font-mono">mesha-connector login --token=&lt;token&gt;</code>
            </p>
          </div>

          <div className="rounded-lg bg-bg-surface-hover/50 border border-border-default p-4 space-y-2">
            <h4 className="text-sm font-medium text-text-primary">Setup Flow</h4>
            <div className="text-xs text-text-tertiary space-y-2">
              <p><strong>Step 1: Login</strong></p>
              <p className="ml-2">Run: <code className="font-mono">mesha-connector login --token=&lt;token-above&gt;</code></p>

              <p className="mt-2"><strong>Step 2: Register Agent</strong></p>
              <p className="ml-2">Run: <code className="font-mono">mesha-connector register --executor-type=docker</code></p>

              <p className="mt-2"><strong>Step 3: Poll Sessions</strong></p>
              <p className="ml-2">Run: <code className="font-mono">mesha-connector poll</code></p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
