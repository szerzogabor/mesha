#!/usr/bin/env bash
set -euo pipefail

SCRIPT_NAME="$(basename "$0")"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_OUT_DIR="$REPO_ROOT/infra/firecracker/base-image/out"
IMAGE_PATH="$BASE_OUT_DIR/mesha-firecracker-alpine-rootfs.ext4"
MANIFEST_PATH="$BASE_OUT_DIR/manifest.json"
STAGING_DIR=""

usage() {
  cat <<USAGE
Usage: $SCRIPT_NAME [options]

Verify the Firecracker base image manifest and filesystem contents. When a
staging rootfs is supplied, also chroot-smoke-test Node.js, npm, and Claude Code.

Options:
  --image PATH        ext4 rootfs image to inspect (default: $IMAGE_PATH)
  --manifest PATH     build manifest to validate (default: $MANIFEST_PATH)
  --staging-dir DIR   populated rootfs directory for executable smoke tests
  -h, --help          Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image)
      IMAGE_PATH="$2"
      shift 2
      ;;
    --manifest)
      MANIFEST_PATH="$2"
      shift 2
      ;;
    --staging-dir)
      STAGING_DIR="$2"
      shift 2
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

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "Required file not found: $1" >&2
    exit 1
  fi
}

require_path_in_image() {
  local path="$1"
  if ! debugfs -R "stat $path" "$IMAGE_PATH" >/dev/null 2>&1; then
    echo "Missing path in image: $path" >&2
    exit 1
  fi
  echo "Found in image: $path"
}

require_command jq
require_command sha256sum
require_command debugfs
require_file "$IMAGE_PATH"
require_file "$MANIFEST_PATH"

EXPECTED_SHA="$(jq -r '.image_sha256' "$MANIFEST_PATH")"
ACTUAL_SHA="$(sha256sum "$IMAGE_PATH" | awk '{print $1}')"
if [[ "$EXPECTED_SHA" != "$ACTUAL_SHA" ]]; then
  echo "Image checksum mismatch" >&2
  echo "manifest: $EXPECTED_SHA" >&2
  echo "actual:   $ACTUAL_SHA" >&2
  exit 1
fi

echo "Manifest checksum matches image: $ACTUAL_SHA"

for key in alpine_version node_version npm_version claude_package claude_code_version claude_reported_version; do
  value="$(jq -r --arg key "$key" '.[$key] // empty' "$MANIFEST_PATH")"
  if [[ -z "$value" ]]; then
    echo "Missing manifest key: $key" >&2
    exit 1
  fi
  echo "Manifest $key=$value"
done

require_path_in_image /sbin/init
require_path_in_image /etc/inittab
require_path_in_image /etc/local.d/mesha-base.start
require_path_in_image /usr/bin/node
require_path_in_image /usr/bin/npm
require_path_in_image /usr/local/bin/claude
require_path_in_image /workspace

if [[ -n "$STAGING_DIR" ]]; then
  if [[ ! -d "$STAGING_DIR" ]]; then
    echo "Staging directory not found: $STAGING_DIR" >&2
    exit 1
  fi
  if [[ $EUID -ne 0 ]]; then
    echo "Error: chroot smoke tests require root privileges. Please run with sudo." >&2
    exit 1
  fi
  require_command chroot
  echo "Running chroot smoke tests in $STAGING_DIR"
  chroot "$STAGING_DIR" /usr/bin/node --version
  chroot "$STAGING_DIR" /usr/bin/npm --version
  chroot "$STAGING_DIR" /usr/local/bin/claude --version
else
  echo "Skipping executable smoke tests; pass --staging-dir or build with --keep-staging to run them."
fi

echo "Firecracker base image verification passed."
