-- 퍼블리시 스냅샷 버전 메타데이터 스키마를 정의한다.

CREATE TABLE publish_version (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    snapshot_id VARCHAR(64) NOT NULL,
    region VARCHAR(32) NOT NULL,
    language VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    rolled_back_at TIMESTAMPTZ,
    CONSTRAINT fk_publish_version_episode FOREIGN KEY (episode_id) REFERENCES episode (id),
    CONSTRAINT uq_publish_version_episode_version UNIQUE (episode_id, version),
    CONSTRAINT uq_publish_version_snapshot_id UNIQUE (snapshot_id),
    CONSTRAINT uq_publish_version_episode_request UNIQUE (episode_id, request_id)
);

-- 에피소드별 활성 버전 조회를 빠르게 하기 위한 인덱스다.
CREATE INDEX idx_publish_version_episode_status ON publish_version (episode_id, status);

-- 스냅샷 ID 기반 조회를 최적화한다.
CREATE INDEX idx_publish_version_snapshot_id ON publish_version (snapshot_id);
