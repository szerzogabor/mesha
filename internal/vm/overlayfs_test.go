package vm

import (
	"os"
	"reflect"
	"strings"
	"testing"
)

type fakeMounter struct {
	mounts  []MountSpec
	unmount []string
	mounted map[string]bool
}

func newFakeMounter() *fakeMounter {
	return &fakeMounter{mounted: map[string]bool{}}
}

func (f *fakeMounter) Mount(spec MountSpec) error {
	f.mounts = append(f.mounts, spec)
	f.mounted[spec.Target] = true
	return nil
}

func (f *fakeMounter) Unmount(target string) error {
	f.unmount = append(f.unmount, target)
	delete(f.mounted, target)
	return nil
}

func (f *fakeMounter) IsMounted(target string) (bool, error) {
	return f.mounted[target], nil
}

func TestMountSessionCreatesIsolatedOverlay(t *testing.T) {
	t.Parallel()

	tmp := t.TempDir()
	baseImage := tmp + "/base.ext4"
	if err := writeTestFile(baseImage); err != nil {
		t.Fatal(err)
	}
	mounter := newFakeMounter()
	manager, err := NewOverlayFSManagerWithMounter(OverlayFSConfig{
		BaseImagePath: baseImage,
		BaseMountDir:  tmp + "/base-ro",
		OverlayRoot:   tmp + "/overlays",
	}, mounter)
	if err != nil {
		t.Fatal(err)
	}

	overlay, err := manager.MountSession("session-1")
	if err != nil {
		t.Fatal(err)
	}

	if len(mounter.mounts) != 2 {
		t.Fatalf("expected base and overlay mounts, got %d", len(mounter.mounts))
	}
	baseMount := mounter.mounts[0]
	if baseMount.Source != baseImage || baseMount.Target != tmp+"/base-ro" || baseMount.FSType != "ext4" {
		t.Fatalf("unexpected base mount: %#v", baseMount)
	}
	if !reflect.DeepEqual(baseMount.Options, []string{"loop", "ro"}) {
		t.Fatalf("base image must be loop-mounted read-only, got %#v", baseMount.Options)
	}

	sessionMount := mounter.mounts[1]
	if sessionMount.Source != "overlay" || sessionMount.Target != overlay.MergedDir || sessionMount.FSType != "overlay" {
		t.Fatalf("unexpected session mount: %#v", sessionMount)
	}
	for _, want := range []string{
		"lowerdir=" + overlay.LowerDir,
		"upperdir=" + overlay.UpperDir,
		"workdir=" + overlay.WorkDir,
		"nosuid",
		"nodev",
	} {
		if !contains(sessionMount.Options, want) {
			t.Fatalf("session mount options missing %q: %#v", want, sessionMount.Options)
		}
	}
}

func TestEnsureBaseMountedUsesReadOnlyBindForDirectoryBase(t *testing.T) {
	t.Parallel()

	tmp := t.TempDir()
	baseDir := tmp + "/rootfs"
	if err := mkdir(baseDir); err != nil {
		t.Fatal(err)
	}
	mounter := newFakeMounter()
	manager, err := NewOverlayFSManagerWithMounter(OverlayFSConfig{
		BaseImagePath: baseDir,
		BaseMountDir:  tmp + "/base-ro",
		OverlayRoot:   tmp + "/overlays",
	}, mounter)
	if err != nil {
		t.Fatal(err)
	}

	if err := manager.EnsureBaseMounted(); err != nil {
		t.Fatal(err)
	}

	if len(mounter.mounts) != 2 {
		t.Fatalf("expected bind mount and read-only remount, got %d", len(mounter.mounts))
	}
	if !reflect.DeepEqual(mounter.mounts[0].Options, []string{"bind"}) {
		t.Fatalf("expected bind mount, got %#v", mounter.mounts[0])
	}
	if !reflect.DeepEqual(mounter.mounts[1].Options, []string{"remount", "bind", "ro"}) {
		t.Fatalf("expected read-only bind remount, got %#v", mounter.mounts[1])
	}
}

func TestCleanupOrphanedLayersUnmountsAndRemovesOnlyInactiveSessions(t *testing.T) {
	t.Parallel()

	tmp := t.TempDir()
	baseImage := tmp + "/base.ext4"
	if err := writeTestFile(baseImage); err != nil {
		t.Fatal(err)
	}
	mounter := newFakeMounter()
	manager, err := NewOverlayFSManagerWithMounter(OverlayFSConfig{
		BaseImagePath: baseImage,
		BaseMountDir:  tmp + "/base-ro",
		OverlayRoot:   tmp + "/overlays",
	}, mounter)
	if err != nil {
		t.Fatal(err)
	}

	active, err := manager.MountSession("active")
	if err != nil {
		t.Fatal(err)
	}
	orphan, err := manager.MountSession("orphan")
	if err != nil {
		t.Fatal(err)
	}

	removed, err := manager.CleanupOrphanedLayers([]string{"active"})
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(removed, []string{"orphan"}) {
		t.Fatalf("unexpected removed sessions: %#v", removed)
	}
	if mounter.mounted[orphan.MergedDir] {
		t.Fatalf("orphan mount still marked mounted")
	}
	if !mounter.mounted[active.MergedDir] {
		t.Fatalf("active mount was removed")
	}
	if len(mounter.unmount) != 1 || mounter.unmount[0] != orphan.MergedDir {
		t.Fatalf("unexpected unmounts: %#v", mounter.unmount)
	}
}

func TestMountSessionRejectsUnsafeSessionIDs(t *testing.T) {
	t.Parallel()

	tmp := t.TempDir()
	baseImage := tmp + "/base.ext4"
	if err := writeTestFile(baseImage); err != nil {
		t.Fatal(err)
	}
	manager, err := NewOverlayFSManagerWithMounter(OverlayFSConfig{
		BaseImagePath: baseImage,
		BaseMountDir:  tmp + "/base-ro",
		OverlayRoot:   tmp + "/overlays",
	}, newFakeMounter())
	if err != nil {
		t.Fatal(err)
	}

	_, err = manager.MountSession("../escape")
	if err == nil || !strings.Contains(err.Error(), "invalid session ID") {
		t.Fatalf("expected invalid session ID error, got %v", err)
	}
}

func contains(values []string, want string) bool {
	for _, value := range values {
		if value == want {
			return true
		}
	}
	return false
}

func writeTestFile(path string) error {
	return os.WriteFile(path, []byte("test"), 0o644)
}

func mkdir(path string) error {
	return os.MkdirAll(path, 0o755)
}
