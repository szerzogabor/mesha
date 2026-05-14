package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"mesha/internal/api"
	"mesha/internal/config"
)

func main() {
	cfg := config.Load()

	logger := newLogger(cfg.LogLevel)
	logger.Info("starting orchestrator",
		"port", cfg.Port,
		"db_path", cfg.DBPath,
		"log_level", cfg.LogLevel,
		"data_dir", cfg.DataDir,
	)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	srv := api.New(api.Addr(cfg.Port), logger)
	if err := srv.Run(ctx); err != nil {
		logger.Error("server error", "err", err)
		os.Exit(1)
	}
	logger.Info("orchestrator stopped")
}

func newLogger(level string) *slog.Logger {
	var l slog.Level
	switch level {
	case "debug":
		l = slog.LevelDebug
	case "warn":
		l = slog.LevelWarn
	case "error":
		l = slog.LevelError
	default:
		l = slog.LevelInfo
	}
	return slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: l}))
}
