-- V18: exam_grade_result에 AI 총평(analysis) 컬럼 추가
--
-- 채점 직후 FastAPI /api/mock-exams/analyze 로 생성한 총평(JSON: {"summary","weakTopics"})을
-- 저장한다. 결과 조회(AttemptResultResponse)에 노출되고, mockexam 도메인 리스너가
-- mock_exam_submission.analysis 로 복제해 ChapterStudio 약점 집계가 읽는다.
-- 분석 실패 시 NULL 허용 (graceful degrade — 채점 자체는 성공 유지).
ALTER TABLE exam_grade_result
    ADD COLUMN IF NOT EXISTS analysis TEXT NULL;

COMMENT ON COLUMN exam_grade_result.analysis IS 'AI 총평 JSON {"summary": 총평본문, "weakTopics": [약점주제]} — 분석 실패 시 NULL';
