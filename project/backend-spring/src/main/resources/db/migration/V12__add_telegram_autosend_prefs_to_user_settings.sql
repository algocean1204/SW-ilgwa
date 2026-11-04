-- user_settings 테이블에 텔레그램 자동 전송 옵션 3종을 추가한다.
-- 기존 telegram_enabled(단일 on/off)는 그대로 두고, 화면의 세 토글(암기노트/퀴즈/친구공유)을
-- 각각 독립 컬럼으로 보관해 per-user 설정을 정확히 저장한다.
-- 기본값 FALSE — 텔레그램 연동 전에는 전송 대상이 없으므로 비활성으로 시작한다.
ALTER TABLE user_settings ADD COLUMN telegram_note_enabled   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_settings ADD COLUMN telegram_quiz_enabled   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_settings ADD COLUMN telegram_friend_enabled BOOLEAN NOT NULL DEFAULT FALSE;
