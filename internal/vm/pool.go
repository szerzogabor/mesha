package vm

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"time"
)

const defaultReplenishInterval = time.Second

// WarmVMFactory creates and destroys idle VMs managed by WarmPool. Production
// code can back this with Firecracker boot/teardown calls, while tests can use
// an in-memory factory.
type WarmVMFactory[T any] interface {
	CreateVM(context.Context) (T, error)
	DestroyVM(context.Context, T) error
}

// WarmPoolConfig controls how many pre-warmed VMs the pool keeps ready for
// low-latency allocation.
type WarmPoolConfig struct {
	// DesiredIdle is the target number of ready-to-allocate VMs. A value of zero
	// disables pre-warming; Allocate will still create VMs on demand.
	DesiredIdle int

	// CreateTimeout bounds each background pre-warm create call. A zero value lets
	// the factory rely on the pool context.
	CreateTimeout time.Duration

	// ReplenishInterval is the delay before retrying a failed background create.
	// When empty, a conservative default is used.
	ReplenishInterval time.Duration
}

// WarmPool maintains a pre-warmed pool of idle VMs and hands each allocation a
// unique VM. It is safe for concurrent use.
type WarmPool[T any] struct {
	config  WarmPoolConfig
	factory WarmVMFactory[T]
	idle    chan T

	mu       sync.Mutex
	ctx      context.Context
	cancel   context.CancelFunc
	started  bool
	closing  bool
	creating int
	lastErr  error

	wg sync.WaitGroup
}

// NewWarmPool returns an unstarted pre-warm pool. Call Start to begin filling
// the idle target before accepting allocations.
func NewWarmPool[T any](config WarmPoolConfig, factory WarmVMFactory[T]) (*WarmPool[T], error) {
	if config.DesiredIdle < 0 {
		return nil, errors.New("desired idle VM count must be non-negative")
	}
	if config.ReplenishInterval <= 0 {
		config.ReplenishInterval = defaultReplenishInterval
	}
	if factory == nil {
		return nil, errors.New("warm VM factory is required")
	}

	capacity := config.DesiredIdle
	if capacity == 0 {
		capacity = 1
	}

	return &WarmPool[T]{
		config:  config,
		factory: factory,
		idle:    make(chan T, capacity),
	}, nil
}

// Start begins asynchronous replenishment up to DesiredIdle. Repeated calls are
// idempotent while the pool is running.
func (p *WarmPool[T]) Start(ctx context.Context) error {
	if ctx == nil {
		return errors.New("start context is required")
	}

	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closing {
		return errors.New("warm VM pool is closing")
	}
	if p.started {
		return nil
	}
	p.ctx, p.cancel = context.WithCancel(ctx)
	p.started = true
	p.replenishLocked()
	return nil
}

// Allocate returns an idle VM when one is available, otherwise it creates a VM
// on demand with the caller's context. After consuming an idle VM, the pool
// automatically schedules background replenishment back to DesiredIdle.
func (p *WarmPool[T]) Allocate(ctx context.Context) (T, error) {
	var zero T
	if ctx == nil {
		return zero, errors.New("allocate context is required")
	}

	p.mu.Lock()
	if err := p.ensureRunningLocked(); err != nil {
		p.mu.Unlock()
		return zero, err
	}
	select {
	case vm := <-p.idle:
		p.replenishLocked()
		p.mu.Unlock()
		return vm, nil
	default:
	}

	poolCtx := p.ctx
	p.wg.Add(1)
	p.mu.Unlock()

	createCtx, cancelCreate := mergeContexts(ctx, poolCtx)
	defer cancelCreate()
	defer p.wg.Done()
	vm, err := p.factory.CreateVM(createCtx)
	if err != nil {
		return zero, fmt.Errorf("create VM for allocation: %w", err)
	}

	p.mu.Lock()
	closing := p.closing
	p.mu.Unlock()
	if closing {
		if destroyErr := p.factory.DestroyVM(context.Background(), vm); destroyErr != nil {
			return zero, errors.Join(errors.New("warm VM pool is closing"), fmt.Errorf("destroy unallocated VM: %w", destroyErr))
		}
		return zero, errors.New("warm VM pool is closing")
	}
	return vm, nil
}

// Release returns a VM to the idle pool when reusable and capacity allows it;
// otherwise it destroys the VM. This lets callers recycle clean session VMs
// without letting the idle count exceed the configured target.
func (p *WarmPool[T]) Release(ctx context.Context, vm T, reusable bool) error {
	if ctx == nil {
		return errors.New("release context is required")
	}

	p.mu.Lock()
	if err := p.ensureRunningLocked(); err != nil {
		p.mu.Unlock()
		return err
	}
	if reusable && p.config.DesiredIdle > 0 {
		select {
		case p.idle <- vm:
			p.mu.Unlock()
			return nil
		default:
		}
	}
	p.wg.Add(1)
	p.mu.Unlock()

	defer p.wg.Done()
	if err := p.factory.DestroyVM(ctx, vm); err != nil {
		return fmt.Errorf("destroy released VM: %w", err)
	}
	p.TriggerReplenish()
	return nil
}

// TriggerReplenish schedules enough background creates to reach DesiredIdle.
func (p *WarmPool[T]) TriggerReplenish() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.replenishLocked()
}

// IdleCount returns the number of currently ready VMs.
func (p *WarmPool[T]) IdleCount() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return len(p.idle)
}

// CreatingCount returns the number of in-flight background pre-warm creates.
func (p *WarmPool[T]) CreatingCount() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.creating
}

// LastError returns the most recent background replenishment error, if any.
func (p *WarmPool[T]) LastError() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.lastErr
}

// Close stops background replenishment, waits for in-flight creates and release
// destroys, and destroys all idle VMs. It does not track VMs that were already
// returned to callers before Close began.
func (p *WarmPool[T]) Close(ctx context.Context) error {
	if ctx == nil {
		return errors.New("close context is required")
	}

	p.mu.Lock()
	if p.closing {
		p.mu.Unlock()
		return nil
	}
	p.closing = true
	if p.cancel != nil {
		p.cancel()
	}
	p.mu.Unlock()

	p.wg.Wait()

	p.mu.Lock()
	defer p.mu.Unlock()
	var errs []error
	for {
		select {
		case vm := <-p.idle:
			if err := p.factory.DestroyVM(ctx, vm); err != nil {
				errs = append(errs, err)
			}
		default:
			return errors.Join(errs...)
		}
	}
}

func (p *WarmPool[T]) ensureRunningLocked() error {
	if p.closing {
		return errors.New("warm VM pool is closing")
	}
	if !p.started {
		return errors.New("warm VM pool is not started")
	}
	return nil
}

func (p *WarmPool[T]) replenishLocked() {
	if !p.started || p.closing || p.config.DesiredIdle == 0 {
		return
	}
	for len(p.idle)+p.creating < p.config.DesiredIdle {
		p.creating++
		p.wg.Add(1)
		go p.createIdleVM(p.ctx)
	}
}

func (p *WarmPool[T]) createIdleVM(parent context.Context) {
	defer p.wg.Done()

	ctx := parent
	cancel := func() {}
	if p.config.CreateTimeout > 0 {
		ctx, cancel = context.WithTimeout(parent, p.config.CreateTimeout)
	}
	defer cancel()

	vm, err := p.factory.CreateVM(ctx)

	var destroyVM bool
	p.mu.Lock()
	p.creating--
	if err != nil {
		if p.closing || parent.Err() != nil {
			p.mu.Unlock()
			return
		}
		p.lastErr = fmt.Errorf("pre-warm VM: %w", err)
		if p.started {
			p.retryReplenishLocked(parent, p.config.ReplenishInterval)
		}
		p.mu.Unlock()
		return
	}
	if p.closing || parent.Err() != nil {
		destroyVM = true
	} else {
		p.lastErr = nil
		select {
		case p.idle <- vm:
		default:
			destroyVM = true
		}
	}
	p.mu.Unlock()

	if destroyVM {
		_ = p.factory.DestroyVM(context.Background(), vm)
	}
}

func (p *WarmPool[T]) retryReplenishLocked(ctx context.Context, delay time.Duration) {
	p.wg.Add(1)
	go func() {
		defer p.wg.Done()
		timer := time.NewTimer(delay)
		defer timer.Stop()
		select {
		case <-ctx.Done():
			return
		case <-timer.C:
			p.TriggerReplenish()
		}
	}()
}

func mergeContexts(parent, canceler context.Context) (context.Context, context.CancelFunc) {
	ctx, cancel := context.WithCancel(parent)
	stop := context.AfterFunc(canceler, cancel)
	return ctx, func() {
		stop()
		cancel()
	}
}
