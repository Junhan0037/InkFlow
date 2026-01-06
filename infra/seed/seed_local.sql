-- InkFlow 로컬 시드 데이터(PostgreSQL).
-- 로컬 개발용으로 반복 실행해도 안전하다.

BEGIN;

-- Work 시드 데이터.
INSERT INTO work (id, title, creator_id, status, default_language, created_at, updated_at)
VALUES
    (1001, 'Seed Work Alpha', 'seed-creator-1', 'ACTIVE', 'ko', NOW(), NOW()),
    (1002, 'Seed Work Beta', 'seed-creator-2', 'DRAFT', 'en', NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    title = EXCLUDED.title,
    creator_id = EXCLUDED.creator_id,
    status = EXCLUDED.status,
    default_language = EXCLUDED.default_language,
    updated_at = NOW();

-- Episode 시드 데이터.
INSERT INTO episode (id, work_id, title, seq, published_at, created_at, updated_at)
VALUES
    (2001, 1001, 'Episode 1', 1, NULL, NOW(), NOW()),
    (2002, 1001, 'Episode 2', 2, NULL, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    work_id = EXCLUDED.work_id,
    title = EXCLUDED.title,
    seq = EXCLUDED.seq,
    published_at = EXCLUDED.published_at,
    updated_at = NOW();

-- WorkflowState 시드 데이터.
INSERT INTO workflow_state (episode_id, state, version, updated_at)
VALUES
    (2001, 'DRAFT', 1, NOW()),
    (2002, 'SUBMITTED', 1, NOW())
ON CONFLICT (episode_id) DO UPDATE SET
    state = EXCLUDED.state,
    version = EXCLUDED.version,
    updated_at = NOW();

-- Asset 시드 데이터.
INSERT INTO asset (id, episode_id, filename, content_type, size, checksum, storage_key, status, created_at)
VALUES
    (3001, 2001, 'episode-1.zip', 'application/zip', 10485760, 'sha256:seed-asset-1', 'seed/episode-1.zip', 'STORED', NOW()),
    (3002, 2002, 'episode-2.zip', 'application/zip', 20971520, 'sha256:seed-asset-2', 'seed/episode-2.zip', 'STORED', NOW())
ON CONFLICT (id) DO UPDATE SET
    episode_id = EXCLUDED.episode_id,
    filename = EXCLUDED.filename,
    content_type = EXCLUDED.content_type,
    size = EXCLUDED.size,
    checksum = EXCLUDED.checksum,
    storage_key = EXCLUDED.storage_key,
    status = EXCLUDED.status,
    created_at = EXCLUDED.created_at;

-- Derivative 시드 데이터.
INSERT INTO derivative (id, asset_id, type, width, height, format, storage_key, status, created_at)
VALUES
    (4001, 3001, 'THUMBNAIL', 320, 180, 'jpg', 'seed/episode-1-thumb.jpg', 'READY', NOW()),
    (4002, 3002, 'THUMBNAIL', 320, 180, 'jpg', 'seed/episode-2-thumb.jpg', 'READY', NOW())
ON CONFLICT (id) DO UPDATE SET
    asset_id = EXCLUDED.asset_id,
    type = EXCLUDED.type,
    width = EXCLUDED.width,
    height = EXCLUDED.height,
    format = EXCLUDED.format,
    storage_key = EXCLUDED.storage_key,
    status = EXCLUDED.status,
    created_at = EXCLUDED.created_at;

-- 폴링 흐름 검증용 OutboxEvent 시드 데이터.
INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, status, created_at, sent_at)
VALUES
    (
        '00000000-0000-0000-0000-000000000001',
        'Episode',
        '2002',
        'EPISODE_SUBMITTED.v1',
        jsonb_build_object(
            'episodeId', 2002,
            'workId', 1001,
            'submitterId', 'seed-creator-1',
            'deadline', '2025-02-01T00:00:00Z'
        ),
        'PENDING',
        NOW(),
        NULL
    )
ON CONFLICT (id) DO UPDATE SET
    aggregate_type = EXCLUDED.aggregate_type,
    aggregate_id = EXCLUDED.aggregate_id,
    event_type = EXCLUDED.event_type,
    payload = EXCLUDED.payload,
    status = EXCLUDED.status,
    created_at = EXCLUDED.created_at,
    sent_at = EXCLUDED.sent_at;

-- 시드 데이터에 맞춰 시퀀스를 정렬한다.
SELECT setval('work_id_seq', COALESCE((SELECT MAX(id) FROM work), 1), true);
SELECT setval('episode_id_seq', COALESCE((SELECT MAX(id) FROM episode), 1), true);
SELECT setval('asset_id_seq', COALESCE((SELECT MAX(id) FROM asset), 1), true);
SELECT setval('derivative_id_seq', COALESCE((SELECT MAX(id) FROM derivative), 1), true);

COMMIT;
