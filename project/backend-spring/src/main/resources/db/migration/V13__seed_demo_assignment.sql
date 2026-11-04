-- 데모 유저용 테스트 과제 seed
-- 조건: 데모 유저(usr_01KT0T8CTKJYAGXS12HA4CXQWN)가 존재하고 active assignment가 없을 때만 삽입한다.
-- 텔레그램 제출 수신 흐름 검증용 과제다.
INSERT INTO assignment (
    id,
    user_id,
    course_id,
    chapter_id,
    status,
    title,
    description,
    due_date,
    total_questions,
    questions,
    max_score,
    created_at,
    updated_at
)
SELECT
    'asg_01KT0T8CTKJYAGXS12DEMO0001',
    'usr_01KT0T8CTKJYAGXS12HA4CXQWN',
    NULL,
    NULL,
    'active',
    '일차방정식 연습문제 — 사진으로 풀이 제출',
    '다음 일차방정식을 풀고 풀이 과정을 사진으로 찍어 텔레그램으로 제출하세요.' || E'\n' ||
    '1) 2x + 3 = 11' || E'\n' ||
    '2) 5x - 7 = 18' || E'\n' ||
    '3) 3(x - 2) = 9',
    NOW() + INTERVAL '7 days',
    3,
    '["2x + 3 = 11", "5x - 7 = 18", "3(x - 2) = 9"]',
    100,
    NOW(),
    NOW()
WHERE
    -- 데모 유저가 존재할 때만 삽입 (FK 위반 방지)
    EXISTS (SELECT 1 FROM users WHERE id = 'usr_01KT0T8CTKJYAGXS12HA4CXQWN')
    -- 동일 ID로 이미 존재하면 삽입 건너뜀 (멱등성 보장)
    AND NOT EXISTS (SELECT 1 FROM assignment WHERE id = 'asg_01KT0T8CTKJYAGXS12DEMO0001');
