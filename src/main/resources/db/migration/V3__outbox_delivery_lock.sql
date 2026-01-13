-- Outbox 전송 중 잠금 시각을 기록한다.
ALTER TABLE outbox_event
    ADD COLUMN locked_at TIMESTAMPTZ;

-- 전송 대상 조회를 위한 잠금 인덱스를 추가한다.
CREATE INDEX idx_outbox_event_status_lock_retry
    ON outbox_event (status, locked_at, next_retry_at, created_at);
