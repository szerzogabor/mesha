-- Idempotent recovery of V35 (schema already applied on production via original V35).
-- COMMENT ON is idempotent — subsequent runs simply overwrite the previous comment.
COMMENT ON COLUMN blocks_sessions.execution_state IS
    'State machine: CREATED → DISPATCHING → PLANNING → EXECUTING → WAITING_REVIEW → PR_OPENED → DONE | FAILED | CANCELED. '
    'DISPATCHING is an atomic dispatch-claim state: only the worker that wins the CREATED→DISPATCHING '
    'CAS update may call the Blocks API, preventing duplicate provider session creation across pods.';
