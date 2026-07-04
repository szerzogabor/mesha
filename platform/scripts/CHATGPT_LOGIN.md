# Connect a ChatGPT subscription to Mesha (no API key)

`chatgpt-login.mjs` connects your **ChatGPT subscription** (Plus/Pro/Team) to Mesha so the
"ChatGPT" AI provider can generate drafts using your plan instead of a billed API key.

It runs the OAuth 2.0 PKCE "Sign in with ChatGPT" flow locally (the same flow the Codex CLI
uses), then pushes the resulting tokens into your Mesha account. Mesha auto-refreshes from
there, so you normally run this only once.

## Usage

1. In the Mesha web app: **Settings → OpenAI / ChatGPT → "Sign in with ChatGPT"**. Generate a
   connector token — the page shows the exact command with the token and API URL filled in.
2. Run it (Node ≥ 18, no npm install needed):

   ```bash
   node platform/scripts/chatgpt-login.mjs --token=mcat_xxxxx --api-url=https://<your-mesha-api>
   ```

3. Your browser opens; sign in with your ChatGPT account. The script exchanges the code for
   tokens and sends them to Mesha. Return to the settings page — it should show **Connected**.

Options: `--model=gpt-5` (optional model override). Env overrides: `MESHA_API_URL`,
`MESHA_CONNECTOR_TOKEN`, and `OPENAI_OAUTH_*` for the OAuth endpoints/client/port.

## ⚠️ Caveats

- This uses OpenAI's Codex OAuth client from a non-Codex tool. That is **unofficial and a gray
  area under OpenAI's Terms** — use it only for your own personal subscription.
- It is **inherently fragile**: OpenAI can change the OAuth endpoints/parameters at any time
  (the removal of `~/.codex/auth.json` was one such change). If sign-in stops working, the OAuth
  constants at the top of `chatgpt-login.mjs` must be re-checked against the current Codex CLI
  source (`github.com/openai/codex`, its login/auth module) — a `redirect_uri`/port mismatch
  fails silently.
- A standard `sk-...` API key remains the stable, supported alternative (Settings → OpenAI →
  "API key"), but it bills per usage rather than using your subscription.
