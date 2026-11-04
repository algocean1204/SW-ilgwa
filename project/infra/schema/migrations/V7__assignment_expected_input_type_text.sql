-- assignment.expected_input_type VARCHAR(40) → TEXT 확장
-- LLM이 생성한 값이 40자를 초과해 VARCHAR(40) 제약 위반 → persist_chapter_state 실패 → 강의 생성 차단 버그 수정
-- DB에는 이미 ALTER TABLE을 통해 TEXT로 변경 완료된 상태이므로, Flyway 이력에 정식 등록한다.
-- IF NOT EXISTS 계열 DDL로 멱등성(idempotent) 보장 — 이미 TEXT인 환경에서도 안전하게 실행된다.

DO $$
BEGIN
    -- 컬럼이 text가 아닌 경우에만 변경 (멱등성 보장)
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'chapter_studio'
          AND table_name = 'assignment'
          AND column_name = 'expected_input_type'
          AND data_type != 'text'
    ) THEN
        ALTER TABLE chapter_studio.assignment
            ALTER COLUMN expected_input_type TYPE TEXT;
    END IF;
END $$;
