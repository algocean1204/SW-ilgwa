-- quiz 테이블에 slide_id FK 추가
ALTER TABLE quiz ADD COLUMN slide_id VARCHAR(30) REFERENCES slide(id);

-- 기존 데이터 마이그레이션: quiz_idx 기준으로 같은 챕터의 slide 매핑
UPDATE quiz q SET slide_id = (
    SELECT s.id FROM slide s
    WHERE s.chapter_id = q.chapter_id AND s.slide_idx = q.quiz_idx
);

-- NOT NULL 제약 추가
ALTER TABLE quiz ALTER COLUMN slide_id SET NOT NULL;

-- UNIQUE 제약: 슬라이드당 퀴즈 1개
ALTER TABLE quiz ADD CONSTRAINT uq_quiz_slide UNIQUE (slide_id);
