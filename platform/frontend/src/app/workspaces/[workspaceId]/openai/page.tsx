"use client";

import { useState } from "react";
import {
  useOpenAiConfig,
  useSaveOpenAiConfig,
  useDisconnectOpenAi,
} from "@/hooks/useOpenAiConfig";
import { OpenAiAuthMode } from "@/types";
import { Spinner } from "@/components/ui/Spinner";

const inputClass =
  "w-full text-sm border border-border-input rounded-lg px-3 py-2 bg-bg-input text-text-primary focus:outline-none focus:ring-2 focus:ring-accent";

/**
 * Attempt to pull ChatGPT OAuth fields out of a pasted ~/.codex/auth.json blob.
 * Returns nulls for anything it can't find so the form fields stay editable.
 */
function parseCodexAuthJson(raw: string): {
  accessToken?: string;
  refreshToken?: string;
  accountId?: string;
} | null {
  try {
    const json = JSON.parse(raw);
    const tokens = json.tokens ?? json;
    return {
      accessToken: tokens.access_token ?? undefined,
      refreshToken: tokens.refresh_token ?? undefined,
      accountId: tokens.account_id ?? undefined,
    };
  } catch {
    return null;
  }
}

export default function OpenAiPage() {
  const { data: config, isLoading, isError } = useOpenAiConfig();
  const saveConfig = useSaveOpenAiConfig();
  const disconnect = useDisconnectOpenAi();

  const [showForm, setShowForm] = useState(false);
  const [authMode, setAuthMode] = useState<OpenAiAuthMode>("CHATGPT_TOKEN");
  const [apiKey, setApiKey] = useState("");
  const [accessToken, setAccessToken] = useState("");
  const [refreshToken, setRefreshToken] = useState("");
  const [accountId, setAccountId] = useState("");
  const [model, setModel] = useState("");
  const [pasteError, setPasteError] = useState<string | null>(null);

  const isConnected = !!config;

  function resetForm() {
    setShowForm(false);
    setApiKey("");
    setAccessToken("");
    setRefreshToken("");
    setAccountId("");
    setModel("");
    setPasteError(null);
  }

  function handlePasteAuthJson(raw: string) {
    setPasteError(null);
    if (!raw.trim()) return;
    const parsed = parseCodexAuthJson(raw);
    if (!parsed || !parsed.accessToken) {
      setPasteError("Couldn't find an access token in that JSON. Paste the contents of ~/.codex/auth.json.");
      return;
    }
    setAccessToken(parsed.accessToken);
    if (parsed.refreshToken) setRefreshToken(parsed.refreshToken);
    if (parsed.accountId) setAccountId(parsed.accountId);
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
          Generate AI ticket drafts with your own OpenAI credential — either a standard API key or
          your ChatGPT subscription token from Codex. This credential is personal to your account.
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
          <form
            onSubmit={handleSubmit}
            className="mt-3 bg-bg-surface border border-border-subtle rounded-lg p-4 space-y-4"
          >
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
            ) : (
              <>
                <div>
                  <label className="block text-xs font-medium text-text-secondary mb-1">
                    Paste your <span className="font-mono">~/.codex/auth.json</span>
                  </label>
                  <textarea
                    rows={4}
                    onChange={(e) => handlePasteAuthJson(e.target.value)}
                    placeholder='{ "tokens": { "access_token": "…", "refresh_token": "…", "account_id": "…" } }'
                    className={`${inputClass} font-mono resize-none`}
                  />
                  <p className="text-xs text-text-muted mt-1">
                    We extract the tokens below in your browser — the raw JSON is not sent anywhere.
                    Find this file where Codex desktop / CLI stores its login.
                  </p>
                  {pasteError && <p className="text-xs text-red-500 mt-1">{pasteError}</p>}
                </div>
                <div>
                  <label className="block text-xs font-medium text-text-secondary mb-1">Access token</label>
                  <input
                    type="password"
                    value={accessToken}
                    onChange={(e) => setAccessToken(e.target.value)}
                    className={inputClass}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-text-secondary mb-1">
                    Refresh token <span className="text-text-tertiary">(recommended — keeps it working)</span>
                  </label>
                  <input
                    type="password"
                    value={refreshToken}
                    onChange={(e) => setRefreshToken(e.target.value)}
                    className={inputClass}
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-text-secondary mb-1">
                    Account ID <span className="text-text-tertiary">(optional)</span>
                  </label>
                  <input
                    type="text"
                    value={accountId}
                    onChange={(e) => setAccountId(e.target.value)}
                    className={`${inputClass} font-mono`}
                  />
                </div>
              </>
            )}

            <div>
              <label className="block text-xs font-medium text-text-secondary mb-1">
                Model <span className="text-text-tertiary">(optional override)</span>
              </label>
              <input
                type="text"
                value={model}
                onChange={(e) => setModel(e.target.value)}
                placeholder={authMode === "API_KEY" ? "gpt-4o-mini" : "gpt-5"}
                className={`${inputClass} font-mono`}
              />
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                disabled={saveConfig.isPending || !canSubmit}
                className="px-4 py-2 bg-accent text-white text-sm rounded-lg hover:bg-accent/90 disabled:opacity-50 transition-colors"
              >
                {saveConfig.isPending ? "Saving…" : isConnected ? "Update" : "Connect"}
              </button>
              <button
                type="button"
                onClick={resetForm}
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

      <section>
        <h2 className="text-lg font-semibold text-text-primary mb-3">How it works</h2>
        <div className="bg-bg-surface border border-border-subtle rounded-lg p-4 space-y-2">
          <p className="text-sm text-text-secondary">
            Once connected, choose <strong>ChatGPT</strong> in the &quot;Generate with AI&quot; modal to
            draft tickets with your own OpenAI account.
          </p>
          <ul className="text-sm text-text-secondary space-y-1 list-disc list-inside">
            <li>
              <strong>ChatGPT subscription</strong> — uses your Plus/Pro plan via the token from Codex.
              Add the refresh token so it renews automatically.
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
