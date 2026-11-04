# 일과 — AI 과외 플랫폼 데이터베이스 스키마

주요 테이블 · PostgreSQL · 최종 기능 범위 기준

> EZ Team · 2026년 4월 · 캡스톤 프로젝트

---

## ID & 프리픽스 규약

### ID 포맷

모든 내부 ID 컬럼은 `{3자리 소문자 프리픽스}_{ULID 26자}` 형식을 따른다. 총 30자.

- 컬럼 타입: `VARCHAR(30) PRIMARY KEY`
- 예시: `usr_01ARZ3NDEKTSV4RRFFQ69G5FAV`
- 생성 주체: 애플리케이션 레이어(서버 사이드). DB `DEFAULT` 값 생성 금지.

ULID를 쓰는 이유: 시간 순 정렬(밀리초 단위 prefix), 충돌 가능성 낮음(80비트 랜덤), URL에 쓰기 쉬움(Base32, 대소문자 무관).

> 예외: `submission_retry.retry_id`, `grading_callback_dlq.dlq_id`, `admin_telegram_credential.id`, `admin_audit_log.id`는 BIGSERIAL PK를 유지한다. `voice_cache.text_hash`는 SHA-256 해시 PK를 유지한다. `subscription_plan.code`는 플랜 코드 자체가 PK다.

### 테이블 정의의 키 열 표기

- `PK` 기본키, `FK` 외래키, `UQ` 고유 제약, `FK·UQ` 고유 외래키(1:1), `PK·FK` 외래키를 겸하는 기본키, `신규` 최근 추가한 컬럼

### FK 컬럼 네이밍

- 일반: `{참조테이블_단수형}_id` (예: `user_id`, `course_id`, `assignment_id`)
- 다형성 FK: `target_id` (예외, 주석으로 참조 대상 명시)

### Soft Delete

soft-delete 대상 테이블에는 `deleted_at TIMESTAMPTZ DEFAULT NULL`을 추가한다. 활성 레코드 조회 시 반드시 `WHERE deleted_at IS NULL` 조건을 붙인다.

### 타임스탬프 규약

```sql
created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- updated_at 갱신: 트리거 또는 앱 레이어 담당
```

### 프리픽스 주요 목록

| 테이블 | 프리픽스 |
|---|---|
| user | usr_ |
| user_profile | upr_ |
| user_settings | ust_ |
| user_preferences | prf_ |
| refresh_token | rtk_ |
| oauth_account | oau_ |
| ai_teacher | tch_ |
| tutor | tut_ |
| voice_profile | vpf_ |
| voice_cache | vch_ |
| subscription | sub_ |
| user_usage | usg_ |
| payment | pay_ |
| subscription_plan | pln_ |
| course | crs_ |
| chapter | chp_ |
| slide | sld_ |
| vector_chunk | vck_ |
| qa | qna_ |
| concept | cnc_ |
| comprehension_test | cht_ |
| curriculum | cur_ |
| curriculum_unit | cun_ |
| playback_event | pbe_ |
| assignment | asg_ |
| question | qst_ |
| submission | sbm_ |
| submission_retry | sbr_ |
| grade | grd_ |
| assignment_set | ast_ |
| step_trace | stp_ |
| ocr_result | ocr_ |
| grading_callback_dlq | dlq_ |
| note | nte_ |
| memo_note | mmo_ |
| bookmark | bkm_ |
| weakness_profile | wkp_ |
| memory | mem_ |
| weakness_snapshot | wks_ |
| user_stats | sts_ |
| telegram_message | tgm_ |
| admin_telegram_credential | atc_ |
| admin_audit_log | aal_ |
| schedule | sch_ |
| mock_exam | mex_ |
| notification | ntf_ |
| calendar_event | cev_ |
| socratic_session | soc_ |
| mock_exam_submission | mes_ |
| mock_exam_grade | meg_ |
| telegram_account | tga_ |
| coin_wallet | cwl_ |
| coin_transaction | ctx_ |

---

## 도메인 구성

| # | 도메인 | 테이블 수 | 설명 |
|---|---|---|---|
| 1 | **사용자** | 6개 | 계정·프로필(`users`) + 프로필 확장(`user_profile`) + 세부 설정(`user_settings`) + 온보딩 선호도(`user_preferences`) + OAuth 계정 연동(`oauth_account`) + refresh token Redis 관리 |
| 2 | **AI 선생님** | 4개 | 캐릭터 페르소나(`ai_teacher`) + 튜터 커스터마이징(`tutor`) + 음성 프로필 + TTS 캐시 |
| 3 | **결제·구독** | 6개 | 구독 계약 / 월별 사용량 / 결제 이력 / 플랜 메타 / AI 코인 잔액(`coin_wallet`) / AI 코인 이력(`coin_transaction`) |
| 4 | **학습 콘텐츠** | 10개 | PDF → 슬라이드 → 음성 과외 생성물 + 개념 지식 그래프 + 학습 행동 이벤트 + QA + 이해도 테스트 + 커리큘럼 문서 + 커리큘럼 차시 |
| 5 | **과제·채점** | 9개 | 문제 생성 → 제출 → AI 채점 → 피드백 + 과제 묶음 + 풀이 단계 + OCR + 재제출 이력 + 채점 콜백 DLQ |
| 6 | **노트·메모리** | 5개 | AI 노트, 암기노트, 약점 추적, Self-Healing Memory + 약점 스냅샷 |
| 7 | **외부·운영** | 10개 | 텔레그램 메시지, 텔레그램 계정 연동, 일정, 모의고사, 알림, 캘린더 이벤트, 소크라테스 세션, 모의고사 응시·채점, 관리자 운영 로그 |

---

## 테이블 인덱스

1. 사용자: `users`, `user_profile`, `user_settings`, `user_preferences`, `oauth_account`
2. AI 선생님: `ai_teacher`, `tutor`, `voice_profile`, `voice_cache`
3. 결제·구독: `subscription`, `user_usage`, `payment`, `subscription_plan`, `coin_wallet`, `coin_transaction`
4. 학습 콘텐츠: `course`, `chapter`, `slide`, `vector_chunk`, `concept`, `playback_event`, `qa`, `comprehension_test`, `curriculum`, `curriculum_unit`
5. 과제·채점: `assignment`, `question`, `submission`, `submission_retry`, `grade`, `assignment_set`, `step_trace`, `ocr_result`, `grading_callback_dlq`
6. 노트·메모리: `note`, `memo_note`, `bookmark`, `weakness_profile`, `memory`, `weakness_snapshot`
7. 외부·운영: `telegram_message`, `telegram_account`, `schedule`, `mock_exam`, `notification`, `calendar_event`, `socratic_session`, `mock_exam_submission`, `mock_exam_grade`, `admin_telegram_credential`, `admin_audit_log`

---

## 1. 사용자

> 회원가입, 인증, 구독 상태 관리

### `user`

플랫폼 회원의 계정·프로필·구독 상태 정보(cold). 구글/카카오 소셜 로그인 전용 — 자체 이메일/비밀번호 인증 없음. 최초 소셜 로그인 시 자동 계정 생성. 사용량은 `user_usage`에 분리됨. `subscription_status`는 PAGE 4·5·6·10·13·15에서 Pro-gated hot-path로 체크되므로 user와 함께 항상 로드되는 구조가 자연스러워 흡수함

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 사용자 고유번호 — ULID with usr_ prefix |
| UQ | `email` | `VARCHAR(255)` | 이메일 (중복 불가) |
|  | `full_name` | `VARCHAR(100)` | 실명 (소셜 프로필에서 자동 수집) |
|  | `nickname` | `VARCHAR(50)` | 공개 닉네임 (full_name과 별도) NOT NULL |
|  | `profile_image_url` | `TEXT` | S3 URL |
|  | `oauth_provider` | `VARCHAR(20)` | 소셜 로그인 제공자 CHECK (oauth_provider IN ('google','kakao')) NOT NULL |
|  | `oauth_id` | `VARCHAR(255)` | 소셜 로그인 고유 ID NOT NULL |
|  | `telegram_chat_id` | `VARCHAR(50)` | 텔레그램 챗 ID (봇 메시지 발송용). **임시 mirror 컬럼** — 동일 값이 `telegram_account.telegram_chat_id`(BIGINT UNIQUE)에도 존재한다. 정본은 `telegram_account.telegram_chat_id`이며, 이 컬럼은 read-only mirror로 취급한다. 최종 deprecate 여부·동기화 방식은 Spring 담당자가 확정한다. |
|  | `telegram_link_token` | `VARCHAR(64)` | 텔레그램 연동용 1회성 토큰 |
| FK | `current_teacher_id` | `VARCHAR(30)` | 현재 선택한 AI 선생님 |
|  | `grade_level` | `VARCHAR(50)` | 학년 (중1/고2/대학생 등) |
|  | `major` | `VARCHAR(100)` | 전공 |
|  | `education` | `VARCHAR(50)` | 학교 구분 CHECK (education IN ('highschool','university','other')) |
|  | `learning_goal` | `TEXT` | 자유 입력 학습 목표 |
|  | `interests` | `JSONB` | 관심 분야 태그 배열 |
|  | `preferred_tone` | `VARCHAR(20)` | 선호 말투 CHECK (preferred_tone IN ('informal','formal')) |
|  | `onboarding_completed` | `BOOLEAN` | 온보딩 완료 여부 (DEFAULT FALSE) |
|  | `notification_settings` | `JSONB` | 알림 설정 (DEFAULT '{}') |
|  | `accessibility_settings` | `JSONB` | 접근성 설정 (DEFAULT '{}') |
| 신규 | `subscription_status` | `VARCHAR(20)` | 현재 구독 상태 CHECK (subscription_status IN ('free','pro','premium','expired')) DEFAULT 'free' |
| 신규 | `subscription_started_at` | `TIMESTAMPTZ` | 구독 시작 시각 (NULLABLE) |
| 신규 | `subscription_expires_at` | `TIMESTAMPTZ` | 구독 만료 시각 (NULLABLE) |
|  | `deleted_at` | `TIMESTAMPTZ DEFAULT NULL` | Soft delete: 활성 레코드는 deleted_at IS NULL 조건 필수 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 가입 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 최종 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_user_email ON "user"(email);
CREATE INDEX idx_user_subscription_status ON "user"(subscription_status);
CREATE INDEX idx_user_created_at ON "user"(created_at DESC);
CREATE INDEX idx_user_interests_gin ON "user" USING gin(interests);
```

> [구독 캐시 규칙]
> subscription_status / subscription_started_at / subscription_expires_at 3필드는
> Subscription 테이블을 원장으로 삼은 hot-path 캐시임. 쓰기 주체는 결제 webhook만.
> 1) 트랜잭션 경계: Payment INSERT → Subscription INSERT/UPDATE → User UPDATE를
>    단일 ACID 트랜잭션으로 원자 처리.
> 2) 야간 reconciliation: 매일 자정 스케줄러가
>    Subscription.ends_at < NOW() AND User.subscription_status IN ('pro','premium') 인 행을 찾아
>    User.subscription_status='expired'로 보정 (webhook 누락 대비).
> 3) Idempotency: 결제 webhook은 toss_payment_key UNIQUE 제약으로 중복 처리 방지,
>    User UPDATE는 SELECT FOR UPDATE로 동시성 직렬화.

> free vs expired: 권한은 동일하나 UI·재구독 분기·마케팅 세그먼트에서 구분할 것

### `user_profile`

user 1:1 확장 프로필. 공개 바이오, SNS 링크, 프로필 공개 범위 등 자주 읽히지 않는 부가 정보를 분리

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with upr_ prefix |
| FK·UQ | `user_id` | `VARCHAR(30) UNIQUE` | 소유 사용자 (1:1) |
|  | `bio` | `TEXT` | 자기소개 |
|  | `social_links` | `JSONB` | SNS 링크 배열 (DEFAULT '{}') |
|  | `visibility` | `VARCHAR(20)` | 프로필 공개 범위 CHECK (visibility IN ('public','friends','private')) DEFAULT 'public' |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 갱신 시각 |

#### 인덱스

```sql
CREATE INDEX idx_upr_user_id ON user_profile(user_id);
```

### `user_settings`

알림·UX 세부 설정 분리 저장. user 1:1

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with ust_ prefix |
| FK·UQ | `user_id` | `VARCHAR(30) UNIQUE` | 소유 사용자 (1:1) |
|  | `push_enabled` | `BOOLEAN` | 푸시 알림 여부 (DEFAULT TRUE) |
|  | `email_enabled` | `BOOLEAN` | 이메일 알림 여부 (DEFAULT TRUE) |
|  | `telegram_enabled` | `BOOLEAN` | 텔레그램 알림 여부 (DEFAULT FALSE) |
|  | `study_reminder_time` | `TIME` | 학습 알림 시각 |
|  | `language` | `VARCHAR(10)` | UI 언어 (DEFAULT 'ko') |
|  | `theme` | `VARCHAR(20)` | 테마 CHECK (theme IN ('light','dark')) DEFAULT 'light' |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 갱신 시각 |

#### 인덱스

```sql
CREATE INDEX idx_ust_user_id ON user_settings(user_id);
```

### `user_preferences`

온보딩 선호도 확장. `user` 테이블의 `grade_level`, `interests`, `preferred_tone`, `learning_goal` 등은 기본 정체성 필드로 `user`에 유지하고, 이 테이블은 학습 스타일·목표 등 확장 선호 필드를 둔다. user 1:1

> [필드 분리 원칙]
> user.grade_level / user.interests / user.preferred_tone / user.learning_goal = 기본 정체성 필드 (인증·프로필 표시에 사용되므로 user에 유지)
> user_preferences.* = 온보딩 이후 학습 경험 커스터마이징용 확장 필드 (AI 과외 말투·목표·학습 세기 등)

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with prf_ prefix |
| FK·UQ | `user_id` | `VARCHAR(30) UNIQUE` | 소유 사용자 (1:1) |
|  | `grade_level` | `VARCHAR(20)` | 학년 상세 (중1/중2/고1/고2/고3/대학생 등, 온보딩 확장용) |
|  | `interests` | `JSONB` | 심화 관심 분야 배열 (예: `["수학","물리","프로그래밍"]`) |
|  | `preferred_tone` | `VARCHAR(20)` | AI 선생님 선호 말투 (정중/친근/활기찬 등) |
|  | `study_goals` | `JSONB` | 학습 목표 배열 (예: `["수능 1등급","코딩 입문"]`) |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 갱신 시각 |

#### 인덱스

```sql
CREATE INDEX idx_prf_user_id ON user_preferences(user_id);
```

### Refresh Token (Redis 관리)

> **DB 테이블 없음** — Refresh Token은 Redis에서 TTL 기반으로 관리한다.

| Redis Key | Value | TTL |
|---|---|---|
| `RT:{SHA256(token)}` | `userId` | 14일 (refreshExpiryMs) |
| `USED_RT:{SHA256(token)}` | `userId` | 5분 (재사용 탐지 윈도우) |

- Token Rotation: `/auth/refresh` 호출 시 `GETDEL`로 원자적 삭제 후 새 토큰 발급
- 재사용 탐지: `USED_RT` key 존재 시 `AUTH_004` 반환 (5분 내 재사용 탐지)

### `oauth_account`

다중 OAuth 계정 연동. 모든 소셜 계정은 이 테이블에서 단일 출처로 관리한다. 지원 제공자: Google, Kakao.

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with oau_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유 사용자 |
|  | `provider` | `VARCHAR(20)` | OAuth 제공자 CHECK (provider IN ('google','kakao')) |
|  | `provider_user_id` | `VARCHAR(255)` | 제공자 측 고유 사용자 ID |
|  | `email` | `VARCHAR(255)` | 제공자에서 받은 이메일 (NULLABLE) |
|  | `linked_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 연동 시각 |
|  | `is_active` | `BOOLEAN` | 활성 연동 여부 (DEFAULT TRUE) |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE UNIQUE INDEX uq_oau_provider ON oauth_account(provider, provider_user_id);
CREATE INDEX idx_oau_user_id ON oauth_account(user_id);
```

---

## 2. AI 선생님

> 캐릭터 페르소나 + 음성 프로필 + TTS 캐시

### `ai_teacher`

AI 선생님 캐릭터(페르소나). 기본 3종 + 사용자 커스텀

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 선생님 고유번호 — ULID with tch_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유자 (NULL이면 기본 캐릭터) |
|  | `name` | `VARCHAR(100)` | 선생님 이름 |
|  | `personality` | `TEXT` | 성격 설명 |
|  | `speech_style` | `TEXT` | TTS 보이스 클론 스타일 설명 텍스트 (S2 연동) |
|  | `expertise` | `VARCHAR(255)` | 전문 분야 — 쉼표 구분 문자열 (예: "수학,과학") |
| FK | `voice_profile_id` | `VARCHAR(30)` | 음성 프로필 연결 (S2) |
|  | `avatar_emoji` | `VARCHAR(10)` | 대표 이모지 |
|  | `is_default` | `BOOLEAN DEFAULT FALSE` | 기본 캐릭터 여부 |
|  | `is_preset` | `BOOLEAN NOT NULL DEFAULT FALSE` | 프리셋 강사 여부. true=플랫폼 제공, false=사용자 커스텀 |
|  | `persona_prompt` | `TEXT` | LangGraph 페르소나 프롬프트 |
| 신규 | `tone_style` | `VARCHAR(20) NOT NULL DEFAULT 'BALANCED'` | 말투 강도 — STRICT / BALANCED / WARM |
| 신규 | `question_style` | `VARCHAR(20) NOT NULL DEFAULT 'HINT_THEN_ANSWER'` | 질문 방식 — DIRECT_ANSWER / HINT_THEN_ANSWER / SOCRATIC |
| 신규 | `explanation_depth` | `VARCHAR(20) NOT NULL DEFAULT 'WITH_EXAMPLES'` | 설명 깊이 — ESSENTIAL / WITH_EXAMPLES / DEEP |
| 신규 | `description` | `TEXT` | 상세 소개 (최대 500자) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
| 신규 | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_tch_user_id ON ai_teacher(user_id);
CREATE INDEX idx_tch_is_preset ON ai_teacher(is_preset);
```

### `tutor`

AI 튜터 커스터마이징 엔티티. PAGE 18 전용. 슬라이더 4종·아바타·배경색·표현 방식 설정을 저장한다. `ai_teacher`와 별개 테이블로 분리 — `ai_teacher`는 음성 프로필 연결·LangGraph 페르소나 프롬프트 역할을 유지한다.

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `tutor_id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with tut_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유 사용자 (NULL이면 기본 제공 튜터) |
|  | `name` | `VARCHAR(20) NOT NULL` | 튜터 이름 (최대 20자) |
|  | `tagline` | `VARCHAR(100)` | 한줄 소개 |
|  | `avatar_style` | `VARCHAR(20)` | CHECK (avatar_style IN ('owl','cat','fox','bear','cloud','sparkle')) |
|  | `background_color` | `VARCHAR(7)` | 배경 색상 hex (예: '#F4F3EE') |
|  | `tone_slider` | `SMALLINT NOT NULL DEFAULT 50` | 말투 — 0(엄격) ~ 100(친근) |
|  | `speed_slider` | `SMALLINT NOT NULL DEFAULT 50` | 설명 속도 — 0(천천히) ~ 100(빠르게) |
|  | `depth_slider` | `SMALLINT NOT NULL DEFAULT 50` | 설명 깊이 — 0(개념만) ~ 100(깊이 파기) |
|  | `question_style_slider` | `SMALLINT NOT NULL DEFAULT 50` | 질문 스타일 — 0(답 먼저) ~ 100(질문 먼저) |
|  | `use_emoji` | `BOOLEAN NOT NULL DEFAULT false` | 응답에 이모지 포함 여부 |
|  | `use_formal_speech` | `BOOLEAN NOT NULL DEFAULT true` | 높임말 여부 |
|  | `is_default` | `BOOLEAN NOT NULL DEFAULT false` | 기본 제공 여부 (true이면 삭제 불가) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_tut_user_id ON tutor(user_id);
CREATE INDEX idx_tut_is_default ON tutor(is_default);
```

#### 시드 데이터 (기본 제공 튜터 2종)

```sql
INSERT INTO tutor (tutor_id, user_id, name, tagline, avatar_style, background_color,
  tone_slider, speed_slider, depth_slider, question_style_slider,
  use_emoji, use_formal_speech, is_default)
VALUES
  ('tut_PRESET_OWL_PROFESSOR_000001', NULL, '올빼미 교수', '차분하고 단단한 설명 · 소크라테스식',
   'owl', '#EEF3F4', 20, 50, 80, 80, false, true, true),
  ('tut_PRESET_CAT_TUTOR_00000001', NULL, '냥 튜터', '친근한 말투 · 비유 잘 씀',
   'cat', '#FFF4F0', 80, 60, 30, 20, true, false, true);
```

---

### `voice_profile`

음성 프로필. 프리셋/클로닝/디자인 3종 (Qwen3-TTS)

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 음성 프로필 고유번호 — ULID with vpf_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유자 |
|  | `name` | `VARCHAR(50)` | 프로필 이름 |
|  | `type` | `VARCHAR(20)` | 종류 CHECK (type IN ('preset','cloned','designed')) |
|  | `s3_url` | `VARCHAR(500)` | Qwen3-TTS 참조 음성 파일 URL |
|  | `sample_text` | `TEXT` | 샘플 발화 텍스트 |
|  | `language` | `VARCHAR(10)` | 언어 CHECK (language IN ('ko','en')) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_vpf_user_id ON voice_profile(user_id);
```

### `voice_cache`

TTS 생성 결과 캐시. 동일 텍스트·음성 프로필 조합은 S3에 한 번만 저장

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `text_hash` | `VARCHAR(64) PRIMARY KEY` | SHA-256 해시 (텍스트 + voice_profile_id 결합). ULID 프리픽스 예외 — 해시 PK 유지 |
| FK | `voice_profile_id` | `VARCHAR(30)` | 사용한 음성 프로필 |
|  | `s3_key` | `VARCHAR(500)` | S3 오브젝트 키 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_vch_voice_profile_id ON voice_cache(voice_profile_id);
```

---

## 3. 결제·구독

> 구독 계약 이력 · 월별 사용량 · 결제 이력 · AI 코인을 책임별로 분리. 구독 상태(`subscription_status`)는 `user`로 흡수됨. v1.5 기준 3종: free/pro/premium (+expired)

#### 책임 매트릭스 (단일 소스 규칙)

| 질문 | 유일한 답 테이블 |
|---|---|
| 지금 이 사람의 구독 상태 뭐야? (권한 체크) | `user.subscription_status` |
| 구독 계약 상태/이력이 뭐야? | `subscription` |
| 이번 달 이 기능 몇 번 썼어? (쿼터 체크) | `user_usage` |
| 이 결제 건 처리됐어? (감사/환불) | `payment` |
| 어떤 플랜이 있고 가격은 얼마야? | `subscription_plan` |

### `subscription`

구독 계약 이력/현재 상태. 결제 webhook이 INSERT/UPDATE하는 이벤트 소스. 한 유저가 여러 레코드를 가질 수 있음 (과거 취소 + 현재 활성 + 미래 예약)

> [구독 원장 규칙]
> 1 user : N rows. 과거 취소 + 현재 활성이 공존 가능.
> 권한 판정은 User 캐시 우선, 감사/이력 조회만 이 테이블 사용.
> status='active' 구독은 유저당 1개로 제한 (업그레이드 race를 행잠금으로 막음).

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 구독 고유번호 — ULID with sub_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 구독자 사용자 ID |
|  | `plan` | `VARCHAR(20)` | 구독 플랜 CHECK (plan IN ('free','pro','premium')) |
|  | `status` | `VARCHAR(20)` | 상태 CHECK (status IN ('active','cancelled','expired')) |
|  | `started_at` | `TIMESTAMPTZ` | 구독 시작일 |
|  | `ends_at` | `TIMESTAMPTZ` | 구독 만료일 |
|  | `auto_renew` | `BOOLEAN` | 자동 갱신 여부 |
|  | `cancelled_at` | `TIMESTAMPTZ` | 취소 시각 (NULL=미취소) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_sub_user_status ON subscription(user_id, status);
```

### `user_usage`

월별/기간별 feature 사용량 카운터(hot). **플랜별 강의·슬라이드 쿼터가 여기 반영됨** (free: 강의 1개, pro: 3개, premium: 8개). 기능별로 서로 다른 quota를 줄 수 있도록 feature 키로 분리

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with usg_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유 사용자 |
|  | `feature` | `VARCHAR(50)` | 기능 키 (course_generation/ai_tutor_session 등) |
|  | `period_start` | `DATE` | 집계 기간 시작 (보통 월초) |
|  | `period_end` | `DATE` | 집계 기간 끝 |
|  | `used_count` | `INTEGER` | 현재까지 사용 횟수 (DEFAULT 0) |
|  | `quota_limit` | `INTEGER` | 해당 기간 한도 (course_generation 기준: free=1, pro=3, premium=8) |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 갱신 시각 |

UNIQUE: `(user_id, feature, period_start)`

#### 인덱스

```sql
CREATE INDEX idx_usg_user_feature_period ON user_usage(user_id, feature, period_start DESC);
```

### `payment`

개별 결제 트랜잭션 이력/감사용. 토스 paymentKey로 멱등 처리

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 결제 고유번호 — ULID with pay_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 결제한 사용자 |
| FK | `subscription_id` | `VARCHAR(30)` | 연결된 구독 |
|  | `amount` | `INTEGER` | 결제 금액 (원) |
| UQ | `toss_payment_key` | `VARCHAR(255) UNIQUE` | 토스 결제 키 (멱등성 보장) |
|  | `toss_order_id` | `VARCHAR(255)` | 토스 주문 ID |
|  | `status` | `VARCHAR(20)` | 결제 상태 CHECK (status IN ('paid','failed','refunded')) |
|  | `paid_at` | `TIMESTAMPTZ` | 결제 완료 시각 |
|  | `deleted_at` | `TIMESTAMPTZ DEFAULT NULL` | Soft delete: 활성 레코드는 deleted_at IS NULL 조건 필수 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 결제 요청 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_pay_user_id ON payment(user_id);
CREATE INDEX idx_pay_subscription_id ON payment(subscription_id);
CREATE UNIQUE INDEX uq_pay_toss_key ON payment(toss_payment_key);
```

### `subscription_plan`

구독 플랜 메타. 플랜 코드별 가격·기간·기능 목록·쿼터를 관리함.
v1.5 기준 3종(free/pro/premium). 플랜 코드가 PK다.

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `code` | `VARCHAR(30)` | 플랜 코드 PK (free/pro/premium) |
|  | `price_krw` | `INTEGER` | 월정액 (원). free=0, pro=14900, premium=49900 (V19 인하, 2026-06-12) |
|  | `period_days` | `INTEGER` | 구독 기간 (일). 월 구독=30 |
|  | `max_courses` | `INTEGER` | 계정당 강의 생성 최대 개수. free=1, pro=2, premium=10 (V19 변경) |
|  | `max_slides_per_course` | `INTEGER NULLABLE` | 강의당 슬라이드 접근 최대 개수. free=롤링 언락(2개 활성), pro/premium=NULL(제한 없음) |
|  | `can_use_telegram` | `BOOLEAN` | 텔레그램 봇 사용 가능 여부. free=false, pro/premium=true |
|  | `can_customize_tutor` | `BOOLEAN` | AI 튜터 커스터마이징 가능 여부. free=false, pro/premium=true |
|  | `features` | `JSONB` | 플랜 기능 설명 목록 JSONB (UI 표시용 텍스트) |
|  | `is_active` | `BOOLEAN` | 판매 중 여부 (DEFAULT TRUE) |

#### seed 데이터

```sql
-- 요금제 v1.5 (2026-04-20) — Plus→Pro, Pro→Premium 명칭 변경
INSERT INTO subscription_plan (code, price_krw, period_days, max_courses, max_slides_per_course, can_use_telegram, can_customize_tutor, features, is_active)
VALUES
  ('free',    0,     30, 1, 2,    false, false,
   '["강의 1개", "슬라이드 롤링 언락(항상 2개 활성)", "기본 튜터 2종 선택"]'::jsonb,
   true),
  ('pro',     49900, 30, 3, NULL, true,  true,
   '["강의 3개", "슬라이드 10~15개 전체 제공", "텔레그램 봇 전 기능", "AI 튜터 커스터마이징"]'::jsonb,
   true),
  ('premium', 99900, 30, 8, NULL, true,  true,
   '["강의 8개", "슬라이드 10~15개 전체 제공", "텔레그램 봇 전 기능", "AI 튜터 커스터마이징"]'::jsonb,
   true);
```

> V19 가격 인하 (2026-06-12) + V20 코인 정정 (2026-06-13): 위 seed는 V1 기준 초기값. V17에서 `coin_grant` 컬럼 추가, V19에서 pro=14900원/2개, premium=49900원/10개로 갱신. coin_grant는 V19가 "원치"를 코인 개수로 잘못 넣어(15,000/50,000), V20에서 번들 단가 환산값으로 정정: **pro=5코인(≈15,000원치=COIN_5), premium=22코인(≈50,000원치=COIN_20)** (`V20__fix_subscription_coin_grant.sql`). V17의 1원=1코인 환산은 폐기.

> course 테이블 연관 주석: `course` 생성 시 해당 사용자의 플랜 `max_courses`를 API 레이어에서 체크한다.
> 슬라이드 접근 시 `max_slides_per_course`가 NULL이 아닌 경우(=free 플랜) 롤링 언락 정책(항상 2개 활성, 1개 완료 시 다음 1개 자동 해제)이 적용된다.
> 쿼터 체크는 DB 제약이 아니라 API 레이어 미들웨어에서 한다.

### 결제 성공 시 데이터 흐름

```
[토스 결제 webhook]
  1. payment INSERT (트랜잭션 이력)
  2. subscription INSERT/UPDATE (계약 상태)
  3. user UPDATE (subscription_status='pro'|'premium', subscription_started_at, subscription_expires_at)
  4. user_usage UPSERT (플랜별 quota_limit 조정, 필요 시 현재 기간 row 생성)
```

### `coin_wallet`

AI 코인 잔액 지갑. 사용자당 1개. 코인 사용/충전 시 balance가 갱신됨

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 지갑 고유번호 — ULID with cwl_ prefix |
| FK·UQ | `user_id` | `VARCHAR(30) UNIQUE` | 소유 사용자 (1:1) |
|  | `balance` | `INTEGER NOT NULL DEFAULT 0` | 현재 코인 잔액 |
|  | `total_purchased` | `INTEGER NOT NULL DEFAULT 0` | 누적 구매 코인 수 |
|  | `total_used` | `INTEGER NOT NULL DEFAULT 0` | 누적 사용 코인 수 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 갱신 시각 |

#### 인덱스

```sql
CREATE INDEX idx_cwl_user_id ON coin_wallet(user_id);
```

> balance 차감은 SELECT FOR UPDATE → UPDATE 순으로 직렬화. 잔액 부족 시 409 Conflict 반환.

### `coin_transaction`

AI 코인 사용·충전 이력. coin_wallet의 변화를 이벤트 소스로 기록

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 트랜잭션 고유번호 — ULID with ctx_ prefix |
| FK | `wallet_id` | `VARCHAR(30)` | 소속 지갑 |
| FK | `user_id` | `VARCHAR(30)` | 소유 사용자 (중복 보관 — 조회 편의) |
|  | `type` | `VARCHAR(20) NOT NULL` | 트랜잭션 유형 CHECK (type IN ('purchase','use_slide','use_exam','refund')). `refund`는 미래 확장용 예약값 (v1.7+에서 정책 확정 예정). `use_course` 값 제거 — 강의 잠금 정책 폐지(v1.6) |
|  | `amount` | `INTEGER NOT NULL` | 코인 변화량. 충전=양수, 사용=음수 |
|  | `balance_after` | `INTEGER NOT NULL` | 트랜잭션 후 잔액 스냅샷 |
|  | `ref_id` | `VARCHAR(30)` | 연관 리소스 ID (slide_id / mock_exam_id, NULLABLE) |
|  | `payment_id` | `VARCHAR(30)` | 연관 결제 ID (코인 구매 시, NULLABLE) |
|  | `note` | `VARCHAR(200)` | 사용 맥락 메모 (NULLABLE) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_ctx_wallet_id ON coin_transaction(wallet_id);
CREATE INDEX idx_ctx_user_created ON coin_transaction(user_id, created_at DESC);
CREATE INDEX idx_ctx_type ON coin_transaction(type, created_at DESC);
```

---

## 4. 학습 콘텐츠

> PDF → 슬라이드 → 음성 과외 생성물

### `course`

과외 단위. PDF 또는 주제로부터 생성된 강의 1세트

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 과외 고유번호 — ULID with crs_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 학습자 |
| FK | `ai_teacher_id` | `VARCHAR(30)` | 담당 AI 선생님 |
|  | `course_name` | `VARCHAR(255)` | 과외 제목 |
|  | `source_type` | `VARCHAR(20)` | 입력 유형 CHECK (source_type IN ('pdf','topic')) |
|  | `source_pdf_url` | `VARCHAR(500)` | 원본 PDF S3 URL |
|  | `topic_text` | `TEXT` | 주제 텍스트 (PDF 없을 때) |
|  | `mode` | `VARCHAR(20)` | 수업 모드 CHECK (mode IN ('lecture','socratic')) |
|  | `tone` | `VARCHAR(20)` | 말투 |
|  | `available_time_min` | `INTEGER` | 가용 시간 (분, 과제 분량 결정용) |
|  | `total_chapters` | `INTEGER` | 총 챕터 수 (DEFAULT 0) |
|  | `total_slides` | `INTEGER` | 총 슬라이드 수 (DEFAULT 0) |
|  | `completed_slides` | `INTEGER` | 완료한 슬라이드 수 (DEFAULT 0) |
|  | `progress_percent` | `INTEGER` | 진도율 % (DEFAULT 0) |
|  | `status` | `VARCHAR(20)` | 상태 CHECK (status IN ('generating','ready','in_progress','completed')) |
|  | `exam_date` | `DATE` | 시험일 (역산 스케줄용) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |
|  | `completed_at` | `TIMESTAMPTZ` | 완료 시각. `status = 'completed'`로 전환될 때 함께 기록됨 |

> 완강 판정 기준: `status = 'completed'` AND `completed_at IS NOT NULL`. 완강 여부 체크는 API 레이어에서 `course.status` 컬럼으로 처리한다. 강의 단위 잠금은 없음 — 강의 진입은 항상 허용된다.

#### 인덱스

```sql
CREATE INDEX idx_crs_user_id ON course(user_id);
CREATE INDEX idx_crs_user_status ON course(user_id, status);
CREATE INDEX idx_crs_ai_teacher_id ON course(ai_teacher_id);
```

### `chapter`

강의 챕터. course의 하위 단위

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 챕터 고유번호 — ULID with chp_ prefix |
| FK | `course_id` | `VARCHAR(30)` | 소속 과외 |
|  | `chapter_name` | `VARCHAR(255)` | 챕터 제목 |
|  | `order_index` | `INTEGER` | 순서 (1, 2, 3...) |
|  | `total_slides` | `INTEGER` | 챕터 내 총 슬라이드 수 (DEFAULT 0) |
|  | `completed_slides` | `INTEGER` | 챕터 내 완료 슬라이드 수 (DEFAULT 0) |
|  | `status` | `VARCHAR(20)` | 상태 CHECK (status IN ('generating','ready','in_progress','completed')) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_chp_course_id ON chapter(course_id);
```

### `slide`

슬라이드. 콘텐츠 JSON + 음성 OGG URL + Mermaid 다이어그램

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 슬라이드 고유번호 — ULID with sld_ prefix |
| FK | `chapter_id` | `VARCHAR(30)` | 소속 챕터 |
|  | `order_index` | `INTEGER` | 챕터 내 순서 |
|  | `slide_title` | `VARCHAR(255)` | 슬라이드 제목 |
|  | `content_json` | `JSONB` | 슬라이드 콘텐츠 (제목/본문/이미지) |
|  | `mermaid_diagram` | `TEXT` | Mermaid 다이어그램 코드 |
|  | `script_text` | `TEXT` | 음성 대본 텍스트 |
|  | `audio_url` | `VARCHAR(500)` | TTS 생성 오디오 파일 URL (S3/MinIO, NULLABLE) |
|  | `audio_duration_sec` | `INTEGER` | 음성 길이 (초) |
|  | `fact_check_passed` | `BOOLEAN` | Opus 팩트체크 통과 여부 |
| 신규 | `audio_duration_ms` | `INTEGER` | 오디오 총 길이 (밀리초, NULLABLE) |
| 신규 | `cue_points` | `JSONB` | 큐포인트 배열. 예: `[{"offset_ms": 0, "anchor": "title"}, {"offset_ms": 3200, "anchor": "bullet_1"}]` (NULLABLE) |
| 신규 | `lock_status` | `VARCHAR(10) NOT NULL DEFAULT 'locked'` | 잠금 상태 CHECK (lock_status IN ('active','locked')). Free 플랜 롤링 언락 정책 적용 — Pro/Premium은 항상 active |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_sld_chapter_id ON slide(chapter_id);
CREATE INDEX idx_sld_lock_status ON slide(chapter_id, lock_status);
```

### `vector_chunk`

RAG 검색용 텍스트 청크. Qdrant point_id로 벡터 DB와 연결

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 청크 고유번호 — ULID with vck_ prefix |
| FK | `course_id` | `VARCHAR(30)` | 소속 과외 |
|  | `chunk_text` | `TEXT` | 청크 원본 텍스트 |
|  | `source_page` | `INTEGER` | 원본 PDF 페이지 번호 |
|  | `qdrant_point_id` | `UUID` | Qdrant 벡터 포인트 ID |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_vck_course_id ON vector_chunk(course_id);
```

### `concept` (신규)

개념 단위. 과외 내 학습 개념을 자기참조 트리로 구성하여 지식 그래프를 형성함. `mastery_threshold`로 숙련 판정 기준을 개념별로 다르게 설정할 수 있음

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 개념 고유번호 — ULID with cnc_ prefix |
| FK | `course_id` | `VARCHAR(30)` | 소속 과외 |
| FK | `parent_concept_id` | `VARCHAR(30)` | 상위 개념 ID (NULLABLE, 자기참조) |
|  | `name` | `VARCHAR(200)` | 개념 이름 |
|  | `description` | `TEXT` | 개념 설명 |
|  | `mastery_threshold` | `DECIMAL(3,2)` | 숙련 판정 임계값 (DEFAULT 0.80) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_cnc_course_id ON concept(course_id);
CREATE INDEX idx_cnc_parent_concept_id ON concept(parent_concept_id);
```

### `playback_event` (신규)

학습 행동 이벤트 로그. 음성-슬라이드 재생 중 발생하는 play/pause/seek/complete/cue_hit 이벤트를 기록함. 완독률·일시정지 지점·큐포인트 도달 여부를 분석하여 학습 행동 패턴을 파악하는 데 사용됨

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 이벤트 고유번호 — ULID with pbe_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 이벤트 발생 사용자 |
| FK | `slide_id` | `VARCHAR(30)` | 대상 슬라이드 |
|  | `event_type` | `VARCHAR(20)` | 이벤트 유형 CHECK (event_type IN ('play','pause','seek','complete','cue_hit')) |
|  | `offset_ms` | `INTEGER` | 이벤트 발생 시점 오디오 오프셋 (밀리초) |
|  | `cue_anchor` | `VARCHAR(100)` | cue_hit 이벤트일 때 도달한 앵커명 (NULLABLE) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 이벤트 기록 시각 |

#### 인덱스

```sql
CREATE INDEX idx_pbe_user_created ON playback_event(user_id, created_at DESC);
CREATE INDEX idx_pbe_slide_event ON playback_event(slide_id, event_type);
```

-- 파티셔닝 계획 (운영 전환 시점):
--   - 전략: created_at 월단위 RANGE 파티셔닝 (e.g., playback_event_2026_04)
--   - 인덱스: BRIN ON created_at (대용량 시간 범위 조회)
--   - 보존: 12개월 후 콜드 스토리지 아카이브

### `qa`

AI 선생님 질의응답 기록. 학습 중 발생한 질문과 AI 답변을 보관함

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | QA 고유번호 — ULID with qna_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 질문자 |
| FK | `course_id` | `VARCHAR(30)` | 연결 과외 (NULLABLE) |
|  | `question` | `TEXT` | 질문 내용 |
|  | `answer` | `TEXT` | AI 답변 |
|  | `citations` | `JSONB` | 출처 슬라이드·청크 참조 배열 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 기록 시각 |

#### 인덱스

```sql
CREATE INDEX idx_qna_user_created ON qa(user_id, created_at DESC);
CREATE INDEX idx_qna_course_id ON qa(course_id);
```

-- 파티셔닝 계획 (운영 전환 시점):
--   - 전략: created_at 월단위 RANGE 파티셔닝 (e.g., qa_2026_04)
--   - 인덱스: BRIN ON created_at (대용량 시간 범위 조회)
--   - 보존: 12개월 후 콜드 스토리지 아카이브

### `comprehension_test`

슬라이드 단위 이해도 체크 테스트. 학습 직후 자동 출제됨

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with cht_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 응시자 |
| FK | `slide_id` | `VARCHAR(30)` | 대상 슬라이드 |
|  | `score` | `NUMERIC(5,2)` | 획득 점수 |
|  | `taken_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 응시 시각 |

#### 인덱스

```sql
CREATE INDEX idx_cht_user_id ON comprehension_test(user_id);
CREATE INDEX idx_cht_slide_id ON comprehension_test(slide_id);
```

### `curriculum`

커리큘럼 문서. PDF 또는 주제에서 생성된 학습 계획서로 `/tutoring/{id}/curriculum` 엔드포인트의 영속 대상이다. AI가 생성하며 초안(draft) → 확정(confirmed) → 보관(archived) 상태로 전이됨

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with cur_ prefix |
| FK | `course_id` | `VARCHAR(30)` | 소속 과외 |
|  | `curriculum_name` | `VARCHAR(255)` | 커리큘럼 제목 |
|  | `description` | `TEXT` | 커리큘럼 본문 (Markdown) |
|  | `generated_by` | `VARCHAR(100)` | 생성에 사용된 LLM 모델명 (NULLABLE) |
|  | `status` | `VARCHAR(20)` | 상태 CHECK (status IN ('draft','confirmed','archived')) DEFAULT 'draft' |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 갱신 시각 |

#### 인덱스

```sql
CREATE INDEX idx_cur_course_status ON curriculum(course_id, status);
```

### `curriculum_unit`

커리큘럼 차시 단위. `curriculum` 1건이 여러 차시로 구성되며, 각 차시는 학습 목표·예상 시간·연결 강의를 가진다. `GET /tutoring/{id}/curriculum` 응답에 포함되어 프론트엔드 타임라인 렌더링에 사용된다. 실제 슬라이드 본문은 확정 이후 ChapterStudio_V1이 강의 1개 단위로 생성해 `iframe_html` 완성본으로 저장한다.

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 차시 고유번호 — ULID with cun_ prefix |
| FK | `curriculum_id` | `VARCHAR(30)` | 소속 커리큘럼 (NOT NULL) |
|  | `order_index` | `INTEGER` | 차시 순서 (1, 2, 3...) NOT NULL |
|  | `unit_name` | `VARCHAR(200)` | 차시명 (예: "1차시 - 이차방정식 기초") NOT NULL |
|  | `objective` | `TEXT` | 학습 목표 (NULLABLE) |
| FK | `lesson_ref` | `VARCHAR(30)` | 연결된 lesson/chapter ID (NULLABLE, 생성 전 단계 허용) |
|  | `duration_min` | `INTEGER` | 예상 학습 시간 (분, NULLABLE) |
|  | `status` | `VARCHAR(20)` | 차시 상태 CHECK (status IN ('pending','ready','completed')) DEFAULT 'pending' |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 갱신 시각 |

UNIQUE: `(curriculum_id, order_index)` — 동일 커리큘럼 내 차시 순서 중복 불가

#### 인덱스

```sql
CREATE INDEX idx_cun_curriculum_order ON curriculum_unit(curriculum_id, order_index);
CREATE INDEX idx_cun_lesson_ref ON curriculum_unit(lesson_ref);
```

---

## 5. 과제·채점

> 문제 생성 → 제출 → AI 채점 → 피드백

### `assignment`

개별 과제 단위. 수업(챕터) 완료 시 자동 생성되며, AI가 만든 문제 묶음 1건을 나타낸다. `/assignments/{id}` 엔드포인트가 이 테이블의 단건을 대상으로 한다. 여러 과제를 묶을 때는 `assignment_set`을 사용하며 `assignment_set_id`로 연결됨. 텔레그램으로 발송되고 제출·채점 사이클을 거친다

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 과제 고유번호 — ULID with asg_ prefix |
| FK | `course_id` | `VARCHAR(30)` | 소속 과외 |
| FK | `chapter_id` | `VARCHAR(30)` | 소속 챕터 |
| FK | `user_id` | `VARCHAR(30)` | 대상 학습자 |
| FK | `assignment_set_id` | `VARCHAR(30)` | 과제 묶음 (NULLABLE) |
|  | `due_date` | `TIMESTAMPTZ NOT NULL` | 과제 마감 시각. AssignmentGrader가 텔레그램 웹훅 수신 시점에 마감 초과 여부를 참조함 |
|  | `expected_input_type` | `VARCHAR(32) NOT NULL` | 기대 제출 유형. 과제 생성 시 필수 지정. 값: `code_python` / `text_essay` / `handwritten_solution` / `audio_oral` / `project_archive` |
|  | `allowed_extensions` | `TEXT[] NOT NULL` | 허용 확장자 목록 (PostgreSQL 배열). `expected_input_type`에서 자동 파생되며 교사가 override 가능. 예: `{".py"}` / `{".png",".jpg",".pdf"}` |
|  | `available_time_min` | `INTEGER` | 풀이 가능 시간 (분) |
|  | `total_questions` | `INTEGER` | 총 문제 수 (DEFAULT 0) |
|  | `status` | `VARCHAR(20)` | 상태 CHECK (status IN ('submitted','graded')) |
|  | `total_score` | `INTEGER` | 총점 (DEFAULT 0) |
|  | `correct_count` | `INTEGER` | 맞춘 문제 수 (DEFAULT 0) |
|  | `sent_to_telegram_at` | `TIMESTAMPTZ` | 텔레그램 발송 시각 |
|  | `submitted_at` | `TIMESTAMPTZ` | 제출 시각 |
|  | `graded_at` | `TIMESTAMPTZ` | 채점 완료 시각 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### `expected_input_type` 허용 값

| 값 | 설명 | 기본 `allowed_extensions` |
|----|------|--------------------------|
| `code_python` | 파이썬 코드 제출 | `{".py"}` |
| `text_essay` | 텍스트 서술 제출 | `{".txt", ".md"}` |
| `handwritten_solution` | 손풀이 스캔/촬영 제출 | `{".png", ".jpg", ".jpeg", ".pdf"}` |
| `audio_oral` | 구술 음성 제출 | `{".mp3", ".m4a", ".wav"}` |
| `project_archive` | 프로젝트 압축 제출 | `{".zip"}` |

#### 인덱스

```sql
CREATE INDEX idx_asg_user_id ON assignment(user_id);
CREATE INDEX idx_asg_due_date ON assignment(due_date);
CREATE INDEX idx_asg_user_status ON assignment(user_id, status);
CREATE INDEX idx_asg_course_id ON assignment(course_id);
CREATE INDEX idx_asg_chapter_id ON assignment(chapter_id);
CREATE INDEX idx_asg_input_type ON assignment(expected_input_type);
```

### `question`

개별 문제. 객관식/주관식/서술형

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 문제 고유번호 — ULID with qst_ prefix |
| FK | `assignment_id` | `VARCHAR(30)` | 소속 과제 |
|  | `order_index` | `INTEGER` | 문제 번호 (1, 2, 3...) |
|  | `type` | `VARCHAR(20)` | 유형 CHECK (type IN ('objective','subjective','essay')) |
|  | `question_text` | `TEXT` | 문제 본문 |
|  | `options` | `JSONB` | 객관식 보기 배열 |
|  | `correct_answer` | `TEXT` | 정답 |
|  | `explanation` | `TEXT` | 해설 |
|  | `points` | `INTEGER` | 배점 (DEFAULT 0) |
|  | `topic` | `VARCHAR(100)` | 관련 토픽 (약점 추적용) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_qst_assignment_id ON question(assignment_id);
```

### `submission`

학생 제출물. 공식 제출(첫 제출) 기준. 재제출 이력은 `submission_retry` 테이블에 별도 저장됨. 텔레그램 웹훅 즉시 채점 방식.

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 제출 고유번호 — ULID with sbm_ prefix |
| FK | `assignment_id` | `VARCHAR(30)` | 소속 과제 |
| FK | `user_id` | `VARCHAR(30)` | 제출자 |
|  | `status` | `VARCHAR(32) NOT NULL DEFAULT 'queued'` | 채점 상태머신. 값: `queued`(웹훅 수신 후 채점 대기), `grading`(AssignmentGrader 처리 중), `done`(채점 완료), `failed`(콜백 5회 실패 DLQ). 재제출은 `queued` / `grading` 구간에서만 허용됨. 구 `pending`(배치 대기) / `queued_for_batch` / `ocr_running` / `rejected` 상태는 텔레그램 즉시 채점 방식 도입으로 폐지 |
|  | `official_file_url` | `VARCHAR(500)` | 공식 제출 파일 S3 URL (성적 반영 기준) |
|  | `official_ocr_text` | `TEXT` | 공식 제출 OCR 추출 텍스트 |
|  | `official_score` | `SMALLINT` | 공식 채점 점수 (성적 기준) |
|  | `official_feedback` | `TEXT` | 공식 채점 AI 피드백 |
|  | `official_ai_confidence` | `DECIMAL(4,3)` | 공식 채점 AI 신뢰도 (0.000~1.000) |
|  | `ocr_failed_pages` | `SMALLINT DEFAULT 0` | OCR 실패 페이지 수 |
|  | `total_pages` | `SMALLINT` | 전체 페이지 수 |
|  | `source` | `VARCHAR(20)` | 제출 경로 (telegram/web) |
|  | `submitted_at` | `TIMESTAMPTZ` | 제출 시각 (DEFAULT NOW()) |
|  | `original_zip_size` | `BIGINT NULL` | 업로드된 `.zip` 원본 용량 (바이트). 검증 실패 케이스도 기록 |
|  | `uncompressed_total_size` | `BIGINT NULL` | 해제 후 내부 파일 총 용량 합계 (바이트). 검증 통과 시에만 기록 |
|  | `file_count` | `INT NULL` | `.zip` 내부 엔트리 개수. 검증 시점에 계산 |
|  | `rejected_reason` | `VARCHAR(64) NULL` | 거절 사유 enum. 통과 시 NULL. 값: `unsupported_outer_extension`, `outer_too_large`, `too_many_files`, `unsupported_inner_extension`, `total_uncompressed_too_large`, `compression_ratio_too_high`, `path_traversal`, `nested_zip_forbidden`, `ocr_all_failed`, `type_mismatch` (L2 — 과제의 `expected_input_type`과 제출 파일 유형 불일치) |
|  | `submission_source` | `VARCHAR(16) NOT NULL DEFAULT 'web'` | 수신 채널. 값: `web`, `telegram` |
|  | `validated_at` | `TIMESTAMPTZ` | 검증 통과 시각. 통과분만 기록 |
|  | `deleted_at` | `TIMESTAMPTZ DEFAULT NULL` | Soft delete: 활성 레코드는 deleted_at IS NULL 조건 필수 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_sbm_assignment_id ON submission(assignment_id);
CREATE INDEX idx_sbm_user_created ON submission(user_id, created_at DESC);
CREATE INDEX idx_sbm_status ON submission(status);
CREATE INDEX idx_sbm_user_status ON submission(user_id, status);
CREATE INDEX idx_sbm_rejected_reason ON submission(rejected_reason);
CREATE INDEX idx_sbm_source ON submission(submission_source);
```

-- 파티셔닝 계획 (운영 전환 시점):
--   - 전략: created_at 월단위 RANGE 파티셔닝 (e.g., submission_2026_04)
--   - 인덱스: BRIN ON created_at (대용량 시간 범위 조회)
--   - 보존: 12개월 후 콜드 스토리지 아카이브

`rejected_reason` 은 FastAPI 수신 레이어의 zip 검증 실패 또는 전체 OCR 실패(`ocr_all_failed`) 시 기록함. 검증 규칙 상세는 `Docs/02_API/Spring_API.md` 과제 제출 엔드포인트 절에 있다. `submission_source` 는 `web` 또는 `telegram` 이며, 두 경로 모두 동일한 검증 규칙이 적용됨. 공식 점수는 `official_score`에 확정 저장되며 재제출 점수는 `submission_retry`에 별도 보관됨.

### `submission_retry`

재제출 이력. `queued` / `grading` 상태에서만 재제출이 허용되며 레코드가 누적됨. 피드백 참고용이며 공식 성적에는 영향을 주지 않음.

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `retry_id` | `BIGINT AUTO_INCREMENT` | PK |
| FK·UQ | `submission_id` + `retry_no` | — | UNIQUE KEY. 동일 submission의 중복 retry_no 방지 |
| FK | `submission_id` | `BIGINT NOT NULL` | 원본 제출 ID (ON DELETE CASCADE) |
|  | `retry_no` | `INT NOT NULL` | 재제출 순번 (1부터 시작) |
|  | `retry_file_url` | `VARCHAR(512) NOT NULL` | 재제출 파일 S3 URL |
|  | `retry_ocr_text` | `TEXT` | OCR 추출 텍스트 |
|  | `retry_score` | `INT` | 재제출 채점 점수 (피드백 참고용) |
|  | `retry_feedback` | `TEXT` | AI 피드백 |
|  | `retry_ocr_failed_pages` | `VARCHAR(16)` | 실패/전체 페이지 수 (예: `"2/10"`) |
|  | `submitted_at` | `DATETIME NOT NULL` | 재제출 시각 |
|  | `graded_at` | `DATETIME` | 채점 완료 시각 |

### `grade`

문제별 채점 결과. Step Trace + 오류 4분류

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 채점 고유번호 — ULID with grd_ prefix |
| FK | `submission_id` | `VARCHAR(30)` | 제출 ID |
| FK | `question_id` | `VARCHAR(30)` | 문제 ID |
|  | `user_answer` | `TEXT` | 학생 답안 |
|  | `is_correct` | `BOOLEAN` | 정답 여부 |
|  | `partial_score` | `INTEGER` | 부분 점수 (DEFAULT 0) |
|  | `error_type` | `VARCHAR(30)` | 오류 분류 (계산/개념/공식/해석) |
|  | `feedback_text` | `TEXT` | AI 피드백 내용 |
|  | `step_trace_json` | `JSONB` | 풀이 과정 추적 JSON |
|  | `deleted_at` | `TIMESTAMPTZ DEFAULT NULL` | Soft delete: 활성 레코드는 deleted_at IS NULL 조건 필수 |
|  | `graded_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 채점 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_grd_submission_id ON grade(submission_id);
CREATE INDEX idx_grd_question_id ON grade(question_id);
CREATE INDEX idx_grd_submission_graded ON grade(submission_id, graded_at DESC);
```

### `assignment_set`

과제 묶음. 복수의 assignment를 하나의 set으로 묶어 관리함

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with ast_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유자 |
|  | `set_name` | `VARCHAR(255)` | 묶음 제목 |
|  | `target_date` | `DATE` | 목표 제출 날짜 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_ast_user_id ON assignment_set(user_id);
```

### `step_trace`

풀이 단계 추적. 제출별로 단계적 입력과 AI 피드백을 저장함

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with stp_ prefix |
| FK | `submission_id` | `VARCHAR(30)` | 소속 제출 |
|  | `step_index` | `INTEGER` | 풀이 단계 번호 |
|  | `input` | `TEXT` | 해당 단계 학생 입력 |
|  | `feedback` | `TEXT` | AI 단계별 피드백 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_stp_submission_id ON step_trace(submission_id);
```

-- 파티셔닝 계획 (운영 전환 시점):
--   - 전략: created_at 월단위 RANGE 파티셔닝 (e.g., step_trace_2026_04)
--   - 인덱스: BRIN ON created_at (대용량 시간 범위 조회)
--   - 보존: 12개월 후 콜드 스토리지 아카이브

### `ocr_result`

OCR 추출 결과. 제출 이미지에서 추출한 텍스트와 신뢰도를 저장함

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with ocr_ prefix |
| FK | `submission_id` | `VARCHAR(30)` | 소속 제출 |
|  | `raw_text` | `TEXT` | OCR 원본 추출 텍스트 |
|  | `confidence` | `NUMERIC(5,4)` | 신뢰도 (0.0~1.0) |
|  | `engine` | `VARCHAR(50)` | OCR 엔진명 |
|  | `processed_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 처리 시각 |

#### 인덱스

```sql
CREATE INDEX idx_ocr_submission_id ON ocr_result(submission_id);
```

### `grading_callback_dlq`

FastAPI → Spring 채점 완료 콜백이 지수 백오프 5회 재시도 후에도 실패한 경우 적재되는 DLQ(Dead Letter Queue). 운영자 수동 처리 대상이며, 해결 시 `submission.status`를 `done`으로 전이시킴.

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `dlq_id` | `BIGSERIAL PRIMARY KEY` | PK (BIGSERIAL 유지. ULID 예외) |
| FK | `submission_id` | `VARCHAR(30) NOT NULL` | 대상 제출 ID (ON DELETE CASCADE) |
|  | `payload` | `JSONB NOT NULL` | FastAPI가 전달하려 했던 원본 콜백 바디 |
|  | `failure_count` | `INT NOT NULL DEFAULT 5` | 실패 횟수 (DLQ 적재 시점에 5) |
|  | `last_error` | `TEXT` | 마지막 실패 에러 메시지 |
|  | `enqueued_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | DLQ 적재 시각 |
|  | `resolved_at` | `TIMESTAMPTZ` | 운영자 처리 완료 시각. NULL이면 미해결 |
|  | `resolved_by` | `VARCHAR(64)` | 처리한 운영자 식별자 |

#### 인덱스

```sql
CREATE INDEX idx_dlq_submission_id ON grading_callback_dlq(submission_id);
CREATE INDEX idx_dlq_unresolved ON grading_callback_dlq(resolved_at) WHERE resolved_at IS NULL;
```

-- 파티셔닝 계획 (운영 전환 시점):
--   - 전략: enqueued_at 월단위 RANGE 파티셔닝 (e.g., grading_callback_dlq_2026_04)
--   - 인덱스: BRIN ON enqueued_at (대용량 시간 범위 조회)
--   - 보존: 12개월 후 콜드 스토리지 아카이브

---

## 6. 노트·메모리

> AI 노트, 암기노트, 약점 추적, Self-Healing Memory

### `note`

학습 노트. AI 자동 생성 + 사용자 메모, 별표 가능

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 노트 고유번호 — ULID with nte_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유자 |
| FK | `course_id` | `VARCHAR(30)` | 소속 과외 |
| FK | `slide_id` | `VARCHAR(30)` | 연결된 슬라이드 |
|  | `type` | `VARCHAR(20)` | 유형 CHECK (type IN ('ai_auto','user_memo')) |
|  | `note_title` | `VARCHAR(255)` | 노트 제목 (NULLABLE) |
|  | `content` | `TEXT` | 노트 내용 |
|  | `is_starred` | `BOOLEAN` | 별표 여부 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_nte_user_id ON note(user_id);
CREATE INDEX idx_nte_course_id ON note(course_id);
CREATE INDEX idx_nte_slide_id ON note(slide_id);
```

### `memo_note`

암기노트. 슬라이드별 핵심 요약 노트, 텔레그램 발송 + 주제별 모음 웹페이지에서 열람

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 암기노트 고유번호 — ULID with mmo_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유자 |
| FK | `course_id` | `VARCHAR(30)` | 소속 과외 (주제별 모음 페이지 조인 기준) |
| FK | `slide_id` | `VARCHAR(30) NOT NULL` | 대상 슬라이드 — slide.id N:1 |
|  | `note_title` | `VARCHAR(255)` | 암기노트 제목 |
|  | `content_md` | `TEXT` | 마크다운 본문 |
|  | `image_url` | `VARCHAR(500)` | 렌더링 이미지 S3 URL |
|  | `sent_to_telegram_at` | `TIMESTAMPTZ` | 텔레그램 발송 시각 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_mmo_user_id ON memo_note(user_id);
CREATE INDEX idx_mmo_course_id ON memo_note(course_id);
CREATE INDEX idx_mmo_slide_id ON memo_note(slide_id);
```

### `bookmark`

슬라이드 북마크. 복습 과외 자동 생성 트리거

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 북마크 고유번호 — ULID with bkm_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유자 |
| FK | `course_id` | `VARCHAR(30)` | 소속 과외 |
| FK | `slide_id` | `VARCHAR(30)` | 북마크한 슬라이드 |
|  | `memo` | `TEXT` | 북마크 메모 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_bkm_user_id ON bookmark(user_id);
CREATE INDEX idx_bkm_slide_id ON bookmark(slide_id);
```

### `weakness_profile`

약점 프로필. 토픽별 오류 패턴 장기 추적

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 약점 프로필 고유번호 — ULID with wkp_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 대상 사용자 |
|  | `topic` | `VARCHAR(255)` | 대분류 토픽 (수열/미적분 등) |
|  | `subtopic` | `VARCHAR(255)` | 소분류 토픽 |
|  | `error_pattern` | `VARCHAR(50)` | 오류 패턴 (계산실수/개념혼동 등) |
|  | `frequency` | `INTEGER` | 오류 발생 횟수 (DEFAULT 0) |
|  | `severity` | `INTEGER` | 심각도 (1~5) CHECK (severity BETWEEN 1 AND 5) |
|  | `first_seen_at` | `TIMESTAMPTZ` | 최초 발견 시각 |
|  | `last_seen_at` | `TIMESTAMPTZ` | 최근 발견 시각 |
|  | `resolved_at` | `TIMESTAMPTZ` | 극복 시각 (극복 시 기록) |

#### 인덱스

```sql
CREATE INDEX idx_wkp_user_topic ON weakness_profile(user_id, topic);
```

### `memory`

Self-Healing Memory 3계층 (인덱스/토픽/원본)

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 메모리 고유번호 — ULID with mem_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유자 |
|  | `layer` | `INTEGER` | 계층 CHECK (layer IN (1,2,3)) — 1=인덱스/2=토픽/3=원본 |
|  | `topic` | `VARCHAR(255)` | 주제 키워드 |
|  | `content_json` | `JSONB` | 메모리 내용 JSON |
|  | `source_type` | `VARCHAR(50)` | 출처 유형 CHECK (source_type IN ('lecture','assignment','chat')) |
|  | `updated_by_agent` | `VARCHAR(50)` | 마지막 갱신 에이전트 이름 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 갱신 시각 |

#### 인덱스

```sql
CREATE INDEX idx_mem_user_id ON memory(user_id);
CREATE INDEX idx_mem_user_topic ON memory(user_id, topic);
```

### `weakness_profile` 갱신 트리거 소스 (2026-04-14 확정)

`weakness_profile` 테이블은 아래 두 경로에서만 갱신된다. 구 "Daily 채점 배치" 경로는 폐기되었다.

| 경로 | 컴포넌트 | 실행 시점 | 갱신 조건 |
|---|---|---|---|
| **②c 퀴즈 소크라테스** | `QAResponder` (FastAPI) | 세션 종료 즉시 | 오답 또는 부분이해로 판정된 개념 |
| **⑥ 과제 채점** | `AssignmentGrader` (FastAPI) | 텔레그램 웹훅 즉시 채점 완료 콜백 수신 후 Spring이 처리 | `errorType`이 null이 아닌 문항의 개념 |

갱신 로직: `topic` + `subtopic` + `user_id` 기준 UPSERT. 기존 행이 있으면 `frequency += 1`, `last_seen_at = NOW()`. 없으면 INSERT.

---

### `weakness_snapshot`

약점 타임시리즈. `/reports/weakness-trend` API용 날짜별 숙련도 스냅샷

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with wks_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 대상 사용자 |
|  | `topic` | `VARCHAR(255)` | 추적 토픽 |
|  | `mastery` | `NUMERIC(5,4)` | 숙련도 (0.0~1.0) |
|  | `snapshot_date` | `DATE` | 스냅샷 날짜 |

UNIQUE: `(user_id, topic, snapshot_date)`

#### 인덱스

```sql
CREATE INDEX idx_wks_user_topic_date ON weakness_snapshot(user_id, topic, snapshot_date DESC);
```

---

## 7. 외부·운영

> 텔레그램, 일정, 모의고사, 알림, 운영 기능

### `telegram_account` (신규)

텔레그램 계정 연동 정보(1:1 with user). 텔레그램 봇과의 계정 바인딩 상태를 관리함. `telegram_message`는 송수신 로그이고, 이 테이블은 계정 연결 메타데이터를 담당함. 파더봇(base bot)으로 사용자가 먼저 메시지를 보내면 `message.chat.id`를 저장하고, 이후 아웃바운드 메시지 전송에 해당 chat_id를 사용함

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 계정 연동 고유번호 — ULID with tga_ prefix. CHECK (id ~ '^tga_[0-9A-Z]{26}$') |
| FK·UQ | `user_id` | `VARCHAR(30) UNIQUE` | 소유 사용자 (1:1) |
| UQ | `telegram_chat_id` | `BIGINT UNIQUE` | 텔레그램 채팅 ID (파더봇 ↔ 사용자 1:1 채팅 식별자, 아웃바운드 전송 타깃, NOT NULL, 외부 API 식별자이므로 BIGINT 유지). **정본 컬럼** — `user.telegram_chat_id`(VARCHAR)는 이 값의 read-only mirror이며 동일 값을 중복 저장하는 임시 구조다. 최종 deprecate 여부·동기화 방식은 Spring 담당자가 확정한다. |
|  | `telegram_username` | `VARCHAR(100)` | 텔레그램 사용자명 |
|  | `linked_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 연동 시각 |
|  | `is_active` | `BOOLEAN` | 활성 연동 여부 (DEFAULT true) |
|  | `last_interaction_at` | `TIMESTAMPTZ` | 마지막 상호작용 시각 (NULLABLE) |

#### 인덱스

```sql
CREATE INDEX idx_telegram_account_chat_id ON telegram_account(telegram_chat_id);
CREATE INDEX idx_telegram_account_user_id ON telegram_account(user_id);
```

### `telegram_message`

텔레그램 송수신 로그. webhook 처리

<!-- 슈퍼 관리자 페이지(PAGE 16 F16.2)에서 chat_id 기준으로 조회되는 원천 데이터다. -->

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 메시지 고유번호 — ULID with tgm_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 사용자 |
|  | `direction` | `VARCHAR(10)` | 방향 CHECK (direction IN ('in','out')) |
|  | `message_type` | `VARCHAR(30)` | 유형 (assignment/grade/memo 등) |
|  | `content` | `TEXT` | 메시지 본문 |
|  | `image_url` | `VARCHAR(500)` | 첨부 이미지 URL |
|  | `reference_type` | `VARCHAR(30)` | 연결 객체 유형 |
|  | `reference_id` | `VARCHAR(30)` | 연결 객체 ID |
|  | `telegram_msg_id` | `VARCHAR(50)` | 텔레그램 원본 메시지 ID |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 시각 |

#### 인덱스

```sql
CREATE INDEX idx_tgm_user_created ON telegram_message(user_id, created_at DESC);
```

-- 파티셔닝 계획 (운영 전환 시점):
--   - 전략: created_at 월단위 RANGE 파티셔닝 (e.g., telegram_message_2026_04)
--   - 인덱스: BRIN ON created_at (대용량 시간 범위 조회)
--   - 보존: 12개월 후 콜드 스토리지 아카이브

### `admin_telegram_credential` (보안 자격정보)

슈퍼 관리자의 텔레그램 봇 운영 자격정보. 봇 토큰 로테이션·관리자 알림 수신 chat_id를 재배포 없이 교체할 수 있도록 DB에 암호화 저장한다. 슈퍼 관리자 계정 1개당 행 1개(1:1).

<!-- 슈퍼 관리자 페이지(PAGE 16 F16.3)에서 조회·수정한다. bot_token_encrypted는 평문 노출 금지. -->

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `BIGSERIAL PRIMARY KEY` | PK (BIGSERIAL 유지. ULID 예외) |
| FK·UQ | `user_id` | `VARCHAR(30) UNIQUE` | FK → `user.id`. 슈퍼 관리자 계정 (1:1). 구 컬럼명: `admin_user_id` |
|  | `bot_token_encrypted` | `TEXT NOT NULL` | 암호화된 봇 토큰 (KMS or 애플리케이션 레벨 AES — 방식은 Spring 담당자 확정) |
|  | `bot_token_fingerprint` | `VARCHAR(32)` | 마스킹 표시·변경 감지용 해시 prefix |
|  | `admin_chat_id` | `BIGINT` | 관리자 본인이 운영 알림·이상 감지 메시지를 수신할 텔레그램 chat_id |
|  | `last_rotated_at` | `TIMESTAMPTZ` | 마지막 봇 토큰 교체 시각 |
|  | `last_health_check_at` | `TIMESTAMPTZ` | `getMe` 헬스체크 성공 시각 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

인덱스:
- `(admin_user_id)` — UNIQUE 제약으로 자동 생성
- `idx_admin_telegram_credential_fingerprint` ON `(bot_token_fingerprint)` — 변경 감지·검색용 (선택적. Spring 담당자가 토큰 변경 감지 메커니즘 확정 시 추가)

> 암호화 방식(KMS 사용 여부·애플리케이션 레벨 AES·키 보관 위치)과 토큰 rotation 정책은 Spring 담당자가 최종 확정한다. FastAPI `app/telegram/` 라우터와의 토큰 동기화 메커니즘도 담당자 합의 후 확정한다.

### `admin_audit_log` *(2026-04-15 stub)*

<!-- F16.1·F16.3 편집 이력 감사 로그. 상세 스키마·인덱스·보존 정책은 Spring 담당자가 확정한다 (2026-04-15 stub). -->

슈퍼 관리자가 수행한 편집 작업의 감사 로그. F16.1(유저 chat_id 편집·연동 해제)과 F16.3(봇 자격정보 편집) 작업 이력을 기록한다. 상세 스키마·인덱스·보존 정책은 Spring 담당자가 최종 확정한다.

| 키 | 컬럼명 | 타입 | 제약 | 설명 |
|---|---|---|---|---|
| PK | `id` | `BIGSERIAL` | PK | 로그 고유번호 (BIGSERIAL 유지. ULID 예외) |
| FK | `user_id` | `VARCHAR(30)` | FK → `user.id` | 작업자 (슈퍼 관리자). 구 컬럼명: `actor_user_id` |
|  | `action` | `VARCHAR(64)` | NOT NULL | 수행 작업 (`edit_chat_id`, `edit_credential`, `unlink_telegram` 등) |
|  | `target_entity` | `VARCHAR(64)` | NOT NULL | 대상 테이블 (`telegram_account`, `admin_telegram_credential` 등) |
|  | `target_id` | `VARCHAR(64)` | | 대상 레코드 id (다형성 FK 예외 — 참조 대상: telegram_account 또는 admin_telegram_credential) |
|  | `before_hash` | `VARCHAR(64)` | | 변경 전 값 해시 (평문 저장 금지) |
|  | `after_hash` | `VARCHAR(64)` | | 변경 후 값 해시 (평문 저장 금지) |
|  | `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT NOW() | 로그 기록 시각 |

> 인덱스·보존 정책(보존 기간, 파티셔닝, 아카이빙)·`before_hash`/`after_hash` 해시 알고리즘은 Spring 담당자가 최종 확정한다.

---

### `schedule`

학습 일정. 캘린더 + 시험 역산 스케줄

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 일정 고유번호 — ULID with sch_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 사용자 |
| FK | `course_id` | `VARCHAR(30)` | 연결 과외 |
|  | `type` | `VARCHAR(20)` | 유형 CHECK (type IN ('study','exam','review')) |
|  | `title` | `VARCHAR(255)` | 일정 제목 |
|  | `scheduled_at` | `TIMESTAMPTZ` | 예정 시각 |
|  | `duration_min` | `INTEGER` | 소요 시간 (분) |
|  | `status` | `VARCHAR(20)` | 상태 CHECK (status IN ('planned','done','skipped')) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_sch_user_id ON schedule(user_id);
CREATE INDEX idx_sch_course_id ON schedule(course_id);
```

### `mock_exam`

모의고사 결과. 시험 일정 자동 트리거

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | 모의고사 고유번호 — ULID with mex_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 응시자 |
| FK | `course_id` | `VARCHAR(30)` | 대상 과외 |
|  | `scope_json` | `JSONB` | 출제 범위 JSON (챕터·토픽 배열) |
|  | `total_questions` | `INTEGER` | 총 문제 수 (DEFAULT 0) |
|  | `time_limit_min` | `INTEGER` | 제한 시간 (분) |
|  | `total_score` | `INTEGER` | 만점 |
|  | `user_score` | `INTEGER` | 획득 점수 (DEFAULT 0) |
| 신규 | `is_free_trial` | `BOOLEAN NOT NULL DEFAULT FALSE` | 완강 무료 1회 제공 여부. Pro/Premium 사용자가 해당 강의 완강 후 첫 1회 생성 시 TRUE. 코인 소모 없이 생성된 모의고사를 식별한다. |
|  | `completed_at` | `TIMESTAMPTZ` | 응시 완료 시각 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

> 무료 1회 중복 방지: `POST /exam-forges` 처리 시 API 레이어에서 `WHERE course_id = ? AND user_id = ? AND is_free_trial = true` 조회로 무료 사용 여부를 판정한다. DB 유니크 제약 없이 애플리케이션 레이어에서 관리한다.

#### 인덱스

```sql
CREATE INDEX idx_mex_user_id ON mock_exam(user_id);
CREATE INDEX idx_mex_course_id ON mock_exam(course_id);
CREATE INDEX idx_mex_user_course ON mock_exam(user_id, course_id, is_free_trial);
```

### `notification`

푸시/인앱 알림 기록

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with ntf_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 수신자 |
|  | `type` | `VARCHAR(50)` | 알림 유형 |
|  | `title` | `VARCHAR(255)` | 알림 제목 |
|  | `body` | `TEXT` | 알림 본문 |
|  | `payload` | `JSONB` | 딥링크 등 추가 데이터 (DEFAULT '{}') |
|  | `read_at` | `TIMESTAMPTZ` | 읽은 시각 (NULL=미읽음) |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 발송 시각 |
|  | `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 수정 시각 |

#### 인덱스

```sql
CREATE INDEX idx_ntf_user_id ON notification(user_id);
CREATE INDEX idx_ntf_user_created ON notification(user_id, created_at DESC);
CREATE INDEX idx_ntf_user_unread ON notification(user_id, read_at) WHERE read_at IS NULL;
```

-- 파티셔닝 계획 (운영 전환 시점):
--   - 전략: created_at 월단위 RANGE 파티셔닝 (e.g., notification_2026_04)
--   - 인덱스: BRIN ON created_at (대용량 시간 범위 조회)
--   - 보존: 12개월 후 콜드 스토리지 아카이브

### `calendar_event`

일정·리마인더. schedule과 별개로 사용자 직접 등록 이벤트를 포함함

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with cev_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 소유자 |
|  | `type` | `VARCHAR(30)` | 유형 CHECK (type IN ('tutoring','assignment_due','review','custom')) |
|  | `start_at` | `TIMESTAMPTZ` | 시작 시각 |
|  | `end_at` | `TIMESTAMPTZ` | 종료 시각 |
| FK | `course_id` | `VARCHAR(30)` | 연결 과외 (NULLABLE) |
|  | `metadata` | `JSONB` | 부가 정보 (DEFAULT '{}') |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_cev_user_id ON calendar_event(user_id);
CREATE INDEX idx_cev_user_start ON calendar_event(user_id, start_at);
```

### `socratic_session`

소크라테스식 대화 세션. AI가 질문으로 유도하는 학습 방식의 대화 내용을 저장함

<!-- 슈퍼 관리자 페이지(PAGE 16 F16.2)에서 chat_id 기준으로 조회되는 원천 데이터다. -->

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with soc_ prefix |
| FK | `user_id` | `VARCHAR(30)` | 참여자 |
|  | `discussion_topic` | `VARCHAR(255)` | 세션 주제 |
|  | `transcript` | `JSONB` | 대화 기록 JSON |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_soc_user_id ON socratic_session(user_id);
```

### `mock_exam_submission`

모의고사 응시 기록

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK | `id` | `VARCHAR(30) PRIMARY KEY` | PK — ULID with mes_ prefix |
| FK | `mock_exam_id` | `VARCHAR(30)` | 대상 모의고사 |
| FK | `user_id` | `VARCHAR(30)` | 응시자 |
|  | `started_at` | `TIMESTAMPTZ` | 응시 시작 시각 |
|  | `submitted_at` | `TIMESTAMPTZ` | 제출 시각 |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

#### 인덱스

```sql
CREATE INDEX idx_mes_mock_exam_id ON mock_exam_submission(mock_exam_id);
CREATE INDEX idx_mes_user_id ON mock_exam_submission(user_id);
```

### `mock_exam_grade`

모의고사 응시별 채점 결과

| 키 | 컬럼명 | 타입 | 설명 |
|---|---|---|---|
| PK·FK | `mock_exam_submission_id` | `VARCHAR(30) PRIMARY KEY` | 대상 응시 (1:1 PK = FK to mock_exam_submission. ULID 예외 — 1:1 구조) |
|  | `total_score` | `INTEGER` | 총점 (DEFAULT 0) |
|  | `breakdown` | `JSONB` | 문제별 채점 상세 JSON |
|  | `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 생성 시각 |

---

## DDL (PostgreSQL)

> 주의: 이 DDL 섹션은 구 UUID v4 / VARCHAR(36) 기반 레거시 DDL이다. 기준은 위 각 도메인 테이블 정의 섹션이며, ULID/VARCHAR(30) 전환과 컬럼 rename은 위 섹션이 최신 상태다. 이 DDL은 2026-04-17 리팩터 이전 형태를 보존한 참고용이며, 실제 마이그레이션 DDL은 별도 생성이 필요하다.

```sql
-- 일과 AI 과외 플랫폼 스키마 (레거시 DDL — 참고용)
-- PostgreSQL 15+, Cloud SQL
-- 2026-04-14 P0+P1 전면 개정 — snake_case 통일 / FK 전체 추가 / 26 테이블 신설
-- 2026-04-17 ULID 전환 리팩터는 위 테이블 정의 섹션에 반영됨 (이 DDL은 미갱신)

-- 플랫폼 회원 (cold). 계정/프로필/구독 상태 포함. 자주 갱신되는 상태는 분리된 테이블로 이동
CREATE TABLE "user" (
  id                     VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  email                  VARCHAR(255) UNIQUE,
  name                   VARCHAR(100),                          -- 소셜 프로필에서 자동 수집
  nickname               VARCHAR(50)  NOT NULL,                 -- 공개 닉네임
  profile_image_url      TEXT,
  oauth_provider         VARCHAR(20)  NOT NULL
                           CHECK (oauth_provider IN ('google','kakao')),
  oauth_id               VARCHAR(255) NOT NULL,
  telegram_chat_id       VARCHAR(50),
  telegram_link_token    VARCHAR(64),
  current_teacher_id     VARCHAR(36),                           -- FK: ai_teacher(id) ON DELETE SET NULL
  grade_level            VARCHAR(50),
  major                  VARCHAR(100),
  education              VARCHAR(50),
  learning_goal          TEXT,
  interests              JSONB,
  preferred_tone         VARCHAR(20),
  onboarding_completed   BOOLEAN      NOT NULL DEFAULT FALSE,
  notification_settings  JSONB        NOT NULL DEFAULT '{}'::jsonb,
  accessibility_settings JSONB        NOT NULL DEFAULT '{}'::jsonb,
  subscription_status    VARCHAR(20)  NOT NULL DEFAULT 'free'
                           CHECK (subscription_status IN ('free','pro','premium','expired')),
  -- [구독 캐시 규칙] v1.5: free/pro/premium/expired 4값
  -- subscription_status / subscription_started_at / subscription_expires_at 3필드는
  -- subscription 테이블을 원장으로 삼은 hot-path 캐시임. 쓰기 주체는 결제 webhook만.
  -- 1) 트랜잭션 경계: payment INSERT → subscription INSERT/UPDATE → user UPDATE를
  --    단일 ACID 트랜잭션으로 원자 처리.
  -- 2) 야간 reconciliation: 매일 자정 스케줄러가
  --    subscription.ends_at < NOW() AND user.subscription_status IN ('pro','premium') 인 행을 찾아
  --    user.subscription_status='expired'로 보정 (webhook 누락 대비).
  -- 3) Idempotency: 결제 webhook은 toss_payment_key UNIQUE 제약으로 중복 처리 방지,
  --    user UPDATE는 SELECT FOR UPDATE로 동시성 직렬화.
  -- free vs expired: 권한은 동일하나 UI·재구독 분기·마케팅 세그먼트에서 구분할 것
  subscription_started_at TIMESTAMPTZ,
  subscription_expires_at TIMESTAMPTZ,
  created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (current_teacher_id) REFERENCES ai_teacher(id) ON DELETE SET NULL
);

-- user 1:1 확장 프로필. 공개 바이오·SNS·공개 범위
CREATE TABLE user_profile (
  id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id     VARCHAR(36)  NOT NULL UNIQUE,
  bio         TEXT,
  social_links JSONB       NOT NULL DEFAULT '{}'::jsonb,
  visibility  VARCHAR(20)  NOT NULL DEFAULT 'public',
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- 알림·UX 세부 설정 분리 저장 (user 1:1)
CREATE TABLE user_settings (
  id                   VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id              VARCHAR(36) NOT NULL UNIQUE,
  push_enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
  email_enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
  telegram_enabled     BOOLEAN     NOT NULL DEFAULT FALSE,
  study_reminder_time  TIME,
  language             VARCHAR(10) NOT NULL DEFAULT 'ko',
  theme                VARCHAR(20) NOT NULL DEFAULT 'light',
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- 온보딩 선호도 확장 (user 1:1)
-- user.grade_level/interests/preferred_tone/learning_goal = 기본 정체성 필드 (user에 유지)
-- user_preferences.* = 학습 경험 커스터마이징용 확장 필드
CREATE TABLE user_preferences (
  id             VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id        VARCHAR(36) NOT NULL UNIQUE,
  grade_level    VARCHAR(20),
  interests      JSONB,
  preferred_tone VARCHAR(20),
  study_goals    JSONB,
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- JWT 갱신 토큰. access token 재발급 + 로그아웃/revocation 추적
-- 원본 토큰 저장 금지 — SHA-256 해시값만 보관
CREATE TABLE refresh_token (
  id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id     VARCHAR(36)  NOT NULL,
  token_hash  VARCHAR(255) NOT NULL UNIQUE,              -- SHA-256 해시 (원본 토큰 저장 금지)
  expires_at  TIMESTAMPTZ  NOT NULL,
  revoked_at  TIMESTAMPTZ,                               -- NULL이면 유효 토큰
  user_agent  VARCHAR(255),
  ip_address  INET,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_token_user_expires ON refresh_token(user_id, expires_at);

-- 다중 OAuth 계정 연동
-- user.oauth_provider + user.oauth_id = 주 소셜 계정 (인증 hot-path, user에 유지)
-- oauth_account = 추가 소셜 계정 다수 관리 (계정 연결/해제 기능)
CREATE TABLE oauth_account (
  id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id          VARCHAR(36) NOT NULL,
  provider         VARCHAR(20) NOT NULL CHECK (provider IN ('google','kakao')),
  provider_user_id VARCHAR(255) NOT NULL,
  email            VARCHAR(255),
  linked_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
  UNIQUE (provider, provider_user_id),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);
CREATE INDEX idx_oauth_account_user ON oauth_account(user_id);

-- AI 선생님 캐릭터(페르소나). 기본 3종 + 사용자 커스텀
CREATE TABLE ai_teacher (
  id               VARCHAR(30)  PRIMARY KEY,
  CONSTRAINT chk_ai_teacher_id CHECK (id ~ '^tch_[0-9A-Z]{26}$'),
  user_id          VARCHAR(30)  REFERENCES users(id) ON DELETE SET NULL, -- NULL이면 기본 캐릭터
  name             VARCHAR(100) NOT NULL,
  personality      TEXT,
  speech_style     TEXT,                                                  -- TTS 보이스 클론 스타일 텍스트 (S2)
  expertise        VARCHAR(255),                                          -- 쉼표 구분 문자열
  voice_profile_id VARCHAR(30),                                           -- FK S2에서 추가
  avatar_emoji     VARCHAR(10),
  is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
  is_preset        BOOLEAN      NOT NULL DEFAULT FALSE,
  persona_prompt   TEXT,
  tone_style       VARCHAR(20)  NOT NULL DEFAULT 'BALANCED',             -- STRICT / BALANCED / WARM
  question_style   VARCHAR(20)  NOT NULL DEFAULT 'HINT_THEN_ANSWER',     -- DIRECT_ANSWER / HINT_THEN_ANSWER / SOCRATIC
  explanation_depth VARCHAR(20) NOT NULL DEFAULT 'WITH_EXAMPLES',        -- ESSENTIAL / WITH_EXAMPLES / DEEP
  description      TEXT,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_tch_tone_style        CHECK (tone_style IN ('STRICT','BALANCED','WARM')),
  CONSTRAINT chk_tch_question_style    CHECK (question_style IN ('DIRECT_ANSWER','HINT_THEN_ANSWER','SOCRATIC')),
  CONSTRAINT chk_tch_explanation_depth CHECK (explanation_depth IN ('ESSENTIAL','WITH_EXAMPLES','DEEP'))
);

-- 음성 프로필. 프리셋/클로닝/디자인 3종 (Qwen3-TTS)
CREATE TABLE voice_profile (
  id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id     VARCHAR(36),
  name        VARCHAR(50),
  type        VARCHAR(20),
  s3_url      VARCHAR(500),
  sample_text TEXT,
  language    VARCHAR(10),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE SET NULL
);

-- TTS 생성 결과 캐시. 동일 텍스트+음성 프로필 조합은 한 번만 저장
CREATE TABLE voice_cache (
  text_hash        VARCHAR(64)  PRIMARY KEY,                    -- SHA-256 (텍스트 + voice_profile_id)
  voice_profile_id VARCHAR(36)  NOT NULL,
  s3_key           VARCHAR(500) NOT NULL,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (voice_profile_id) REFERENCES voice_profile(id) ON DELETE CASCADE
);

-- 구독 플랜 메타. 플랜 코드별 가격·기간·쿼터·기능 목록 (v1.5: free/pro/premium 3종)
CREATE TABLE subscription_plan (
  code                    VARCHAR(30)  PRIMARY KEY,
  price_krw               INTEGER      NOT NULL,
  period_days             INTEGER      NOT NULL,
  max_courses             INTEGER      NOT NULL,                -- free=1, pro=3, premium=8
  max_slides_per_course   INTEGER,                             -- NULL = 제한 없음 (pro/premium). free=롤링 언락(2개 활성)
  can_use_telegram        BOOLEAN      NOT NULL DEFAULT FALSE,  -- pro/premium=true
  can_customize_tutor     BOOLEAN      NOT NULL DEFAULT FALSE,  -- pro/premium=true
  features                JSONB        NOT NULL DEFAULT '[]'::jsonb,
  is_active               BOOLEAN      NOT NULL DEFAULT TRUE
);

-- [구독 원장 규칙]
-- 1 user : N rows. 과거 취소 + 현재 활성이 공존 가능.
-- 권한 판정은 user 캐시 우선, 감사/이력 조회만 이 테이블 사용.
-- status='active' 구독은 유저당 1개로 제한 (업그레이드 race를 행잠금으로 막음).
-- 구독 계약 이력/현재 상태. 결제 webhook이 INSERT/UPDATE하는 이벤트 소스
CREATE TABLE subscription (
  id          VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id     VARCHAR(36) NOT NULL,
  plan        VARCHAR(20),
  status      VARCHAR(20),
  started_at  TIMESTAMPTZ,
  ends_at     TIMESTAMPTZ,
  auto_renew  BOOLEAN,
  cancelled_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE RESTRICT
);

-- 월별/기간별 feature 사용량 카운터 (hot, 1:N). 플랜별 강의·슬라이드 쿼터가 여기에 반영됨 (v1.5: free=1, pro=3, premium=8)
CREATE TABLE user_usage (
  id           VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id      VARCHAR(36) NOT NULL,
  feature      VARCHAR(50) NOT NULL,
  period_start DATE        NOT NULL,
  period_end   DATE,
  used_count   INTEGER     NOT NULL DEFAULT 0,
  quota_limit  INTEGER,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, feature, period_start),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- 개별 결제 트랜잭션. 토스 paymentKey로 멱등 처리
CREATE TABLE payment (
  id               VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id          VARCHAR(36)  NOT NULL,
  subscription_id  VARCHAR(36),
  amount           INTEGER,
  toss_payment_key VARCHAR(255) UNIQUE,
  toss_order_id    VARCHAR(255),
  status           VARCHAR(20),
  paid_at          TIMESTAMPTZ,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id)         REFERENCES "user"(id)        ON DELETE RESTRICT,
  FOREIGN KEY (subscription_id) REFERENCES subscription(id)  ON DELETE SET NULL
);
CREATE UNIQUE INDEX uq_payment_toss_key ON payment(toss_payment_key);

-- AI 코인 잔액 지갑. 사용자당 1개 (1:1)
CREATE TABLE coin_wallet (
  id              VARCHAR(30) PRIMARY KEY,                    -- ULID with cwl_ prefix
  user_id         VARCHAR(30) NOT NULL UNIQUE,
  balance         INTEGER     NOT NULL DEFAULT 0,
  total_purchased INTEGER     NOT NULL DEFAULT 0,
  total_used      INTEGER     NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);
CREATE INDEX idx_cwl_user_id ON coin_wallet(user_id);

-- AI 코인 사용·충전 이력 (이벤트 소스)
-- use_course 제거 (v1.6 강의 잠금 정책 폐지). refund는 v1.7+ 정책 확정 시 활성화 예정.
CREATE TABLE coin_transaction (
  id            VARCHAR(30)  PRIMARY KEY,                    -- ULID with ctx_ prefix
  wallet_id     VARCHAR(30)  NOT NULL,
  user_id       VARCHAR(30)  NOT NULL,
  type          VARCHAR(20)  NOT NULL
                  CHECK (type IN ('purchase','use_slide','use_exam','refund')),
  amount        INTEGER      NOT NULL,                        -- 충전=양수, 사용=음수
  balance_after INTEGER      NOT NULL,
  ref_id        VARCHAR(30),                                  -- 연관 리소스 ID (slide_id / mock_exam_id, NULLABLE)
  payment_id    VARCHAR(30),                                  -- 코인 구매 결제 ID (NULLABLE)
  note          VARCHAR(200),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (wallet_id) REFERENCES coin_wallet(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id)   REFERENCES "user"(id)      ON DELETE CASCADE
);
CREATE INDEX idx_ctx_wallet_id ON coin_transaction(wallet_id);
CREATE INDEX idx_ctx_user_created ON coin_transaction(user_id, created_at DESC);
CREATE INDEX idx_ctx_type ON coin_transaction(type, created_at DESC);

-- 과외 단위. PDF 또는 주제로부터 생성된 강의 1세트
-- 강의 단위 잠금 없음 (course.lock_status 제거 — v1.6). 진입은 항상 허용. 슬라이드 단위 잠금만 존재.
CREATE TABLE course (
  id                 VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id            VARCHAR(36)  NOT NULL,
  ai_teacher_id      VARCHAR(36),
  title              VARCHAR(255),
  source_type        VARCHAR(20),
  source_pdf_url     VARCHAR(500),
  topic_text         TEXT,
  mode               VARCHAR(20),
  tone               VARCHAR(20),
  available_time_min INTEGER,
  total_chapters     INTEGER      NOT NULL DEFAULT 0,
  total_slides       INTEGER      NOT NULL DEFAULT 0,
  completed_slides   INTEGER      NOT NULL DEFAULT 0,
  progress_percent   INTEGER      NOT NULL DEFAULT 0,
  status             VARCHAR(20),
  exam_date          DATE,
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  completed_at       TIMESTAMPTZ, -- 완강 시각. status='completed' 전환 시 함께 기록
  FOREIGN KEY (user_id)       REFERENCES "user"(id)       ON DELETE CASCADE,
  FOREIGN KEY (ai_teacher_id) REFERENCES ai_teacher(id)   ON DELETE SET NULL
);
CREATE INDEX idx_course_user ON course(user_id);

-- 강의 챕터. course의 하위 단위
CREATE TABLE chapter (
  id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  course_id        VARCHAR(36) NOT NULL,
  title            VARCHAR(255),
  order_index      INTEGER,
  total_slides     INTEGER     NOT NULL DEFAULT 0,
  completed_slides INTEGER     NOT NULL DEFAULT 0,
  status           VARCHAR(20),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE
);

-- 슬라이드. 콘텐츠 JSON + 음성 OGG URL + Mermaid 다이어그램
CREATE TABLE slide (
  id                 VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  chapter_id         VARCHAR(36)  NOT NULL,
  order_index        INTEGER,
  title              VARCHAR(255),
  content_json       JSONB,
  mermaid_diagram    TEXT,
  script_text        TEXT,
  audio_url          VARCHAR(500),
  audio_duration_sec INTEGER,
  fact_check_passed  BOOLEAN,
  audio_duration_ms  INTEGER,
  cue_points         JSONB,
  lock_status        VARCHAR(10)  NOT NULL DEFAULT 'locked'
                       CHECK (lock_status IN ('active','locked')),
  -- free 플랜: 롤링 언락(항상 2개 active, 1개 완료 시 다음 1개 자동 해제)
  -- pro/premium: 전체 슬라이드 active
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (chapter_id) REFERENCES chapter(id) ON DELETE CASCADE
);
CREATE INDEX idx_slide_chapter_lock ON slide(chapter_id, lock_status);

-- RAG 검색용 텍스트 청크. Qdrant point_id로 벡터 DB와 연결
CREATE TABLE vector_chunk (
  id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  course_id       VARCHAR(36) NOT NULL,
  chunk_text      TEXT,
  source_page     INTEGER,
  qdrant_point_id UUID,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE
);

-- 개념 단위. 자기참조 트리로 지식 그래프를 구성함
CREATE TABLE concept (
  id                VARCHAR(36)    PRIMARY KEY DEFAULT gen_random_uuid()::text,
  course_id         VARCHAR(36)    NOT NULL,
  parent_concept_id VARCHAR(36),
  name              VARCHAR(200),
  description       TEXT,
  mastery_threshold DECIMAL(3,2)   NOT NULL DEFAULT 0.80,
  created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
  FOREIGN KEY (course_id)         REFERENCES course(id)   ON DELETE CASCADE,
  FOREIGN KEY (parent_concept_id) REFERENCES concept(id)  ON DELETE SET NULL
);
CREATE INDEX idx_concept_course  ON concept(course_id);
CREATE INDEX idx_concept_parent  ON concept(parent_concept_id);

-- 학습 행동 이벤트 로그. 음성-슬라이드 재생 중 발생하는 이벤트를 기록함
CREATE TABLE playback_event (
  id         VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    VARCHAR(36) NOT NULL,
  slide_id   VARCHAR(36) NOT NULL,
  event_type VARCHAR(20) NOT NULL
    CHECK (event_type IN ('play','pause','seek','complete')),
  offset_ms  INTEGER NOT NULL,
  payload    JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id)  REFERENCES "user"(id)  ON DELETE CASCADE,
  FOREIGN KEY (slide_id) REFERENCES slide(id)   ON DELETE CASCADE
);
CREATE INDEX idx_playback_event_user  ON playback_event(user_id, created_at DESC);
CREATE INDEX idx_playback_event_slide ON playback_event(slide_id, event_type);
-- payload 예시:
--   {"direction":"back"}   → 되감기 seek
--   {"direction":"forward"} → 앞으로 seek
--   {"volume":0.8}          → 볼륨 변경
--   {"speed":1.25}          → 배속 변경
--   {"qa_id":"..."}         → 질답 중 일시정지 컨텍스트
-- 되감기 집계 쿼리: WHERE event_type='seek' AND payload->>'direction'='back'

-- AI 선생님 질의응답 기록
CREATE TABLE qa (
  id         VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    VARCHAR(36) NOT NULL,
  course_id  VARCHAR(36),
  slide_id   VARCHAR(36) NULL,
  question   TEXT        NOT NULL,
  answer     TEXT,
  citations  JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id)  REFERENCES "user"(id)  ON DELETE CASCADE,
  FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE SET NULL,
  FOREIGN KEY (slide_id)  REFERENCES slide(id)  ON DELETE SET NULL
);
CREATE INDEX idx_qa_user_created ON qa(user_id, created_at DESC);
CREATE INDEX idx_qa_slide ON qa(slide_id) WHERE slide_id IS NOT NULL;
-- 슬라이드 단위 질문빈도 집계 지원. slide_id는 NULL 허용(과거 레코드 호환).

-- 슬라이드 단위 이해도 체크 테스트
CREATE TABLE comprehension_test (
  id       VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id  VARCHAR(36)  NOT NULL,
  slide_id VARCHAR(36)  NOT NULL,
  score    NUMERIC(5,2),
  taken_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id)  REFERENCES "user"(id)  ON DELETE CASCADE,
  FOREIGN KEY (slide_id) REFERENCES slide(id)   ON DELETE CASCADE
);


-- 커리큘럼 문서. PDF/주제에서 AI가 생성한 학습 계획서. /tutoring/{id}/curriculum 대상
CREATE TABLE curriculum (
  id           VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  course_id    VARCHAR(36)  NOT NULL,
  title        VARCHAR(255) NOT NULL,
  content      TEXT         NOT NULL,              -- Markdown 본문
  generated_by VARCHAR(100),                       -- 생성에 사용된 LLM 모델명 (NULLABLE)
  status       VARCHAR(20)  NOT NULL DEFAULT 'draft'
                 CHECK (status IN ('draft','confirmed','archived')),
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE
);
CREATE INDEX idx_curriculum_course_status ON curriculum(course_id, status);

-- 커리큘럼 차시 단위. curriculum 1건이 여러 차시로 구성됨. GET /tutoring/{id}/curriculum 응답에 포함
-- 실제 슬라이드 본문은 ChapterStudio_V1이 강의 확정 후 iframe_html 완성본으로 별도 저장
CREATE TABLE curriculum_unit (
  id            VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  curriculum_id VARCHAR(36)  NOT NULL,                -- 소속 커리큘럼
  order_index   INTEGER      NOT NULL,                -- 차시 순서 (1, 2, 3...)
  unit_name     VARCHAR(200) NOT NULL,                -- 차시명 (예: "1차시 - 이차방정식 기초")
  objective     TEXT,                                 -- 학습 목표 (NULLABLE)
  lesson_ref    VARCHAR(36),                          -- 연결된 lesson/chapter ID (NULLABLE, 생성 전 단계 허용)
  duration_min  INTEGER,                              -- 예상 학습 시간 (분, NULLABLE)
  status        VARCHAR(20)  NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending','ready','completed')),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  UNIQUE (curriculum_id, order_index),               -- 동일 커리큘럼 내 차시 순서 중복 불가
  FOREIGN KEY (curriculum_id) REFERENCES curriculum(id) ON DELETE CASCADE,
  FOREIGN KEY (lesson_ref)    REFERENCES chapter(id)    ON DELETE SET NULL
);
CREATE INDEX idx_curriculum_unit_curriculum ON curriculum_unit(curriculum_id, order_index);
CREATE INDEX idx_curriculum_unit_lesson     ON curriculum_unit(lesson_ref);

-- 과제 묶음
CREATE TABLE assignment_set (
  id          VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id     VARCHAR(36)  NOT NULL,
  title       VARCHAR(255),
  target_date DATE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- 과제 세트. 수업 완료 시 자동 생성, 텔레그램으로 발송
CREATE TABLE assignment (
  id                   VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  course_id            VARCHAR(36),
  chapter_id           VARCHAR(36),
  user_id              VARCHAR(36)  NOT NULL,
  assignment_set_id    VARCHAR(36),
  due_date             TIMESTAMPTZ  NOT NULL,  -- 마감 시각. AssignmentGrader가 웹훅 수신 시점에 참조
  expected_input_type  VARCHAR(32)  NOT NULL,  -- 기대 제출 유형. 값: code_python / text_essay / handwritten_solution / audio_oral / project_archive
  allowed_extensions   TEXT[]       NOT NULL,  -- 허용 확장자 목록. 예: '{".py"}' / '{".png",".jpg",".pdf"}'
  available_time_min   INTEGER,
  total_questions      INTEGER      NOT NULL DEFAULT 0,
  status               VARCHAR(20),
  total_score          INTEGER      NOT NULL DEFAULT 0,
  correct_count        INTEGER      NOT NULL DEFAULT 0,
  sent_to_telegram_at  TIMESTAMPTZ,
  submitted_at         TIMESTAMPTZ,
  graded_at            TIMESTAMPTZ,
  created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (course_id)         REFERENCES course(id)          ON DELETE SET NULL,
  FOREIGN KEY (chapter_id)        REFERENCES chapter(id)         ON DELETE SET NULL,
  FOREIGN KEY (user_id)           REFERENCES "user"(id)          ON DELETE CASCADE,
  FOREIGN KEY (assignment_set_id) REFERENCES assignment_set(id)  ON DELETE SET NULL,
  CHECK (expected_input_type IN ('code_python','text_essay','handwritten_solution','audio_oral','project_archive'))
);
CREATE INDEX idx_assignment_user       ON assignment(user_id);
CREATE INDEX idx_assignment_due_date   ON assignment(due_date);
CREATE INDEX idx_assignment_input_type ON assignment(expected_input_type);

-- 개별 문제. 객관식/주관식/서술형
CREATE TABLE question (
  id             VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  assignment_id  VARCHAR(36) NOT NULL,
  order_index    INTEGER,
  type           VARCHAR(20),
  content        TEXT,
  options        JSONB,
  correct_answer TEXT,
  explanation    TEXT,
  points         INTEGER     NOT NULL DEFAULT 0,
  topic          VARCHAR(100),
  FOREIGN KEY (assignment_id) REFERENCES assignment(id) ON DELETE CASCADE
);

-- 학생 제출물. 공식 제출(첫 제출) 기준. 재제출 이력은 submission_retry에 별도 저장
CREATE TABLE submission (
  id                      VARCHAR(36)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
  assignment_id           VARCHAR(36)   NOT NULL,
  user_id                 VARCHAR(36)   NOT NULL,
  status                  VARCHAR(32)   NOT NULL DEFAULT 'queued',
  -- status 값: queued, grading, done, failed
  -- 재제출 허용 구간: queued/grading
  -- 구 값(pending, queued_for_batch, ocr_running, rejected)은 텔레그램 즉시 채점 방식 도입으로 폐지
  official_file_url       VARCHAR(500),
  official_ocr_text       TEXT,
  official_score          SMALLINT,
  official_feedback       TEXT,
  official_ai_confidence  DECIMAL(4,3),
  ocr_failed_pages        SMALLINT      NOT NULL DEFAULT 0,
  total_pages             SMALLINT,
  source                  VARCHAR(20),
  submitted_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  original_zip_size       BIGINT,
  uncompressed_total_size BIGINT,
  file_count              INT,
  rejected_reason         VARCHAR(64),
  submission_source       VARCHAR(16)   NOT NULL DEFAULT 'web',
  validated_at            TIMESTAMP,
  FOREIGN KEY (assignment_id) REFERENCES assignment(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id)       REFERENCES "user"(id)     ON DELETE CASCADE
);
CREATE INDEX idx_submission_rejected_reason ON submission(rejected_reason);
CREATE INDEX idx_submission_source          ON submission(submission_source);
CREATE INDEX idx_submission_status          ON submission(status);

-- 재제출 이력. queued/grading 구간에서만 추가 가능. 피드백 참고용, 성적 미반영
CREATE TABLE submission_retry (
  retry_id                BIGINT        PRIMARY KEY AUTO_INCREMENT,
  submission_id           BIGINT        NOT NULL,
  retry_no                INT           NOT NULL,
  retry_file_url          VARCHAR(512)  NOT NULL,
  retry_ocr_text          TEXT,
  retry_score             INT,
  retry_feedback          TEXT,
  retry_ocr_failed_pages  VARCHAR(16),
  submitted_at            DATETIME      NOT NULL,
  graded_at               DATETIME,
  FOREIGN KEY (submission_id) REFERENCES submission(submission_id) ON DELETE CASCADE,
  UNIQUE KEY uk_submission_retry (submission_id, retry_no),
  INDEX idx_submission_retry_submission (submission_id)
);

-- 채점 완료 콜백 5회 재시도 실패 시 적재되는 DLQ. 운영자 수동 처리 대상
CREATE TABLE grading_callback_dlq (
  dlq_id        BIGINT        PRIMARY KEY AUTO_INCREMENT,
  submission_id BIGINT        NOT NULL,
  payload       JSON          NOT NULL,
  failure_count INT           NOT NULL DEFAULT 5,
  last_error    TEXT,
  enqueued_at   DATETIME      NOT NULL,
  resolved_at   DATETIME,
  resolved_by   VARCHAR(64),
  FOREIGN KEY (submission_id) REFERENCES submission(submission_id) ON DELETE CASCADE,
  INDEX idx_dlq_unresolved (resolved_at)
);

-- 풀이 단계 추적
CREATE TABLE step_trace (
  id            VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  submission_id VARCHAR(36) NOT NULL,
  step_index    INTEGER,
  input         TEXT,
  feedback      TEXT,
  FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE CASCADE
);

-- OCR 추출 결과
CREATE TABLE ocr_result (
  id            VARCHAR(36)   PRIMARY KEY DEFAULT gen_random_uuid()::text,
  submission_id VARCHAR(36)   NOT NULL,
  raw_text      TEXT,
  confidence    NUMERIC(5,4),
  engine        VARCHAR(50),
  processed_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE CASCADE
);

-- 문제별 채점 결과. Step Trace + 오류 4분류
CREATE TABLE grade (
  id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  submission_id   VARCHAR(36) NOT NULL,
  question_id     VARCHAR(36) NOT NULL,
  user_answer     TEXT,
  is_correct      BOOLEAN,
  partial_score   INTEGER     NOT NULL DEFAULT 0,
  error_type      VARCHAR(30),
  feedback_text   TEXT,
  step_trace_json JSONB,
  graded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE CASCADE,
  FOREIGN KEY (question_id)   REFERENCES question(id)   ON DELETE CASCADE
);

-- 학습 노트. AI 자동 생성 + 사용자 메모, 별표 가능
CREATE TABLE note (
  id         VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    VARCHAR(36) NOT NULL,
  course_id  VARCHAR(36),
  slide_id   VARCHAR(36),
  type       VARCHAR(20),
  content    TEXT,
  is_starred BOOLEAN,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id)  REFERENCES "user"(id)  ON DELETE CASCADE,
  FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE SET NULL,
  FOREIGN KEY (slide_id)  REFERENCES slide(id)  ON DELETE SET NULL
);

-- 암기노트. 슬라이드별 핵심 요약 노트, 텔레그램 발송 + 주제별 모음 웹페이지에서 열람
CREATE TABLE memo_note (
  id                  VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id             VARCHAR(36)  NOT NULL,
  course_id           VARCHAR(36),
  slide_id            VARCHAR(36)  NOT NULL,
  title               VARCHAR(255),
  content_md          TEXT,
  image_url           VARCHAR(500),
  sent_to_telegram_at TIMESTAMPTZ,
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id)   REFERENCES "user"(id)   ON DELETE CASCADE,
  FOREIGN KEY (course_id) REFERENCES course(id)   ON DELETE SET NULL,
  FOREIGN KEY (slide_id)  REFERENCES slide(id)    ON DELETE CASCADE
);
CREATE INDEX idx_mmo_slide_id ON memo_note(slide_id);

-- 슬라이드 북마크. 복습 과외 자동 생성 트리거
CREATE TABLE bookmark (
  id        VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id   VARCHAR(36) NOT NULL,
  course_id VARCHAR(36),
  slide_id  VARCHAR(36) NOT NULL,
  memo      TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id)  REFERENCES "user"(id)  ON DELETE CASCADE,
  FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE SET NULL,
  FOREIGN KEY (slide_id)  REFERENCES slide(id)  ON DELETE CASCADE
);

-- 약점 프로필. 토픽별 오류 패턴 장기 추적
CREATE TABLE weakness_profile (
  id            VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id       VARCHAR(36)  NOT NULL,
  topic         VARCHAR(255),
  subtopic      VARCHAR(255),
  error_pattern VARCHAR(50),
  frequency     INTEGER      NOT NULL DEFAULT 0,
  severity      INTEGER,
  first_seen_at TIMESTAMPTZ,
  last_seen_at  TIMESTAMPTZ,
  resolved_at   TIMESTAMPTZ,
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);
CREATE INDEX idx_weakness_user_topic ON weakness_profile(user_id, topic);

-- 약점 타임시리즈. /reports/weakness-trend API용
CREATE TABLE weakness_snapshot (
  id            VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id       VARCHAR(36)  NOT NULL,
  topic         VARCHAR(255) NOT NULL,
  mastery       NUMERIC(5,4) NOT NULL,
  snapshot_date DATE         NOT NULL,
  UNIQUE (user_id, topic, snapshot_date),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);
CREATE INDEX idx_weakness_snapshot_user_topic ON weakness_snapshot(user_id, topic, snapshot_date DESC);

-- Self-Healing Memory 3계층 (인덱스/토픽/원본)
CREATE TABLE memory (
  id               VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id          VARCHAR(36) NOT NULL,
  layer            INTEGER,
  topic            VARCHAR(255),
  content_json     JSONB,
  source_type      VARCHAR(50),
  updated_by_agent VARCHAR(50),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- 푸시/인앱 알림
CREATE TABLE notification (
  id         VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    VARCHAR(36)  NOT NULL,
  type       VARCHAR(50),
  title      VARCHAR(255),
  body       TEXT,
  payload    JSONB        NOT NULL DEFAULT '{}'::jsonb,
  read_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);
CREATE INDEX idx_notification_user_unread ON notification(user_id, read_at) WHERE read_at IS NULL;

-- 일정·리마인더 (사용자 직접 등록 포함)
CREATE TABLE calendar_event (
  id         VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    VARCHAR(36)  NOT NULL,
  type       VARCHAR(30),
  start_at   TIMESTAMPTZ,
  end_at     TIMESTAMPTZ,
  course_id  VARCHAR(36),
  metadata   JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id)  REFERENCES "user"(id)  ON DELETE CASCADE,
  FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE SET NULL
);
CREATE INDEX idx_calendar_event_user_range ON calendar_event(user_id, start_at);

-- 소크라테스식 대화 세션
CREATE TABLE socratic_session (
  id         VARCHAR(36)  PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    VARCHAR(36)  NOT NULL,
  topic      VARCHAR(255),
  transcript JSONB,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- 성능 최적화 추가 인덱스
CREATE INDEX idx_subscription_user       ON subscription(user_id, status);
CREATE INDEX idx_user_usage_period       ON user_usage(user_id, feature, period_start DESC);
CREATE INDEX idx_assignment_user_status  ON assignment(user_id, status);
```

---

## 설계 원칙

1. **Soft Delete 부분 적용** — `user`, `payment`, `submission`, `grade` 4개 테이블에 `deleted_at TIMESTAMPTZ DEFAULT NULL` 적용 완료. 나머지 테이블은 캡스톤 단계 단순화로 hard delete 유지.
2. **JSONB 활용** — 슬라이드 콘텐츠, Step Trace, 메모리 등 가변 구조는 JSONB로 저장
3. **타임스탬프** — 모든 테이블에 `created_at DEFAULT NOW() NOT NULL` 기본. 변경 가능 테이블은 `updated_at DEFAULT NOW() NOT NULL` 추가. 갱신 트리거는 애플리케이션 레이어 처리
4. **외부 ID 분리** — Toss(`toss_payment_key`), Telegram(`telegram_msg_id`), Qdrant(`qdrant_point_id`) 등 외부 시스템 ID는 별도 컬럼
5. **음성·이미지 파일** — DB에 직접 저장하지 않고 S3 URL만 보관 (CloudFront로 서빙)
6. **벡터 데이터** — Qdrant에 저장, PostgreSQL에는 메타데이터(`vector_chunk`)만 보관
7. **테이블 네이밍** — 전체 snake_case 통일. 예약어 `user`는 `"user"`로 큰따옴표 인용
8. **FK 정책** — 소유/계층 관계는 ON DELETE CASCADE, nullable 참조는 ON DELETE SET NULL, 결제 등 감사 필수 관계는 ON DELETE RESTRICT
9. **카운터 DEFAULT 0** — INTEGER 카운터 컬럼 전체에 `NOT NULL DEFAULT 0` 적용

---

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-04-14 | 텔레그램 비동기 과제 채점 계약 반영. `assignment` 테이블에 `due_date DATETIME NOT NULL` 컬럼 추가, `idx_assignment_due_date` 인덱스 추가. `submission` 테이블에 `status VARCHAR(32) NOT NULL DEFAULT 'queued'` 컬럼 추가(`idx_submission_status` 인덱스, 값: `queued`/`grading`/`done`/`failed`), 기존 `image_url`·`ocr_text` 계열을 `official_file_url`·`official_ocr_text`·`official_score`·`official_feedback`·`official_ai_confidence`·`ocr_failed_pages`·`total_pages`로 리네임/추가. `submission_retry` 테이블 신규. `grading_callback_dlq` 테이블 신규. 도메인 5 테이블 수 7 → 9, 전체 67 → 69. 구 `pending`(배치 대기) 상태 폐지 — `queued`(웹훅 수신 후 채점 대기)로 재정의 |
| 2026-04-14 | `weakness_profile` 갱신 트리거 소스 섹션 신설. 구 "Daily 채점 배치" 갱신 경로 폐기. 확정 경로: ②c 퀴즈 소크라테스(`QAResponder`, 즉시) + ⑥ 과제 채점(`AssignmentGrader`, 텔레그램 웹훅 즉시 채점 콜백 수신 후). UPSERT 로직 명시(`topic`+`subtopic`+`user_id` 기준 `frequency += 1` / `last_seen_at = NOW()`). |
| 2026-04-14 | **P0-v2 재교정**: submission DDL DEFAULT `'pending'` → `'queued'` 반영(앞서 변경이력 기록과 실제 DDL 불일치 수정). status enum 주석 4개 값으로 확정(`queued`/`grading`/`done`/`failed`), 구 값(`pending`, `queued_for_batch`, `ocr_running`, `rejected`) 폐지 명시. submission_retry DDL·본문 "queued/grading 구간"으로 통일. assignment.due_date 설명에서 배치 스케줄러 언급 제거. |
| 2026-04-14 | `submission` 테이블에 zip 업로드 검증 메타데이터 6컬럼 추가 (`original_zip_size`, `uncompressed_total_size`, `file_count`, `rejected_reason`, `submission_source`, `validated_at`). 인덱스 2개 추가 (`idx_submission_rejected_reason`, `idx_submission_source`) |
| 2026-04-14 | `curriculum_unit` 테이블 신설 (66→67) — 학습 콘텐츠 도메인 / `GET /tutoring/{id}/curriculum` endpoint #24 참조 대상 / FK: `curriculum_id → curriculum(id)` ON DELETE CASCADE, `lesson_ref → chapter(id)` ON DELETE SET NULL / UNIQUE `(curriculum_id, order_index)` / 인덱스 2개 / 도메인 4 테이블 수 12→13 반영 |
| 2026-04-14 | 4개 테이블 신설 (62→66) — `curriculum`(학습 콘텐츠 도메인), `refresh_token`·`oauth_account`·`user_preferences`(사용자 도메인) / `assignment` 설명 재기술(개별 과제 단위, `/assignments/{id}` 대상임을 명시) / 도메인 집계·인덱스 갱신 |
| 2026-04-13 | 카운트 41→42 정정 (산술 오류) |
| 2026-04-13 | Domain 4 language 필드 제거(한국어 전용 서비스), TelegramIntegration telegram_user_id → telegram_chat_id 변경 (파더봇 아웃바운드 전송 타깃) |
| 2026-04-13 | 모든 내부 ID 컬럼 BIGINT/BIGSERIAL → VARCHAR(36) UUID v4 전환 (telegram_chat_id 등 외부 API 식별자 예외) |
| 2026-04-13 | Slide 테이블에 audio_duration_ms/cue_points 2필드 추가(audio_url은 기존 컬럼 유지), PlaybackEvent 테이블 신설 (42→43 Tables) |
| 2026-04-13 | AccessibilitySettings 테이블 제거 (43→42 Tables, Domain 1 사용자 3개→2개) |
| 2026-04-17 | ULID + 3글자 프리픽스 도입, 70테이블 PK/FK VARCHAR(30) 전환, 모호 컬럼 20건 rename, FK 비표준 2건 정정, timestamps 전면 보정, soft delete 4테이블 추가, CHECK 제약 표준화, FK/복합 인덱스 명시, 대용량 테이블 파티셔닝 계획 명시 |
| 2026-04-17 | `telegram_account` 프리픽스 확정 — `tga_` 부여. 프리픽스 목록(70→71), 헤더 테이블 수(70→71) 갱신. PK 컬럼 타입 `VARCHAR(36)` → `VARCHAR(30)` + tga_ ULID CHECK 제약 적용. DDL 동기화. 주의: 프리픽스 미지정 발견 섹션 제거. |
| 2026-04-20 | 과제 형식 L2 검증 방식 확정 반영. `assignment` 테이블에 `expected_input_type VARCHAR(32) NOT NULL` + `allowed_extensions TEXT[] NOT NULL` 2컬럼 추가. CHECK 제약 5개 enum 값 명시. 인덱스 `idx_asg_input_type` 추가. `submission.rejected_reason` enum에 `type_mismatch` (L2 형식 불일치) 추가. DDL 동기화 완료. |
| 2026-04-20 | **요금제 v1.5 반영**. 플랜 명칭 변경: Plus→Pro, Pro→Premium. `user.subscription_status` CHECK `('free','plus','pro','expired')` → `('free','pro','premium','expired')`. `subscription.plan` CHECK `('free','plus','pro')` → `('free','pro','premium')`. `user_usage.quota_limit` 주석 `plus=3, pro=8` → `pro=3, premium=8`. `subscription_plan` 시드 코드 `plus`→`pro`, `pro`→`premium` (가격·기능 동일). `coin_wallet`·`coin_transaction` 테이블 신규 추가 (도메인 3 테이블 수 4→6, 전체 69→71). `slide.lock_status` 컬럼 추가 (active/locked, Free 롤링 언락 상태 관리). `course.lock_status` 컬럼 추가 (pending). 헤더 테이블 수 69→71. 프리픽스 목록에 `cwl_`·`ctx_` 추가. |
| 2026-04-20 | **요금제 v1.6 반영**. `course.lock_status` 컬럼 **제거** — 강의 단위 잠금 정책 폐지. 강의 진입은 항상 허용. 잠금은 `slide.lock_status`에서만 처리. `course` 테이블에 `completed_at` 완강 시각 주석 보강. `coin_transaction.type` CHECK에서 `use_course` 제거, `refund` 예약값 추가. `mock_exam` 테이블에 `is_free_trial BOOLEAN NOT NULL DEFAULT FALSE` 컬럼 추가 (Pro/Premium 완강 무료 1회 추적) + `idx_mex_user_course_free` 인덱스 추가. `mock_exam` DDL 동기화. 헤더 테이블 수 71→70. |
| 2026-04-27 | 인증 도메인(이슈 #3) 구현 확정 반영. `refresh_token` DB 테이블 제거 → Redis 관리(`RT:{hash}`, `USED_RT:{hash}`)로 교체. `oauth_account.provider` CHECK에서 `naver` 제거 (Google·Kakao만 지원). 테이블명 `user` → `users` 수정. 도메인 1 테이블 수 7 → 6. |
| 2026-04-14 | 도메인 4(학습 콘텐츠)에 `curriculum_unit` 신규 추가. 도메인 4 테이블 수 12 → 13, 전체 66 → 67. |
| 2026-04-14 | `playback_event` 스키마 개편(`cue_hit` 제거, `cue_anchor` 삭제, `payload JSONB` 추가). `qa` 테이블에 `slide_id` 컬럼 추가(질문빈도 기반 출제 로직 지원). |
| 2026-04-14 | 텔레그램 비동기 과제 채점 계약 반영. `assignment` 테이블에 `due_date` 컬럼·인덱스 추가. `submission` 테이블에 `status` 컬럼·인덱스 추가(값: `queued`/`grading`/`done`/`failed` — 배치 대기 상태 폐지, `pending(배치 대기)`을 `queued(웹훅 수신 후 채점 대기)`로 재정의), 기존 score/ocr_text 계열 컬럼을 `official_*`로 리네임. `submission_retry` 테이블 신규 추가. `grading_callback_dlq` 테이블 신규 추가. 도메인 5 테이블 수 7 → 9, 전체 67 → 69. |
| 2026-04-14 | `weakness_profile` 갱신 트리거 소스 섹션 신설. 구 Daily 채점 배치 경로 폐기. 확정 갱신 경로: ②c 소크라테스(`QAResponder`) + ⑥ 과제 채점(`AssignmentGrader`). |
| 2026-04-14 | **P0-v2 재교정**: `submission` DDL DEFAULT `'pending'` → `'queued'`로 수정. status enum 주석 `queued/grading/done/failed` 4개로 확정, 구 값(`pending`, `queued_for_batch`, `ocr_running`, `rejected`) 폐지 주석 추가. `submission_retry` DDL 주석 "pending/queued_for_batch 구간" → "queued/grading 구간"으로 수정. `submission_retry` 본문 설명 동일하게 수정. `assignment.due_date` 설명에서 배치 스케줄러 언급 제거. 하단 변경이력 테이블에 이번 재교정 추가. |
| 2026-04-20 | 암기노트 슬라이드 기반 재설계. `memo_note` 테이블에 `slide_id VARCHAR(36) NOT NULL` 컬럼·FK·인덱스(`idx_mmo_slide_id`) 추가. `review_schedule` 테이블 전체 삭제(에빙하우스 망각곡선 복습 시스템 제거). `schedule` 테이블 주석에서 에빙하우스 언급 제거. 도메인 4(학습 콘텐츠) 테이블 수 13 → 12, 전체 69 → 68. FK 테이블에 `memo_note.slide_id → slide.id` 추가, `review_schedule.user_id → user.id` 삭제. |
| 2026-04-20 | **소셜 로그인 전용 전환**. `user` 테이블에서 `password_hash` 컬럼 삭제 (자체 로그인 제거). `oauth_provider` CHECK 제약에서 `'naver'` 제거 → `('google','kakao')` 2개만 허용. `oauth_provider`, `oauth_id` 컬럼 NOT NULL로 변경 (소셜 로그인 필수화). 소셜 전용 전환 결정 내용은 `Docs/00_개요/기능명세서.md` § 5 및 `Docs/00_개요/페이지별_기능정리.md` PAGE 2에 통합됨. |
| 2026-04-20 | **AI 튜터 커스터마이징 신규 도입**. `tutor` 테이블 신규 추가 (도메인 2 AI 선생님 편입). 컬럼: `tutor_id`(PK, ULID tut_ prefix), `user_id`(FK, NULL이면 기본 제공), `name`(VARCHAR 20), `tagline`(VARCHAR 100), `avatar_style`(ENUM: owl/cat/fox/bear/cloud/sparkle), `background_color`(VARCHAR 7, hex), `tone_slider`(SMALLINT 0~100), `speed_slider`(SMALLINT 0~100), `depth_slider`(SMALLINT 0~100), `question_style_slider`(SMALLINT 0~100), `use_emoji`(BOOLEAN), `use_formal_speech`(BOOLEAN), `is_default`(BOOLEAN — 기본 제공 2종 삭제 방어), `created_at`, `updated_at`. 인덱스: `idx_tut_user_id ON tutor(user_id)`. 시드: 기본 튜터 2개(올빼미 교수·냥 튜터, `is_default=true`). 도메인 2 테이블 수 3 → 4, 전체 68 → 69. `memo_note.topic` 필드에 `idx_mmo_topic` 인덱스 추가 (주제 검색 지원, 해당 컬럼 존재 시 적용). |
| 2026-11-02 | 포트폴리오 정리: 도메인 구성 아래에 있던 변경 메모를 이 표로 옮기고, 키 열 기호를 PK/FK/UQ 글자로 바꿈. 문서 위치를 `03_데이터/ERD.md`로 옮김. |
