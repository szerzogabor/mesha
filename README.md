# mesha

Mesha is an MVP Claude Code Session Platform for a single user. The platform is intended to run on a home Windows PC through Ubuntu on WSL2, expose a browser/PWA UI through Cloudflare Tunnel, and host isolated Claude Code sessions in Firecracker microVMs.

## Architecture at a glance

The project structure follows the architect proposal in `claude-code-platform-architecture.docx`:

- **Frontend:** React SPA/PWA with xterm.js for terminal access from a phone browser or Android WebView.
- **Backend:** Single Go orchestrator binary with REST APIs, WebSocket terminal bridging, SQLite state, idle watchers, and VM lifecycle management.
- **Infrastructure:** Caddy reverse proxy, Cloudflare Tunnel, Firecracker microVMs, local NVMe-backed snapshots, overlays, and project storage.

## Top-level project layout

```text
apps/                 User-facing clients: web PWA and optional Android WebView shell.
cmd/orchestrator/     Go backend entrypoint for the single orchestrator binary.
internal/             Backend packages for API, sessions, DB, VM lifecycle, and terminal bridge.
deploy/               Local deployment configuration for Caddy, cloudflared, and systemd.
infra/firecracker/    Firecracker assets, VM image workflow, overlays, snapshots, and vsock notes.
data/                 Local runtime state placeholders; real runtime artifacts are gitignored.
docs/                 Architecture analysis and folder-level implementation plan.
scripts/              Future setup/build helpers for WSL2, images, and local development.
```

See `docs/architecture-analysis.md` for the interpreted architecture and `docs/project-structure.md` for the folder-level responsibilities.
