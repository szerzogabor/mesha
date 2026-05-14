# Architecture analysis

This document captures the implementation-relevant interpretation of `claude-code-platform-architecture.docx`.

## MVP scope

The MVP is a single-user Claude Code Session Platform designed for home PC deployment with no hosting cost beyond electricity. The core capabilities are:

1. Launch multiple isolated Claude Code sessions.
2. Pause sessions by snapshotting VM memory and per-session filesystem changes.
3. Resume sessions later from a phone browser, PWA, or Android WebView.
4. Keep the public access path simple by using Cloudflare Tunnel instead of inbound ports.

## Logical layers

### Frontend layer

- React SPA/PWA served as static assets.
- xterm.js terminal panels connected over WebSocket.
- Session controls for create, pause, resume, and destroy.
- Optional Android WebView wrapper that loads the same PWA URL.

### Backend layer

- Single Go orchestrator binary.
- REST API for session lifecycle operations.
- WebSocket handler that bridges browser terminal traffic to the VM terminal channel.
- SQLite metadata store for session records and state transitions.
- Idle watcher that auto-pauses inactive sessions.
- Firecracker lifecycle adapter for start, snapshot, restore, and destroy.
- Pre-warm pool support for one or two idle VMs in the MVP.

### Infrastructure layer

- Cloudflare Tunnel provides public HTTPS and WebSocket ingress without router configuration.
- Caddy receives local traffic from cloudflared and routes static frontend, `/api/*`, and `/ws/*` requests.
- Firecracker microVMs run inside WSL2 when `/dev/kvm` is available.
- OverlayFS stores per-session filesystem changes separately from the shared base image.
- Local NVMe storage holds SQLite data, project files, overlays, and snapshots.

## Session lifecycle

The folder structure is organized around the proposed state machine:

```text
Requested -> Provisioning -> Running -> Pausing -> Paused -> Resuming -> Running
                                      \-> Destroyed
Paused -----------------------------------------------> Destroyed
```

The orchestrator owns the state transitions and persists them in SQLite so that the UI can recover after browser refreshes or orchestrator restarts.

## MVP simplifications

The initial project intentionally excludes multi-user auth, billing, distributed workers, Redis/NATS, PostgreSQL, object storage, and cloud autoscaling. These are represented by clear package and directory boundaries so they can be added later without reshaping the repository.

## Primary implementation risks

- Firecracker requires `/dev/kvm`; if unavailable in WSL2, the project needs a Docker + CRIU fallback path.
- Snapshot and restore correctness depends on aligning Firecracker memory snapshots with OverlayFS upper layer persistence.
- WebSocket-to-vsock terminal bridging needs backpressure handling to avoid terminal stalls on mobile networks.
- Runtime artifacts can become large, so the repository keeps only placeholders and ignores generated images, overlays, and snapshots.
