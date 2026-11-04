# Chat_V1 ChapterStudio 학습 챗봇 모듈

## 목적

ChapterStudio가 만든 강의 콘텐츠(슬라이드·음성대본·퀴즈)를 컨텍스트로 학생 질문에 답하는 학습 챗봇이다. 텍스트 채팅과 음성 대화 두 모드가 있다.

---

## 두 가지 모드

| 모드 | 엔드포인트 | 텍스트 모델 | 입력 | 출력 |
|---|---|---|---|---|
| 텍스트 채팅 | `POST /api/chat/v1/ask` | Claude Sonnet (`CHAT_V1_TEXT_MODEL`, 기본 `claude_sonnet`) | 텍스트 질문 | 텍스트 답변(JSON 하나) |
| 텍스트 채팅 스트리밍 | `POST /api/chat/v1/ask/stream` | `/ask`와 같음 | 텍스트 질문(`/ask`와 같은 요청) | 답변 조각(SSE) |
| 음성 대화 | `POST /api/chat/v1/voice` | Gemini 2.0 Flash Live | 음성(base64) | 음성(base64) |

텍스트 모드 기본 모델은 Claude Sonnet이다. `service.py`의 `call_text_model()`이 `get_text_connector(CHAT_V1_TEXT_MODEL)`로 커넥터를 받는다.
공용 `ACTIVE_TEXT_MODEL`은 따르지 않는다. 그 값이 `gemini_flash`(Gemini → OpenAI → Claude 폴백 래퍼)여도 채팅 기본 체인에는 Gemini·OpenAI가 끼지 않는다. 다른 모델은 `CHAT_V1_TEXT_MODEL`을 명시해야 쓴다.

두 모드 모두 강의 컨텍스트(슬라이드·음성대본·퀴즈)를 함께 받아 답한다.

---

## IN/OUT 인터페이스

### 텍스트 채팅 (POST `/api/chat/v1/ask`)

```json
{
  "session_id": "sess_abc123",
  "user_message": "2번 슬라이드에서 이진 탐색 시간복잡도가 왜 O(log n)인가요?",
  "lecture_context": {
    "chapter_title": "자료구조 기초 — 탐색 알고리즘",
    "slides": [
      { "slide_idx": 0, "title": "선형 탐색", "content": "배열을 처음부터 끝까지 순회한다. 시간복잡도 O(n)." },
      { "slide_idx": 1, "title": "이진 탐색", "content": "정렬된 배열에서 중간 값과 비교해 탐색 범위를 절반으로 줄인다. 시간복잡도 O(log n)." }
    ],
    "voice_scripts": ["이번 시간에는 탐색 알고리즘을 배워볼게요."],
    "quiz_items": ["이진 탐색의 전제 조건은 무엇인가요?"]
  }
}
```

### 응답

```json
{
  "session_id": "sess_abc123",
  "answer": "이진 탐색은 [슬라이드 2]에서 설명하듯이 탐색할 때마다 범위가 절반으로 줄어들기 때문에 O(log n)이에요...",
  "referenced_slides": [1]
}
```

`referenced_slides`는 0-based 인덱스다. 답변의 `[슬라이드 N]` 패턴(1-based)을 뽑아 바꾼다. 슬라이드 총 개수를 상한으로 확인해 없는 슬라이드 인용은 버린다.

---

### 텍스트 채팅 스트리밍 (POST `/api/chat/v1/ask/stream`)

요청 본문은 `/ask`와 같다. 응답은 `text/event-stream`이고 이벤트가 차례로 온다.

```
event: delta
data: {"text": "이진 탐색은 "}

event: delta
data: {"text": "탐색할 때마다 범위가 절반으로 줄어요. [슬라이드 2]"}

event: done
data: {"session_id": "sess_abc123", "answer": "이진 탐색은 탐색할 때마다 범위가 절반으로 줄어요. [슬라이드 2]", "referenced_slides": [1]}
```

| 이벤트 | data | 설명 |
|---|---|---|
| `delta` | `{"text": "..."}` | 모델이 내보낸 답변 조각. 조각마다 한 번 온다 |
| `done` | `/ask` 응답과 같은 필드 | 마지막에 한 번 온다. `answer`는 `/ask`와 같은 마무리(`<think>` 제거, 범위 밖 안내문, 슬라이드 인용 추출)를 거친 최종 답변이라 조각을 이어 붙인 것과 다를 수 있다 |
| `error` | `{"message": "..."}` | 실패하면 `done` 대신 온다. 조각을 이미 보낸 뒤에 실패해도 `error`로 끝난다 |

- 모델은 `/ask`와 같이 `CHAT_V1_TEXT_MODEL`로 고르고, 프롬프트도 같은 함수(`build_system_prompt`, `build_text_request`)로 만든다
- Claude 커넥터는 `stream()`(Anthropic SDK `messages.stream`)으로 조각을 보낸다. `stream()`이 없는 커넥터는 `generate()` 결과를 조각 하나로 보낸다
- 요청 스키마가 틀리면 스트림을 열기 전에 422로 끝난다

---

### 음성 대화 (POST `/api/chat/v1/voice`)

Gemini 2.0 Flash Live로 음성 입력을 받아 음성으로 답한다.

#### 요청

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
| `lecture_context` | object | 필수 | 강의 콘텐츠 컨텍스트 (텍스트 모드와 동일 구조) |

#### 응답

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

---

## 환경변수

| 변수명 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `CHAT_V1_TEXT_MODEL` | 선택 | `claude_sonnet` | 텍스트 채팅에 쓸 커넥터 이름. `ai_connectors.registry`에 등록된 이름을 지정한다 |
| `GEMINI_API_KEY` | 조건부 | — | Google Gemini API 인증 키 (음성 모드 필수). `GOOGLE_API_KEY`로도 인식 |
| `GOOGLE_API_KEY` | 조건부 | — | `GEMINI_API_KEY`의 대체 환경변수명. 둘 중 하나만 있으면 됨 |
| `GEMINI_FLASH_LIVE_MODEL` | 선택 | `gemini-2.0-flash-live-001` | 음성 대화에 사용할 Gemini 모델 식별자 |
| `GEMINI_FLASH_LIVE_TIMEOUT_SEC` | 선택 | `60` | 음성 대화 API 호출 제한 시간(초) |

텍스트 커넥터별 환경변수(API 키, 타임아웃 등)는 해당 커넥터 문서를 본다. `claude_sonnet` 커넥터는 `CLAUDE_SONNET_API_KEY` 또는 `ANTHROPIC_API_KEY`, 모델은 `CLAUDE_SONNET_MODEL`(기본 `claude-sonnet-4-6`)을 쓴다.

---

## 의존성

- `ai_connectors.registry`: `get_text_connector()`로 텍스트 커넥터를 받는다
- `ai_connectors.text_schemas`: `ChapterAIRequest` / `ChapterAIResponse`
- `common.llm_output`: `strip_thinking`으로 답변에 붙은 `<think>` 블록을 지운다 (`common/llm_output.py`)
- `google-genai>=1.10.0`: Gemini Flash Live API 클라이언트 (음성 모드)
- `langgraph`: 챗봇 파이프라인 상태 머신
- `pydantic` v2: 요청/응답 스키마 검증
- `fastapi`: HTTP 라우터

---

## 파이프라인 구조

아래 그래프는 `/ask` 경로다. `/ask/stream`은 그래프를 거치지 않고 `service.stream_answer`가 커넥터 `stream()`을 직접 부른 뒤, 마지막에 `format_response`와 같은 `finalize_answer`로 `done` 답변을 만든다.

```
validate_input
      │
      ▼
generate_answer  ← CHAT_V1_TEXT_MODEL 커넥터 호출 (기본 Claude Sonnet)
      │
      ▼
format_response  ← strip_thinking → 환각 가드 → 슬라이드 참조 추출
      │
      ▼
     END
```

### 각 노드 설명

- `validate_input`: 입력 유효성 검증
- `generate_answer`: 텍스트 모델을 부른다. 오류가 나면 `error_message`에 기록한다
- `format_response`: `service.finalize_answer`로 아래 세 단계를 순서대로 한다
  1. `common.llm_output.strip_thinking`: `<think>...</think>` reasoning 블록을 지워 추론 과정이 답변에 보이지 않게 한다
  2. `app.scope_guard.apply_hallucination_guard`: 강의 키워드와 겹치지 않는 답변은 그대로 두고 끝에 범위 밖 안내문(`SCOPE_NOTICE`) 한 줄을 붙인다. 슬라이드는 참고 자료라 답변을 거절문으로 바꾸지 않는다
  3. `app.scope_guard.extract_referenced_slides`: `[슬라이드 N]` 패턴을 0-based 인덱스로 바꾸고, `slide_count`를 넘는 인용은 버린다

### 답변 품질 장치

- 시스템 프롬프트: 비교·혼동 질문에는 짧은 대조 예시 1개를 들게 하고, 자료를 근거로 한 문장 끝에 `[슬라이드 N]` 인용을 붙이게 한다. few-shot 예시 3개(개념/비교/범위 밖)를 넣는다
- 범위 가드(`app/scope_guard.py`): 강의 키워드 어간과 답변 어간이 하나도 겹치지 않으면 답변 끝에 `SCOPE_NOTICE`("이 내용은 이번 강의 범위 밖이에요.")를 붙인다. 슬라이드 인용이 있거나 안내문이 이미 있으면 붙이지 않는다. 질문 자체를 거절하지는 않는다
- 슬라이드 상한 확인: `slide_count`를 넘는 참조를 버려, 없는 슬라이드 번호가 Spring·프론트로 넘어가지 않게 한다

오류가 나면 `error_message`가 상태에 기록되고 `format_response`가 안내 문구로 바꾼다.

---

## 사용 예시 (Python)

```python
from app.modules.Chat_V1 import ChatRequest, answer_question
from app.modules.Chat_V1.app.schemas import LectureContext, SlideContext

ctx = LectureContext(
    chapter_title="자료구조 기초",
    slides=[
        SlideContext(slide_idx=0, title="이진 탐색", content="O(log n) 탐색...")
    ],
)
req = ChatRequest(
    session_id="test_session",
    user_message="이진 탐색 시간복잡도를 설명해 주세요.",
    lecture_context=ctx,
)
response = await answer_question(req)
print(response.answer)
print(response.referenced_slides)
```

---

## 에러 핸들링 정책

| 상황 | HTTP 상태 | 동작 |
|---|---|---|
| 빈 질문 / 컨텍스트 없음 | 422 | `validate_input` 노드가 오류 상태 설정, 라우터가 422 반환 |
| 커넥터 오류 (인증 실패·레이트 리밋 등) | 500 | `AIConnectorError` → `format_response`가 안내문으로 바꿈 |
| 파이프라인 타임아웃 | 500 | 상위 서킷 브레이커 또는 커넥터 타임아웃 설정으로 제한 |
| 강의 범위 밖 답변 | 200 | 답변 유지 + 끝에 범위 밖 안내문(`SCOPE_NOTICE`) 추가, `referenced_slides: []` |
| 슬라이드 참조 없음 | 200 | `referenced_slides: []`로 정상 반환 |
| 스트리밍 중 커넥터 오류 | 200 | `/ask/stream`은 헤더를 먼저 보내므로 `event: error`로 안내문을 보내고 끝낸다 |

오류는 사용자에게 "죄송해요, 답변을 생성하는 중에 오류가 발생했어요. 잠시 후 다시 시도해 주세요." 같은 한국어 안내로 보인다.
