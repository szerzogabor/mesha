-- Blocks webhook event ingestion table for replay and debugging

CREATE TABLE blocks_webhook_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id VARCHAR(255) NOT NULL UNIQUE,
    event_type  VARCHAR(100) NOT NULL,
    payload     TEXT         NOT NULL,
    processed   BOOLEAN      NOT NULL DEFAULT FALSE,
    error       TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_blocks_webhook_events_delivery_id ON blocks_webhook_events(delivery_id);
CREATE INDEX idx_blocks_webhook_events_event_type  ON blocks_webhook_events(event_type);
CREATE INDEX idx_blocks_webhook_events_created_at  ON blocks_webhook_events(created_at);
