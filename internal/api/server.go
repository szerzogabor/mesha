package api

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"time"
)

const (
	readHeaderTimeout = 5 * time.Second
	shutdownTimeout   = 10 * time.Second
)

// Server wraps the HTTP server and its dependencies.
type Server struct {
	http   *http.Server
	logger *slog.Logger
}

// New creates a Server listening on addr. Call Run to start accepting
// connections.
func New(addr string, logger *slog.Logger) *Server {
	mux := http.NewServeMux()
	s := &Server{logger: logger}
	s.http = &http.Server{
		Addr:              addr,
		Handler:           mux,
		ReadHeaderTimeout: readHeaderTimeout,
	}
	mux.HandleFunc("GET /health", s.handleHealth)
	return s
}

// Run starts the HTTP server and blocks until ctx is cancelled, then performs a
// graceful shutdown.
func (s *Server) Run(ctx context.Context) error {
	errCh := make(chan error, 1)
	go func() {
		s.logger.Info("HTTP server listening", "addr", s.http.Addr)
		if err := s.http.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			errCh <- err
		}
		close(errCh)
	}()

	select {
	case err := <-errCh:
		return err
	case <-ctx.Done():
	}

	shutCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()
	s.logger.Info("shutting down HTTP server")
	return s.http.Shutdown(shutCtx)
}

type healthResponse struct {
	Status string `json:"status"`
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(healthResponse{Status: "ok"}); err != nil {
		s.logger.Error("health encode error", "err", err)
	}
}

// Addr returns the address string in the format ":PORT".
func Addr(port int) string {
	return fmt.Sprintf(":%d", port)
}
