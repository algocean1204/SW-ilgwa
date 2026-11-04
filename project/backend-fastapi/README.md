# backend-fastapi

LLM 호출, GPU 추론, OCR, RAG, AI 에이전트를 처리하는 서버다.

---

## 역할

LLM·GPU 추론·OCR·RAG·AI 에이전트를 여기서 처리한다. 오래 걸리는 AI 작업은 Celery 워커 큐로 보낸다.

| 구분 | 내용 |
|---|---|
| LLM 호출 | Claude Opus 4.6(커리큘럼 설계, 1강당 1회), Claude Sonnet 4.6(사실·논리 검증, 채팅, 채점). 커리큘럼 외 텍스트 생성은 Modal → Claude 폴백. TTS·ASR·임베딩은 Claude에 해당 API가 없어 폴백이 없다 |
| GPU 추론 | Modal 서버리스 (Qwen3.6-27B FP8 vLLM 강의·문제 생성 B200 8슬롯 병렬, Qwen3-TTS 음성 합성 H100, Whisper V3 Turbo 음성 인식, BGE-M3·reranker 임베딩·재순위, Kanana-2 OCR 교정 B200) |
| OCR 추론 | Marker로 먼저 추출하고 품질 게이트에 걸린 페이지만 MinerU로 다시 추출 (`app/modules/OCR_v1`) |
| 벡터 검색 | Qdrant RAG (BGE-M3 Dense + Sparse). 임베딩은 Modal 앱 `ocr-bge-m3`(`OCR_V1_EMBED_BACKEND=modal` 기본), `local`이면 프로세스 안에서 돌린다 |
| 비동기 큐 | Celery 5.4.0 + Redis |
| CPU 추론 | Kanana Nano 2.1B LoRA SFT(`aisamdasu/algocean-2.1B-nano-friend`) Q4, 과제 알림 문구 |

---

## 하지 않는 것

- 메인 비즈니스 로직·결제·인증·세션 처리 (Spring이 맡는다)
- SSE 스트리밍. 예외로 Chat_V1의 `POST /api/chat/v1/ask/stream`만 SSE로 답변을 흘려보낸다(`POST /api/chat/v1/ask`는 JSON 한 번에 응답).

---

## 기술 스택

| 항목 | 버전 |
|---|---|
| Language | Python 3.12 |
| Framework | FastAPI 0.115.5 |
| 의존성 관리 | uv |
| 비동기 큐 | Celery 5.4.0 |
| Cache / 브로커 | Redis 5.2.0 |
| OCR | Marker + MinerU (품질 게이트) |
| 벡터 DB | Qdrant |

---

## 데이터 플로우

```
Spring Boot → FastAPI (AI·채점 요청)
FastAPI     → Modal GPU / Claude API / Qdrant
FastAPI     → RDS·S3 (생성 결과 적재), Spring 콜백
```

PDF 래스터화는 lib-rust PyO3 휠이 설치돼 있으면 그 경로를, 없으면 pypdfium2를 쓴다.

---

## 텔레그램 봇

운영 봇 주소: https://t.me/ilgwa_EZ_Team_bot

Telegram API 호출과 웹훅 수신은 FastAPI가 한다. 공개 웹훅 경로는 API 명세서 기준 `POST /telegram/webhook`이고, 계정 연동 기준 필드는 ERD의 `telegram_account.telegram_chat_id`다.

### 사용자 연동 방법

1. 유저가 위 봇에 아무 메시지나 보내면 FastAPI `POST /telegram/webhook`이 받는다.
2. 웹훅 페이로드에서 `chat.id`를 꺼내 해당 유저의 `telegram_account.telegram_chat_id`에 저장한다.
3. 이후 과제 알림·AI 튜터 캡션은 이 `chat_id`로 보낸다.

### 자동 승인 로직

- 새 `chat_id`를 어느 유저에게 붙일지(인증 코드, 전화번호 매칭, 수동 승인 등)는 아직 정하지 않았다.
- 어떤 방식이든 승인 로직은 FastAPI 안에서 처리한다. Spring·Rust는 관여하지 않는다.

개념 스케치(미확정): 아직 유저에 붙지 않은 chat_id는 `telegram_account_pending` 임시 테이블이나 Redis 큐에 잠시 넣고, 관리자 승인이나 자동 매칭(인증 코드·전화번호 비교 등)으로 최종 유저에 붙이는 방식을 생각하고 있다. 적재 방식·테이블 스키마·매칭 알고리즘은 FastAPI 담당자가 정한다.

### 보안 주의

- 봇 토큰을 코드에 평문으로 넣지 않는다. 토큰은 환경변수나 `admin_telegram_credential` 테이블(슈퍼 관리자 페이지 F16.3)에서 관리한다.

---

## 로컬 실행

```bash
# 의존성 설치 (uv 사용)
uv sync

# 개발 서버 실행
uv run uvicorn main:app --reload

# Celery 워커 실행
uv run celery -A app.celery_app worker --loglevel=info --concurrency 2
```

DB 등 의존 서비스는 infra 레포의 docker-compose로 먼저 띄운다.

---

## 관련 저장소

| 레포 | 역할 |
|---|---|
| [backend-spring](https://github.com/SW-EZ-Team/backend-spring) | 메인 백엔드 (REST API·비즈니스 로직·인증·결제) |
| [lib-rust](https://github.com/SW-EZ-Team/lib-rust) | PDF 래스터화 PyO3 Rust 라이브러리 (wheel) |
| [infra](https://github.com/SW-EZ-Team/infra) | 로컬 DB 서버 묶음 (PostgreSQL·Redis·Qdrant) |

---

## 모듈 설계 원칙 (라이브러리형 모듈)

AI 파이프라인 단계마다 `app/modules/<ModuleName>/`에 독립된 라이브러리형 모듈로 나눈다. 라우터·Celery task·다른 모듈은 `from app.modules.<ModuleName> import ...`로 가져다 쓴다.

### 구조

```
app/modules/<ModuleName>/
├── __init__.py        공개 API만 re-export
├── config.py          상수·프롬프트·파라미터
├── <core>.py          주요 로직
├── model_loader.py    (AI 모듈 한정) 싱글톤 모델 로더
├── fallback.py        (선택) 폴백 구현
├── validator.py       (선택) 출력 검증
├── README.md          모듈 사용법·내부 구조
└── tests/             모듈 단위 테스트
```

### 규칙

- 공개 API는 `__init__.py`에서만 정의한다. 외부는 `from app.modules.X import func`만 쓰고 내부 파일을 직접 import하지 않는다.
- 모듈끼리 직접 import하지 않는다. 공통 기능이 필요하면 `app/core/`로 올린다.
- 무거운 리소스(LLM·ASR·OCR 모델)는 싱글톤 로더로 처음 쓸 때 올린다. Celery 워커 프로세스당 한 번만 로드한다.
- 모듈별 README에 사용법·IN/OUT·파라미터·폴백 정책을 적는다.
- 테스트는 모듈 안 `tests/`에 둔다.

### 현재 등록된 모듈

| 모듈 | 역할 |
|---|---|
| `Agent_orchestrator` | 비동기 Job, 멱등 키, Spring 콜백 |
| `ChapterStudio_V1` | 커리큘럼·강의·슬라이드 생성 (템플릿 75종) |
| `ExamForge_V1` | 모의고사 생성 (LangGraph 6단계) |
| `OCR_v1` | 교재 OCR(Marker + MinerU)·RAG 적재·검색 |
| `TTS_V2` | 음성 수업, 문장 단위 합성 |
| `Chat_V1` | 강의 맞춤 Q&A (JSON 응답, SSE 스트리밍) |
| `AssignmentGrader_V1` | 과제 채점 (Claude Sonnet) |
| `ASR_V1` | 음성 인식 (Whisper V3 Turbo, Modal GPU) |
| `ObjectStorage_V1` | S3 업로드 |
| `AI_CPU_Kanana_Nano_Q4` | 텔레그램 과제 캡션 생성 (CPU, Kanana Nano 2.1B LoRA SFT Q4_K_M, llama.cpp) |
| `Telegram_control_module` | Telegram Bot API `sendMessage`·`POST /telegram/webhook`·`telegram_chat_id` 추출 |
| `FilePreprocessor` | 오피스 문서(.docx/.pptx/.xlsx) → Markdown 변환 (MarkItDown). 계획만 있고 구현 전 |
