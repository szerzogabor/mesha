# Firecracker base VM image

This directory contains the reproducible build pipeline for the Mesha base
Firecracker root filesystem. The generated image is an Alpine Linux ext4 rootfs
with Node.js, npm, and the Claude Code CLI preinstalled.

Generated artifacts are written to `infra/firecracker/base-image/out/` and are
ignored by Git.

## Prerequisites

Install the host tools used by the builder:

- Docker or Podman for the isolated Alpine rootfs build
- `mkfs.ext4` from `e2fsprogs` to create the ext4 filesystem
- `debugfs` from `e2fsprogs` for verification
- `jq` and `sha256sum` for manifest creation and validation

The builder runs the container with `--privileged` so it can mount `proc`,
`sysfs`, and `/dev` while installing and validating packages in the staged
rootfs.

## Build

```sh
./scripts/build-firecracker-base-image.sh
```

By default, the build uses:

- Alpine `3.20.3`
- `@anthropic-ai/claude-code` `2.1.132`
- A 2048 MiB ext4 image named `mesha-firecracker-alpine-rootfs.ext4`

Override pinned versions or sizing when needed:

```sh
ALPINE_VERSION=3.20.3 \
CLAUDE_CODE_VERSION=2.1.132 \
IMAGE_SIZE_MB=2048 \
./scripts/build-firecracker-base-image.sh
```

Use `SOURCE_DATE_EPOCH` to normalize filesystem mtimes for reproducible image
metadata:

```sh
SOURCE_DATE_EPOCH=0 ./scripts/build-firecracker-base-image.sh
```

The build writes:

- `out/mesha-firecracker-alpine-rootfs.ext4` — bootable ext4 root filesystem
- `out/manifest.json` — versions, image size, and image SHA-256 checksum

## Verify

Validate the image checksum, manifest fields, and expected filesystem paths:

```sh
./scripts/verify-firecracker-base-image.sh
```

For executable smoke tests, keep the staging rootfs and pass it to the verifier:

```sh
./scripts/build-firecracker-base-image.sh --keep-staging
./scripts/verify-firecracker-base-image.sh \
  --staging-dir infra/firecracker/base-image/out/rootfs.staging
```

The build itself also chroots into the staged rootfs and runs:

```sh
node --version
npm --version
claude --version
```

That check verifies that the Claude Code CLI starts before the ext4 image is
created.

## Firecracker boot configuration

Use the generated ext4 image as the root block device and boot with a kernel
that supports virtio block devices and serial console output. Recommended kernel
arguments:

```text
console=ttyS0 reboot=k panic=1 pci=off init=/sbin/init root=/dev/vda rw
```

Example Firecracker drive configuration:

```json
{
  "drive_id": "rootfs",
  "path_on_host": "infra/firecracker/base-image/out/mesha-firecracker-alpine-rootfs.ext4",
  "is_root_device": true,
  "is_read_only": false
}
```

At boot, OpenRC starts `/sbin/init`, configures a serial `ttyS0` getty, and runs
`/etc/local.d/mesha-base.start`, which prints Node.js, npm, and Claude Code
versions to the VM console. The working directory for interactive shells is
`/workspace`.
