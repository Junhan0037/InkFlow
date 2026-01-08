-- 업로드 세션과 Asset 메타데이터 확장 스키마를 정의한다.

-- 업로드 세션 메타데이터를 저장한다.
CREATE TABLE upload_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    upload_id VARCHAR(64) NOT NULL,
    episode_id BIGINT NOT NULL,
    creator_id VARCHAR(64) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    total_size BIGINT NOT NULL,
    uploaded_size BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    total_parts INT NOT NULL,
    chunk_size BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    storage_bucket VARCHAR(128) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    multipart_upload_id VARCHAR(128) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_upload_session_episode FOREIGN KEY (episode_id) REFERENCES episode (id),
    CONSTRAINT uq_upload_session_upload_id UNIQUE (upload_id)
);

-- 업로드 세션 조회 최적화를 위한 인덱스다.
CREATE INDEX idx_upload_session_status_expires_at ON upload_session (status, expires_at);

-- Asset 메타데이터 필드를 확장한다.
ALTER TABLE asset
    ADD COLUMN creator_id VARCHAR(64) NOT NULL DEFAULT 'system',
    ADD COLUMN upload_id VARCHAR(64) NOT NULL DEFAULT 'upl_unknown',
    ADD COLUMN storage_bucket VARCHAR(128) NOT NULL DEFAULT 'default',
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 기본값을 제거해 명시적 입력을 유도한다.
ALTER TABLE asset
    ALTER COLUMN creator_id DROP DEFAULT,
    ALTER COLUMN upload_id DROP DEFAULT,
    ALTER COLUMN storage_bucket DROP DEFAULT,
    ALTER COLUMN updated_at DROP DEFAULT;

-- 업로드 세션과 Asset의 연계를 보장한다.
ALTER TABLE asset
    ADD CONSTRAINT fk_asset_upload_session FOREIGN KEY (upload_id) REFERENCES upload_session (upload_id);

-- 업로드 ID 기준 조회 속도를 개선한다.
CREATE INDEX idx_asset_upload_id ON asset (upload_id);
