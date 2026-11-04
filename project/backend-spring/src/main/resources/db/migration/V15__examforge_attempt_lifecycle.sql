-- Copyright (c) EZ TEAM
-- V15__examforge_attempt_lifecycle.sql
-- ExamForge — exam_attempt에 mock_exam 브리지 컬럼 추가 (additive only)
-- 기존 V1~V14 테이블 구조 변경 없음. 데이터 손실 0, 락 최소.

-- mock_exam.id 타입: V1 기준 VARCHAR(30), mex_ 접두사 패턴
-- 독립 응시(모의고사 카드 없이 생성) 시 NULL 허용 — 브리지 역할이므로 nullable 설계
ALTER TABLE exam_attempt
    ADD COLUMN mock_exam_id VARCHAR(30) NULL;

-- mock_exam 삭제 시 SET NULL — 응시 기록을 보존하면서 참조만 해제
ALTER TABLE exam_attempt
    ADD CONSTRAINT fk_ea_mock_exam_id
        FOREIGN KEY (mock_exam_id)
            REFERENCES mock_exam (id)
            ON DELETE SET NULL;

-- mock_exam_id 기반 조회(응시 이력 집계, 모의고사별 통계) 최적화 인덱스
CREATE INDEX idx_ea_mock_exam_id ON exam_attempt (mock_exam_id);
