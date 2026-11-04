# Telegram_control_module

FastAPI 안에서 Telegram Bot API 메시지를 보내고 받는다. Bot API 호출은 표준 라이브러리 `urllib`만 쓰고, 웹훅 수신 → Update 파싱 → 명령·파일 제출 처리를 이 모듈에서 한다.

---

## 모듈 목적

- `POST /telegram/sendMessage`로 학생이나 운영자에게 Telegram DM을 보낸다.
- `POST /telegram/send-assignment-caption`으로 과제 알림 문구를 만들어 바로 보낸다.
- `POST /telegram/webhook`으로 Telegram이 보내는 Update를 받아 `telegram_chat_id`를 뽑고, `/job_status` 명령과 과제 파일 제출을 처리한다.
- 채점 완료 알림, Agent_orchestrator Job 상태 알림처럼 다른 모듈의 알림을 보내는 창구로 쓴다.

---

## 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/telegram/health` | 모듈 연결 상태 확인 |
| `GET` | `/telegram/console` | 브라우저 테스트 콘솔 HTML (스키마 노출 안 함) |
| `POST` | `/telegram/sendMessage` | 지정 채팅방에 메시지 발송 |
| `POST` | `/telegram/send-assignment-caption` | 과제 알림 문구 생성 후 발송 |
| `POST` | `/telegram/webhook` | Telegram Update 웹훅 수신 |

---

## IN/OUT 인터페이스

### POST `/telegram/sendMessage`: 메시지 발송

#### Request (`application/json`)

```json
{
  "token": null,
  "telegram_chat_id": "123456789",
  "message": "채점 결과가 도착했습니다."
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `token` | str \| null | 선택 | Bot 토큰. null이면 `TELEGRAM_BOT_TOKEN` 환경변수 사용 |
| `telegram_chat_id` | str | 필수 | 수신자의 Telegram chat ID (ERD 컬럼명 기준) |
| `message` | str | 필수 | 전송할 텍스트 메시지 |

#### Response (`200 OK`)

```json
{
  "ok": true,
  "telegram_message_id": 42,
  "description": null
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `ok` | bool | Telegram API 호출 성공 여부 |
| `telegram_message_id` | int \| null | 전송된 메시지의 Telegram message ID |
| `description` | str \| null | 실패 시 Telegram 반환 오류 메시지 |

#### Response (`400 Bad Request`)

토큰 없음, chat_id 누락, Telegram API 오류일 때 돌려준다.

---

### POST `/telegram/send-assignment-caption`: 과제 알림 발송

`AI_CPU_Kanana_Nano_Q4.generate_caption`으로 알림 문구를 만든 뒤 `TELEGRAM_BOT_TOKEN`으로 보낸다. 문구는 Kanana(LoRA SFT, Q4 GGUF, llama.cpp CPU) → Claude Haiku → Jinja2 템플릿 순으로 폴백해 만든다.

#### Request (`application/json`)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `telegram_chat_id` | str | 필수 | 수신자 chat ID. `chat_id`로 보내도 받는다 |
| `name` | str | 필수 | 학생 이름 |
| `assignment` | str | 필수 | 과제 이름 |
| `weakness` | str \| null | 선택 | 약점 |
| `difficulty` | str \| null | 선택 | 난이도 |
| `deadline` | str \| null | 선택 | 마감 |

#### Response (`200 OK`)

| 필드 | 타입 | 설명 |
|---|---|---|
| `ok` | bool | Telegram 발송 성공 여부 |
| `text` | str | 보낸 알림 문구 |
| `source` | str | 문구를 만든 경로 |
| `telegram_message_id` | int \| null | 전송된 메시지 ID |
| `description` | str \| null | 실패 시 Telegram 오류 메시지 |

---

### POST `/telegram/webhook`: 웹훅 수신

Telegram이 Update를 보낼 때 호출한다.

#### Request Header

```
X-Telegram-Bot-Api-Secret-Token: <secret>
```

`TELEGRAM_WEBHOOK_SECRET` 환경변수가 설정된 경우에만 검증한다. 로컬 테스트에서 설정하지 않으면 검증을 건너뛴다.

#### Request Body (`application/json`)

Telegram Update 객체 원문이다.

```json
{
  "message": {
    "message_id": 17,
    "chat": {
      "id": 123456789,
      "username": "student_user",
      "first_name": "민준"
    },
    "text": "안녕하세요"
  }
}
```

#### Response (`200 OK`)

```json
{ "ok": true }
```

#### Response (`400 Bad Request`)

Secret token이 일치하지 않을 때 돌려준다.

#### 수신 후 처리

1. `parsers/update_parser.py`가 `telegram_chat_id`, `telegram_msg_id`, `telegram_username`, `content`, `message_type`, `file_id`, `file_name`을 뽑아 `TelegramMessageSummary`로 만든다. `telegram_chat_id`가 없는 Update는 무시한다.
2. `services/command_dispatcher.py`가 `/job_status <job_id>`(또는 `/status`)면 Job 상태를 답장한다.
3. document·photo 파일이고 DB 커넥션이 있으면 `services/grading_orchestrator.py`가 채점한다. 순서는 파일 다운로드 → submission INSERT → Claude Sonnet VLM 채점 → submission UPDATE → 약점 갱신 → DM 회신이다.

---

### GET `/telegram/health`: 헬스체크

#### Response (`200 OK`)

```json
{ "status": "ok", "module": "Telegram_control_module" }
```

---

## 환경변수

| 변수 | 설명 |
|---|---|
| `TELEGRAM_BOT_TOKEN` | 기본 봇 토큰. `sendMessage` 요청의 `token`이 null일 때와 캡션 발송·채점 DM에 사용 |
| `TELEGRAM_WEBHOOK_SECRET` | 웹훅 secret token 검증값. 설정하지 않으면 검증을 건너뜀 |
| `ANTHROPIC_API_KEY` | 파일 채점용 Claude API 키 |
| `GRADING_SONNET_MODEL` | 채점 모델. 기본 `claude-sonnet-4-6` |

토큰은 코드, 문서, 커밋 로그, 테스트 픽스처에 저장하지 않는다.

---

## 의존성

- `fastapi`: 라우터
- `pydantic >= 2`: 입출력 스키마
- 표준 라이브러리 `urllib`, `asyncio`: Telegram Bot API HTTP 호출 (추가 의존성 없음)
- `anthropic`: 파일 채점용 Claude 호출

---

## 폴더 구조

```
Telegram_control_module/
├── __init__.py
├── config.py                   # 환경변수 로더
├── schemas.py                  # 입출력 Pydantic 모델
├── api/
│   └── router.py               # FastAPI 라우터
├── parsers/
│   └── update_parser.py        # Telegram Update → TelegramMessageSummary 변환
├── services/
│   ├── telegram_client.py      # Telegram Bot API HTTP 클라이언트
│   ├── command_dispatcher.py   # 명령·파일 제출 분기
│   ├── file_downloader.py      # getFile 조회와 파일 다운로드
│   ├── assignment_grader.py    # Claude Sonnet VLM 채점
│   ├── grading_orchestrator.py # 다운로드부터 DM 회신까지 채점 흐름
│   └── protocols.py            # TelegramClientLike 프로토콜
├── tests/
│   ├── test_schemas.py
│   ├── test_update_parser.py
│   └── test_command_dispatcher.py
└── web_console/
    └── index.html              # 브라우저 테스트 콘솔
```

---

## 사용 예시

### FastAPI 앱 연결

```python
from app.modules.Telegram_control_module import router as telegram_router

app.include_router(telegram_router)
```

### 다른 모듈에서 메시지 전송

```python
from app.modules.Telegram_control_module.services.telegram_client import TelegramClient
from app.modules.Telegram_control_module.config import get_default_bot_token

token = get_default_bot_token()
await TelegramClient().send_message(
    token=token,
    chat_id="123456789",
    message="AI 작업이 완료되었습니다."
)
```

---

## 에러 처리

| 상황 | HTTP 상태 | 설명 |
|---|---|---|
| `TELEGRAM_BOT_TOKEN`이 없고 요청의 `token`도 null | 400 | 토큰 필요 메시지 반환 |
| `telegram_chat_id` 또는 `message` 누락 | 400 | 두 필드 모두 필수 |
| Telegram API 호출 실패 | 400 | `TelegramApiError`를 400으로 변환해 반환 |
| 웹훅 secret 불일치 | 400 | 검증 실패 메시지 반환 |
| `telegram_chat_id` 없는 Update | 해당 없음 | 무시하고 200 ACK 반환 |

`TelegramClient`의 실패는 `TelegramApiError` 하나로 모은다. 호출자는 HTTP 세부 사항을 몰라도 된다.
