-- ChapterStudio_V1 프론트 삽입형 렌더링 계약 보강.
-- 슬라이드 완성본은 iframe_html로 저장하고, 도메인 계층은 tutoring_id/lesson_id로 연결한다.

ALTER TABLE chapter_studio.slide
    ADD COLUMN IF NOT EXISTS tutoring_id VARCHAR,
    ADD COLUMN IF NOT EXISTS lesson_id VARCHAR,
    ADD COLUMN IF NOT EXISTS slide_idx INTEGER,
    ADD COLUMN IF NOT EXISTS title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS template_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS iframe_html TEXT,
    ADD COLUMN IF NOT EXISTS semantic_meta JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS validation_status VARCHAR(32) DEFAULT 'pending' NOT NULL,
    ADD COLUMN IF NOT EXISTS design_version VARCHAR(32) DEFAULT 'v1' NOT NULL,
    ADD COLUMN IF NOT EXISTS audio_url VARCHAR,
    ADD COLUMN IF NOT EXISTS cue_start_sec REAL,
    ADD COLUMN IF NOT EXISTS cue_end_sec REAL,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

UPDATE chapter_studio.slide
   SET slide_idx = index
 WHERE slide_idx IS NULL;

CREATE INDEX IF NOT EXISTS idx_chapter_studio_slide_lesson_order
    ON chapter_studio.slide (lesson_id, slide_idx);

CREATE UNIQUE INDEX IF NOT EXISTS uq_chapter_studio_slide_lesson_order
    ON chapter_studio.slide (lesson_id, slide_idx)
 WHERE lesson_id IS NOT NULL AND slide_idx IS NOT NULL;

ALTER TABLE chapter_studio.quiz
    ADD COLUMN IF NOT EXISTS tutoring_id VARCHAR,
    ADD COLUMN IF NOT EXISTS lesson_id VARCHAR,
    ADD COLUMN IF NOT EXISTS quiz_idx INTEGER,
    ADD COLUMN IF NOT EXISTS difficulty VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source_slide_ids JSONB DEFAULT '[]'::jsonb;

UPDATE chapter_studio.quiz
   SET difficulty = depth
 WHERE difficulty IS NULL;

CREATE INDEX IF NOT EXISTS idx_chapter_studio_quiz_lesson_order
    ON chapter_studio.quiz (lesson_id, quiz_idx);

ALTER TABLE chapter_studio.note
    ADD COLUMN IF NOT EXISTS tutoring_id VARCHAR,
    ADD COLUMN IF NOT EXISTS lesson_id VARCHAR,
    ADD COLUMN IF NOT EXISTS summary_json JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT NOW();

CREATE INDEX IF NOT EXISTS idx_chapter_studio_note_lesson
    ON chapter_studio.note (lesson_id);

ALTER TABLE chapter_studio.assignment
    ADD COLUMN IF NOT EXISTS tutoring_id VARCHAR,
    ADD COLUMN IF NOT EXISTS lesson_id VARCHAR,
    ADD COLUMN IF NOT EXISTS title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS expected_input_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS allowed_extensions JSONB DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS display_html TEXT;

CREATE INDEX IF NOT EXISTS idx_chapter_studio_assignment_lesson
    ON chapter_studio.assignment (lesson_id);

ALTER TABLE chapter_studio.voice_script
    ADD COLUMN IF NOT EXISTS tutoring_id VARCHAR,
    ADD COLUMN IF NOT EXISTS lesson_id VARCHAR,
    ADD COLUMN IF NOT EXISTS slide_id VARCHAR,
    ADD COLUMN IF NOT EXISTS script_text TEXT,
    ADD COLUMN IF NOT EXISTS tone VARCHAR(80);

UPDATE chapter_studio.voice_script
   SET script_text = text
 WHERE script_text IS NULL;

CREATE INDEX IF NOT EXISTS idx_chapter_studio_voice_script_lesson_order
    ON chapter_studio.voice_script (lesson_id, slide_index);

ALTER TABLE chapter_studio.chapter_audio
    ADD COLUMN IF NOT EXISTS tutoring_id VARCHAR,
    ADD COLUMN IF NOT EXISTS lesson_id VARCHAR,
    ADD COLUMN IF NOT EXISTS cue_points JSONB DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_chapter_studio_audio_lesson
    ON chapter_studio.chapter_audio (lesson_id);

CREATE TABLE IF NOT EXISTS chapter_studio.curriculum_plan (
    id              VARCHAR PRIMARY KEY,
    tutoring_id     VARCHAR      NOT NULL,
    user_id         VARCHAR      NOT NULL,
    title           VARCHAR(255) NOT NULL,
    source_type     VARCHAR(32)  NOT NULL,
    source_ref      VARCHAR,
    status          VARCHAR(32)  NOT NULL DEFAULT 'draft',
    planner_model   VARCHAR(120) NOT NULL,
    created_at      TIMESTAMPTZ  DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chapter_studio_curriculum_plan_tutoring
    ON chapter_studio.curriculum_plan (tutoring_id, status);

CREATE TABLE IF NOT EXISTS chapter_studio.curriculum_unit (
    id                 VARCHAR PRIMARY KEY,
    curriculum_plan_id VARCHAR      NOT NULL REFERENCES chapter_studio.curriculum_plan (id),
    lesson_id          VARCHAR,
    order_index        INTEGER      NOT NULL,
    title              VARCHAR(255) NOT NULL,
    summary            TEXT         NOT NULL,
    learning_goal      TEXT,
    slide_count        INTEGER      NOT NULL,
    estimated_minutes  INTEGER      NOT NULL,
    status             VARCHAR(32)  NOT NULL DEFAULT 'draft',
    created_at         TIMESTAMPTZ  DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  DEFAULT NOW(),
    UNIQUE (curriculum_plan_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_chapter_studio_curriculum_unit_plan_order
    ON chapter_studio.curriculum_unit (curriculum_plan_id, order_index);

CREATE INDEX IF NOT EXISTS idx_chapter_studio_curriculum_unit_lesson
    ON chapter_studio.curriculum_unit (lesson_id);

CREATE TABLE IF NOT EXISTS chapter_studio.lesson_generation_status (
    lesson_id        VARCHAR PRIMARY KEY,
    tutoring_id      VARCHAR,
    status           VARCHAR(32) NOT NULL,
    current_node     VARCHAR(80),
    completed_nodes  INTEGER     NOT NULL DEFAULT 0,
    total_nodes      INTEGER     NOT NULL DEFAULT 11,
    progress_percent INTEGER     NOT NULL DEFAULT 0,
    error_message    TEXT,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_chapter_studio_generation_status
    ON chapter_studio.lesson_generation_status (tutoring_id, status);
