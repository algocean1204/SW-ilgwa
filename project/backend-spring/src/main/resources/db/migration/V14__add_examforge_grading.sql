-- Copyright (c) EZ TEAM
-- V14__add_examforge_grading.sql
-- ExamForge 채점 레이어 5개 테이블 추가 (additive only — 기존 테이블 변경 없음)
-- attempt_id는 생성 경로가 미확정이므로 길이·NOT NULL만 느슨하게 검사

-- exam_attempt: 응시 단위 — 생성부터 채점 완료까지 수명주기 관리
CREATE TABLE exam_attempt (
  attempt_id          VARCHAR(80)   PRIMARY KEY,
  CONSTRAINT chk_ea_attempt_id CHECK (char_length(attempt_id) >= 8),

  exam_id             VARCHAR(80)   NOT NULL,

  user_id             VARCHAR(30)   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT chk_ea_user_id CHECK (user_id ~ '^usr_[0-9A-Z]{26}$'),

  -- course_id는 비과외(독립 응시) 시 NULL 허용
  course_id           VARCHAR(30)   REFERENCES course(id) ON DELETE SET NULL,

  -- 응시 상태: 생성·제출 대기·제출 완료·채점 완료·수동 검토 대기
  status              VARCHAR(30)   NOT NULL DEFAULT 'CREATED',
  CONSTRAINT chk_ea_status CHECK (
    status IN ('CREATED', 'IN_PROGRESS', 'SUBMITTED', 'GRADED', 'MANUAL_REVIEW_REQUIRED')
  ),

  -- 합격 기준 점수 비율 (0.00~100.00)
  pass_percentage     NUMERIC(5, 2) NOT NULL DEFAULT 0,

  -- FastAPI 생성 시 서명된 정답지 seal — 프론트에 절대 노출 금지
  answer_key_seal     TEXT,

  created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  submitted_at        TIMESTAMPTZ,
  graded_at           TIMESTAMPTZ
);

CREATE INDEX idx_ea_user_id    ON exam_attempt(user_id);
CREATE INDEX idx_ea_course_id  ON exam_attempt(course_id);

-- exam_attempt_question: 응시 당시 서버에 스냅샷 저장된 문항 — 정답/해설 포함, 프론트 비노출
CREATE TABLE exam_attempt_question (
  id                      BIGSERIAL     PRIMARY KEY,
  attempt_id              VARCHAR(80)   NOT NULL REFERENCES exam_attempt(attempt_id) ON DELETE CASCADE,
  question_id             VARCHAR(80)   NOT NULL,
  template_id             VARCHAR(80),
  display_order           INTEGER       NOT NULL DEFAULT 0,
  stem                    TEXT          NOT NULL,

  -- 보기 목록 JSONB (QuestionOption[] 직렬화 — label/text/is_correct)
  options_json            JSONB,

  points                  NUMERIC(7, 2) NOT NULL DEFAULT 1,

  -- 채점에 사용하는 정답 — 프론트 응답 DTO에 절대 매핑 금지
  correct_answer          TEXT,

  -- 해설 — 프론트 응답 DTO에 절대 매핑 금지
  explanation             TEXT,

  -- 출처 — 프론트 응답 DTO에 절대 매핑 금지
  source_reference        TEXT,

  -- 추가 채점 메타데이터 JSONB (연결형 우항, 순서형 정답 등) — 프론트 비노출
  grading_metadata_json   JSONB
);

CREATE INDEX idx_eaq_attempt_id ON exam_attempt_question(attempt_id);

-- exam_submitted_answer: 응시자가 제출한 답안 원본 (answer_json은 문항 유형별 구조 다름)
CREATE TABLE exam_submitted_answer (
  id              BIGSERIAL     PRIMARY KEY,
  attempt_id      VARCHAR(80)   NOT NULL REFERENCES exam_attempt(attempt_id) ON DELETE CASCADE,
  question_id     VARCHAR(80)   NOT NULL,

  -- 답안 JSONB — 객관식이면 {"label":"A"}, 서술형이면 {"text":"..."}, 순서형이면 {"order":[...]}
  answer_json     JSONB,

  submitted_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_esa_attempt_id ON exam_submitted_answer(attempt_id);

-- exam_question_grade: 문항별 채점 결과 — DETERMINISTIC·AI_RUBRIC·MANUAL_REVIEW 구분
CREATE TABLE exam_question_grade (
  id                      BIGSERIAL       PRIMARY KEY,
  attempt_id              VARCHAR(80)     NOT NULL REFERENCES exam_attempt(attempt_id) ON DELETE CASCADE,
  question_id             VARCHAR(80)     NOT NULL,

  score                   NUMERIC(7, 2)   NOT NULL DEFAULT 0,
  max_score               NUMERIC(7, 2)   NOT NULL DEFAULT 0,

  -- 채점 방식 구분 (GradingMode enum)
  grading_mode            VARCHAR(30)     NOT NULL,
  CONSTRAINT chk_eqg_grading_mode CHECK (
    grading_mode IN ('DETERMINISTIC', 'AI_RUBRIC', 'MANUAL_REVIEW_REQUIRED')
  ),

  -- 공개 피드백 — 정답/해설 문자열이 혼입되지 않도록 GradingLeakGuard가 검사 후 저장
  feedback                TEXT,

  -- AI 루브릭 기준별 점수 JSONB (RubricCriterion[] — criterion/score/max_score/reason)
  rubric_breakdown_json   JSONB,

  -- AI 채점 신뢰도 (0.0~1.0)
  confidence              NUMERIC(4, 3)   NOT NULL DEFAULT 0,

  -- 수동 검토 필요 여부
  needs_manual_review     BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_eqg_attempt_id ON exam_question_grade(attempt_id);

-- exam_grade_result: 응시별 최종 채점 결과 요약 — attempt_id를 PK 겸 FK로 사용
CREATE TABLE exam_grade_result (
  attempt_id      VARCHAR(80)     PRIMARY KEY REFERENCES exam_attempt(attempt_id) ON DELETE CASCADE,

  total_score     NUMERIC(9, 2)   NOT NULL DEFAULT 0,
  max_score       NUMERIC(9, 2)   NOT NULL DEFAULT 0,

  -- 백분율 (0.00~100.00)
  percentage      NUMERIC(5, 2)   NOT NULL DEFAULT 0,
  passed          BOOLEAN         NOT NULL DEFAULT FALSE,

  -- 최종 채점 상태 (GradingStatus enum)
  grading_status  VARCHAR(30)     NOT NULL,
  CONSTRAINT chk_egr_grading_status CHECK (
    grading_status IN ('GRADED', 'MANUAL_REVIEW_REQUIRED')
  ),

  graded_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
