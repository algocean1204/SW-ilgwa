# ChapterStudio_V1

FastAPI 기반 AI 챕터 생성 모듈이다.

## 모듈 목적

챕터 하나에 들어갈 슬라이드, 퀴즈, 노트, 과제, 음성 대본을 한 모듈에서 만든다.

## IN·OUT

IN은 `ChapterRequest`와 HTTP 요청이며 OUT은 `ChapterResponse`, DB 행 묶음, `/healthz` 응답이다.

## 환경변수

| 변수명 | 필수 | 기본값 | 설명 |
|--------|------|--------|------|
| `DATABASE_URL` | 필수 | — | PostgreSQL 연결 문자열 |
| `DATABASE_SCHEMA` | 선택 | `chapter_studio` | DB 스키마명 |
| `LOG_LEVEL` | 선택 | `INFO` | 로그 출력 레벨 |
| `ACTIVE_TEXT_MODEL` | 선택 | `qwen27b_sonnet_fallback` | 강의 생성 커넥터. Modal Qwen3.6-27B 1순위, 실패 시 Claude Sonnet |
| `ACTIVE_PLANNER_MODEL` | 선택 | `opus46` | 커리큘럼 계획 커넥터(Claude Opus 4.6) |
| `ACTIVE_TTS_MODEL` | 선택 | `qwen3_tts_modal` | 튜터 프로필 없이 호출할 때의 TTS 커넥터(Modal Qwen3-TTS) |
| `TTS_CLOUD_FALLBACK_ENABLED` | 선택 | `false` | `true`면 튜터 TTS 체인에서 Modal 뒤에 Gemini → OpenAI TTS를 붙인다 |
| `ACTIVE_VERIFIER_MODEL` | 선택 | `claude_sonnet` | 내용 정확성 검증 커넥터(Claude Sonnet 4.6) |
| `CHAPTERSTUDIO_LESSON_SELF_REPAIR` | 선택 | `true` | 강의 생성 후 분량·형식 self-check + targeted-repair 활성화. `false`로 끄면 수정 없이 다음 단계로 넘어간다 |
| `CHAPTERSTUDIO_CONTENT_VERIFY` | 선택 | `true` | 내용 정확성(사실·논리 오류) 검증 패스 활성화. `false`로 끄면 생성된 강의를 그대로 후처리로 넘긴다 |
| `ANTHROPIC_API_KEY` | 조건부 | — | Claude 커넥터 사용 시 필요 |
| `MODAL_TOKEN_ID` | 조건부 | — | Modal GPU 배포 시 필요 |
| `MODAL_TOKEN_SECRET` | 조건부 | — | Modal GPU 배포 시 필요 |
| `TTS_ENDPOINT` | 조건부 | — | TTS 서비스 엔드포인트 |

## 의존성

`app`, `pipeline`, `schemas`, `db`, `common`, `infra/schema/migrations/V2__chapter_studio.sql`에 의존한다.

공통 유틸 `common/llm_output.py`의 `strip_thinking`·`extract_json_block`·`loads_lenient`를 파이프라인 전반에서 사용한다. 모델이 Qwen·Claude·Gemini 중 무엇이든 `<think>` 블록과 마크다운 펜스를 같은 방식으로 걷어낸다. 자세한 내용은 `common/llm_output.py` 참조.

## 파이프라인 구조

```
prepare_context
      │
      ▼
generate_lesson  ← ACTIVE_TEXT_MODEL 호출 + 분량·형식 self-check/repair
      │            (CHAPTERSTUDIO_LESSON_SELF_REPAIR=false면 repair 생략)
      │
      ▼
content_verify   ← 사실·논리 오류 검증(1회) → 교정(1회) → 재검증(1회)
      │            (CHAPTERSTUDIO_CONTENT_VERIFY=false면 이 단계 생략)
      │            검증/교정 커넥터: ACTIVE_VERIFIER_MODEL (기본 claude_sonnet)
      │
      ▼
postprocess_slides  ← DB records(slides·quiz_set·voice_scripts 등) emit
      │
      ▼
     END
```

### 각 단계 설명

- `prepare_context`: 챕터 요청·커리큘럼 정보를 파이프라인 상태로 변환한다. 커리큘럼은 이 파이프라인 밖에서 Claude Opus 4.6으로 먼저 만들어 확정한 상태로 들어온다
- `generate_lesson`: `ACTIVE_TEXT_MODEL` 커넥터로 강의 JSON을 생성한다. batch를 지원하는 커넥터(Modal Qwen)면 슬라이드 플랜이 확정된 뒤 슬라이드를 장마다 독립 요청으로 만들어 `generate_batch`로 보내고, vLLM이 최대 8슬롯에 묶어 병렬 생성한다. 퀴즈·노트·과제는 같은 시점에 따로 만들고, 음성대본은 생성된 슬라이드를 보고 장마다 만든다. 생성 직후 `pipeline/quality.py`로 분량·형식을 self-check하고, 미달 항목이 있으면 `pipeline/repair.py`의 targeted-repair로 1회 보강한다. 결과는 `lesson_payload`에 stash해 다음 노드로 넘긴다
- `content_verify`: `lesson_payload`의 사실·논리 오류를 LLM 1회 검증한다. 명백한 오류가 검출되면 1회 교정하고, 교정 후 1회 재검증한다. 인덱스 계약(slide_idx 집합)이 깨지면 원본으로 되돌린다. 검증 커넥터는 `ACTIVE_VERIFIER_MODEL`(기본 `claude_sonnet`)이다. 검증·교정 단계가 실패하면 원본을 유지한다
- `postprocess_slides`: 검증·교정을 거친 payload를 DB records(slides, quiz_set, voice_scripts 등)로 단 한 번 emit한다

### 실패 처리

- repair·검증·교정 단계의 parsing 실패나 검증 실패는 원본을 유지한 채 다음 단계로 넘어간다
- `strip_thinking` 적용으로 모델 스왑 시에도 `<think>` reasoning이 강의 본문에 섞이지 않는다
- content_verify의 교정은 검증기가 "명백한 오류"로 지목한 것만 손댄다. 오탐을 줄이려고 확신이 낮으면 원본을 둔다
- slide_idx·키 계약은 바꾸지 않는다. 교정 병합 후 인덱스가 깨지면 원본으로 되돌린다

## 사용 예시

```bash
# backend-fastapi 루트에서 실행한다
uv --cache-dir .uv-cache run uvicorn app.modules.ChapterStudio_V1.app.main:app --port 8800
```

## 에러 정책

입력 검증 실패는 Pydantic 오류로, 변환 실패는 `ConversionError`로, DB ping 실패는 `/healthz` 503으로 보고한다.

## 인프라 사용 정책

- DB: PostgreSQL 16 (`infra/docker-compose.yml`의 `sw-ez-postgres`). schema = `chapter_studio`
- 마이그레이션: Flyway (`infra/schema/migrations/V*__*.sql`). ChapterStudio_V1 자체 마이그레이션 보유 금지
- redis: 사용 안 함. infra에 떠 있어도 ChapterStudio_V1은 연결하지 않는다
- qdrant: 사용 안 함. infra에 떠 있어도 ChapterStudio_V1은 연결하지 않는다
- 앱 런타임: uv venv. Docker는 쓰지 않는다. Mac MPS GPU를 쓰고 로컬에서 빠르게 반복 실행하려는 것이다
- 포트: FastAPI 8800

## 실행

```bash
cd infra
./scripts/dev-up.sh

cd ../backend-fastapi
uv --cache-dir .uv-cache run uvicorn app.modules.ChapterStudio_V1.app.main:app --port 8800
```

## 헬스체크

```bash
curl http://localhost:8800/healthz
```

정상 응답은 `{"status":"ok","db":"ok","modal":"skipped"}`다. Modal ping은 하지 않는다.
