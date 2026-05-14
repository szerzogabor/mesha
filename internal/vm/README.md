# internal/vm

`internal/vm` owns host-side VM lifecycle helpers. The current implementation
provides the OverlayFS session filesystem manager used by the orchestrator before
Firecracker starts a microVM.

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
