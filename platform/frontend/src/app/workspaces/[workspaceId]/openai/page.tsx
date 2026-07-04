"use client";

import { useState } from "react";
import {
  useOpenAiConfig,
  useSaveOpenAiConfig,
  useDisconnectOpenAi,
} from "@/hooks/useOpenAiConfig";
import { useConnectorAuthLogin } from "@/hooks/useConnectorAuthLogin";
import { OpenAiAuthMode } from "@/types";
import { Spinner } from "@/components/ui/Spinner";

const inputClass =
  "w-full text-sm border border-border-input rounded-lg px-3 py-2 bg-bg-input text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

const API_URL = process.env.NEXT_PUBLIC_API_URL || "https://mesha-api.onrender.com";

export default function OpenAiPage() {
  const { data: config, isLoading, isError, refetch } = useOpenAiConfig();
  const saveConfig = useSaveOpenAiConfig();
  const disconnect = useDisconnectOpenAi();

  const [showForm, setShowForm] = useState(false);
  const [authMode, setAuthMode] = useState<OpenAiAuthMode>("CHATGPT_TOKEN");
  const [apiKey, setApiKey] = useState("");
  const [accessToken, setAccessToken] = useState("");
  const [refreshToken, setRefreshToken] = useState("");
  const [accountId, setAccountId] = useState("");
  const [model, setModel] = useState("");
  const [showAdvanced, setShowAdvanced] = useState(false);

  const isConnected = !!config;

  function resetForm() {
    setShowForm(false);
    setApiKey("");
    setAccessToken("");
    setRefreshToken("");
    setAccountId("");
    setModel("");
    setShowAdvanced(false);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    await saveConfig.mutateAsync({
      authMode,
      apiKey: authMode === "API_KEY" ? apiKey.trim() : undefined,
      accessToken: authMode === "CHATGPT_TOKEN" ? accessToken.trim() : undefined,
      refreshToken: authMode === "CHATGPT_TOKEN" ? refreshToken.trim() || undefined : undefined,
      accountId: authMode === "CHATGPT_TOKEN" ? accountId.trim() || undefined : undefined,
      model: model.trim() || undefined,
    });
    resetForm();
  }

  async function handleDisconnect() {
    if (!confirm("Disconnect your OpenAI credential? AI drafts will fall back to Blocks or the default provider.")) return;
    await disconnect.mutateAsync();
  }

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
            Failed to load OpenAI configuration
          </p>
          <p className="text-xs text-red-600 dark:text-red-400">Please refresh the page.</p>
        </div>
      </div>
    );
  }

  const canSubmit =
    authMode === "API_KEY" ? apiKey.trim().length > 0 : accessToken.trim().length > 0;

  return (
    <div className="p-6 max-w-3xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text-primary">OpenAI / ChatGPT</h1>
        <p className="text-text-muted mt-1">
          Generate AI ticket drafts with your own OpenAI credential — either your ChatGPT
          subscription (via &quot;Sign in with ChatGPT&quot;) or a standard API key. This credential
          is personal to your account.
        </p>
      </div>

      <section className="mb-8">
        <h2 className="text-lg font-semibold text-text-primary mb-3">Connection Status</h2>
        <div className="bg-bg-surface border border-border-subtle rounded-lg px-4 py-4">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div className="flex items-center gap-3">
              <div className={`w-3 h-3 rounded-full ${isConnected ? "bg-green-500" : "bg-border-subtle"}`} />
              <div>
                {isConnected ? (
                  <>
                    <p className="text-sm font-medium text-text-primary">
                      Connected · {config.authMode === "API_KEY" ? "API key" : "ChatGPT subscription"}
                      {config.status === "expired" ? " (token expired)" : ""}
                    </p>
                    <p className="text-xs text-text-muted mt-0.5">
                      Connected {new Date(config.connectedAt).toLocaleDateString()}
                      {" · "}Last updated {new Date(config.updatedAt).toLocaleString()}
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-sm font-medium text-text-primary">Not connected</p>
                    <p className="text-xs text-text-muted mt-0.5">
                      Connect OpenAI to pick &quot;ChatGPT&quot; in the Generate with AI modal.
                    </p>
                  </>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2 shrink-0">
              {isConnected ? (
                <>
                  <button
                    onClick={() => {
                      setAuthMode(config.authMode);
                      setModel(config.model ?? "");
                      setShowForm((v) => !v);
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-border-subtle text-text-secondary rounded-lg hover:bg-bg-subtle transition-colors"
                  >
                    Update
                  </button>
                  <button
                    onClick={handleDisconnect}
                    disabled={disconnect.isPending}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-red-200 text-red-600 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors disabled:opacity-50"
                  >
                    {disconnect.isPending ? <Spinner size="sm" /> : null}
                    {disconnect.isPending ? "Disconnecting…" : "Disconnect"}
                  </button>
                </>
              ) : (
                <button
                  onClick={() => setShowForm((v) => !v)}
                  className="inline-flex items-center gap-1.5 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent/90 transition-colors"
                >
                  Connect OpenAI
                </button>
              )}
            </div>
          </div>
        </div>

        {showForm && (
          <div className="mt-3 bg-bg-surface border border-border-subtle rounded-lg p-4 space-y-4">
            {/* Mode toggle */}
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setAuthMode("CHATGPT_TOKEN")}
                className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors ${
                  authMode === "CHATGPT_TOKEN"
                    ? "bg-accent text-white border-accent"
                    : "border-border-subtle text-text-secondary hover:bg-bg-subtle"
                }`}
              >
                ChatGPT subscription
              </button>
              <button
                type="button"
                onClick={() => setAuthMode("API_KEY")}
                className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors ${
                  authMode === "API_KEY"
                    ? "bg-accent text-white border-accent"
                    : "border-border-subtle text-text-secondary hover:bg-bg-subtle"
                }`}
              >
                API key
              </button>
            </div>

            {authMode === "API_KEY" ? (
              <form onSubmit={handleSubmit} className="space-y-4">
                <div>
                  <label className="block text-xs font-medium text-text-secondary mb-1">
                    OpenAI API key
                  </label>
                  <input
                    type="password"
                    value={apiKey}
                    onChange={(e) => setApiKey(e.target.value)}
                    placeholder="sk-…"
                    className={inputClass}
                  />
                  <p className="text-xs text-text-muted mt-1">
                    Billed per usage against your OpenAI API credits (not your ChatGPT subscription).
                  </p>
                </div>
                <div>
                  <label className="block text-xs font-medium text-text-secondary mb-1">
                    Model <span className="text-text-tertiary">(optional override)</span>
                  </label>
                  <input
                    type="text"
                    value={model}
                    onChange={(e) => setModel(e.target.value)}
                    placeholder="gpt-4o-mini"
                    className={`${inputClass} font-mono`}
                  />
                </div>
                <FormActions
                  saving={saveConfig.isPending}
                  canSubmit={canSubmit}
                  isConnected={isConnected}
                  onCancel={resetForm}
                  error={saveConfig.isError ? (saveConfig.error as Error)?.message : null}
                />
              </form>
            ) : (
              <ChatGptSignInSection
                apiUrl={API_URL}
                onConnected={() => {
                  refetch();
                }}
                advanced={{
                  show: showAdvanced,
                  toggle: () => setShowAdvanced((v) => !v),
                  accessToken,
                  setAccessToken,
                  refreshToken,
                  setRefreshToken,
                  accountId,
                  setAccountId,
                  model,
                  setModel,
                  onSubmit: handleSubmit,
                  canSubmit,
                  saving: saveConfig.isPending,
                  isConnected,
                  onCancel: resetForm,
                  error: saveConfig.isError ? (saveConfig.error as Error)?.message : null,
                }}
              />
            )}
          </div>
        )}
      </section>

      <section>
        <h2 className="text-lg font-semibold text-text-primary mb-3">How it works</h2>
        <div className="bg-bg-surface border border-border-subtle rounded-lg p-4 space-y-2">
          <p className="text-sm text-text-secondary">
            Once connected, choose <strong>ChatGPT</strong> in the &quot;Generate with AI&quot; modal to
            draft tickets with your own OpenAI account.
          </p>
          <ul className="text-sm text-text-secondary space-y-1 list-disc list-inside">
            <li>
              <strong>ChatGPT subscription</strong> — &quot;Sign in with ChatGPT&quot; runs the OAuth
              login locally and connects your Plus/Pro plan. Mesha refreshes the token automatically.
            </li>
            <li>
              <strong>API key</strong> — a standard <span className="font-mono">sk-…</span> key, billed
              per usage.
            </li>
          </ul>
        </div>
      </section>
    </div>
  );
}

function FormActions({
  saving,
  canSubmit,
  isConnected,
  onCancel,
  error,
}: {
  saving: boolean;
  canSubmit: boolean;
  isConnected: boolean;
  onCancel: () => void;
  error: string | null;
}) {
  return (
    <>
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={saving || !canSubmit}
          className="px-4 py-2 bg-accent text-white text-sm rounded-lg hover:bg-accent/90 disabled:opacity-50 transition-colors"
        >
          {saving ? "Saving…" : isConnected ? "Update" : "Connect"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-sm text-text-secondary rounded-lg hover:bg-bg-subtle transition-colors"
        >
          Cancel
        </button>
      </div>
      {error && <p className="text-xs text-red-500">{error ?? "Failed to save configuration."}</p>}
    </>
  );
}

interface AdvancedProps {
  show: boolean;
  toggle: () => void;
  accessToken: string;
  setAccessToken: (v: string) => void;
  refreshToken: string;
  setRefreshToken: (v: string) => void;
  accountId: string;
  setAccountId: (v: string) => void;
  model: string;
  setModel: (v: string) => void;
  onSubmit: (e: React.FormEvent) => void;
  canSubmit: boolean;
  saving: boolean;
  isConnected: boolean;
  onCancel: () => void;
  error: string | null;
}

/**
 * "Sign in with ChatGPT" — generate a connector token, then run the local helper
 * (chatgpt-login.mjs) which completes the OAuth loopback flow and pushes the
 * resulting tokens back into Mesha. The old ~/.codex/auth.json paste path is gone
 * (Codex removed that file), so manual token entry is kept only as an Advanced fallback.
 */
function ChatGptSignInSection({
  apiUrl,
  onConnected,
  advanced,
}: {
  apiUrl: string;
  onConnected: () => void;
  advanced: AdvancedProps;
}) {
  const { mutate: generateToken, isPending, data: tokenData, error } = useConnectorAuthLogin();
  const [copied, setCopied] = useState<"token" | "command" | null>(null);

  const command = tokenData
    ? `node platform/scripts/chatgpt-login.mjs --token=${tokenData.accessToken} --api-url=${apiUrl}`
    : "";

  function copy(text: string, which: "token" | "command") {
    if (!navigator.clipboard) return;
    navigator.clipboard
      .writeText(text)
      .then(() => {
        setCopied(which);
        setTimeout(() => setCopied(null), 2000);
      })
      .catch(() => {
        /* clipboard denied — the value is still visible for manual copy */
      });
  }

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-border-subtle bg-bg-subtle/40 p-4 space-y-3">
        <div>
          <h3 className="text-sm font-semibold text-text-primary">Sign in with ChatGPT</h3>
          <p className="text-xs text-text-muted mt-1">
            Connects your ChatGPT <strong>subscription</strong> (no API key). Generate a one-time
            token below, then run the login helper on your machine — it opens your browser to sign
            in and sends the credential straight back to Mesha.
          </p>
        </div>

        <button
          type="button"
          onClick={() => generateToken()}
          disabled={isPending}
          className="inline-flex items-center gap-1.5 px-4 py-2 bg-accent text-white rounded-lg text-sm font-medium hover:bg-accent/90 disabled:opacity-50 transition-colors"
        >
          {isPending ? "Generating…" : tokenData ? "Regenerate token" : "Generate sign-in token"}
        </button>

        {error && (
          <p className="text-xs text-red-500">Failed to generate a token. Please try again.</p>
        )}

        {tokenData && (
          <div className="space-y-3 pt-1">
            <ol className="text-xs text-text-secondary space-y-1 list-decimal list-inside">
              <li>Make sure you have Node.js 18+ and this repo checked out.</li>
              <li>Run the command below in a terminal (valid for a limited time):</li>
            </ol>

            <div className="flex gap-2">
              <code className="flex-1 px-3 py-2 rounded-lg bg-bg-input text-text-secondary text-xs font-mono overflow-x-auto break-all">
                {command}
              </code>
              <button
                type="button"
                onClick={() => copy(command, "command")}
                className="px-3 py-2 rounded-lg bg-bg-surface-hover hover:bg-bg-surface-hover/80 text-text-secondary hover:text-text-primary text-sm font-medium transition-colors shrink-0"
              >
                {copied === "command" ? "✓" : "Copy"}
              </button>
            </div>

            <div className="flex items-center gap-2">
              <span className="text-xs text-text-tertiary">Token only:</span>
              <button
                type="button"
                onClick={() => copy(tokenData.accessToken, "token")}
                className="text-xs text-accent hover:underline"
              >
                {copied === "token" ? "Copied ✓" : "Copy token"}
              </button>
            </div>

            <div className="flex items-center justify-between pt-1">
              <p className="text-xs text-text-muted">
                After it prints &quot;ChatGPT connected&quot;, refresh this status.
              </p>
              <button
                type="button"
                onClick={onConnected}
                className="text-xs font-medium text-accent hover:underline"
              >
                Refresh status
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Advanced: manual token entry (fallback if you already have the raw tokens) */}
      <div className="border-t border-border-subtle pt-3">
        <button
          type="button"
          onClick={advanced.toggle}
          className="text-xs font-medium text-text-secondary hover:text-text-primary"
        >
          {advanced.show ? "▾ " : "▸ "}Advanced: enter tokens manually
        </button>

        {advanced.show && (
          <form onSubmit={advanced.onSubmit} className="mt-3 space-y-4">
            <div>
              <label className="block text-xs font-medium text-text-secondary mb-1">Access token</label>
              <input
                type="password"
                value={advanced.accessToken}
                onChange={(e) => advanced.setAccessToken(e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-text-secondary mb-1">
                Refresh token <span className="text-text-tertiary">(recommended — keeps it working)</span>
              </label>
              <input
                type="password"
                value={advanced.refreshToken}
                onChange={(e) => advanced.setRefreshToken(e.target.value)}
                className={inputClass}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-text-secondary mb-1">
                Account ID <span className="text-text-tertiary">(optional)</span>
              </label>
              <input
                type="text"
                value={advanced.accountId}
                onChange={(e) => advanced.setAccountId(e.target.value)}
                className={`${inputClass} font-mono`}
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-text-secondary mb-1">
                Model <span className="text-text-tertiary">(optional override)</span>
              </label>
              <input
                type="text"
                value={advanced.model}
                onChange={(e) => advanced.setModel(e.target.value)}
                placeholder="gpt-5"
                className={`${inputClass} font-mono`}
              />
            </div>
            <FormActions
              saving={advanced.saving}
              canSubmit={advanced.canSubmit}
              isConnected={advanced.isConnected}
              onCancel={advanced.onCancel}
              error={advanced.error}
            />
          </form>
        )}
      </div>
    </div>
  );
}
