-- Introduce DISPATCHING as an atomic dispatch-claim state to prevent duplicate Blocks session creation.
-- execution_state is VARCHAR(30) — no column-type migration required.
-- A worker atomically transitions CREATED → DISPATCHING before calling the Blocks API.
-- Only the worker that succeeds in that transition may call blocksAdapter.createSession().
-- On success: DISPATCHING → PLANNING (provider_session_id is set simultaneously).
-- On retryable failure: DISPATCHING → CREATED so the next poll cycle may retry.
-- On terminal failure: DISPATCHING → FAILED.

COMMENT ON COLUMN blocks_sessions.execution_state IS
    'State machine: CREATED → DISPATCHING → PLANNING → EXECUTING → WAITING_REVIEW → PR_OPENED → DONE | FAILED | CANCELED. '
    'DISPATCHING is an atomic dispatch-claim state: only the worker that wins the CREATED→DISPATCHING '
    'CAS update may call the Blocks API, preventing duplicate provider session creation across pods.';
