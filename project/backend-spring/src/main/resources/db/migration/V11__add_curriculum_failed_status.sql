-- course.status CHECK 제약에 CURRICULUM_FAILED 추가.
-- FastAPI mark_course_curriculum_failed()가 커리큘럼 생성 실패 시 이 값으로 전이하는데,
-- 기존 chk_crs_status에 누락되어 DB 제약 위반이 발생했다.
-- Flyway는 적용된 마이그레이션 수정을 허용하지 않으므로 신규 파일로 DROP 후 재생성한다.

-- 현재 제약 정의 백업 (롤백 참고용):
-- CONSTRAINT chk_crs_status CHECK (status IN (
--   'CURRICULUM_GENERATING','CURRICULUM_READY','ACTIVE','COMPLETED','PAUSED','DELETED'
-- ))

-- 기존 CHECK 제약을 삭제하고 CURRICULUM_FAILED를 포함한 제약으로 재생성한다.
-- 멱등성: 제약이 이미 없는 경우에도 안전하게 DROP 후 재생성한다.
ALTER TABLE course DROP CONSTRAINT IF EXISTS chk_crs_status;

ALTER TABLE course
    ADD CONSTRAINT chk_crs_status CHECK (status IN (
        'CURRICULUM_GENERATING',
        'CURRICULUM_READY',
        'CURRICULUM_FAILED',
        'ACTIVE',
        'COMPLETED',
        'PAUSED',
        'DELETED'
    ));
