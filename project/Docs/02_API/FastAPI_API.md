# FastAPI AI 모듈 API 명세서

> AI 1:1 과외 플랫폼 FastAPI AI 모듈 명세  
> 버전: 5.0.0 | 최종 갱신: 2026-06-07  
> 목적: Spring 팀 연동을 위한 FastAPI AI 모듈 최종 엔드포인트 정리  
>
> Spring Boot ↔ FastAPI 내부 호출 공통 인증 헤더: `X-Internal-Auth`  
> 전체 명세는 [`Spring_API.md`](./Spring_API.md) § 17에 있다.

---

## 목차

1. [공통 정보](#1-공통-정보)
2. [Agent Orchestrator — 비동기 Job 관리](#2-agent-orchestrator--비동기-job-관리)
3. [ChapterStudio_V1 — 과외 생성](#3-chapterstudio_v1--과외-생성)
4. [ExamForge_V1 — AI 모의고사](#4-examforge_v1--ai-모의고사)
5. [ASR_V1 — 음성 인식](#5-asr_v1--음성-인식)
6. [TTS_V2 — 음성 합성](#6-tts_v2--음성-합성)
7. [OCR_V1 — 광학 문자 인식](#7-ocr_v1--광학-문자-인식)
8. [Chat_V1 — 학습 챗봇](#8-chat_v1--학습-챗봇)

---

## 1. 공통 정보

### AI 모델 토글 (.env)

| 환경변수 | 허용값 | 설명 |
|---|---|---|
| `ACTIVE_TEXT_MODEL` | `gemini_flash` / `qwen27b_modal` 등 | 텍스트 생성 모델 전환. 강의·문제 생성 운영 구성은 Modal Qwen3.6-27B FP8이고 실패하면 Claude로 폴백한다. 실제 기본값은 `.env.example`을 따른다 |
| `AI_MODEL_TTS` | `gemini-tts` / `qwen3-tts-modal` | TTS 모델 전환. 운영 구성은 `qwen3-tts-modal`(Qwen3-TTS, Modal H100, 문장 단위 합성) |
| `AI_MODEL_ASR` | `whisper-modal` / `gemini-asr` / `qwen3-asr-modal` / `asr-fallback` | ASR 모델 전환. 운영 구성은 `whisper-modal`(Whisper V3 Turbo, Modal 앱 `asr-whisper-v3-turbo`). `gemini-asr`는 `ASR_GEMINI_FALLBACK=true`일 때만 폴백으로 쓴다 |
| `OCR_V1_EMBED_BACKEND` | `modal` / `local` | BGE-M3·reranker 실행 위치. 기본 `modal`(Modal 앱 `ocr-bge-m3`), `local`은 FastAPI 프로세스 안에서 돌리며 project/benchmarks 재현에 쓴다 |

> 2026-06-07: MLX 로컬 커넥터(`mlx-audio-qwen3-tts`, `mlx-whisper-turbo`)를 제거했다. 운영 구성은 Modal GPU(Qwen3.6-27B FP8, Qwen3-TTS, Whisper V3 Turbo, BGE-M3·reranker)를 먼저 쓰고, 커리큘럼 외 텍스트 호출이 실패하면 Claude로 넘어간다. TTS·ASR·임베딩은 Claude에 해당 API가 없어 Claude 폴백이 없다.

모델을 바꿀 때 호출부 코드는 변경하지 않는다. registry + `.env` 패턴으로 커넥터만 교체된다.

### 공통 에러 코드

| HTTP 상태 | 의미 |
|---|---|
| 400 | 요청 파라미터 유효성 오류 |
| 404 | 리소스 없음 (job_id 등) |
| 409 | 상태 충돌 (아직 완료되지 않은 job 결과 요청 등) |
| 422 | 비즈니스 유효성 오류 (스키마는 맞으나 값이 허용 범위 밖) |
| 429 | LLM 예산 초과 |
| 500 | 파이프라인 내부 실패 |
| 504 | 타임아웃 |

### UI 정책

라이트 모드 전용이다. 다크 모드는 지원하지 않는다.

---

## 2. Agent Orchestrator — 비동기 Job 관리

prefix: `/api/agent`

Spring이 AI 작업을 동기 응답 없이 제출하고, 나중에 결과를 폴링하거나 callback URL로 받는 비동기 Job 시스템이다. 지원 모듈: `chapter_studio_v1`, `tts_v2`, `exam_forge_v1`, `ocr_v1`, `chat_v1`.

### Job 생명주기

```
POST /api/agent/jobs → status: queued
                       → status: running
                       → status: done / error
                       → callback_url POST (있을 때)
                       → Telegram 알림 (있을 때)
```

---

### POST `/api/agent/jobs` — Job 생성

#### Request Body

```json
{
  "module": "tts_v2",
  "action": "synthesize",
  "payload": { "...모듈별 파라미터..." },
  "request_id": "spring-req-abc123",
  "correlation_id": "flow-xyz789",
  "idempotency_key": "unique-key-20260521",
  "callback_url": "https://spring.internal/ai/callback",
  "notify_telegram_chat_id": "123456789",
  "run_inline": false
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `module` | str | 필수 | `chapter_studio_v1` / `tts_v2` / `exam_forge_v1` / `ocr_v1` / `chat_v1` |
| `action` | str | 필수 | 모듈별 지원 action (아래 표 참고) |
| `payload` | dict | 필수 | 해당 모듈의 요청 파라미터 (각 모듈 섹션 참고) |
| `request_id` | str \| null | 선택 | Spring 요청 추적 ID |
| `correlation_id` | str \| null | 선택 | 분산 추적 ID |
| `idempotency_key` | str \| null | 선택 | 중복 제출 방지. 동일 키 있으면 기존 Job 반환 |
| `callback_url` | str \| null | 선택 | 완료 시 결과를 POST할 URL |
| `notify_telegram_chat_id` | str \| null | 선택 | 완료 알림 보낼 Telegram chat ID |
| `run_inline` | bool | 선택 | true이면 동기 실행 (테스트·프리뷰 전용). 기본 false |

#### 모듈별 지원 action

| module | action | payload 참조 | 설명 |
|---|---|---|---|
| `chapter_studio_v1` | `curriculum_preview` | § 3 ChapterStudio_V1 | — |
| `tts_v2` | `synthesize` | § 6 TTS_V2 Request 필드 | — |
| `exam_forge_v1` | `generate` | § 4 ExamForge_V1 Request 필드 | — |
| `ocr_v1` | `search` | § 7 OCR_V1 검색 요청 필드 | — |
| `chat_v1` | `ask` | § 8 Chat_V1 텍스트 채팅 Request 필드 | 텍스트 채팅 (Claude Sonnet 4.6) |
| `chat_v1` | `voice` | § 8 Chat_V1 음성 대화 Request 필드 | 음성 대화 (Gemini 2.0 Flash Live) |

### 모달리티 라우팅

`chat_v1` 모듈은 `action` 값에 따라 서로 다른 모델로 라우팅된다.

| action | 모델 | 입력 | 출력 |
|---|---|---|---|
| `ask` | Claude Sonnet 4.6 | 텍스트 질문 (`user_message`) | 텍스트 답변 (`answer`) |
| `voice` | Gemini 2.0 Flash Live | 음성 바이너리 (`audio_data`) | 음성 바이너리 (`audio_data`) |

#### Response (`202 Accepted`)

```json
{
  "job_id": "job_4f3a2b1c...",
  "request_id": "spring-req-abc123",
  "correlation_id": "flow-xyz789",
  "idempotency_key": "unique-key-20260521",
  "module": "tts_v2",
  "action": "synthesize",
  "status": "queued",
  "progress": 0.0,
  "current_stage": "queued",
  "payload": { "...": "..." },
  "result": null,
  "error_code": null,
  "error_message": null,
  "callback_url": "https://spring.internal/ai/callback",
  "callback_status": "pending",
  "callback_attempts": 0,
  "callback_error": null,
  "callback_last_at": null,
  "notify_telegram_chat_id": "123456789",
  "created_at": "2026-05-21T10:00:00Z",
  "updated_at": "2026-05-21T10:00:00Z"
}
```

---

### GET `/api/agent/jobs/{job_id}` — Job 상태 조회

Path Parameter: `job_id` — 생성 시 반환된 `job_id`

#### Response (`200 OK`)

위 `AgentJobRecord` 전체 구조

#### Response (`404 Not Found`)

```json
{ "detail": "job을 찾을 수 없습니다." }
```

---

### GET `/api/agent/jobs/{job_id}/result` — 완료 결과 조회

`status == "done"`일 때만 반환한다.

#### Response (`200 OK`)

해당 모듈이 반환한 결과 dict 또는 list

#### Response (`409 Conflict`)

```json
{ "detail": "job이 완료되지 않았습니다: running" }
```

---

### GET `/api/agent/health` — 헬스체크

#### Response (`200 OK`)

```json
{
  "status": "ok",
  "service": "agent-orchestrator",
  "job_store": "InMemoryJobStore",
  "executor": "background"
}
```

---

### Callback 규격 (Spring이 수신할 형식)

`callback_url`로 전송되는 POST 요청 Body:

```json
{
  "job_id": "job_4f3a2b1c...",
  "status": "done",
  "result": { "...모듈 결과..." },
  "error_code": null,
  "error_message": null
}
```

- 콜백 실패 시 최대 3회 재시도한다.
- `callback_status: failed`가 되면 추가 재시도 없음. `GET /api/agent/jobs/{job_id}/result`로 직접 조회한다.

---

### Spring 폴링 권장 패턴

```
1. POST /api/agent/jobs → job_id 저장
2. GET /api/agent/jobs/{job_id} 를 1~5초 간격으로 폴링
3. status == "done" 확인 후 result 사용
   또는 callback_url로 push 수신
```

---

## 3. ChapterStudio_V1 — 과외 생성

prefix: `/demo/chapter-studio`

### 사용 모델

| 역할 | 모델 |
|---|---|
| 주력 생성 | Qwen3.6-27B FP8 · vLLM (Modal B200, 슬라이드 8슬롯 병렬) |
| 플래너 | Claude Opus 4.6 (커리큘럼 설계, 1강당 1회) |
| 폴백 | Claude (Modal 실패 시). 토글로 Gemini Flash도 선택 가능 |

### 산출물 (5종)

슬라이드, 퀴즈, 핵심노트, 과제 씨드, 음성대본. 슬라이드는 템플릿 75종(JSON) 중 하나를 골라 빈칸을 채운다.

### 엔드포인트 목록

#### HTML 페이지 (GET)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/demo/chapter-studio` | 데모 메인 HTML |
| GET | `/demo/chapter-studio/chat` | 채팅 HTML |
| GET | `/demo/chapter-studio/curriculum` | 커리큘럼 HTML |
| GET | `/demo/chapter-studio/visual-compare` | 시각 비교 HTML |
| GET | `/demo/chapter-studio/reference-book` | 참고서 HTML |

#### 데이터 조회 (GET)

| 메서드 | 경로 | 응답 | 설명 |
|---|---|---|---|
| GET | `/demo/chapter-studio/visual-preview` | `VisualPreviewResponse` | 시각 프리뷰 |
| GET | `/demo/chapter-studio/contract` | — | 계약 정보 |
| GET | `/demo/chapter-studio/frontend-preview` | — | 프론트엔드 프리뷰 |

#### 액션 (POST)

| 메서드 | 경로 | 응답 | 설명 |
|---|---|---|---|
| POST | `/demo/chapter-studio/chat/context` | — | 채팅 컨텍스트 설정 |
| POST | `/demo/chapter-studio/curriculum/preview` | — | 커리큘럼 프리뷰 |
| POST | `/demo/chapter-studio/reference-book/context-preview` | `ReferenceBookContext` | 참고서 컨텍스트 |

---

## 4. ExamForge_V1 — AI 모의고사

prefix: `/api/exam-forge`

### 정책

- 완강 조건 없음. 모든 플랜에서 **3코인/회** 소모
- Free 포함 전체 플랜 사용 가능

### 파이프라인

10노드 LangGraph: `parse → plan → generate → distractors → answers → verify → validate → retry → calibrate → format`

- LLM 예산: 50 calls/run
- 타임아웃: 900초

### 지원 문제 유형 (20종)

- 한국어: 9종
- 영어: 8종
- 전문 자격: 3종

---

### POST `/api/exam-forge/generate` — 모의고사 생성

#### Request Body

```json
{
  "source_text": "string (100~50000자)",
  "subject": "string (1~200자)",
  "exam_config": {
    "total_questions": 50,
    "time_limit_minutes": 60,
    "locale": "ko",
    "category": "korean",
    "question_types": ["ko_multiple_choice_5"],
    "difficulty_distribution": {"1": 0.2, "2": 0.3, "3": 0.3, "4": 0.2},
    "passing_score": 60.0,
    "include_explanations": true
  }
}
```

| 필드 | 타입 | 범위/기본값 | 필수 | 설명 |
|---|---|---|---|---|
| `source_text` | str | 100~50000자 | 필수 | 출제 원문 |
| `subject` | str | 1~200자 | 필수 | 과목명 |
| `total_questions` | int | 5~100, 기본 50 | 선택 | 총 문항 수 |
| `time_limit_minutes` | int | 10~300, 기본 60 | 선택 | 시험 시간(분) |
| `locale` | str | 기본 `"ko"` | 선택 | 언어 설정 |
| `category` | str | 기본 `"korean"` | 선택 | 문제 카테고리 |
| `question_types` | list[str] | 기본 `["ko_multiple_choice_5"]` | 선택 | 문제 유형 목록 |
| `difficulty_distribution` | dict[int, float] | 합계 1.0 | 선택 | 난이도별 비율 |
| `passing_score` | float | 기본 60.0 | 선택 | 합격 점수 |
| `include_explanations` | bool | 기본 true | 선택 | 해설 포함 여부 |

#### Response

| 필드 | 타입 | 설명 |
|---|---|---|
| `exam_id` | str | 생성된 시험 ID |
| `exam_plan` | object | 출제 계획 |
| `questions` | list | 문항 목록 |
| `quality_metrics` | object | 품질 지표 (accuracy, dedup, coverage) |
| `pipeline_outcome` | object | 파이프라인 실행 결과 |
| `exam_html` | str | 시험지 HTML |
| `answers_html` | str | 정답/해설 HTML |

#### 에러

| 상태 | 조건 |
|---|---|
| 429 | LLM 예산 50 calls 초과 |
| 504 | 900초 타임아웃 |
| 500 | 파이프라인 내부 실패 |

---

### GET `/api/exam-forge/templates` — 템플릿 목록

Query Params: `locale` (str), `category` (str)

사용 가능한 문제 유형 템플릿 목록을 반환한다.

---

### GET `/api/exam-forge/health` — 헬스체크

모의고사 모듈 상태를 반환한다.

---

## 5. ASR_V1 — 음성 인식

### 사용 모델

| 역할 | 모델 |
|---|---|
| 운영 구성 | Whisper V3 Turbo (Modal GPU, 커넥터 `whisper-modal`, Modal 앱 `asr-whisper-v3-turbo`) |
| Gemini 폴백 | Gemini ASR (`gemini-asr`), `ASR_GEMINI_FALLBACK=true`일 때만 |
| 대체 경로 | Qwen3-ASR Modal |
| 보조 | asr-fallback |

`AI_MODEL_ASR` 환경변수로 전환한다.

---

### WebSocket `/ws/asr-v1` — 실시간 음성 인식

실시간 오디오 스트림을 수신해 인식 결과를 반환한다.

---

### POST `/api/asr-v1/offline` — 오프라인 배치 ASR

오디오 파일을 업로드해 배치 방식으로 인식 결과를 반환한다.

Request: `multipart/form-data` — 오디오 파일

Response: `ASRResponse`

---

## 6. TTS_V2 — 음성 합성

prefix: `/api/tts-v2`

### 사용 모델

| 환경 | 모델 |
|---|---|
| 운영 구성 | Qwen3-TTS Modal H100 (문장 단위 합성, 문장 사이 0.06초 무음, temperature 0.8) |
| 대체 경로 | Gemini TTS (`gemini-3.1-flash-tts-preview`) |

`AI_MODEL_TTS` 환경변수로 전환한다.

---

### POST `/api/tts-v2` — 텍스트 직접 입력 TTS

#### Request Body

```json
{
  "text": "합성할 텍스트",
  "voice_profile_id": "tutor_1",
  "ref_audio_base64": null,
  "ref_text": null,
  "language": "ko",
  "speed": 1.0,
  "skip_planner": false,
  "skip_postfx": false,
  "qc_engine": "whisperx"
}
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `text` | str | 필수 | — | 합성 텍스트 (1~50,000자) |
| `voice_profile_id` | str \| null | 선택 | null | 서버 등록 튜터 프로필 ID. `GET /api/tts-v2/voices`로 조회. 사용자 등록 프로필(`vpf_` 접두사)도 지원 |
| `ref_audio_base64` | str \| null | 선택 | null | base64 인코딩 레퍼런스 오디오. 지정 시 `ref_text`도 필수 |
| `ref_text` | str \| null | 조건부 | null | 레퍼런스 오디오 전사 텍스트 (최대 500자) |
| `language` | str | 선택 | `"ko"` | 합성 언어 코드 |
| `speed` | float | 선택 | `1.0` | 재생 속도 배율 (0.5~2.0) |
| `skip_planner` | bool | 선택 | `false` | true이면 LLM 낭독 계획 단계 생략 |
| `skip_postfx` | bool | 선택 | `false` | true이면 loudness·크로스페이드 후처리 생략 |
| `qc_engine` | str | 선택 | `"whisperx"` | `"whisperx"` / `"v1_asr"` / `"both"` |

#### Response (`200 OK`)

```json
{
  "audio_base64": "<WAV base64 문자열>",
  "total_duration_sec": 42.3,
  "sample_rate": 24000,
  "chunk_count": 12,
  "failed_chunk_count": 0,
  "failed_chunk_ids": [],
  "avg_cer": 0.04,
  "avg_wer": 0.05,
  "qc_reason_counts": {},
  "chunk_qc_reasons": [],
  "timings": {
    "loading": 12,
    "cleaning": 30,
    "chunking": 45,
    "planning": 1200,
    "synthesize": 8400,
    "postfx": 310,
    "qc": 4200,
    "merge": 80
  },
  "model": "gemini-3.1-flash-tts-preview",
  "qc_engine_used": "whisperx"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `audio_base64` | str | base64 인코딩 WAV 바이너리 |
| `total_duration_sec` | float | 전체 오디오 길이(초) |
| `sample_rate` | int | 샘플링 레이트 (기본 24,000 Hz) |
| `chunk_count` | int | 처리된 총 청크 수 |
| `failed_chunk_count` | int | QC 재시도 소진 실패 청크 수 |
| `failed_chunk_ids` | list[str] | 실패 청크 ID 목록 |
| `avg_cer` | float \| null | 평균 문자 오류율 |
| `avg_wer` | float \| null | 평균 단어 오류율 |
| `qc_reason_counts` | dict[str, int] | QC 실패 사유별 청크 수 |
| `chunk_qc_reasons` | list[dict] | 청크별 QC 실패 사유 (`chunk_id`, `qc_reason`) |
| `timings` | dict[str, float] | 단계별 소요 시간 (ms) |
| `model` | str | 사용된 TTS 모델명 |
| `qc_engine_used` | str | 실제 사용된 QC 엔진 |

#### 에러

| 상태 | 조건 |
|---|---|
| 400 | 알 수 없는 `voice_profile_id` / `ref_audio_base64`만 있고 `ref_text` 누락 / base64 디코딩 실패 |
| 500 | 파이프라인 오디오 생성 실패 |

`failed_chunk_count > 0`이어도 200을 반환한다. 실패 청크를 포함한 채로 병합된 오디오를 제공하며, `failed_chunk_ids`에 목록을 기재한다.

---

### POST `/api/tts-v2/file` — 파일 업로드 TTS

#### Request (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `text_file` | file | 필수 | .txt 또는 .md 파일 |
| `ref_audio` | file | 선택 | 레퍼런스 오디오 파일. 없으면 기본 튜터 프로필 사용 |
| `ref_text` | str | 조건부 | `ref_audio` 지정 시 필수 |
| `voice_profile_id` | str | 선택 | 튜터 프로필 ID |
| `language` | str | 선택 | 기본 `"ko"` |
| `speed` | float | 선택 | 기본 `1.0` (0.5~2.0) |
| `skip_planner` | bool | 선택 | 기본 `false` |
| `skip_postfx` | bool | 선택 | 기본 `false` |
| `qc_engine` | str | 선택 | 기본 `"whisperx"` |

Response: 텍스트 직접 입력과 동일한 구조

---

### GET `/api/tts-v2/voices` — 음성 프로필 목록

#### Response (`200 OK`)

```json
{
  "profiles": [
    {
      "profile_id": "tutor_1",
      "display_name": "튜터 1",
      "tone": "차분하고 명료한 설명 톤",
      "language": "ko",
      "duration_sec": 8.5,
      "sample_rate": 22050
    }
  ]
}
```

---

### Voice Profile CRUD — `/api/tts-v2/voice-profiles`

사용자가 직접 등록한 음성 프로필을 관리한다. Qwen3-TTS ICL 모드 기반 음성 클로닝에 사용한다.

#### POST `/api/tts-v2/voice-profiles` — 사용자 음성 프로필 등록

##### Request Body

```json
{
  "user_id": "string (1~50자)",
  "name": "string (1~100자)",
  "ref_audio_base64": "string (base64 인코딩 오디오)",
  "ref_text": "string (레퍼런스 오디오 전사 텍스트)",
  "language": "string (기본 ko)"
}
```

###### Response (`200 OK`)

`VoiceProfileSummary`

```json
{
  "profile_id": "vpf_abc123...",
  "name": "내 목소리",
  "language": "ko",
  "sample_rate": 24000,
  "duration_sec": 8.5,
  "source": "user"
}
```

에러: 400 (base64 디코딩 실패 또는 빈 오디오), 503 (DB 연결 실패)

---

#### GET `/api/tts-v2/voice-profiles` — 사용자 음성 프로필 목록 조회

Query Params: `user_id` (필수, 1~50자)

##### Response (`200 OK`)

```json
{
  "profiles": [VoiceProfileSummary, ...]
}
```

---

#### GET `/api/tts-v2/voice-profiles/{profile_id}` — 음성 프로필 단건 조회

Response: `VoiceProfileSummary` (위와 동일 구조)

에러: 404 (프로필 없음)

---

#### DELETE `/api/tts-v2/voice-profiles/{profile_id}` — 음성 프로필 삭제

Query Params: `user_id` (필수, 1~50자)

##### Response (`200 OK`)

```json
{
  "deleted": true,
  "profile_id": "vpf_abc123..."
}
```

에러: 404 (프로필 없음 또는 삭제 권한 없음)

---

## 7. OCR_V1 — 광학 문자 인식

prefix: `/api/ocr/v1`

교재를 포함한 모든 PDF를 처리하는 단일 OCR 경로다. 기존 ①b PaddleOCR-VL-1.5 Modal A10 파이프라인을 대체했다.

### 사용 모델

| 단계 | 모델 |
|---|---|
| 1차 추출 | Marker (marker-pdf) |
| 폴백 추출 | MinerU (magic-pdf), 품질 게이트에 걸린 페이지만 |
| 교정 | Kanana-2 (Modal B200), MinerU로 다시 뽑은 페이지만. 실패하면 Claude, 그것도 실패하면 MinerU 텍스트 유지 |
| 임베딩 | BGE-M3 dense(1024d) + sparse (Modal 앱 `ocr-bge-m3`, `OCR_V1_EMBED_BACKEND=modal` 기본) |
| 리랭커 | BGE-Reranker-v2-m3 (같은 Modal 앱) |

---

### POST `/api/ocr/v1/ingest` — PDF 인제스트

PDF를 업로드해 OCR 처리 후 텍스트를 추출한다.

#### Request (`multipart/form-data`)

PDF 파일

내부 산출물: `ocr_json: [{page, bbox, text, conf}]` + `plain_text`

품질 게이트(4-메트릭, overall < 0.70)에 걸린 페이지는 MinerU로 다시 추출하고 Kanana-2로 교정한다.

#### Response (`200 OK`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `plain_text` | str | 추출된 전체 텍스트 |
| `page_count` | int | 처리된 페이지 수 |
| `collection_name` | str | Qdrant 저장 컬렉션명 (`ocr_v1_chunks`) |
| `avg_confidence` | float | 평균 신뢰도 |

---

### POST `/api/ocr/v1/search` — RAG 하이브리드 검색

#### Request Body

```json
{
  "query": "뉴턴의 운동 법칙",
  "collection_name": "doc_abc123",
  "top_k": 5
}
```

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `query` | str | 필수 | — | 검색 쿼리 |
| `collection_name` | str | 필수 | — | 인제스트 시 반환된 컬렉션명 |
| `top_k` | int | 선택 | `5` | 반환할 최대 결과 수 |

##### Response (`200 OK`)

```json
{
  "query": "뉴턴의 운동 법칙",
  "hits": [
    { "text": "...", "score": 0.92, "page": 3 }
  ],
  "total_found": 3
}
```

검색 파이프라인: Dense+Sparse 병렬 검색 → RRF(k=60) 통합 → BGE-Reranker-v2-m3 재정렬

Agent Orchestrator로 호출할 때는 `action: "search"`, payload에 위 필드를 넣는다. `ingest`는 직접 `/api/ocr/v1/ingest`를 사용한다 (multipart 제약).

---

### GET `/api/ocr/v1/health` — 헬스체크

OCR 모듈 상태를 반환한다.

---

## 8. Chat_V1 — 학습 챗봇

prefix: `/api/chat/v1`

강의 콘텐츠(슬라이드·음성대본·퀴즈)를 컨텍스트로 제공하면 학생 질문에 맞는 답변을 반환한다. 텍스트 채팅과 음성 대화 두 가지 모드를 지원한다.

---

### POST `/api/chat/v1/ask` — 학습 질문 답변 (텍스트 채팅)

Spring이 학생의 질문과 강의 컨텍스트를 함께 전송하면, 챗봇이 답변을 생성한다. 운영 구성은 Claude Sonnet 4.6이다. 답변을 JSON으로 한 번에 돌려준다.

#### Request Body (`ChatRequest`)

```json
{
  "session_id": "session-abc123",
  "user_message": "뉴턴의 제2법칙을 쉽게 설명해줄 수 있어?",
  "lecture_context": {
    "chapter_title": "뉴턴의 운동 법칙",
    "slides": [
      {
        "slide_idx": 0,
        "title": "제1법칙 — 관성의 법칙",
        "content": "외부 힘이 없으면 물체는 현재 상태를 유지한다."
      },
      {
        "slide_idx": 1,
        "title": "제2법칙 — 가속도의 법칙",
        "content": "F = ma. 힘은 질량 × 가속도와 같다."
      }
    ],
    "voice_scripts": ["슬라이드 1 음성대본...", "슬라이드 2 음성대본..."],
    "quiz_items": ["뉴턴 제2법칙의 공식은?"]
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `session_id` | str | 필수 | 채팅 세션 식별자 (Spring이 생성해서 전달) |
| `user_message` | str | 필수 | 학생 질문 텍스트 |
| `lecture_context` | object | 필수 | 강의 콘텐츠 컨텍스트 |
| `lecture_context.chapter_title` | str | 필수 | 챕터 제목 |
| `lecture_context.slides` | list | 필수 | 슬라이드 목록 (최소 1개) |
| `lecture_context.slides[].slide_idx` | int | 필수 | 슬라이드 인덱스 (0-based) |
| `lecture_context.slides[].title` | str | 필수 | 슬라이드 제목 |
| `lecture_context.slides[].content` | str | 필수 | 슬라이드 핵심 내용 |
| `lecture_context.voice_scripts` | list[str] \| null | 선택 | 슬라이드별 음성대본 |
| `lecture_context.quiz_items` | list[str] \| null | 선택 | 퀴즈 항목 목록 |

#### Response (`200 OK`, `ChatResponse`)

```json
{
  "session_id": "session-abc123",
  "answer": "뉴턴의 제2법칙은 F = ma로 표현됩니다...",
  "referenced_slides": [1]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `session_id` | str | 요청의 `session_id`를 그대로 반환 |
| `answer` | str | 챗봇이 생성한 답변 텍스트 |
| `referenced_slides` | list[int] | 답변 생성에 참조한 슬라이드 `slide_idx` 목록 |

#### 에러

| 상태 | 조건 |
|---|---|
| 422 | 비즈니스 유효성 오류 (슬라이드 목록 비어있음 등) |
| 500 | LLM 호출 실패 또는 내부 오류 |

---

### POST `/api/chat/v1/ask/stream`: 학습 질문 답변 (SSE 스트리밍)

요청 본문은 `/ask`와 같은 `ChatRequest`다. 답변은 `text/event-stream`(SSE)으로 토큰 단위로 흘려보낸다. 모델은 Claude Sonnet 4.6이다. AI 파이프라인에서 SSE를 쓰는 곳은 이 엔드포인트 하나다. 이벤트 형식은 Chat_V1 모듈 README를 따른다.

---

### POST `/api/chat/v1/voice` — 음성 대화

학생의 음성 입력을 받아 음성으로 답변을 반환한다. 내부적으로 Gemini 2.0 Flash Live를 사용한다.

#### Request Body (`VoiceChatRequest`)

```json
{
  "session_id": "sess-abc-123",
  "audio_data": "<base64 인코딩된 음성>",
  "audio_format": "webm",
  "sample_rate": 16000,
  "lecture_context": {
    "chapter_title": "Python 데코레이터",
    "slides": [
      { "slide_idx": 0, "title": "개요", "content": "데코레이터는 함수를 감싸는 도구이다" }
    ]
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `session_id` | str | 필수 | 채팅 세션 식별자 |
| `audio_data` | str | 필수 | base64 인코딩된 입력 음성 바이너리 |
| `audio_format` | str | 필수 | 입력 오디오 포맷 (예: `"webm"`, `"wav"`, `"ogg"`) |
| `sample_rate` | int | 필수 | 입력 오디오 샘플링 레이트 (Hz) |
| `lecture_context` | object | 필수 | 강의 콘텐츠 컨텍스트 (`/ask`와 동일 구조) |

#### Response (`200 OK`, `VoiceChatResponse`)

```json
{
  "session_id": "sess-abc-123",
  "audio_data": "<base64 인코딩된 음성 응답>",
  "audio_format": "wav",
  "transcript_input": "데코레이터가 뭐예요?",
  "transcript_output": "데코레이터는 함수를 받아 새 함수를 돌려주는 구조예요...",
  "referenced_slides": [0]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `session_id` | str | 요청의 `session_id`를 그대로 반환 |
| `audio_data` | str | base64 인코딩된 응답 음성 (WAV) |
| `audio_format` | str | 응답 오디오 포맷 (`"wav"` 고정) |
| `transcript_input` | str | 입력 음성의 전사 텍스트 |
| `transcript_output` | str | 챗봇 응답의 전사 텍스트 |
| `referenced_slides` | list[int] | 응답 생성에 참조한 슬라이드 `slide_idx` 목록 |

#### 에러

| 상태 | 조건 |
|---|---|
| 422 | 비즈니스 유효성 오류 (슬라이드 목록 비어있음 등) |
| 500 | LLM API 호출 실패 또는 내부 오류 |

---

### POST `/api/chat/v1/health` — 헬스체크

#### Response (`200 OK`)

```json
{ "status": "ok", "service": "chat-v1" }
```

---

### Agent Orchestrator를 통한 호출

비동기가 필요한 경우 Agent Orchestrator를 경유한다.

#### 텍스트 채팅 (`action: "ask"`)

```json
{
  "module": "chat_v1",
  "action": "ask",
  "payload": {
    "session_id": "session-abc123",
    "user_message": "뉴턴의 제2법칙을 설명해줘",
    "lecture_context": {
      "chapter_title": "뉴턴의 운동 법칙",
      "slides": [
        { "slide_idx": 0, "title": "제1법칙", "content": "관성의 법칙" }
      ],
      "voice_scripts": null,
      "quiz_items": null
    }
  },
  "callback_url": "https://spring.internal/chat/callback"
}
```

#### 음성 대화 (`action: "voice"`)

```json
{
  "module": "chat_v1",
  "action": "voice",
  "payload": {
    "session_id": "sess-abc-123",
    "audio_data": "<base64 인코딩된 음성>",
    "audio_format": "webm",
    "sample_rate": 16000,
    "lecture_context": {
      "chapter_title": "Python 데코레이터",
      "slides": [
        { "slide_idx": 0, "title": "개요", "content": "데코레이터는 함수를 감싸는 도구이다" }
      ]
    }
  },
  "callback_url": "https://spring.internal/chat/voice-callback"
}
```

Job 완료 후 `result` 필드에 각각 `ChatResponse` / `VoiceChatResponse` 구조가 담긴다.

---

## 변경 이력

| 버전 | 날짜 | 변경 내용 |
|---|---|---|
| — | 2026-11-02 | 최종 구성 반영: ASR `whisper-modal`(Gemini ASR은 `ASR_GEMINI_FALLBACK=true`일 때만), TTS Modal H100, BGE-M3·reranker Modal 앱 `ocr-bge-m3`, Kanana-2 교정 폴백, Chat_V1 `/ask/stream`(SSE) 추가. `.env.example` 기본값 표기는 뺐다. |
| — | 2026-11-02 | 포트폴리오 정리: 파일명에서 `_최종`을 빼고 `02_API/FastAPI_API.md`로 옮김. 변경 이력을 문서 끝으로 이동. |
| v5.0 | 2026-06-07 | **AI 모델 Gemini 단일 경로 통합**. MLX 로컬 커넥터(`mlx-audio-qwen3-tts`, `mlx-whisper-turbo`) 및 CLI 기반 로컬 LLM 커넥터 완전 제거. 텍스트·TTS·ASR 모두 Gemini API 경유로 단일화. OCR_V1을 공식 단일 OCR 경로로 확정(①b PaddleOCR-VL 완전 폐기). API 명세 파일 v3.0·v4.0 삭제, v4.1(내용 v4.3) 파일명 혼란 해소 — 본 파일로 통합. |
| v4.3 | 2026-05-21 | TTS_V2 Voice Profile CRUD 추가. ①b 참고도서 OCR → OCR_V1 대체 반영. |
| v4.1 | 2026-05-21 | Chat_V1 음성 엔드포인트 추가 (`POST /api/chat/v1/voice`). 모달리티 라우팅 규칙 명세화. |
| v4.0 | 2026-05-21 | Chat_V1 모듈 추가. Agent Orchestrator `chat_v1` 모듈 dispatch 추가. TTS_V2 요청/응답 필드 전체 명세화. |
| v3.0 | 2026-05-20 | 초안 작성. |
