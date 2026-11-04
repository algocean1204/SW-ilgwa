-- ChapterStudio_V1 7 테이블. SSOT는 Test_FastAPI_Module/MainAI_docs/09_Database.md.
-- 공용 sw_ez DB 안에서 다른 도메인과 충돌하지 않도록 전용 schema를 사용한다.

CREATE SCHEMA IF NOT EXISTS chapter_studio;

CREATE TABLE IF NOT EXISTS chapter_studio.slide (
    id          VARCHAR PRIMARY KEY,
    chapter_id  VARCHAR        NOT NULL,
    index       INTEGER        NOT NULL,
    category    VARCHAR(64)    NOT NULL,
    html        TEXT           NOT NULL,
    css         TEXT           NOT NULL,
    created_at  TIMESTAMPTZ    DEFAULT NOW()
);

-- 강의별 슬라이드 조회는 chapter_id와 index 조합이 기본 경로다.
CREATE INDEX IF NOT EXISTS idx_chapter_studio_slide_chapter
    ON chapter_studio.slide (chapter_id, index);

CREATE TABLE IF NOT EXISTS chapter_studio.voice_script (
    id                 VARCHAR PRIMARY KEY,
    chapter_id         VARCHAR      NOT NULL,
    slide_index        INTEGER      NOT NULL,
    text               TEXT         NOT NULL,
    duration_hint_sec  REAL,
    audio_url          VARCHAR,
    created_at         TIMESTAMPTZ  DEFAULT NOW()
);

-- 슬라이드 순서대로 음성 대본을 읽어야 하므로 복합 인덱스를 둔다.
CREATE INDEX IF NOT EXISTS idx_chapter_studio_voice_script_chapter
    ON chapter_studio.voice_script (chapter_id, slide_index);

CREATE TABLE IF NOT EXISTS chapter_studio.quiz (
    id           VARCHAR PRIMARY KEY,
    chapter_id   VARCHAR      NOT NULL,
    question     TEXT         NOT NULL,
    choices      JSONB        NOT NULL,
    answer_idx   INTEGER      NOT NULL,
    explanation  TEXT         NOT NULL,
    depth        VARCHAR(32)  NOT NULL,
    created_at   TIMESTAMPTZ  DEFAULT NOW()
);

-- 강의 단위 퀴즈 조회가 기본 조회 패턴이다.
CREATE INDEX IF NOT EXISTS idx_chapter_studio_quiz_chapter
    ON chapter_studio.quiz (chapter_id);

CREATE TABLE IF NOT EXISTS chapter_studio.note (
    id          VARCHAR PRIMARY KEY,
    chapter_id  VARCHAR      NOT NULL UNIQUE,
    html        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chapter_studio.assignment (
    id                VARCHAR PRIMARY KEY,
    chapter_id        VARCHAR      NOT NULL,
    prompt            TEXT         NOT NULL,
    criteria          JSONB        NOT NULL,
    expected_minutes  INTEGER      NOT NULL,
    -- 과제 난이도: 상/중상/중 등 (기본값 "상")
    difficulty_level  VARCHAR(16)  NOT NULL DEFAULT '상',
    -- 이 과제가 테스트하는 핵심 개념 목록
    target_concepts   JSONB        NOT NULL DEFAULT '[]',
    -- 사용자 약점 타겟 요소 목록
    weakness_focus    JSONB        NOT NULL DEFAULT '[]',
    created_at        TIMESTAMPTZ  DEFAULT NOW()
);

-- 과제는 강의 단위로 조회한다.
CREATE INDEX IF NOT EXISTS idx_chapter_studio_assignment_chapter
    ON chapter_studio.assignment (chapter_id);

CREATE TABLE IF NOT EXISTS chapter_studio.chapter_audio (
    id                VARCHAR PRIMARY KEY,
    chapter_id        VARCHAR      NOT NULL UNIQUE,
    merged_audio_url  VARCHAR      NOT NULL,
    duration_sec      REAL         NOT NULL,
    created_at        TIMESTAMPTZ  DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chapter_studio.voice_script_queue (
    id               VARCHAR PRIMARY KEY,
    voice_script_id  VARCHAR      NOT NULL REFERENCES chapter_studio.voice_script (id),
    status           VARCHAR(16)  NOT NULL,
    enqueued_at      TIMESTAMPTZ  DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

-- TTS 워커가 대기 작업을 시간 순서로 가져간다.
CREATE INDEX IF NOT EXISTS idx_chapter_studio_queue_status
    ON chapter_studio.voice_script_queue (status, enqueued_at);
