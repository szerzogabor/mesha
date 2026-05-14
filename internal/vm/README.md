# internal/vm

`internal/vm` owns host-side VM lifecycle helpers. The current implementation
provides the OverlayFS session filesystem manager used by the orchestrator before
Firecracker starts a microVM, plus a generic pre-warmed VM pool used to keep idle
Firecracker instances ready for low-latency session startup.

## Pre-warmed VM pool

`WarmPool` maintains a configurable number of idle VMs through a
`WarmVMFactory`. Production wiring can implement the factory with Firecracker
boot and teardown calls, while unit tests can use an in-memory fake.

The pool is safe for concurrent callers:

- `Start` asynchronously creates idle VMs until `DesiredIdle` is reached.
- `Allocate` hands out a single idle VM to one caller, falls back to on-demand
  creation when the idle pool is temporarily empty, and triggers automatic
  replenishment after consuming a warm VM.
- `Release` either returns a reusable VM to the idle pool or destroys it when it
  is dirty, not reusable, or would exceed the configured idle target.
- failed background pre-warm attempts are retried after `ReplenishInterval`.
- `Close` cancels pending creates, waits for in-flight pool operations, and
  destroys idle VMs so shutdown does not leak pre-warmed instances.

## OverlayFS layout

The manager builds a lower/upper/work/merged layout that keeps the Firecracker
base root filesystem immutable while giving each session its own writable layer:

```text
<base-mount-dir>/                  Shared read-only lowerdir mounted once.
<overlay-root>/<session-id>/upper/  Per-session writable copy-on-write changes.
<overlay-root>/<session-id>/work/   OverlayFS workdir; must share a filesystem with upper/.
<overlay-root>/<session-id>/merged/ Unified session root passed to Firecracker tooling.
```

`OverlayFSManager.EnsureBaseMounted` accepts either an ext4 base image file or an
already-populated rootfs directory. Image files are mounted with `loop,ro`;
directories are mounted with a bind mount followed by a read-only remount. The
per-session merged mount uses OverlayFS options in the form
`lowerdir=<base>,upperdir=<session>/upper,workdir=<session>/work` with `nosuid`
and `nodev` safety flags.

## Cleanup

`CleanupOrphanedLayers` receives the set of active session IDs from the session
store. Any directory under the overlay root that is not active is unmounted if
needed and then deleted, removing stale upper/work/merged layers left behind by
crashes or interrupted destroys.
