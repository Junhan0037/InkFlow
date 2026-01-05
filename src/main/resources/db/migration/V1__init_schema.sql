-- InkFlow용 Flyway 초기 스키마(PostgreSQL).
-- 핵심 도메인 테이블과 Outbox 패턴을 초기화한다.

-- Outbox 이벤트용 UUID 생성 확장 활성화.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Work는 콘텐츠 최상위 애그리게이트 루트다.
CREATE TABLE work (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    creator_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    default_language VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    -- creator별 중복 제목을 방지한다.
    CONSTRAINT uq_work_creator_title UNIQUE (creator_id, title)
);

-- Episode는 Work에 속하며 퍼블리시 가능한 단위다.
CREATE TABLE episode (
    id BIGSERIAL PRIMARY KEY,
    work_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    seq INT NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_episode_work FOREIGN KEY (work_id) REFERENCES work (id),
    -- Work 내 회차 순번 중복을 방지한다.
    CONSTRAINT uq_episode_work_seq UNIQUE (work_id, seq)
);

-- Asset은 업로드된 원본 파일 메타데이터를 보관한다.
CREATE TABLE asset (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_asset_episode FOREIGN KEY (episode_id) REFERENCES episode (id),
    -- Episode 내 동일 체크섬 중복 업로드를 방지한다.
    CONSTRAINT uq_asset_episode_checksum UNIQUE (episode_id, checksum)
);

-- Derivative는 파생 리소스(썸네일/리사이즈 등) 메타데이터를 보관한다.
CREATE TABLE derivative (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    width INT,
    height INT,
    format VARCHAR(32) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_derivative_asset FOREIGN KEY (asset_id) REFERENCES asset (id),
    -- 동일 사양 파생본의 중복 생성을 방지한다.
    CONSTRAINT uq_derivative_spec UNIQUE (asset_id, type, width, height, format)
);

-- WorkflowState는 낙관적 버전으로 현재 상태를 추적한다.
CREATE TABLE workflow_state (
    episode_id BIGINT PRIMARY KEY,
    state VARCHAR(32) NOT NULL,
    version INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_state_episode FOREIGN KEY (episode_id) REFERENCES episode (id) ON DELETE CASCADE
);

-- OutboxEvent는 이벤트 발행 정합성을 보장한다.
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    -- 중복 발행을 줄이기 위한 권장 유니크 제약이다.
    CONSTRAINT uq_outbox_event UNIQUE (aggregate_type, aggregate_id, event_type, created_at)
);

-- 상태/시간 기준 Outbox 폴링을 최적화한다.
CREATE INDEX idx_outbox_event_status_created_at ON outbox_event (status, created_at);
