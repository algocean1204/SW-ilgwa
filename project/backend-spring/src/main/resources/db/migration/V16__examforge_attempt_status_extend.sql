-- Copyright (c) EZ TEAM
-- V16__examforge_attempt_status_extend.sql
-- exam_attempt.status check constraint 확장 — GENERATING·FAILED·READY 추가
--
-- 배경: AttemptStatus enum(Java)에는 GENERATING/FAILED/READY가 정의돼 있으나
--      V14의 chk_ea_status 에 해당 값이 누락되어 INSERT 시 constraint violation(SYSTEM_001)이
--      발생함. enum 전체 값을 포함하도록 constraint를 재정의한다.
--
-- 안전성:
--   - 기존 행에 GENERATING/FAILED/READY 데이터가 없으므로 기존 데이터에 영향 없음.
--   - DROP CONSTRAINT + ADD CONSTRAINT는 DDL 잠금이 짧음(PostgreSQL 네이티브 CHECK는 테이블 수준 공유 잠금).
--   - Flyway 트랜잭션 안에서 실행되므로 실패 시 롤백 보장.

ALTER TABLE exam_attempt
    DROP CONSTRAINT IF EXISTS chk_ea_status;

ALTER TABLE exam_attempt
    ADD CONSTRAINT chk_ea_status CHECK (
        status IN (
            'CREATED',
            'GENERATING',
            'FAILED',
            'READY',
            'IN_PROGRESS',
            'SUBMITTED',
            'GRADED',
            'MANUAL_REVIEW_REQUIRED'
        )
    );
