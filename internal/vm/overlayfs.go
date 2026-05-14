package vm

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
)

const (
	defaultBaseFSType = "ext4"
)

var sessionIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$`)

// OverlayFSConfig describes the host-side filesystem layout used to isolate
// writable session state from a shared read-only Firecracker base image.
type OverlayFSConfig struct {
	// BaseImagePath points to either a rootfs image file or an already-populated
	// rootfs directory. Files are mounted read-only through a loop device;
	// directories are mounted as read-only bind mounts.
	BaseImagePath string

	// BaseMountDir is the shared lowerdir path consumed by per-session overlays.
	BaseMountDir string

	// OverlayRoot stores one directory per session. Each session directory owns
	// isolated upper, work, and merged subdirectories.
	OverlayRoot string

	// BaseFSType is the filesystem type for BaseImagePath when it is a file.
	// When empty, ext4 is used because the base-image build pipeline emits ext4.
	BaseFSType string
}

// SessionOverlay describes one mounted session overlay.
type SessionOverlay struct {
	SessionID string
	LowerDir  string
	UpperDir  string
	WorkDir   string
	MergedDir string
}

// MountSpec is a normalized mount request. Options are rendered as a comma
// separated -o list by the default command-backed mounter.
type MountSpec struct {
	Source  string
	Target  string
	FSType  string
	Options []string
}

// Mounter abstracts privileged mount operations so the OverlayFS layout can be
// unit tested without requiring root privileges or /dev/loop access.
type Mounter interface {
	Mount(MountSpec) error
	Unmount(target string) error
	IsMounted(target string) (bool, error)
	// ListMountedPaths returns all currently mounted targets keyed for O(1) lookup.
	ListMountedPaths() (map[string]struct{}, error)
}

// OverlayFSManager creates, mounts, unmounts, and garbage-collects session
// OverlayFS directories.
type OverlayFSManager struct {
	config  OverlayFSConfig
	mounter Mounter
}

// NewOverlayFSManager returns a manager using the system mount and umount
// commands. The commands require the caller to run with sufficient privileges.
func NewOverlayFSManager(config OverlayFSConfig) (*OverlayFSManager, error) {
	return NewOverlayFSManagerWithMounter(config, systemMounter{})
}

// NewOverlayFSManagerWithMounter returns a manager with an injected mounter.
func NewOverlayFSManagerWithMounter(config OverlayFSConfig, mounter Mounter) (*OverlayFSManager, error) {
	if strings.TrimSpace(config.BaseImagePath) == "" {
		return nil, errors.New("base image path is required")
	}
	if strings.TrimSpace(config.BaseMountDir) == "" {
		return nil, errors.New("base mount directory is required")
	}
	if strings.TrimSpace(config.OverlayRoot) == "" {
		return nil, errors.New("overlay root is required")
	}
	if mounter == nil {
		return nil, errors.New("mounter is required")
	}
	if config.BaseFSType == "" {
		config.BaseFSType = defaultBaseFSType
	}

	baseImagePath, err := filepath.Abs(config.BaseImagePath)
	if err != nil {
		return nil, fmt.Errorf("resolve base image path: %w", err)
	}
	baseMountDir, err := filepath.Abs(config.BaseMountDir)
	if err != nil {
		return nil, fmt.Errorf("resolve base mount directory: %w", err)
	}
	overlayRoot, err := filepath.Abs(config.OverlayRoot)
	if err != nil {
		return nil, fmt.Errorf("resolve overlay root: %w", err)
	}

	config.BaseImagePath = baseImagePath
	config.BaseMountDir = baseMountDir
	config.OverlayRoot = overlayRoot

	return &OverlayFSManager{config: config, mounter: mounter}, nil
}

// EnsureBaseMounted mounts the shared lower rootfs read-only. It is safe to call
// repeatedly; an already-mounted lowerdir is left untouched.
func (m *OverlayFSManager) EnsureBaseMounted() error {
	if err := os.MkdirAll(m.config.BaseMountDir, 0o755); err != nil {
		return fmt.Errorf("create base mount directory: %w", err)
	}
	if err := os.MkdirAll(m.config.OverlayRoot, 0o755); err != nil {
		return fmt.Errorf("create overlay root: %w", err)
	}

	mounted, err := m.mounter.IsMounted(m.config.BaseMountDir)
	if err != nil {
		return fmt.Errorf("check base mount: %w", err)
	}
	if mounted {
		return nil
	}

	baseInfo, err := os.Stat(m.config.BaseImagePath)
	if err != nil {
		return fmt.Errorf("stat base image path: %w", err)
	}

	if baseInfo.IsDir() {
		if err := m.mounter.Mount(MountSpec{
			Source:  m.config.BaseImagePath,
			Target:  m.config.BaseMountDir,
			Options: []string{"bind"},
		}); err != nil {
			return fmt.Errorf("bind mount base rootfs: %w", err)
		}
		if err := m.mounter.Mount(MountSpec{
			Source:  m.config.BaseImagePath,
			Target:  m.config.BaseMountDir,
			Options: []string{"remount", "bind", "ro"},
		}); err != nil {
			_ = m.mounter.Unmount(m.config.BaseMountDir)
			return fmt.Errorf("remount base rootfs read-only: %w", err)
		}
		return nil
	}

	if err := m.mounter.Mount(MountSpec{
		Source:  m.config.BaseImagePath,
		Target:  m.config.BaseMountDir,
		FSType:  m.config.BaseFSType,
		Options: []string{"loop", "ro"},
	}); err != nil {
		return fmt.Errorf("mount base image read-only: %w", err)
	}
	return nil
}

// MountSession prepares and mounts an isolated OverlayFS view for sessionID.
func (m *OverlayFSManager) MountSession(sessionID string) (SessionOverlay, error) {
	overlay, err := m.sessionOverlay(sessionID)
	if err != nil {
		return SessionOverlay{}, err
	}
	if err := m.EnsureBaseMounted(); err != nil {
		return SessionOverlay{}, err
	}
	for _, dir := range []string{overlay.UpperDir, overlay.WorkDir, overlay.MergedDir} {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return SessionOverlay{}, fmt.Errorf("create overlay directory %s: %w", dir, err)
		}
	}

	mounted, err := m.mounter.IsMounted(overlay.MergedDir)
	if err != nil {
		return SessionOverlay{}, fmt.Errorf("check session overlay mount: %w", err)
	}
	if mounted {
		return overlay, nil
	}

	if err := m.mounter.Mount(MountSpec{
		Source: "overlay",
		Target: overlay.MergedDir,
		FSType: "overlay",
		Options: []string{
			"lowerdir=" + overlay.LowerDir,
			"upperdir=" + overlay.UpperDir,
			"workdir=" + overlay.WorkDir,
			"nosuid",
			"nodev",
		},
	}); err != nil {
		return SessionOverlay{}, fmt.Errorf("mount session overlay: %w", err)
	}

	return overlay, nil
}

// UnmountSession detaches a session's merged OverlayFS mount when present.
func (m *OverlayFSManager) UnmountSession(sessionID string) error {
	overlay, err := m.sessionOverlay(sessionID)
	if err != nil {
		return err
	}
	mounted, err := m.mounter.IsMounted(overlay.MergedDir)
	if err != nil {
		return fmt.Errorf("check session overlay mount: %w", err)
	}
	if !mounted {
		return nil
	}
	if err := m.mounter.Unmount(overlay.MergedDir); err != nil {
		return fmt.Errorf("unmount session overlay: %w", err)
	}
	return nil
}

// RemoveSessionUnmounted removes a session's writable layer after it has been
// unmounted. It refuses to delete a mounted merged directory.
func (m *OverlayFSManager) RemoveSessionUnmounted(sessionID string) error {
	overlay, err := m.sessionOverlay(sessionID)
	if err != nil {
		return err
	}
	mounted, err := m.mounter.IsMounted(overlay.MergedDir)
	if err != nil {
		return fmt.Errorf("check session overlay mount: %w", err)
	}
	if mounted {
		return fmt.Errorf("refusing to remove mounted session overlay %q", sessionID)
	}
	if err := os.RemoveAll(filepath.Dir(overlay.UpperDir)); err != nil {
		return fmt.Errorf("remove session overlay directory: %w", err)
	}
	return nil
}

// CleanupOrphanedLayers unmounts and removes overlay directories whose session
// IDs are absent from activeSessionIDs. It returns the IDs removed.
func (m *OverlayFSManager) CleanupOrphanedLayers(activeSessionIDs []string) ([]string, error) {
	active := make(map[string]struct{}, len(activeSessionIDs))
	for _, sessionID := range activeSessionIDs {
		if err := validateSessionID(sessionID); err != nil {
			return nil, err
		}
		active[sessionID] = struct{}{}
	}

	entries, err := os.ReadDir(m.config.OverlayRoot)
	if errors.Is(err, os.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read overlay root: %w", err)
	}

	// Read mount info once to avoid O(N*M) scans of /proc/self/mountinfo.
	mountedPaths, err := m.mounter.ListMountedPaths()
	if err != nil {
		return nil, fmt.Errorf("list mounted paths: %w", err)
	}

	var removed []string
	var errs []error
	for _, entry := range entries {
		if !entry.IsDir() {
			continue
		}
		sessionID := entry.Name()
		if err := validateSessionID(sessionID); err != nil {
			continue
		}
		if _, ok := active[sessionID]; ok {
			continue
		}
		overlay, err := m.sessionOverlay(sessionID)
		if err != nil {
			errs = append(errs, err)
			continue
		}
		if _, isMounted := mountedPaths[overlay.MergedDir]; isMounted {
			if err := m.mounter.Unmount(overlay.MergedDir); err != nil {
				errs = append(errs, fmt.Errorf("unmount orphan %q: %w", sessionID, err))
				continue
			}
		}
		if err := os.RemoveAll(filepath.Dir(overlay.UpperDir)); err != nil {
			errs = append(errs, fmt.Errorf("remove orphan %q: %w", sessionID, err))
			continue
		}
		removed = append(removed, sessionID)
	}

	return removed, errors.Join(errs...)
}

func (m *OverlayFSManager) sessionOverlay(sessionID string) (SessionOverlay, error) {
	if err := validateSessionID(sessionID); err != nil {
		return SessionOverlay{}, err
	}
	sessionRoot := filepath.Join(m.config.OverlayRoot, sessionID)
	return SessionOverlay{
		SessionID: sessionID,
		LowerDir:  m.config.BaseMountDir,
		UpperDir:  filepath.Join(sessionRoot, "upper"),
		WorkDir:   filepath.Join(sessionRoot, "work"),
		MergedDir: filepath.Join(sessionRoot, "merged"),
	}, nil
}

func validateSessionID(sessionID string) error {
	if !sessionIDPattern.MatchString(sessionID) || sessionID == "." || sessionID == ".." {
		return fmt.Errorf("invalid session ID %q", sessionID)
	}
	return nil
}

type systemMounter struct{}

func (systemMounter) Mount(spec MountSpec) error {
	args := []string{}
	if spec.FSType != "" {
		args = append(args, "-t", spec.FSType)
	}
	if len(spec.Options) > 0 {
		args = append(args, "-o", strings.Join(spec.Options, ","))
	}
	args = append(args, spec.Source, spec.Target)
	cmd := exec.Command("mount", args...)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("mount %s on %s: %w: %s", spec.Source, spec.Target, err, strings.TrimSpace(string(output)))
	}
	return nil
}

func (systemMounter) Unmount(target string) error {
	cmd := exec.Command("umount", target)
	output, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("umount %s: %w: %s", target, err, strings.TrimSpace(string(output)))
	}
	return nil
}

func (systemMounter) IsMounted(target string) (bool, error) {
	return mountInfoContains("/proc/self/mountinfo", target)
}

func (systemMounter) ListMountedPaths() (map[string]struct{}, error) {
	return parseMountedPathSet("/proc/self/mountinfo")
}

func mountInfoContains(mountInfoPath, target string) (bool, error) {
	// target is already absolute from manager config initialization
	paths, err := parseMountedPathSet(mountInfoPath)
	if err != nil {
		return false, err
	}
	_, ok := paths[target]
	return ok, nil
}

func parseMountedPathSet(mountInfoPath string) (map[string]struct{}, error) {
	file, err := os.Open(mountInfoPath)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	paths := make(map[string]struct{})
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) >= 5 {
			paths[unescapeMountInfoPath(fields[4])] = struct{}{}
		}
	}
	if err := scanner.Err(); err != nil {
		return nil, err
	}
	return paths, nil
}

var mountInfoReplacer = strings.NewReplacer(`\040`, " ", `\011`, "\t", `\012`, "\n", `\134`, `\`)

func unescapeMountInfoPath(path string) string {
	return mountInfoReplacer.Replace(path)
}
