# Project structure

## `apps/`

Client applications live under `apps/`.

- `apps/web/` is the React SPA/PWA. It will own session list screens, xterm.js terminals, controls, offline indicators, and PWA assets.
- `apps/android/` is reserved for the optional native Android WebView shell. It should stay thin and reuse the hosted PWA.

## `cmd/orchestrator/`

The Go executable entrypoint belongs here. It should wire configuration, logging, database access, HTTP routes, WebSocket handling, idle watchers, and VM lifecycle services into one deployable binary.

## `internal/`

Backend implementation packages are split by responsibility:

- `internal/api/` for REST routes, request/response DTOs, middleware, and health checks.
- `internal/config/` for environment and file-based configuration.
- `internal/db/` for SQLite connections, migrations, and repositories.
- `internal/session/` for the session state machine and lifecycle coordination.
- `internal/idle/` for inactivity tracking and auto-pause triggers.
- `internal/firecracker/` for Firecracker API client code and Unix socket interactions.
- `internal/vm/` for higher-level VM pool, snapshot, restore, and OverlayFS orchestration. It currently includes an OverlayFS manager that mounts a shared read-only base rootfs and creates isolated per-session upper/work/merged directories.
- `internal/terminal/` for WebSocket-to-vsock terminal bridging.

## `deploy/`

Deployment templates live here:

- `deploy/caddy/` for local reverse proxy configuration.
- `deploy/cloudflared/` for Cloudflare Tunnel examples.
- `deploy/systemd/` for service unit templates used inside WSL2 or Linux.

## `infra/firecracker/`

Firecracker-specific runtime assets and workflows live here:

- `base-image/` for shared Alpine rootfs image build outputs.
- `kernel/` for Firecracker-compatible kernels.
- `overlays/` for per-session OverlayFS upper, work, and merged directories managed by `internal/vm`.
- `snapshots/` for memory and device snapshots.
- `vsock/` for notes or helpers around host-to-guest terminal channels.

Generated images, kernels, overlays, and snapshots are intentionally gitignored.

## `data/`

Local runtime data placeholders live here:

- `data/db/` for the SQLite database file.
- `data/projects/` for user project directories mounted into sessions.
- `data/snapshots/` for local snapshot storage if the implementation keeps it separate from `infra/firecracker/snapshots/`.

Real runtime data is intentionally gitignored.

## `scripts/`

Helper scripts for setup and local operations will live here, such as WSL2 prerequisites, base image builds, local development startup, and `/dev/kvm` checks.
