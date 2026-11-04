-- Copyright (c) EZ TEAM
-- V21__examforge_attempt_generation_outcome.sql
-- exam_attempt에 생성 결과 보조 컬럼 3개 추가 (additive only — 기존 컬럼·제약 변경 없음)
--
-- 배경: FastAPI 모의고사 생성 콜백이 좁은 소스에서 0문항 FAILED 대신
--       generationOutcome(passed/partial_success/needs_more_source/failed)을 보낼 수 있게 바뀐다.
--       Spring은 이를 graceful 하게 수용한다:
--         - passed/partial_success → READY (부분 출고 허용, 요청/생성 문항 수 보존)
--         - needs_more_source      → FAILED + failure_reason='NEEDS_MORE_SOURCE' (자료 부족, 비-에러)
--         - failed / 0문항          → FAILED + failure_reason='GENERATION_ERROR'
--
-- 설계 선택: needs_more_source를 AttemptStatus(상태) 신규 값이 아닌 failure_reason 사유 코드로 처리한다.
--           상태 enum 추가는 chk_ea_status 재정의(V16류)·제출 가드·폴링 응답을 동시에 손대야 하므로
--           스키마 영향을 최소화하기 위해 nullable 사유 코드 컬럼만 추가하고 FAILED 흐름을 재사용한다.
--
-- 안전성:
--   - 세 컬럼 모두 NULL 허용 + ADD COLUMN IF NOT EXISTS → 기존 행·기존 콜백(필드 미전송)에 영향 없음.
--   - status·chk_ea_status는 그대로 두므로 CHECK constraint 재정의가 없다.

ALTER TABLE exam_attempt
    ADD COLUMN IF NOT EXISTS requested_question_count INTEGER NULL;

ALTER TABLE exam_attempt
    ADD COLUMN IF NOT EXISTS actual_question_count INTEGER NULL;

ALTER TABLE exam_attempt
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(40) NULL;

COMMENT ON COLUMN exam_attempt.requested_question_count IS '사용자가 요청한 문항 수 — 프론트 "요청 N / 생성 M" 표기용, 레거시/미생성 시 NULL';
COMMENT ON COLUMN exam_attempt.actual_question_count IS '실제 생성된 문항 수 — 부분 출고 시 요청보다 적음, 레거시/미생성 시 NULL';
COMMENT ON COLUMN exam_attempt.failure_reason IS 'FAILED 사유 구분 코드 — NEEDS_MORE_SOURCE(자료 부족)/GENERATION_ERROR. FAILED 아니거나 사유 미상 시 NULL';
