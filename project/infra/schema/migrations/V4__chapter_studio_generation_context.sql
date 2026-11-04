-- ChapterStudio_V1 강의 생성 입력 스냅샷.
-- Spring이 권한 확인 후 저장하고 FastAPI는 lesson_id로 읽는다.

ALTER TABLE chapter_studio.lesson_generation_status
    ADD COLUMN IF NOT EXISTS generation_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS requested_template VARCHAR(80) NOT NULL DEFAULT 'auto',
    ADD COLUMN IF NOT EXISTS requested_slide_count INTEGER;

ALTER TABLE chapter_studio.lesson_generation_status
    ADD CONSTRAINT chk_chapter_studio_generation_context_object
    CHECK (jsonb_typeof(generation_context) = 'object');

ALTER TABLE chapter_studio.lesson_generation_status
    ADD CONSTRAINT chk_chapter_studio_requested_slide_count
    CHECK (requested_slide_count IS NULL OR requested_slide_count BETWEEN 10 AND 15);

CREATE INDEX IF NOT EXISTS idx_chapter_studio_generation_context_gin
    ON chapter_studio.lesson_generation_status USING GIN (generation_context);
