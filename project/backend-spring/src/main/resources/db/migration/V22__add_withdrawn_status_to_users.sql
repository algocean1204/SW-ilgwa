-- 회원 탈퇴(소프트 삭제) 지원
-- 1) status 체크 제약에 WITHDRAWN 추가 (V1 chk_users_status: ACTIVE/SUSPENDED → +WITHDRAWN)
-- 2) 탈퇴 시각 컬럼 추가 (null = 미탈퇴)
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_status;
ALTER TABLE users
  ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'));

ALTER TABLE users ADD COLUMN withdrawn_at TIMESTAMP;
