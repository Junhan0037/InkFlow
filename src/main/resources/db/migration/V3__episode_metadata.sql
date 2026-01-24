-- 에피소드 메타 자동 생성/승인 데이터를 저장한다.
CREATE TABLE episode_metadata (
    episode_id BIGINT PRIMARY KEY,
    summary TEXT NOT NULL,
    tags JSONB NOT NULL,
    approved_by VARCHAR(64) NOT NULL,
    approved_at TIMESTAMPTZ NOT NULL,
    version INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_episode_metadata_episode FOREIGN KEY (episode_id) REFERENCES episode (id) ON DELETE CASCADE
);

-- 메타 자동 생성 제안 히스토리를 저장한다.
CREATE TABLE episode_metadata_suggestion (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    summary TEXT NOT NULL,
    tags JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    generator VARCHAR(64) NOT NULL,
    requested_by VARCHAR(64) NOT NULL,
    reviewed_by VARCHAR(64),
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_episode_metadata_suggestion_episode FOREIGN KEY (episode_id) REFERENCES episode (id) ON DELETE CASCADE
);

-- 에피소드별 상태 조회를 위한 인덱스를 추가한다.
CREATE INDEX idx_episode_metadata_suggestion_episode_status
    ON episode_metadata_suggestion (episode_id, status);
