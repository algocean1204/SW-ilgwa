-- 채점 결과 약점 프로필 저장소.
-- FastAPI search_path에 chapter_studio가 없으므로 서비스 SQL은 스키마를 명시한다.

CREATE TABLE IF NOT EXISTS chapter_studio.weakness_profile (
    id            varchar PRIMARY KEY,
    user_id       varchar NOT NULL,
    topic         varchar NOT NULL,
    subtopic      varchar,
    error_pattern text,
    frequency     integer NOT NULL DEFAULT 1,
    severity      integer NOT NULL DEFAULT 1,
    first_seen_at timestamptz NOT NULL DEFAULT NOW(),
    last_seen_at  timestamptz NOT NULL DEFAULT NOW(),
    resolved_at   timestamptz
);

-- subtopic이 있는 약점은 사용자·토픽·하위토픽 조합으로 누적 갱신한다.
CREATE UNIQUE INDEX IF NOT EXISTS ux_weakness_user_topic_subtopic
    ON chapter_studio.weakness_profile (user_id, topic, subtopic)
    WHERE subtopic IS NOT NULL;

-- 미해결 약점 목록은 사용자 기준으로 자주 조회한다.
CREATE INDEX IF NOT EXISTS ix_weakness_user_unresolved
    ON chapter_studio.weakness_profile (user_id) WHERE resolved_at IS NULL;
