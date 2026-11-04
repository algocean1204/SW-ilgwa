-- Copyright (c) EZ TEAM
-- V1__init.sql

-- users: 서비스 핵심 사용자 테이블
-- oauth_provider/oauth_id는 oauth_account 테이블에서 관리 (다중 소셜 계정 연동 지원)
-- refresh_token은 Redis에서 관리 (TTL 자동 만료)
CREATE TABLE users (
  id                      VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_users_id CHECK (id ~ '^usr_[0-9A-Z]{26}$'),

  email                   VARCHAR(255)  UNIQUE NOT NULL,
  password_hash           VARCHAR(255),
  nickname                VARCHAR(100)  NOT NULL,
  role                    VARCHAR(20)   NOT NULL DEFAULT 'USER',
  CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN', 'SUPER_ADMIN')),
  status                  VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  profile_image_url       TEXT,

  telegram_chat_id        BIGINT,
  telegram_link_token     VARCHAR(255),

  current_tutor_id        VARCHAR(30),

  grade_level             VARCHAR(50),
  major                   VARCHAR(100),
  education               VARCHAR(100),
  learning_goal           TEXT,
  interests               JSONB,
  preferred_tone          VARCHAR(50),

  onboarding_completed    BOOLEAN       NOT NULL DEFAULT FALSE,
  notification_settings   JSONB,
  accessibility_settings  JSONB,

  subscription_status     VARCHAR(20)   NOT NULL DEFAULT 'free',
  CONSTRAINT chk_users_subscription_status CHECK (subscription_status IN ('free', 'pro', 'premium', 'expired')),
  subscription_started_at TIMESTAMPTZ,
  subscription_expires_at TIMESTAMPTZ,

  created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_status ON users(status);

-- oauth_account: 소셜 로그인 연동 계정 (Google·Kakao·Naver 동시 연동 지원)
CREATE TABLE oauth_account (
  id               VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_oauth_account_id CHECK (id ~ '^oau_[0-9A-Z]{26}$'),

  user_id          VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider         VARCHAR(20)   NOT NULL,
  CONSTRAINT chk_oauth_account_provider CHECK (provider IN ('google', 'kakao', 'naver')),
  provider_user_id VARCHAR(255)  NOT NULL,
  email            VARCHAR(255),
  linked_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  is_active        BOOLEAN       NOT NULL DEFAULT TRUE,

  UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_oauth_account_user ON oauth_account(user_id);

-- user_settings: 사용자별 앱 환경 설정 (1:1)
CREATE TABLE user_settings (
  id                   VARCHAR(30)  PRIMARY KEY,
  CONSTRAINT chk_user_settings_id CHECK (id ~ '^ust_[0-9A-Z]{26}$'),

  user_id              VARCHAR(30)  NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  push_enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
  email_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
  telegram_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
  study_reminder_time  TIME,
  language             VARCHAR(10)  NOT NULL DEFAULT 'ko',
  theme                VARCHAR(20)  NOT NULL DEFAULT 'light',

  updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- user_preferences: 학습 선호도 (1:1)
CREATE TABLE user_preferences (
  id             VARCHAR(30)  PRIMARY KEY,
  CONSTRAINT chk_user_preferences_id CHECK (id ~ '^prf_[0-9A-Z]{26}$'),

  user_id        VARCHAR(30)  NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  grade_level    VARCHAR(50),
  interests      JSONB,
  preferred_tone VARCHAR(50),
  study_goals    JSONB,

  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- telegram_account: 텔레그램 계정 연동 (1:1)
CREATE TABLE telegram_account (
  id                  VARCHAR(30)  PRIMARY KEY,
  CONSTRAINT chk_telegram_account_id CHECK (id ~ '^tga_[0-9A-Z]{26}$'),

  user_id             VARCHAR(30)  NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  telegram_chat_id    BIGINT       NOT NULL UNIQUE,
  telegram_username   VARCHAR(255),
  linked_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
  last_interaction_at TIMESTAMPTZ
);

CREATE INDEX idx_telegram_account_cid ON telegram_account(telegram_chat_id);

-- course: 과외 단위 (FastAPI가 생성, Spring이 조회)
CREATE TABLE course (
  id               VARCHAR(30)    PRIMARY KEY,
  CONSTRAINT chk_course_id CHECK (id ~ '^crs_[0-9A-Z]{26}$'),
  user_id          VARCHAR(30)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tutor_id         VARCHAR(30),
  course_name      VARCHAR(200)   NOT NULL,
  subject          VARCHAR(100),
  source_type      VARCHAR(20),
  source_pdf_url   TEXT,
  topic_text       TEXT,
  mode             VARCHAR(20),
  total_chapters   INTEGER        NOT NULL DEFAULT 0,
  completed_slides INTEGER        NOT NULL DEFAULT 0,
  progress_percent NUMERIC(5,2)   NOT NULL DEFAULT 0,
  status           VARCHAR(30)    NOT NULL DEFAULT 'CURRICULUM_GENERATING',
  CONSTRAINT chk_crs_status CHECK (status IN (
    'CURRICULUM_GENERATING','CURRICULUM_READY','ACTIVE','COMPLETED','PAUSED','DELETED'
  )),
  exam_date        DATE,
  created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
  completed_at     TIMESTAMPTZ
);

CREATE INDEX idx_crs_user_id ON course(user_id);
CREATE INDEX idx_crs_status ON course(status);
CREATE INDEX idx_crs_subject ON course(subject);

-- chapter: 강의 단위 (API의 /lessons = DB의 chapter)
CREATE TABLE chapter (
  id                VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_chapter_id CHECK (id ~ '^chp_[0-9A-Z]{26}$'),
  course_id         VARCHAR(30)   NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  chapter_name      VARCHAR(200)  NOT NULL,
  description       TEXT,
  order_index       INTEGER       NOT NULL,
  total_slides      INTEGER       NOT NULL DEFAULT 0,
  completed_slides  INTEGER       NOT NULL DEFAULT 0,
  estimated_minutes INTEGER,
  key_topics        TEXT,
  status            VARCHAR(20)   NOT NULL DEFAULT 'LOCKED',
  CONSTRAINT chk_chp_status CHECK (status IN (
    'LOCKED','AVAILABLE','IN_PROGRESS','COMPLETED'
  )),
  xp_earned         INTEGER       NOT NULL DEFAULT 0,
  created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  completed_at      TIMESTAMPTZ
);

CREATE INDEX idx_chp_course_id ON chapter(course_id);
CREATE INDEX idx_chp_order_index ON chapter(course_id, order_index);

-- note: 사용자 작성 노트 (Spring은 user_memo만 생성, ai_auto는 FastAPI 전용)
CREATE TABLE note (
  id            VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_note_id CHECK (id ~ '^nte_[0-9A-Z]{26}$'),
  user_id       VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id     VARCHAR(30)   NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  chapter_id    VARCHAR(30)   REFERENCES chapter(id) ON DELETE CASCADE,
  type          VARCHAR(20)   NOT NULL DEFAULT 'user_memo',
  title         VARCHAR(200),
  content       TEXT          NOT NULL,
  slide_index   INTEGER,
  timestamp_sec NUMERIC(10,2),
  is_starred    BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_nte_user_chapter ON note(user_id, chapter_id);
CREATE INDEX idx_nte_starred ON note(user_id, is_starred);

-- bookmark: 슬라이드 북마크 — slide_id FK 없음, chapter_id + slide_index 기준
CREATE TABLE bookmark (
  id          VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_bookmark_id CHECK (id ~ '^bkm_[0-9A-Z]{26}$'),
  user_id     VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id   VARCHAR(30)   NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  chapter_id  VARCHAR(30)   NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
  slide_index INTEGER       NOT NULL,
  memo        TEXT,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_bkm_unique ON bookmark(user_id, chapter_id, slide_index);
CREATE INDEX idx_bkm_chapter ON bookmark(user_id, chapter_id);

-- calendar_event: 학습 일정 이벤트
CREATE TABLE calendar_event (
  id             VARCHAR(30)  PRIMARY KEY,
  CONSTRAINT chk_cev_id CHECK (id ~ '^cev_[0-9A-Z]{26}$'),
  user_id        VARCHAR(30)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title          VARCHAR(100) NOT NULL,
  type           VARCHAR(20)  NOT NULL,
  CONSTRAINT chk_cev_type CHECK (type IN (
    'LESSON','ASSIGNMENT_DUE','REVIEW','EXAM','CUSTOM'
  )),
  event_date     DATE         NOT NULL,
  scheduled_time TIME,
  duration_min   INTEGER,
  status         VARCHAR(20)  NOT NULL DEFAULT 'scheduled',
  CONSTRAINT chk_cev_status CHECK (status IN (
    'scheduled','completed','cancelled','missed'
  )),
  course_id      VARCHAR(30)  REFERENCES course(id) ON DELETE SET NULL,
  description    TEXT,
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cev_user_date ON calendar_event(user_id, event_date);
CREATE INDEX idx_cev_user_status ON calendar_event(user_id, status);

-- assignment: 과제 단위
CREATE TABLE assignment (
  id              VARCHAR(30)  PRIMARY KEY,
  CONSTRAINT chk_asg_id CHECK (id ~ '^asg_[0-9A-Z]{26}$'),
  user_id         VARCHAR(30)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id       VARCHAR(30)  REFERENCES course(id) ON DELETE SET NULL,
  chapter_id      VARCHAR(30)  REFERENCES chapter(id) ON DELETE SET NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'active',
  title           VARCHAR(200),
  description     TEXT,
  due_date        TIMESTAMPTZ,
  total_questions INTEGER      NOT NULL DEFAULT 0,
  questions       TEXT,
  max_score       INTEGER,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_asg_user_id ON assignment(user_id);
CREATE INDEX idx_asg_course_id ON assignment(course_id);
CREATE INDEX idx_asg_due_date ON assignment(due_date);

-- submission: 제출물
CREATE TABLE submission (
  id                     VARCHAR(30)    PRIMARY KEY,
  CONSTRAINT chk_sbm_id CHECK (id ~ '^sbm_[0-9A-Z]{26}$'),
  assignment_id          VARCHAR(30)    NOT NULL REFERENCES assignment(id) ON DELETE CASCADE,
  user_id                VARCHAR(30)    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status                 VARCHAR(20)    NOT NULL DEFAULT 'queued',
  CONSTRAINT chk_sbm_status CHECK (status IN ('queued','grading','done','failed')),
  official_file_url      TEXT,
  official_ocr_text      TEXT,
  official_score         INTEGER,
  official_feedback      TEXT,
  official_ai_confidence NUMERIC(4,3),
  submitted_answers      TEXT,
  ocr_failed_pages       INTEGER        NOT NULL DEFAULT 0,
  total_pages            INTEGER,
  is_retry               BOOLEAN        NOT NULL DEFAULT FALSE,
  submitted_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
  graded_at              TIMESTAMPTZ,
  created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sbm_assignment_id ON submission(assignment_id);
CREATE INDEX idx_sbm_user_id ON submission(user_id);
CREATE INDEX idx_sbm_status ON submission(status);

-- tutor: 슬라이더 기반 AI 튜터 커스터마이징
CREATE TABLE tutor (
  tutor_id              VARCHAR(30)   PRIMARY KEY,
  user_id               VARCHAR(30)   REFERENCES users(id) ON DELETE CASCADE,
  name                  VARCHAR(20)   NOT NULL,
  tagline               VARCHAR(100),
  profile_image_url     TEXT,
  voice_sample_url      TEXT,
  tone_slider           SMALLINT      NOT NULL DEFAULT 50 CHECK (tone_slider BETWEEN 0 AND 100),
  speed_slider          SMALLINT      NOT NULL DEFAULT 50 CHECK (speed_slider BETWEEN 0 AND 100),
  depth_slider          SMALLINT      NOT NULL DEFAULT 50 CHECK (depth_slider BETWEEN 0 AND 100),
  question_style_slider SMALLINT      NOT NULL DEFAULT 50 CHECK (question_style_slider BETWEEN 0 AND 100),
  use_emoji             BOOLEAN       NOT NULL DEFAULT FALSE,
  use_formal_speech     BOOLEAN       NOT NULL DEFAULT TRUE,
  is_default            BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tut_user_id ON tutor(user_id);
CREATE INDEX idx_tut_is_default ON tutor(is_default);

INSERT INTO tutor (tutor_id, user_id, name, tagline, profile_image_url, voice_sample_url,
  tone_slider, speed_slider, depth_slider, question_style_slider,
  use_emoji, use_formal_speech, is_default)
VALUES
  ('tut_00000000000000PRESET_OWL01', NULL, '올빼미 교수',
   '차분하고 단단한 설명 · 소크라테스식', NULL, NULL,
   20, 50, 80, 80, false, true, true),
  ('tut_00000000000000PRESET_CAT01', NULL, '냥 튜터',
   '친근한 말투 · 비유 잘 씀', NULL, NULL,
   80, 60, 30, 20, true, false, true);

-- tutor 테이블 생성 후 FK 연결
ALTER TABLE users
  ADD CONSTRAINT fk_users_current_tutor
  FOREIGN KEY (current_tutor_id) REFERENCES tutor(tutor_id) ON DELETE SET NULL;

ALTER TABLE course
  ADD CONSTRAINT fk_course_tutor
  FOREIGN KEY (tutor_id) REFERENCES tutor(tutor_id) ON DELETE SET NULL;

-- subscription_plan: 요금제 정의 테이블 (free/pro/premium)
CREATE TABLE subscription_plan (
  code                   VARCHAR(20)   PRIMARY KEY,
  CONSTRAINT chk_spl_code CHECK (code IN ('free','pro','premium')),
  price_krw              INTEGER       NOT NULL DEFAULT 0,
  period_days            INTEGER       NOT NULL DEFAULT 30,
  max_courses            INTEGER       NOT NULL,
  max_slides_per_course  INTEGER,
  can_use_telegram       BOOLEAN       NOT NULL DEFAULT FALSE,
  can_customize_tutor    BOOLEAN       NOT NULL DEFAULT FALSE,
  features               JSONB         NOT NULL DEFAULT '[]'::jsonb,
  is_active              BOOLEAN       NOT NULL DEFAULT TRUE
);

INSERT INTO subscription_plan (code, price_krw, period_days, max_courses, max_slides_per_course,
  can_use_telegram, can_customize_tutor, features, is_active)
VALUES
  ('free', 0, 30, 1, 2, false, false,
   '["강의 1개","슬라이드 롤링 언락(항상 2개 활성)","기본 튜터 2종 선택"]'::jsonb, true),
  ('pro', 49900, 30, 3, NULL, true, true,
   '["강의 3개","슬라이드 전체 제공","텔레그램 봇 전 기능","AI 튜터 커스터마이징"]'::jsonb, true),
  ('premium', 99900, 30, 8, NULL, true, true,
   '["강의 8개","슬라이드 전체 제공","텔레그램 봇 전 기능","AI 튜터 커스터마이징"]'::jsonb, true);

-- subscription: 구독 계약 (사용자별 이력 관리)
CREATE TABLE subscription (
  id              VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_sub_id CHECK (id ~ '^sub_[0-9A-Z]{26}$'),
  user_id         VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  plan            VARCHAR(20)   NOT NULL REFERENCES subscription_plan(code),
  status          VARCHAR(20)   NOT NULL DEFAULT 'active',
  CONSTRAINT chk_sub_status CHECK (status IN ('active','cancelled','expired')),
  started_at      TIMESTAMPTZ   NOT NULL,
  ends_at         TIMESTAMPTZ   NOT NULL,
  auto_renew      BOOLEAN       NOT NULL DEFAULT TRUE,
  cancelled_at    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sub_user_status ON subscription(user_id, status);

-- payment: 결제 트랜잭션
CREATE TABLE payment (
  id                VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_pay_id CHECK (id ~ '^pay_[0-9A-Z]{26}$'),
  user_id           VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  subscription_id   VARCHAR(30)   REFERENCES subscription(id) ON DELETE SET NULL,
  amount            INTEGER       NOT NULL,
  toss_payment_key  VARCHAR(255)  UNIQUE,
  toss_order_id     VARCHAR(255),
  status            VARCHAR(20)   NOT NULL DEFAULT 'paid',
  CONSTRAINT chk_pay_status CHECK (status IN ('paid','failed','refunded')),
  paid_at           TIMESTAMPTZ,
  deleted_at        TIMESTAMPTZ,
  created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pay_user_id ON payment(user_id);
CREATE INDEX idx_pay_subscription_id ON payment(subscription_id);

-- coin_wallet: 사용자별 코인 지갑 (1인 1지갑)
CREATE TABLE coin_wallet (
  id               VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_cwl_id CHECK (id ~ '^cwl_[0-9A-Z]{26}$'),
  user_id          VARCHAR(30)   UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  balance          INTEGER       NOT NULL DEFAULT 0,
  total_purchased  INTEGER       NOT NULL DEFAULT 0,
  total_used       INTEGER       NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- coin_transaction: 코인 사용/구매 이벤트 소싱 테이블
CREATE TABLE coin_transaction (
  id               VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_ctx_id CHECK (id ~ '^ctx_[0-9A-Z]{26}$'),
  wallet_id        VARCHAR(30)   NOT NULL REFERENCES coin_wallet(id) ON DELETE CASCADE,
  user_id          VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type             VARCHAR(20)   NOT NULL,
  CONSTRAINT chk_ctx_type CHECK (type IN ('purchase','use_slide','use_exam','refund')),
  amount           INTEGER       NOT NULL,
  balance_after    INTEGER       NOT NULL,
  ref_id           VARCHAR(30),
  payment_id       VARCHAR(30)   REFERENCES payment(id) ON DELETE SET NULL,
  note             VARCHAR(200),
  created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ctx_wallet_id ON coin_transaction(wallet_id);
CREATE INDEX idx_ctx_user_created ON coin_transaction(user_id, created_at DESC);
CREATE INDEX idx_ctx_type ON coin_transaction(type, created_at DESC);

-- notification: 인앱 알림 (읽음 여부는 read_at NULL 체크)
CREATE TABLE notification (
  id          VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_ntf_id CHECK (id ~ '^ntf_[0-9A-Z]{26}$'),
  user_id     VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type        VARCHAR(50)   NOT NULL,
  CONSTRAINT chk_ntf_type CHECK (type IN ('assignment','lesson','mission','streak','community','system')),
  title       VARCHAR(255)  NOT NULL,
  body        TEXT,
  payload     JSONB         NOT NULL DEFAULT '{}'::jsonb,
  read_at     TIMESTAMPTZ,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ntf_user_id ON notification(user_id);
CREATE INDEX idx_ntf_user_created ON notification(user_id, created_at DESC);
CREATE INDEX idx_ntf_user_unread ON notification(user_id) WHERE read_at IS NULL;

-- admin_telegram_credential: 봇 토큰 암호화 저장 (1 슈퍼관리자 = 1 행)
CREATE TABLE admin_telegram_credential (
  id                     BIGSERIAL     PRIMARY KEY,
  user_id                VARCHAR(30)   UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  bot_token_encrypted    TEXT          NOT NULL,
  bot_token_fingerprint  VARCHAR(32),
  admin_chat_id          BIGINT,
  last_rotated_at        TIMESTAMPTZ,
  last_health_check_at   TIMESTAMPTZ,
  created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_telegram_credential_fingerprint
  ON admin_telegram_credential(bot_token_fingerprint);

-- study_time_log: 세션 단위 학습 시간 기록 (리포트 통계 집계 소스)
CREATE TABLE study_time_log (
  id                VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_stl_id CHECK (id ~ '^stl_[0-9A-Z]{26}$'),
  user_id           VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id         VARCHAR(30)   REFERENCES course(id) ON DELETE SET NULL,
  chapter_id        VARCHAR(30)   REFERENCES chapter(id) ON DELETE SET NULL,
  activity_type     VARCHAR(20)   NOT NULL,
  CONSTRAINT chk_stl_activity_type CHECK (activity_type IN ('slide_view','assignment','exam','review','memo')),
  device_type       VARCHAR(20),
  CONSTRAINT chk_stl_device_type CHECK (device_type IS NULL OR device_type IN ('web','mobile','tablet')),
  started_at        TIMESTAMPTZ   NOT NULL,
  ended_at          TIMESTAMPTZ,
  duration_minutes  INTEGER       NOT NULL DEFAULT 0,
  created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stl_user_id ON study_time_log(user_id);
CREATE INDEX idx_stl_user_created ON study_time_log(user_id, created_at DESC);
CREATE INDEX idx_stl_course_id ON study_time_log(course_id);
CREATE INDEX idx_stl_activity ON study_time_log(activity_type);

-- telegram_message: 관리자 텔레그램 메시지 조회 원천
CREATE TABLE telegram_message (
  id               VARCHAR(30)  PRIMARY KEY,
  CONSTRAINT chk_telegram_message_id CHECK (id ~ '^tgm_[0-9A-Z]{26}$'),

  user_id          VARCHAR(30)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  direction        VARCHAR(10)  NOT NULL,
  CONSTRAINT chk_telegram_message_direction CHECK (direction IN ('in', 'out')),
  message_type     VARCHAR(30),
  content          TEXT,
  image_url        VARCHAR(500),
  reference_type   VARCHAR(30),
  reference_id     VARCHAR(30),
  telegram_msg_id  VARCHAR(50),
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tgm_user_created ON telegram_message(user_id, created_at DESC);

-- socratic_session: 관리자 텔레그램 세션 조회 원천
CREATE TABLE socratic_session (
  id                VARCHAR(30)  PRIMARY KEY,
  CONSTRAINT chk_socratic_session_id CHECK (id ~ '^soc_[0-9A-Z]{26}$'),

  user_id           VARCHAR(30)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  discussion_topic  VARCHAR(255),
  transcript        JSONB,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_soc_user_created ON socratic_session(user_id, created_at DESC);

-- slide: 슬라이드 단위 (FastAPI가 생성, Spring이 조회)
CREATE TABLE slide (
  id             VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_slide_id CHECK (id ~ '^sld_[0-9A-Z]{26}$'),
  chapter_id     VARCHAR(30)   NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
  slide_idx      INTEGER       NOT NULL,
  title          VARCHAR(200),
  content        TEXT,
  audio_url      TEXT,
  duration_sec   NUMERIC(8,1)  DEFAULT 0,
  created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sld_chapter_id ON slide(chapter_id);
CREATE UNIQUE INDEX idx_sld_chapter_order ON slide(chapter_id, slide_idx);

-- lesson_progress: 강의 진행률 추적
CREATE TABLE lesson_progress (
  id                VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_lpg_id CHECK (id ~ '^lpg_[0-9A-Z]{26}$'),
  chapter_id        VARCHAR(30)   NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
  user_id           VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  current_slide_idx INTEGER       NOT NULL DEFAULT 0,
  playback_sec      NUMERIC(10,1) NOT NULL DEFAULT 0,
  is_completed      BOOLEAN       NOT NULL DEFAULT FALSE,
  completed_at      TIMESTAMPTZ,
  updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  UNIQUE(chapter_id, user_id)
);

-- quiz: 이해도 체크 퀴즈
CREATE TABLE quiz (
  id              VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_qz_id CHECK (id ~ '^qz_[0-9A-Z]{26}$'),
  chapter_id      VARCHAR(30)   NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
  quiz_idx        INTEGER       NOT NULL,
  question_text   TEXT          NOT NULL,
  options         TEXT,
  correct_option  INTEGER       NOT NULL,
  explanation     TEXT,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qz_chapter_id ON quiz(chapter_id);

-- quiz_answer: 퀴즈 답변 기록
CREATE TABLE quiz_answer (
  id              VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_qa_id CHECK (id ~ '^qa_[0-9A-Z]{26}$'),
  quiz_id         VARCHAR(30)   NOT NULL REFERENCES quiz(id) ON DELETE CASCADE,
  user_id         VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  selected_option INTEGER       NOT NULL,
  is_correct      BOOLEAN       NOT NULL,
  answered_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  UNIQUE(quiz_id, user_id)
);

-- mock_exam: 모의고사 단위
CREATE TABLE mock_exam (
  id              VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_mex_id CHECK (id ~ '^mex_[0-9A-Z]{26}$'),
  course_id       VARCHAR(30)   NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  user_id         VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title           VARCHAR(200)  NOT NULL,
  exam_type       VARCHAR(20)   NOT NULL,
  question_count  INTEGER       NOT NULL,
  difficulty      VARCHAR(20)   NOT NULL,
  time_limit      INTEGER,
  status          VARCHAR(20)   NOT NULL DEFAULT 'GENERATING',
  CONSTRAINT chk_mex_status CHECK (status IN ('GENERATING','READY','COMPLETED')),
  total_points    INTEGER,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mex_course_id ON mock_exam(course_id);
CREATE INDEX idx_mex_user_id ON mock_exam(user_id);

CREATE TABLE mock_exam_question (
  id              VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_mq_id CHECK (id ~ '^mq_[0-9A-Z]{26}$'),
  mock_exam_id    VARCHAR(30)   NOT NULL REFERENCES mock_exam(id) ON DELETE CASCADE,
  question_idx    INTEGER       NOT NULL,
  question_text   TEXT          NOT NULL,
  question_type   VARCHAR(20)   NOT NULL DEFAULT 'multiple_choice',
  options         TEXT,
  correct_option  VARCHAR(10),
  explanation     TEXT,
  points          INTEGER       NOT NULL DEFAULT 5,
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mq_exam_id ON mock_exam_question(mock_exam_id);

CREATE TABLE mock_exam_submission (
  id              VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_ms_id CHECK (id ~ '^ms_[0-9A-Z]{26}$'),
  mock_exam_id    VARCHAR(30)   NOT NULL REFERENCES mock_exam(id) ON DELETE CASCADE,
  user_id         VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  answers         TEXT          NOT NULL,
  score           INTEGER,
  total_points    INTEGER,
  time_taken      INTEGER,
  grade           VARCHAR(5),
  analysis        TEXT,
  submitted_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ms_exam_id ON mock_exam_submission(mock_exam_id);
CREATE INDEX idx_ms_user_id ON mock_exam_submission(user_id);

-- chat_message: AI 채팅 메시지 저장
CREATE TABLE chat_message (
  id            VARCHAR(30)   PRIMARY KEY,
  CONSTRAINT chk_cmg_id CHECK (id ~ '^cmg_[0-9A-Z]{26}$'),
  user_id       VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id     VARCHAR(30)   NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  chapter_id    VARCHAR(30)   REFERENCES chapter(id) ON DELETE SET NULL,
  slide_id      VARCHAR(30),
  slide_idx     INTEGER,
  role          VARCHAR(10)   NOT NULL,
  CONSTRAINT chk_cmg_role CHECK (role IN ('user','assistant')),
  message       TEXT          NOT NULL,
  citations     TEXT,
  playback_sec  NUMERIC(10,2),
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cmg_user_course ON chat_message(user_id, course_id);
CREATE INDEX idx_cmg_course_chapter ON chat_message(course_id, chapter_id);
CREATE INDEX idx_cmg_course_user_created ON chat_message(course_id, user_id, created_at DESC);
