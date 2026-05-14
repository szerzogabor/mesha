package vm

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"testing"
	"time"
)

type testVM struct {
	id string
}

type testVMFactory struct {
	mu        sync.Mutex
	created   int
	destroyed []string
	failNext  int
	delay     time.Duration
}

func (f *testVMFactory) CreateVM(ctx context.Context) (testVM, error) {
	if f.delay > 0 {
		select {
		case <-ctx.Done():
			return testVM{}, ctx.Err()
		case <-time.After(f.delay):
		}
	}

	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failNext > 0 {
		f.failNext--
		return testVM{}, errors.New("injected create failure")
	}
	f.created++
	return testVM{id: fmt.Sprintf("vm-%d", f.created)}, nil
}

func (f *testVMFactory) DestroyVM(_ context.Context, vm testVM) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.destroyed = append(f.destroyed, vm.id)
	return nil
}

func (f *testVMFactory) createdCount() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.created
}

func TestWarmPoolMaintainsConfiguredIdleCount(t *testing.T) {
	t.Parallel()

	factory := &testVMFactory{}
	pool, err := NewWarmPool[testVM](WarmPoolConfig{DesiredIdle: 3}, factory)
	if err != nil {
		t.Fatal(err)
	}
	defer closePool(t, pool)

	if err := pool.Start(context.Background()); err != nil {
		t.Fatal(err)
	}
	waitFor(t, func() bool { return pool.IdleCount() == 3 }, "idle VMs to reach configured target")

	if got := factory.createdCount(); got != 3 {
		t.Fatalf("expected exactly 3 pre-warmed VMs, got %d", got)
	}
}

func TestWarmPoolConcurrentAllocationGetsUniqueVMs(t *testing.T) {
	t.Parallel()

	factory := &testVMFactory{}
	pool, err := NewWarmPool[testVM](WarmPoolConfig{DesiredIdle: 2}, factory)
	if err != nil {
		t.Fatal(err)
	}
	defer closePool(t, pool)
	if err := pool.Start(context.Background()); err != nil {
		t.Fatal(err)
	}
	waitFor(t, func() bool { return pool.IdleCount() == 2 }, "initial pre-warm")

	const allocations = 12
	var wg sync.WaitGroup
	allocated := make(chan testVM, allocations)
	errs := make(chan error, allocations)
	for range allocations {
		wg.Add(1)
		go func() {
			defer wg.Done()
			vm, err := pool.Allocate(context.Background())
			if err != nil {
				errs <- err
				return
			}
			allocated <- vm
		}()
	}
	wg.Wait()
	close(allocated)
	close(errs)

	for err := range errs {
		t.Fatalf("allocation failed: %v", err)
	}
	seen := make(map[string]struct{}, allocations)
	for vm := range allocated {
		if _, ok := seen[vm.id]; ok {
			t.Fatalf("VM %q allocated more than once", vm.id)
		}
		seen[vm.id] = struct{}{}
	}
	if len(seen) != allocations {
		t.Fatalf("expected %d allocated VMs, got %d", allocations, len(seen))
	}
}

func TestWarmPoolReplenishesAfterAllocation(t *testing.T) {
	t.Parallel()

	factory := &testVMFactory{}
	pool, err := NewWarmPool[testVM](WarmPoolConfig{DesiredIdle: 2}, factory)
	if err != nil {
		t.Fatal(err)
	}
	defer closePool(t, pool)
	if err := pool.Start(context.Background()); err != nil {
		t.Fatal(err)
	}
	waitFor(t, func() bool { return pool.IdleCount() == 2 }, "initial pre-warm")

	first, err := pool.Allocate(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	second, err := pool.Allocate(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if first.id == second.id {
		t.Fatalf("expected distinct VMs, got %q twice", first.id)
	}

	waitFor(t, func() bool { return pool.IdleCount() == 2 }, "pool replenishment")
	if got := factory.createdCount(); got < 4 {
		t.Fatalf("expected replenishment to create replacement VMs, got %d creates", got)
	}
}

func TestWarmPoolRetriesFailedReplenishment(t *testing.T) {
	t.Parallel()

	factory := &testVMFactory{failNext: 1}
	pool, err := NewWarmPool[testVM](WarmPoolConfig{DesiredIdle: 1, ReplenishInterval: time.Millisecond}, factory)
	if err != nil {
		t.Fatal(err)
	}
	defer closePool(t, pool)
	if err := pool.Start(context.Background()); err != nil {
		t.Fatal(err)
	}

	waitFor(t, func() bool { return pool.IdleCount() == 1 }, "retry replenishment")
	if err := pool.LastError(); err != nil {
		t.Fatalf("expected last replenishment error to be cleared after retry, got %v", err)
	}
}

func closePool(t *testing.T, pool *WarmPool[testVM]) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	if err := pool.Close(ctx); err != nil {
		t.Fatalf("close pool: %v", err)
	}
}

func waitFor(t *testing.T, condition func() bool, description string) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if condition() {
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatalf("timed out waiting for %s", description)
}
