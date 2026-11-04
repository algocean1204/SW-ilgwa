# Telegram_control_module

`backend-fastapi` 안의 Telegram Bot API 연결 모듈이다. 루트 `Teletest/`에서 `sendMessage` 호출을 확인한 흐름을 FastAPI로 옮겼다(옮긴 내역은 문서 끝 참고).

이 프로젝트에서 Telegram API 호출과 웹훅 수신은 FastAPI가 맡는다. 이름은 `Docs/API명세서/API_명세서.md`와 `Docs/ERD/일과_ERD.md`에 맞춘다. 공개 웹훅 경로는 `POST /telegram/webhook`, 계정 연동 필드는 `telegram_account.telegram_chat_id`, 송수신 로그 테이블은 `telegram_message`다.

---

## 폴더 구조

```text
Telegram_control_module/
├── __init__.py
├── config.py
├── schemas.py
├── Telegram_control_module.md
├── api/
│   ├── __init__.py
│   └── router.py
├── parsers/
│   ├── __init__.py
│   └── update_parser.py
├── services/
│   ├── __init__.py
│   ├── telegram_client.py
│   ├── command_dispatcher.py
│   ├── file_downloader.py
│   ├── assignment_grader.py
│   ├── grading_orchestrator.py
│   └── protocols.py
├── tests/
│   ├── test_schemas.py
│   ├── test_update_parser.py
│   └── test_command_dispatcher.py
└── web_console/
    └── index.html
```

### `api/`

FastAPI 라우터가 있다. 공개 경로는 다음과 같다.

| Method | Path | 역할 |
|---|---|---|
| `GET` | `/telegram/health` | 모듈 라우터가 연결됐는지 확인함 |
| `GET` | `/telegram/console` | 브라우저 테스트 콘솔 HTML을 반환함 |
| `POST` | `/telegram/sendMessage` | Telegram Bot API `sendMessage`를 호출함 |
| `POST` | `/telegram/send-assignment-caption` | 과제 알림 문구를 만들어 보냄 |
| `POST` | `/telegram/webhook` | API 명세서의 Telegram Update 웹훅을 수신함 |

`router.py`의 `create_router()`는 `TelegramClient`를 주입받을 수 있어 테스트에서 가짜 클라이언트를 넣는다.

### `services/`

Telegram 외부 API 호출과 채점 흐름이 있다. `telegram_client.py`와 `file_downloader.py`는 표준 라이브러리 `urllib`만 쓴다. `backend-fastapi`에 HTTP 클라이언트 의존성을 새로 넣지 않으려고 이렇게 했다.

| 객체 | 역할 |
|---|---|
| `TelegramClient` | Telegram Bot API `sendMessage` 호출 |
| `TelegramApiError` | Telegram API 실패를 모듈 내부 예외 하나로 모음 |
| `dispatch_telegram_command` | `/job_status`·`/status` 명령과 파일 제출을 나눠 처리 |
| `handle_file_submission` | 파일 다운로드 → submission INSERT → Claude Sonnet VLM 채점 → submission UPDATE → 약점 갱신 → DM 회신 |
| `grade_submission` | Claude Sonnet으로 이미지·PDF 과제를 채점해 점수·피드백·약점 JSON을 받음 |

`send_message()`는 FastAPI 이벤트 루프를 막지 않도록 `asyncio.to_thread()`로 동기 HTTP 호출을 따로 돌린다.

### `parsers/`

웹훅 Update에서 ERD 저장 후보 필드를 뽑는다. `message`, `edited_message`, `channel_post`, `edited_channel_post`, `callback_query.message`를 지원한다.

```python
from app.modules.Telegram_control_module import extract_telegram_message_summary
```

이 함수는 `telegram_chat_id`, `telegram_msg_id`, `telegram_username`, `content`, `message_type`, `file_id`, `file_name`을 `TelegramMessageSummary`로 만든다. `telegram_chat_id`가 없으면 `None`을 돌려준다.

### `web_console/`

`Teletest/public/index.html`을 옮긴 브라우저 테스트 콘솔이다. FastAPI 실행 후 아래 경로로 연다.

```text
http://localhost:8000/telegram/console
```

콘솔은 `/telegram/sendMessage`를 호출한다. `Bot Token` 입력값을 비워두면 서버의 `TELEGRAM_BOT_TOKEN` 환경변수를 쓴다.

### `tests/`

| 테스트 파일 | 검증 내용 |
|---|---|
| `test_schemas.py` | ERD 기준 `telegram_chat_id` 입력 필드, 빈 메시지 차단 |
| `test_update_parser.py` | 일반 메시지·콜백 쿼리·chat 미포함 Update 처리 |
| `test_command_dispatcher.py` | `/job_status` 응답, 일반 텍스트 무시 |

---

## 사용 방법

### FastAPI 라우터 연결

`backend-fastapi/main.py`에서 다음처럼 연결한다.

```python
from app.modules.Telegram_control_module import router as telegram_control_router

app.include_router(telegram_control_router)
```

### 메시지 전송

```http
POST /telegram/sendMessage
Content-Type: application/json

{
  "token": null,
  "telegram_chat_id": "123456789",
  "message": "테스트 메시지"
}
```

`token`이 `null`이면 서버 환경변수 `TELEGRAM_BOT_TOKEN`을 쓴다. 운영에서는 토큰을 코드, 문서, 로그에 남기지 않는다.

### 웹훅 수신

```http
POST /telegram/webhook
X-Telegram-Bot-Api-Secret-Token: <secret>
Content-Type: application/json

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

`TELEGRAM_WEBHOOK_SECRET`이 설정돼 있으면 `X-Telegram-Bot-Api-Secret-Token` 헤더와 비교하고, 없으면 로컬 테스트용으로 검증을 건너뛴다. 응답은 API 명세서대로 `{"ok": true}`다.

---

## 처리 흐름

1. 사용자가 봇에 메시지를 보내면 `/telegram/webhook`이 Update를 받는다.
2. `parsers/update_parser.py`가 `telegram_chat_id`와 파일 정보를 뽑는다.
3. `/job_status <job_id>` 명령이면 Agent_orchestrator job store에서 상태를 읽어 답장한다.
4. document·photo 파일이면 DB 커넥션이 있을 때 `grading_orchestrator`가 채점한다. `telegram_account`에서 user_id를, `assignment`에서 가장 최근 미완료 과제를 찾고, 없으면 기본 기준(완성도·정확성·창의성)으로 채점한다.
5. 채점 결과는 `submission`에 저장하고 약점은 `weakness_profile`에 반영한 뒤 학생에게 DM으로 보낸다.

`telegram_account.telegram_chat_id` 저장(계정 연동)과 관리자 알림, 메시지 내역 조회는 이 모듈에 아직 없다.

---

## 환경변수

| 변수 | 설명 |
|---|---|
| `TELEGRAM_BOT_TOKEN` | 기본 봇 토큰. `/telegram/sendMessage` 요청의 `token`이 없을 때 사용함 |
| `TELEGRAM_WEBHOOK_SECRET` | Telegram 웹훅 secret token 검증값 |
| `ANTHROPIC_API_KEY` | 파일 채점용 Claude API 키 |
| `GRADING_SONNET_MODEL` | 채점 모델. 기본 `claude-sonnet-4-6` |

토큰은 코드, 문서, 테스트 데이터, 커밋 로그에 저장하지 않는다.

---

## 주의사항

- 입력 필드는 ERD 컬럼명에 맞춰 `telegram_chat_id`를 쓴다.
- Telegram 외부 ID인 `telegram_chat_id`는 음수 채널 ID까지 고려해 정수로 파싱한다.
- Telegram API 호출 이름은 공식 표기(`sendMessage`, `getMe`, `getFile`)를 따른다.

---

## 참고: Teletest에서 옮긴 내역

| 원본 `Teletest` | 옮긴 위치 | 설명 |
|---|---|---|
| `server.js` | `api/router.py`, `services/telegram_client.py` | Express `/api/send` 흐름을 FastAPI `/telegram/sendMessage` 라우터와 Telegram API 클라이언트로 나눔 |
| `public/index.html` | `web_console/index.html` | 브라우저에서 메시지 전송을 확인하는 테스트 콘솔로 유지함 |
| `package.json`, `package-lock.json` | 옮기지 않음 | Node 실행 환경용 의존성 파일이라 FastAPI 모듈에 넣지 않음 |
| `node_modules/` | 옮기지 않음 | 설치 산출물이라 소스 모듈에 넣지 않음 |

원본에서 확인한 동작은 그대로다. Bot Token, `telegram_chat_id`, 메시지를 받아 `sendMessage`로 보내고, 실패하면 Telegram `description`을 돌려준다. 런타임만 Express에서 FastAPI로 바꿨고, 토큰은 환경변수 `TELEGRAM_BOT_TOKEN`을 먼저 쓸 수 있게 했다.
