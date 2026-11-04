# common

## 모듈 목적
환경변수, 로깅, 공통 예외를 모아 둔다.

## IN·OUT
IN은 `.env` 값이며 OUT은 설정 함수, logger, 공통 예외 클래스다.

## 환경변수
`DATABASE_URL`, `DATABASE_SCHEMA`, `LOG_LEVEL`, `ANTHROPIC_API_KEY`, `MODAL_TOKEN_ID`, `MODAL_TOKEN_SECRET`, `ACTIVE_TEXT_MODEL`, `ACTIVE_PLANNER_MODEL`, `ACTIVE_TTS_MODEL`, `TTS_ENDPOINT`를 사용한다.

## 의존성
`python-dotenv`, `loguru`에 의존한다.

## 사용 예시
```python
from common.config import database_url
```

## 에러 정책
필수 환경값이 없거나 schema 이름이 `^[A-Za-z_][A-Za-z0-9_]*$` 형식이 아니면 `RuntimeError`로 바로 중단한다.
