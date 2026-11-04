# app

## 모듈 목적
FastAPI HTTP 진입점, 라우터, 커리큘럼·강의 생성 흐름을 둔다.

## IN·OUT
IN은 HTTP 요청이며 OUT은 FastAPI 응답 객체다. 독립 실행용 `app.main`은 `/healthz`만 제공하고, 업무 API는 `routers/`에 있다.

## 환경변수
`DATABASE_URL`, `DATABASE_SCHEMA`, `LOG_LEVEL`을 사용한다.

## 의존성
`app.db`, `common.logging`에 의존한다.

## 사용 예시
```bash
uv --cache-dir .uv-cache run uvicorn app.main:app --port 8800
```

## 에러 정책
DB 연결 실패는 로그를 남기고 `/healthz`에서 503을 반환한다.
