# infra/firecracker

Firecracker image assets live here. See `base-image/README.md` for the Alpine
root filesystem build pipeline.

## Runtime layout

Generated Firecracker artifacts are intentionally gitignored. Local deployments
should use this structure:

```text
infra/firecracker/base-image/out/mesha-firecracker-alpine-rootfs.ext4  Shared rootfs image.
infra/firecracker/base-image/mnt/                                      Read-only lowerdir mount.
infra/firecracker/kernel/                                             Firecracker kernel images.
infra/firecracker/overlays/<session-id>/upper/                        Session writable layer.
infra/firecracker/overlays/<session-id>/work/                         OverlayFS workdir.
infra/firecracker/overlays/<session-id>/merged/                       Session merged root.
infra/firecracker/snapshots/                                          Firecracker memory/device snapshots.
```

The orchestrator should mount the base image once read-only, then create a
separate OverlayFS upper/work/merged directory for every session. Session cleanup
should remove overlay directories whose IDs are no longer active in the SQLite
session store.
