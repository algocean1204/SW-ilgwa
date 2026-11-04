-- users 테이블에 일일 학습 목표 시간(분) 컬럼 추가
ALTER TABLE users ADD COLUMN daily_study_goal_minutes INTEGER NOT NULL DEFAULT 30;
