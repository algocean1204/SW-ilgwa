-- V17: 구독 플랜별 코인 지급량 필드 추가 + coin_transaction 유형 확장
-- 목적:
--   (1) subscription_plan 테이블에 coin_grant 컬럼 추가 (구독 활성화 시 즉시 지급 코인 수)
--   (2) coin_transaction.type CHECK 제약에 'subscription_grant' 값 추가
-- 환산 기준: 1원 = 1코인 (pro=49900, premium=99900). free는 0코인.

-- 1. subscription_plan 에 coin_grant 컬럼 추가 (기본값 0, NOT NULL)
ALTER TABLE subscription_plan
    ADD COLUMN coin_grant INTEGER NOT NULL DEFAULT 0;

-- 2. 기존 시드 데이터 coin_grant 값 반영
UPDATE subscription_plan SET coin_grant = 0     WHERE code = 'free';
UPDATE subscription_plan SET coin_grant = 49900 WHERE code = 'pro';
UPDATE subscription_plan SET coin_grant = 99900 WHERE code = 'premium';

-- 3. coin_transaction.type CHECK 제약 확장 (기존 4종 → 5종)
--    PostgreSQL은 CHECK 제약을 ALTER로 교체할 수 없으므로 DROP + ADD 순서로 처리한다.
ALTER TABLE coin_transaction DROP CONSTRAINT IF EXISTS chk_ctx_type;
ALTER TABLE coin_transaction
    ADD CONSTRAINT chk_ctx_type
        CHECK (type IN ('purchase', 'use_slide', 'use_exam', 'refund', 'subscription_grant'));
