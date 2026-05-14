# scripts

Placeholder for the Mesha MVP project structure. See `docs/project-structure.md` for this directory's responsibility.

## Firecracker base image

- `build-firecracker-base-image.sh` builds the Alpine ext4 rootfs image.
- `verify-firecracker-base-image.sh` validates the generated image and optional staging rootfs.

See `../infra/firecracker/base-image/README.md` for full usage.
