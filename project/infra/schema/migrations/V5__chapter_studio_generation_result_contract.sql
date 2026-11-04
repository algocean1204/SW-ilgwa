-- ChapterStudio_V1 생성 결과 저장과 재시도 계약.
-- 기존 V2~V4는 수정하지 않고 결과 저장에 필요한 상태/검증 컬럼만 보강한다.

ALTER TABLE chapter_studio.lesson_generation_status
    ADD COLUMN IF NOT EXISTS chapter_id VARCHAR,
    ADD COLUMN IF NOT EXISTS generation_model VARCHAR(120),
    ADD COLUMN IF NOT EXISTS result_summary JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE chapter_studio.lesson_generation_status
    ADD CONSTRAINT chk_chapter_studio_result_summary_object
    CHECK (jsonb_typeof(result_summary) = 'object') NOT VALID;

ALTER TABLE chapter_studio.lesson_generation_status
    ADD CONSTRAINT chk_chapter_studio_generation_status_value
    CHECK (status IN ('queued', 'running', 'persisting', 'done', 'failed')) NOT VALID;

ALTER TABLE chapter_studio.slide
    ADD CONSTRAINT chk_chapter_studio_slide_idx_range
    CHECK (slide_idx IS NULL OR slide_idx BETWEEN 0 AND 14) NOT VALID;

ALTER TABLE chapter_studio.quiz
    ADD CONSTRAINT chk_chapter_studio_quiz_idx_range
    CHECK (quiz_idx IS NULL OR quiz_idx BETWEEN 0 AND 14) NOT VALID;

ALTER TABLE chapter_studio.voice_script
    ADD CONSTRAINT chk_chapter_studio_voice_slide_index_range
    CHECK (slide_index BETWEEN 0 AND 14) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_chapter_studio_generation_status_chapter
    ON chapter_studio.lesson_generation_status (chapter_id);

CREATE INDEX IF NOT EXISTS idx_chapter_studio_slide_lesson_validation
    ON chapter_studio.slide (lesson_id, validation_status, slide_idx);

CREATE INDEX IF NOT EXISTS idx_chapter_studio_quiz_lesson_difficulty
    ON chapter_studio.quiz (lesson_id, difficulty);
