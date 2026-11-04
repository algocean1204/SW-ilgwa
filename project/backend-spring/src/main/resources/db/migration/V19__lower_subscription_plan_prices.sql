-- V19: 구독 요금제 인하
-- 목적:
--   (1) pro: 49,900원 → 14,900원, 과외 3개 → 2개
--   (2) premium: 99,900원 → 49,900원, 과외 8개 → 10개
--   (3) 코인 지급 기준 변경: 기존 1원=1코인 환산(V17) → 플랜별 명시 지급량 (pro 15,000 / premium 50,000)
--   (4) features 문구를 새 한도·코인 지급량 기준으로 갱신 (프론트 plansData 폴백과 동일 문구 유지)
-- free 플랜과 코인 번들(COIN_5/20/60 구매 팩)은 변경하지 않는다.

UPDATE subscription_plan
SET price_krw   = 14900,
    max_courses = 2,
    coin_grant  = 15000,
    features    = '["과외 2개 — 강의자료 전체 무제한 해제","코인 15,000 즉시 지급","텔레그램 암기노트·과제·채점·총평","나만의 튜터 커스터마이징","학습 리포트로 약점 파악","완강 시 모의고사 1회 무료 (과외별)"]'::jsonb
WHERE code = 'pro';

UPDATE subscription_plan
SET price_krw   = 49900,
    max_courses = 10,
    coin_grant  = 50000,
    features    = '["과외 10개 — Pro의 모든 기능 포함","코인 50,000 즉시 지급","모의고사 추가 2회 더 (총 3회)","우선 지원 — 가장 빠른 응답"]'::jsonb
WHERE code = 'premium';
