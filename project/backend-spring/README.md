# backend-spring

서비스의 메인 백엔드 서버다.

---

## 역할 및 책임 범위

REST API, 비즈니스 로직, 인증, 결제, 세션 관리를 맡는다. 외부 서비스 연동과 FastAPI 호출도 이 서버에서 한다.

| 구분 | 내용 |
|---|---|
| REST API | 유저·과외·결제·세션 엔드포인트 |
| 외부 서비스 연동 | OAuth, 토스페이먼츠, 텔레그램 봇 |
| FastAPI 호출 | AI 처리와 과제 채점 요청을 FastAPI(사설 IP:8000)로 전달 (예: POST /assignments/{id}/submit) |
| DB 통신 | PostgreSQL (주 DB), Redis (캐시·세션) |

---

## 하지 않는 것

- AI 추론·LLM 호출은 하지 않는다 (FastAPI에서 처리)
- PDF/이미지 전처리는 하지 않는다 (FastAPI와 lib-rust에서 처리)
- SseEmitter 기반 실시간 스트리밍은 쓰지 않는다 (상태 조회는 REST)
- OCR 모델을 직접 실행하지 않는다 (FastAPI에서 처리)

---

## 기술 스택

| 항목 | 버전 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.5 |
| Build | Gradle |
| DB | PostgreSQL |
| Cache | Redis |

---

## 데이터 플로우

```
Client
  → Spring Boot (REST API)
  → [FastAPI: AI 처리 요청]
  → Spring Boot
  → Client
```

---

## 로컬 실행

```bash
# 빌드
./gradlew build

# 개발 서버 실행
./gradlew bootRun
```

DB 등 의존 서비스는 infra 레포의 docker-compose로 먼저 기동한다.

---

## 관련 저장소

| 레포 | 역할 |
|---|---|
| [lib-rust](https://github.com/SW-EZ-Team/lib-rust) | PDF/이미지 전처리 PyO3 라이브러리 |
| [backend-fastapi](https://github.com/SW-EZ-Team/backend-fastapi) | AI 추론·OCR·채점 |
| [infra](https://github.com/SW-EZ-Team/infra) | 로컬 DB 서버 묶음 (PostgreSQL·Redis·Qdrant) |
