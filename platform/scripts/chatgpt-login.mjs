#!/usr/bin/env node
// @ts-nocheck
/*
 * chatgpt-login.mjs — connect a ChatGPT *subscription* (not an API key) to Mesha.
 *
 * Runs the OAuth 2.0 PKCE "Sign in with ChatGPT" flow (the same one the Codex CLI
 * uses) on your machine, then pushes the resulting access/refresh tokens straight
 * into Mesha's per-user config via a connector token. Mesha refreshes the token
 * from there, so you only run this once (until you disconnect / revoke).
 *
 * Usage:
 *   node chatgpt-login.mjs --token=mcat_xxx [--api-url=https://mesha-api...] [--model=gpt-5]
 *
 * Get the connector token (`mcat_...`) from the Mesha web app:
 *   Settings → OpenAI / ChatGPT → "Sign in with ChatGPT". The page shows the exact
 *   command with the token and --api-url pre-filled.
 *
 * Requires Node >= 18 (global fetch + Web Crypto). Zero npm dependencies.
 *
 * ⚠️  IMPORTANT / CAVEATS
 *  - This uses the Codex OAuth client from a non-Codex tool. That is UNOFFICIAL and a
 *    gray area under OpenAI's Terms — use it only for your own personal subscription.
 *  - It is inherently FRAGILE: OpenAI can change these endpoints/params at any time
 *    (the removal of ~/.codex/auth.json is exactly such a change). If sign-in fails,
 *    the OAuth constants below (authorize URL, client id, redirect port/path, scopes,
 *    extra params) must be re-checked against the CURRENT Codex CLI source
 *    (github.com/openai/codex, its login/auth module). A redirect_uri mismatch fails
 *    silently at the browser step.
 */

import http from "node:http";
import crypto from "node:crypto";
import { spawn } from "node:child_process";

// --- OAuth constants (env-overridable; verify against current Codex source) --------
const ISSUER = process.env.OPENAI_OAUTH_ISSUER || "https://auth.openai.com";
const AUTHORIZE_URL = `${ISSUER}/oauth/authorize`;
const TOKEN_URL = process.env.OPENAI_OAUTH_TOKEN_URL || `${ISSUER}/oauth/token`;
const CLIENT_ID = process.env.OPENAI_OAUTH_CLIENT_ID || "app_EMoamEEZ73f0CkXaXp7hrann";
const REDIRECT_PORT = Number(process.env.OPENAI_OAUTH_REDIRECT_PORT || 1455);
const REDIRECT_PATH = process.env.OPENAI_OAUTH_REDIRECT_PATH || "/auth/callback";
const REDIRECT_URI = `http://localhost:${REDIRECT_PORT}${REDIRECT_PATH}`;
const SCOPE = process.env.OPENAI_OAUTH_SCOPE || "openid profile email offline_access";

const DEFAULT_API_URL = process.env.MESHA_API_URL || "https://mesha-api.onrender.com";

// --- args --------------------------------------------------------------------------
function parseArgs(argv) {
  const out = {};
  for (const arg of argv.slice(2)) {
    const m = arg.match(/^--([^=]+)=(.*)$/);
    if (m) out[m[1]] = m[2];
    else if (arg.startsWith("--")) out[arg.slice(2)] = true;
  }
  return out;
}

const args = parseArgs(process.argv);
const connectorToken = typeof args.token === "string" ? args.token : process.env.MESHA_CONNECTOR_TOKEN;
const rawApiUrl = typeof args["api-url"] === "string" ? args["api-url"] : DEFAULT_API_URL;
const apiUrl = rawApiUrl.replace(/\/$/, "");
const model = typeof args.model === "string" ? args.model : process.env.OPENAI_CHATGPT_MODEL || "gpt-5";

if (!connectorToken) {
  console.error(
    "Missing --token. Generate a connector token in Mesha (Settings → OpenAI / ChatGPT)\n" +
      "and run:  node chatgpt-login.mjs --token=mcat_... --api-url=" + DEFAULT_API_URL,
  );
  process.exit(1);
}
if (!/^mcat_/.test(connectorToken)) {
  console.error("The --token value should be a Mesha connector token (starts with 'mcat_').");
  process.exit(1);
}

// --- PKCE helpers ------------------------------------------------------------------
const base64url = (buf) =>
  buf.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

const codeVerifier = base64url(crypto.randomBytes(64));
const codeChallenge = base64url(crypto.createHash("sha256").update(codeVerifier).digest());
const state = base64url(crypto.randomBytes(24));

function buildAuthorizeUrl() {
  const p = new URLSearchParams({
    response_type: "code",
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: SCOPE,
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
    state,
    // Codex-specific extras — harmless if ignored, but keep aligned with Codex source.
    id_token_add_organizations: "true",
    codex_cli_simplified_flow: "true",
  });
  return `${AUTHORIZE_URL}?${p.toString()}`;
}

function openBrowser(url) {
  const platform = process.platform;
  // spawn (no shell) instead of exec — avoids any shell metacharacter handling of the URL.
  const child =
    platform === "darwin" ? spawn("open", [url])
    : platform === "win32" ? spawn("cmd", ["/c", "start", "", url])
    : spawn("xdg-open", [url]);
  child.on("error", () => {
    /* Non-fatal (e.g. command not found): the URL is also printed for manual opening. */
  });
}

// --- JWT claim decode (to derive the ChatGPT account id) ---------------------------
function decodeJwtPayload(jwt) {
  try {
    const part = jwt.split(".")[1];
    if (!part) return null;
    const json = Buffer.from(part, "base64url").toString("utf8");
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function extractAccountId(tokens) {
  for (const jwt of [tokens.id_token, tokens.access_token]) {
    if (!jwt) continue;
    const claims = decodeJwtPayload(jwt);
    const auth = claims && claims["https://api.openai.com/auth"];
    const id = auth && (auth.chatgpt_account_id || auth.chatgpt_user_id);
    if (id) return id;
  }
  return null;
}

// --- token exchange ----------------------------------------------------------------
async function exchangeCodeForTokens(code) {
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    code,
    redirect_uri: REDIRECT_URI,
    client_id: CLIENT_ID,
    code_verifier: codeVerifier,
  });
  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString(),
  });
  if (!res.ok) {
    throw new Error(`token exchange failed (HTTP ${res.status}): ${await res.text()}`);
  }
  return res.json();
}

// --- push to Mesha -----------------------------------------------------------------
async function pushToMesha({ accessToken, refreshToken, accountId }) {
  const res = await fetch(`${apiUrl}/api/connector/openai/config`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${connectorToken}`,
    },
    body: JSON.stringify({
      authMode: "CHATGPT_TOKEN",
      accessToken,
      refreshToken,
      accountId,
      model,
    }),
  });
  if (!res.ok) {
    throw new Error(`Mesha rejected the config (HTTP ${res.status}): ${await res.text()}`);
  }
}

// --- loopback server that waits for the OAuth redirect -----------------------------
function waitForCode() {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, `http://localhost:${REDIRECT_PORT}`);
      if (url.pathname !== REDIRECT_PATH) {
        res.writeHead(404).end("Not found");
        return;
      }
      const code = url.searchParams.get("code");
      const returnedState = url.searchParams.get("state");
      const error = url.searchParams.get("error");
      // Ignore stray hits (browser pre-fetch, extensions, double-clicks) so we don't
      // tear the server down before the real redirect arrives.
      if (!code && !error) {
        res.writeHead(400).end("Bad Request: missing code or error");
        return;
      }
      res.writeHead(200, { "Content-Type": "text/html" });
      res.end(
        `<html><body style="font-family:sans-serif;padding:2rem">` +
          `<h2>${error ? "Sign-in failed" : "Signed in to ChatGPT ✓"}</h2>` +
          `<p>${error ? error : "You can close this tab and return to Mesha."}</p>` +
          `</body></html>`,
      );
      server.close();
      if (error) return reject(new Error(`authorization error: ${error}`));
      if (!code) return reject(new Error("no authorization code in callback"));
      if (returnedState !== state) return reject(new Error("state mismatch (possible CSRF)"));
      resolve(code);
    });
    server.on("error", reject);
    server.listen(REDIRECT_PORT, "127.0.0.1");
  });
}

// --- main --------------------------------------------------------------------------
async function main() {
  const authorizeUrl = buildAuthorizeUrl();
  console.log("\nOpening your browser to sign in with ChatGPT…");
  console.log("If it doesn't open, paste this URL manually:\n\n  " + authorizeUrl + "\n");
  openBrowser(authorizeUrl);

  const code = await waitForCode();
  console.log("Got authorization code, exchanging for tokens…");
  const tokens = await exchangeCodeForTokens(code);

  const accessToken = tokens.access_token;
  const refreshToken = tokens.refresh_token || null;
  const accountId = extractAccountId(tokens);
  if (!accessToken) throw new Error("no access_token in token response");
  if (!refreshToken) {
    console.warn("⚠  No refresh token returned — Mesha won't be able to auto-renew; you may need to re-run later.");
  }
  if (!accountId) {
    console.warn("⚠  Could not derive the ChatGPT account id from the token; continuing without it.");
  }

  console.log("Sending the credential to Mesha…");
  await pushToMesha({ accessToken, refreshToken, accountId });

  console.log("\n✓ ChatGPT connected to Mesha. Return to the settings page — it should show \"Connected\".\n");
}

main().catch((err) => {
  console.error("\n✗ Sign-in failed: " + (err && err.message ? err.message : err));
  console.error(
    "If this keeps failing, the OpenAI OAuth flow may have changed — re-check the constants at the top of\n" +
      "this script against the current Codex CLI source.",
  );
  process.exit(1);
});
