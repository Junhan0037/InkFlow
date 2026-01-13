-- Outbox 재시도 정책을 위한 컬럼을 추가한다.
ALTER TABLE outbox_event
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMPTZ,
    ADD COLUMN last_error TEXT;

-- 재시도 대상 이벤트 조회를 최적화한다.
CREATE INDEX idx_outbox_event_status_retry_at
    ON outbox_event (status, next_retry_at, created_at);
