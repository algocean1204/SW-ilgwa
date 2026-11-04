# Agent_orchestrator: 비동기 AI Job 실행

Spring Boot 등 호출자가 요청한 AI 작업을 비동기 Job으로 실행하고 상태를 보관한다. Job 생성 → 백그라운드 실행 → 폴링 → 결과 조회 순으로 쓰며, 완료되면 Spring callback URL로 결과를 보내거나 Telegram으로 알림을 보낸다.

---

## 목적

- Spring이 동기 응답을 기다리지 않고 AI 작업을 제출하고 나중에 결과를 가져갈 수 있게 한다.
- 지원 모듈(`chapter_studio_v1`, `tts_v2`, `exam_forge_v1`, `ocr_v1`, `chat_v1`)을 한 엔드포인트로 받는다.
- 멱등성 키(`idempotency_key`)로 네트워크 재시도 때 같은 Job이 두 번 실행되지 않게 한다.
- Job 저장소는 기본 인메모리이고, 운영에서는 Redis로 바꿀 수 있다.

---

## 지원 모듈 목록

| `module` 값 | 지원 `action` | 호출 대상 |
|---|---|---|
| `chapter_studio_v1` | `curriculum_preview` | `ChapterStudio_V1` |
| `tts_v2` | `synthesize` | `TTS_V2` |
| `exam_forge_v1` | `generate` | `ExamForge_V1` |
| `ocr_v1` | `search` | `OCR_v1` (인제스트는 직접 `/api/ocr/v1/ingest` 사용) |
| `chat_v1` | `ask`(텍스트, Claude Sonnet), `voice`(음성, Gemini Flash Live) | `Chat_V1` |

---

## Job 생명주기

```
[POST /api/agent/jobs]
         ↓
    status: queued
         ↓
    status: running  (백그라운드/Celery)
         ↓
    status: done / error
         ↓
    callback_url POST (있을 때)
    Telegram 알림 (notify_telegram_chat_id 있을 때)
```

| 상태 | 설명 |
|---|---|
| `queued` | Job 생성 완료, 아직 실행 전 |
| `running` | AI 모듈 실행 중 |
| `done` | 정상 완료. `result` 필드에 결과 포함 |
| `error` | 실행 실패. `error_code` / `error_message` 필드에 원인 포함 |
| `cancelled` | 스키마에만 있다. 현재 취소 API는 없다 |

---

## IN/OUT 인터페이스

### POST `/api/agent/jobs`: Job 생성

#### Request (`application/json`)

```json
{
  "module": "tts_v2",
  "action": "synthesize",
  "payload": {
    "text": "강의 내용 텍스트",
    "voice_profile_id": "tutor_1",
    "speed": 1.0
  },
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
| `module` | str | 필수 | 위 지원 모듈 목록의 값 중 하나 |
| `action` | str | 필수 | 모듈별 지원 action |
| `payload` | dict | 필수 | 해당 모듈의 요청 파라미터 |
| `request_id` | str \| null | 선택 | Spring 측 요청 추적용 ID |
| `correlation_id` | str \| null | 선택 | 분산 추적용 correlation ID |
| `idempotency_key` | str \| null | 선택 | 동일 키가 있으면 기존 Job 반환 |
| `callback_url` | str \| null | 선택 | 완료 시 결과를 POST할 Spring 엔드포인트 |
| `notify_telegram_chat_id` | str \| null | 선택 | 완료 알림을 보낼 Telegram chat ID |
| `run_inline` | bool | 선택 | true이면 응답 전에 동기 실행. 기본 false |

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

### GET `/api/agent/jobs/{job_id}`: Job 상태 조회

- `200 OK`: `AgentJobRecord` 전체 (위 응답과 같은 구조)
- `404 Not Found`: `job_id`가 없을 때

---

### GET `/api/agent/jobs/{job_id}/result`: 완료된 결과 조회

`status == "done"` 일 때만 `result`를 반환한다.

- `200 OK`: 해당 모듈이 반환한 결과 dict 또는 list
- `404 Not Found`: `job_id`가 없을 때
- `409 Conflict`: 아직 완료되지 않았을 때

```json
{ "detail": "job이 완료되지 않았습니다: running" }
```

---

### GET `/api/agent/health`: 헬스체크

```json
{
  "status": "ok",
  "service": "agent-orchestrator",
  "job_store": "InMemoryJobStore",
  "executor": "background"
}
```

---

## 콜백 URL 패턴

`callback_url`이 있으면 Job이 끝날 때 FastAPI가 그 URL로 Job 레코드(`AgentJobRecord`) 전체를 POST한다. 헤더에는 `X-FastAPI-Job-Id`, `X-Internal-Token`(`APP_INTERNAL_TOKEN`), 있으면 `X-Correlation-Id`가 붙는다. Spring InternalTokenFilter는 토큰이 없거나 다르면 403으로 거절한다.

#### Callback Request Body (주요 필드)

```json
{
  "job_id": "job_4f3a2b1c...",
  "status": "done",
  "result": { "...": "..." },
  "error_code": null,
  "error_message": null
}
```

- 콜백은 한 번 보낼 때 최대 3회(`AGENT_CALLBACK_MAX_ATTEMPTS`) 시도한다. Celery beat를 쓰면 5분마다 `failed` 상태이고 시도 횟수가 3회 미만인 Job을 다시 보낸다.
- `callback_status` 값: `not_requested` / `pending` / `sent` / `failed`

---

## 폴링 권장 패턴 (Spring 측)

```
1. POST /api/agent/jobs → job_id 저장
2. GET /api/agent/jobs/{job_id} 를 1~5초 간격으로 폴링
3. status == "done" 확인 후 GET /api/agent/jobs/{job_id}/result 호출
   또는 callback_url을 통해 push 방식으로 수신
```

`run_inline=true`는 테스트·짧은 프리뷰 전용이다. 운영 AI 작업에는 사용하지 않는다.

---

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `AGENT_JOB_EXECUTOR` | `"background"` | `"background"` (FastAPI BackgroundTasks) 또는 `"celery"` |
| `AGENT_JOB_STORE` | `"memory"` | `"memory"` 또는 `"redis"` |
| `AGENT_JOB_REDIS_URL` | — | Redis URL. `AGENT_JOB_STORE=redis`일 때 필요. 없으면 `REDIS_URL`을 쓴다 |
| `AGENT_JOB_STORE_STRICT` | — | `"1"`이면 Redis URL이 없거나 연결에 실패할 때 예외 발생 (기본은 인메모리로 폴백) |
| `AGENT_CALLBACK_TIMEOUT_SEC` | `5` | 콜백 HTTP 타임아웃(초) |
| `AGENT_CALLBACK_MAX_ATTEMPTS` | `3` | 콜백 1회 전송 시 최대 시도 횟수 |
| `APP_INTERNAL_TOKEN` | `""` | 콜백 `X-Internal-Token` 헤더 값 |

---

## 주기 태스크 (Celery beat, `app/celery_app.py`)

| 태스크 | 주기 | 내용 |
|---|---|---|
| `agent_orchestrator.cleanup_stale_jobs` | 10분 | 30분 넘게 `running`인 Job을 `error`(`STALE_TIMEOUT`)로 바꾼다 |
| `agent_orchestrator.purge_old_completed_jobs` | 1시간 | 24시간 지난 `done`/`error` Job을 저장소에서 지운다 |
| `agent_orchestrator.retry_failed_callbacks` | 5분 | 콜백 실패 Job을 다시 보낸다 |

---

## 의존성

- `fastapi`: 라우터 및 BackgroundTasks
- `pydantic >= 2`: Job 스키마
- `redis`: Redis job store 사용 시 (선택)
- `celery`: Celery executor 사용 시 (선택)
- 각 AI 모듈 (`TTS_V2`, `ChapterStudio_V1`, `ExamForge_V1`, `OCR_v1`, `Chat_V1`)

---

## 에러 처리 정책

| 상황 | 처리 |
|---|---|
| 지원하지 않는 `module` | 어댑터에서 `ValueError` 발생 → `status: error`, `error_code: ValueError` |
| 지원하지 않는 `action` | 어댑터에서 `ValueError` 발생 → `status: error` |
| AI 모듈 내부 예외 | `status: error`, `error_message`에 원인 기록 |
| callback POST 실패 | 최대 3회 시도, 그래도 실패하면 `callback_status: failed` |
| Telegram 알림 실패 | 경고 로그만 남기고 넘어간다. Job 상태에는 영향 없음 |
| `idempotency_key` 중복 | 기존 Job 레코드를 그대로 반환. 새 Job을 만들지 않음 |
