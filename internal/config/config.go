package config

import (
	"os"
	"strconv"
)

// Config holds all runtime configuration for the orchestrator, sourced from
// environment variables with sensible defaults.
type Config struct {
	// Port is the TCP port the HTTP server listens on.
	Port int

	// DBPath is the file path for the SQLite database.
	DBPath string

	// LogLevel controls structured log verbosity: debug, info, warn, error.
	LogLevel string

	// DataDir is the root directory for runtime data (overlays, snapshots).
	DataDir string
}

// Load reads environment variables and returns a Config. Missing variables fall
// back to defaults suitable for local development.
func Load() Config {
	return Config{
		Port:     envInt("PORT", 8080),
		DBPath:   envStr("DB_PATH", "data/db/mesha.db"),
		LogLevel: envStr("LOG_LEVEL", "info"),
		DataDir:  envStr("DATA_DIR", "data"),
	}
}

func envStr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func envInt(key string, fallback int) int {
	v := os.Getenv(key)
	if v == "" {
		return fallback
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return n
}
