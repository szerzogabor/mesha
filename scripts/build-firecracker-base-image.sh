#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR_DEFAULT="$REPO_ROOT/infra/firecracker/base-image/out"

ALPINE_VERSION="${ALPINE_VERSION:-3.20.3}"
ALPINE_REPOSITORY="${ALPINE_REPOSITORY:-https://dl-cdn.alpinelinux.org/alpine}"
CLAUDE_CODE_VERSION="${CLAUDE_CODE_VERSION:-2.1.132}"
IMAGE_SIZE_MB="${IMAGE_SIZE_MB:-2048}"
OUT_DIR="$OUT_DIR_DEFAULT"
IMAGE_NAME="mesha-firecracker-alpine-rootfs.ext4"
KEEP_STAGING="${KEEP_STAGING:-0}"
CONTAINER_ENGINE="${CONTAINER_ENGINE:-}"

usage() {
  cat <<USAGE
Usage: $SCRIPT_NAME [options]

Build an Alpine-based Firecracker root filesystem image with Node.js and the
Claude Code CLI preinstalled.

Options:
  --out-dir DIR              Output directory (default: $OUT_DIR_DEFAULT)
  --image-name NAME          Rootfs image filename (default: $IMAGE_NAME)
  --image-size-mb MB         ext4 image size in MiB (default: $IMAGE_SIZE_MB)
  --alpine-version VERSION   Alpine release tag (default: $ALPINE_VERSION)
  --claude-version VERSION   @anthropic-ai/claude-code version (default: $CLAUDE_CODE_VERSION)
  --keep-staging             Keep the populated rootfs staging directory
  -h, --help                 Show this help

Environment overrides:
  CONTAINER_ENGINE           docker or podman
  ALPINE_REPOSITORY          Alpine package mirror (default: $ALPINE_REPOSITORY)
  SOURCE_DATE_EPOCH          Timestamp used for reproducible metadata

Outputs:
  <out-dir>/<image-name>     Bootable ext4 root filesystem image
  <out-dir>/manifest.json    Build manifest with versions and checksums
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    --image-name)
      IMAGE_NAME="$2"
      shift 2
      ;;
    --image-size-mb)
      IMAGE_SIZE_MB="$2"
      shift 2
      ;;
    --alpine-version)
      ALPINE_VERSION="$2"
      shift 2
      ;;
    --claude-version)
      CLAUDE_CODE_VERSION="$2"
      shift 2
      ;;
    --keep-staging)
      KEEP_STAGING=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

choose_container_engine() {
  if [[ -n "$CONTAINER_ENGINE" ]]; then
    require_command "$CONTAINER_ENGINE"
    echo "$CONTAINER_ENGINE"
    return
  fi

  if command -v docker >/dev/null 2>&1; then
    echo docker
    return
  fi

  if command -v podman >/dev/null 2>&1; then
    echo podman
    return
  fi

  echo "Required command not found: docker or podman" >&2
  exit 1
}

validate_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    echo "$name must be an integer: $value" >&2
    exit 2
  fi
}

validate_integer IMAGE_SIZE_MB "$IMAGE_SIZE_MB"
if (( IMAGE_SIZE_MB < 512 )); then
  echo "IMAGE_SIZE_MB must be at least 512" >&2
  exit 2
fi

ENGINE="$(choose_container_engine)"
require_command sha256sum
require_command jq

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
STAGING_DIR="$OUT_DIR/rootfs.staging"
IMAGE_PATH="$OUT_DIR/$IMAGE_NAME"
MANIFEST_PATH="$OUT_DIR/manifest.json"
BUILD_EPOCH="${SOURCE_DATE_EPOCH:-0}"

rm -rf "$STAGING_DIR" "$IMAGE_PATH" "$MANIFEST_PATH"
mkdir -p "$STAGING_DIR"

cat > "$OUT_DIR/build-rootfs-inside-container.sh" <<'BUILDER'
#!/bin/sh
set -eu

ROOTFS=/rootfs
ALPINE_REPOSITORY="$1"
ALPINE_VERSION="$2"
CLAUDE_CODE_VERSION="$3"
BUILD_EPOCH="$4"
IMAGE_SIZE_MB="$5"
IMAGE_NAME="$6"

RELEASE_MINOR="${ALPINE_VERSION%.*}"
MAIN_REPO="$ALPINE_REPOSITORY/v$RELEASE_MINOR/main"
COMMUNITY_REPO="$ALPINE_REPOSITORY/v$RELEASE_MINOR/community"

mkdir -p "$ROOTFS/etc/apk"
printf '%s\n%s\n' "$MAIN_REPO" "$COMMUNITY_REPO" > "$ROOTFS/etc/apk/repositories"

apk --root "$ROOTFS" --initdb --no-cache --update-cache \
  --repository "$MAIN_REPO" --repository "$COMMUNITY_REPO" \
  add \
  alpine-base \
  bash \
  bind-tools \
  ca-certificates \
  curl \
  e2fsprogs \
  git \
  iproute2 \
  iputils \
  jq \
  libstdc++ \
  nodejs \
  npm \
  openrc \
  openssh-client \
  ripgrep \
  shadow \
  sudo \
  tini \
  util-linux

cleanup() {
  umount "$ROOTFS/dev" 2>/dev/null || true
  umount "$ROOTFS/sys" 2>/dev/null || true
  umount "$ROOTFS/proc" 2>/dev/null || true
}
trap cleanup EXIT

mount -t proc proc "$ROOTFS/proc"
mount -t sysfs sys "$ROOTFS/sys"
mount -t devtmpfs dev "$ROOTFS/dev" 2>/dev/null || mount --bind /dev "$ROOTFS/dev"

chroot "$ROOTFS" /bin/sh -eux <<CHROOT
update-ca-certificates
npm install --global --omit=dev --no-audit --no-fund @anthropic-ai/claude-code@$CLAUDE_CODE_VERSION
adduser -D -s /bin/bash -h /home/mesha mesha
addgroup mesha wheel
passwd -d root
passwd -d mesha
rc-update add devfs sysinit
rc-update add dmesg sysinit
rc-update add mdev sysinit
rc-update add hwclock boot
rc-update add modules boot
rc-update add sysctl boot
rc-update add hostname boot
rc-update add bootmisc boot
rc-update add syslog boot
rc-update add networking boot
rc-update add local default
CHROOT

cat > "$ROOTFS/etc/hostname" <<'EOF_HOSTNAME'
mesha-base
EOF_HOSTNAME

cat > "$ROOTFS/etc/hosts" <<'EOF_HOSTS'
127.0.0.1 localhost localhost.localdomain
127.0.1.1 mesha-base
::1 localhost localhost.localdomain
EOF_HOSTS

cat > "$ROOTFS/etc/fstab" <<'EOF_FSTAB'
/dev/vda / ext4 defaults,noatime 0 1
proc /proc proc defaults 0 0
sysfs /sys sysfs defaults 0 0
devtmpfs /dev devtmpfs mode=0755,nosuid 0 0
devpts /dev/pts devpts mode=0620,gid=5,nosuid,noexec 0 0
tmpfs /run tmpfs mode=0755,nosuid,nodev 0 0
tmpfs /tmp tmpfs mode=1777,nosuid,nodev 0 0
EOF_FSTAB

cat > "$ROOTFS/etc/inittab" <<'EOF_INITTAB'
::sysinit:/sbin/openrc sysinit
::sysinit:/sbin/openrc boot
::wait:/sbin/openrc default
::respawn:/sbin/getty -L 115200 ttyS0 vt100
::respawn:/sbin/getty 38400 tty1
::ctrlaltdel:/sbin/reboot
::shutdown:/sbin/openrc shutdown
EOF_INITTAB

mkdir -p "$ROOTFS/etc/local.d" "$ROOTFS/root" "$ROOTFS/workspace"
cat > "$ROOTFS/etc/local.d/mesha-base.start" <<'EOF_LOCAL'
#!/bin/sh
set -eu
{
  echo "Mesha Firecracker base image booted"
  node --version
  npm --version
  claude --version
} >/dev/console 2>&1 || true
EOF_LOCAL
chmod +x "$ROOTFS/etc/local.d/mesha-base.start"

cat > "$ROOTFS/etc/motd" <<'EOF_MOTD'
Mesha Firecracker Alpine base image

Claude Code is installed globally as: claude
Workspace directory: /workspace
EOF_MOTD

cat > "$ROOTFS/root/.profile" <<'EOF_PROFILE'
cd /workspace 2>/dev/null || true
EOF_PROFILE
cp "$ROOTFS/root/.profile" "$ROOTFS/home/mesha/.profile"
chown -R 1000:1000 "$ROOTFS/home/mesha" "$ROOTFS/workspace"
chmod 0755 "$ROOTFS/workspace"

cat > "$ROOTFS/etc/profile.d/mesha.sh" <<'EOF_PROFILE_D'
export EDITOR=vi
export PAGER=less
export NPM_CONFIG_UPDATE_NOTIFIER=false
EOF_PROFILE_D

chroot "$ROOTFS" /usr/bin/node --version > "$ROOTFS/etc/mesha-base-node-version"
chroot "$ROOTFS" /usr/bin/npm --version > "$ROOTFS/etc/mesha-base-npm-version"
chroot "$ROOTFS" /usr/local/bin/claude --version > "$ROOTFS/etc/mesha-base-claude-version"

find "$ROOTFS" -xdev -exec touch -h -d "@$BUILD_EPOCH" {} +

truncate -s "${IMAGE_SIZE_MB}M" "/out/${IMAGE_NAME}"
mkfs.ext4 -q -F -d "$ROOTFS" -E root_owner=0:0,lazy_itable_init=0,lazy_journal_init=0 "/out/${IMAGE_NAME}"
BUILDER
chmod +x "$OUT_DIR/build-rootfs-inside-container.sh"

ENGINE_ARGS=(run --rm --privileged -v "$STAGING_DIR:/rootfs" -v "$OUT_DIR:/out" -v "$OUT_DIR/build-rootfs-inside-container.sh:/build-rootfs.sh:ro" "alpine:$ALPINE_VERSION" /build-rootfs.sh "$ALPINE_REPOSITORY" "$ALPINE_VERSION" "$CLAUDE_CODE_VERSION" "$BUILD_EPOCH" "$IMAGE_SIZE_MB" "$IMAGE_NAME")
if [[ "$ENGINE" == "podman" ]]; then
  ENGINE_ARGS=(run --rm --privileged -v "$STAGING_DIR:/rootfs:Z" -v "$OUT_DIR:/out:Z" -v "$OUT_DIR/build-rootfs-inside-container.sh:/build-rootfs.sh:ro,Z" "alpine:$ALPINE_VERSION" /build-rootfs.sh "$ALPINE_REPOSITORY" "$ALPINE_VERSION" "$CLAUDE_CODE_VERSION" "$BUILD_EPOCH" "$IMAGE_SIZE_MB" "$IMAGE_NAME")
fi

"$ENGINE" "${ENGINE_ARGS[@]}"
rm -f "$OUT_DIR/build-rootfs-inside-container.sh"
IMAGE_SHA256="$(sha256sum "$IMAGE_PATH" | awk '{print $1}')"
IMAGE_BYTES="$(stat -c '%s' "$IMAGE_PATH")"
NODE_VERSION="$(cat "$STAGING_DIR/etc/mesha-base-node-version")"
NPM_VERSION="$(cat "$STAGING_DIR/etc/mesha-base-npm-version")"
CLAUDE_VERSION="$(cat "$STAGING_DIR/etc/mesha-base-claude-version")"

jq -n \
  --arg image_name "$IMAGE_NAME" \
  --arg image_sha256 "$IMAGE_SHA256" \
  --arg alpine_version "$ALPINE_VERSION" \
  --arg alpine_repository "$ALPINE_REPOSITORY" \
  --arg claude_package "@anthropic-ai/claude-code" \
  --arg claude_code_version "$CLAUDE_CODE_VERSION" \
  --arg claude_reported_version "$CLAUDE_VERSION" \
  --arg node_version "$NODE_VERSION" \
  --arg npm_version "$NPM_VERSION" \
  --arg source_date_epoch "$BUILD_EPOCH" \
  --argjson image_size_bytes "$IMAGE_BYTES" \
  '{image_name: $image_name, image_size_bytes: $image_size_bytes, image_sha256: $image_sha256, alpine_version: $alpine_version, alpine_repository: $alpine_repository, node_version: $node_version, npm_version: $npm_version, claude_package: $claude_package, claude_code_version: $claude_code_version, claude_reported_version: $claude_reported_version, source_date_epoch: $source_date_epoch}' \
  > "$MANIFEST_PATH"

if [[ "$KEEP_STAGING" != "1" ]]; then
  rm -rf "$STAGING_DIR"
fi

echo "Built $IMAGE_PATH"
echo "Wrote $MANIFEST_PATH"
echo "sha256: $IMAGE_SHA256"
