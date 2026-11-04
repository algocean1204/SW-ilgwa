# Self-Healing Memory 설계안

> 상태: 미구현 설계안. 아래 모듈(autoDream, MemoryPointerService 등)은 코드에 구현하지 않았다.  
> 작성일: 2026-04-14  
> 도메인 경계: FastAPI만 (Spring·Rust 관여 없음)  
> AI 파이프라인(FastAPI) 안에서는 SSE를 쓰지 않는다. ②c 질답·퀴즈 소크라테스 스트리밍은 HTTP chunked `StreamingResponse`로만 처리한다. Spring Boot도 강의·알림 SseEmitter 없이 REST 상태 조회나 다음 요청 piggyback을 쓴다. (2026-04-29 정리) 예외는 Chat_V1 `POST /api/chat/v1/ask/stream` 하나로, 채팅 답변을 SSE로 흘려보낸다.

---

## 1. 개요 — 왜 Self-Healing Memory인가

### 해결 대상: context entropy

LLM 기반 AI 튜터는 세션이 길어질수록 정확도와 응답 품질이 떨어진다. 이전 대화가 컨텍스트 창에서 밀려나거나 오래된 사실이 최신 상태를 덮어쓰는 일이 반복된다. 이를 context entropy라 부른다.

재접속마다 학생 맥락을 처음부터 재구성하거나, 에이전트가 이전 세션의 오개념을 그대로 가져오는 문제가 이 entropy에서 비롯된다.

### 참고한 구조: 3계층 에이전트 메모리

2026-03~04에 공개된 에이전트 메모리 설계를 참고했다. 인덱스 포인터(L2)만 항상 컨텍스트에 올리고, 실제 도메인 내용(L3)은 필요할 때만 읽는다. 에이전트가 이 레이어를 스스로 다시 쓰므로 사람이 따로 관리하지 않는다.

이 구조를 개인 맞춤 AI 튜터링에 옮긴다.

### 기존 ERD와의 대응

[`ERD.md`](../03_데이터/ERD.md)에 이미 `memory` 테이블이 있다. 컬럼 구성:
- `layer` — 1·2·3 중 하나
- `content_json` — L2 포인터 인덱스 저장
- `updated_by_agent` — 마지막으로 다시 쓴 에이전트 식별자

ERD가 이미 이 개념에 맞으므로 새 테이블은 필요 없다.

### 도메인 경계

- Self-Healing Memory는 FastAPI에만 둔다.
- Spring은 조회를 넘겨주는 프록시만 한다.
- Rust는 관여하지 않는다.
- FastAPI AI 파이프라인 안에서는 SSE를 쓰지 않는다. 예외는 Chat_V1 `POST /api/chat/v1/ask/stream` 하나로, 채팅 답변을 SSE로 흘려보낸다. 완료 통지는 polling이나 다음 요청 piggyback으로 한다. Spring Boot도 강의·알림 SseEmitter를 구현하지 않는다.

---

## 2. 네이밍 충돌 해소

### 충돌 원인

기존 API 명세서와 기능 문서에서 "Self-Healing Memory"를 **학생 복습 시스템**(취약점 출제·리포트)을 지칭하는 이름으로 써왔다. 실제 Self-Healing Memory는 AI 에이전트의 context 관리 구조다. 두 개념을 같은 이름으로 부르면 구현할 때 헷갈린다.

### 새 네이밍 원칙

| 개념 | 기존 이름 (혼동 원인) | 새 이름 | 데이터 |
|---|---|---|---|
| AI 에이전트 메모리 | 명시적 이름 없음 | **Self-Healing Memory** | `memory` 테이블 + S3/MinIO `.md` 파일 |
| 학생 취약점 추적 시스템 | Self-Healing Memory (오용) | **Spaced Review System** | `error_pattern`, `weakness_snapshot` 테이블 |

### Rename 대상 위치

실제 파일 수정은 후속 작업으로 남겼다. 대상 위치는 아래와 같다.

| 파일 | 위치 | 변경 전 | 변경 후 |
|---|---|---|---|
| `Docs/02_API/Spring_API.md` | `GET /reports/self-healing-memory` 절 | `GET /reports/self-healing-memory` | `GET /reports/spaced-review` |
| `Docs/02_API/엔드포인트_분류표.md` | 리포트/통계 카테고리 | self-healing-memory 엔드포인트 항목 | spaced-review로 rename |
| `Docs/00_개요/페이지별_기능정리.md` | "Self-Healing Memory" 표기 11건 이상 | "Self-Healing Memory 숙달도/연동/상태" | "Spaced Review 숙달도/연동/상태" |

> `error_pattern`·`weakness_snapshot` ERD 테이블명은 내부 구현명으로 이미 의미가 일치하므로 유지한다.

---

## 3. 3계층 아키텍처

| Layer | 역할 | 저장 매체 | 로드 정책 |
|---|---|---|---|
| **L1 in-context** | 현재 세션 활성 컨텍스트 (프롬프트 내부) | 프로세스 메모리 | 매 턴 조립 |
| **L2 memory.md 포인터** | ~150자/라인 포인터. "이 학생 취약 개념 → `weak_concepts_{user_id}.md`" 형식. 항상 컨텍스트에 로드 | **Postgres `memory` 테이블** `content_json` | 항상 |
| **L3 도메인 파일** | 실제 내용. 취약 개념, 풀이 이력, 선호 설명 스타일 등 | **S3/MinIO** `{user_id}/{topic}.md` | on-demand |

### L3 파일명 네이밍 규칙

```
{user_id}/weak_concepts_{user_id}.md      ← 취약 개념 목록 (②c 소크라테스·⑥ 과제 오답 시 append)
{user_id}/solve_history_{user_id}.md      ← 풀이 이력
{user_id}/style_pref_{user_id}.md         ← 선호 설명 스타일
{user_id}/session_summary_{user_id}.md    ← 세션 요약 (Daily Consolidation append)
{user_id}/learning_status_{user_id}.md    ← 학습 상태 누적 (Daily Consolidation append)
```

### 조회 흐름

```
매 턴 진입
    │
    ▼
L2 포인터만 로드 (Postgres memory 테이블 조회)
    │
    ▼
필요한 토픽 식별 (포인터에서 파일명 추출)
    │
    ▼
L3 on-demand 조회 (S3/MinIO, grep-like 검색 포함)
    │
    ▼
L1 활성 컨텍스트 조립 (프롬프트 주입)
```

항상 L2만 로드하고, L3는 해당 턴에 필요한 토픽만 읽는다. 전체 도메인 파일을 매 턴 로드하지 않는다.

---

## 4. Self-Healing 메커니즘

### L3 갱신 트리거 목록

L3 도메인 파일은 아래 이벤트가 발생할 때 갱신(append 또는 재작성)된다.

| 트리거 | 대상 파일 | 방식 |
|--------|-----------|------|
| ②c 퀴즈 소크라테스 대화 종료 후 오답/부분이해 | `weak_concepts_{user_id}.md` | WeakConceptLinker 호출 → append |
| ⑥ 과제 채점 결과 오답 (AssignmentGrader 완료 후) | `weak_concepts_{user_id}.md` | WeakConceptLinker 호출 → append |
| ③ Daily Consolidation (매일 자정 스케줄 배치) | `session_summary_{user_id}.md` + `learning_status_{user_id}.md` | DailySummarizer → 누적 append |
| autoDream 유휴 감지 (1시간 이상 유휴) | L2 전체 + L3 전체 | Dream Agent(Sonnet) → 전면 재작성 |

> 퀴즈 채점 방식: 퀴즈는 실시간 소크라테스 대화(②c)로 처리한다. 별도 배치 채점 없음. 오답/부분이해가 감지된 시점에 즉시 WeakConceptLinker를 호출해 `weak_concepts_{user_id}.md`에 append한다.

### Daily Consolidation vs autoDream — 데몬 분리

두 프로세스는 서로 독립된 데몬이다.

| 항목 | Daily Consolidation | autoDream (KAIROS) |
|------|--------------------|--------------------|
| 트리거 | **매일 자정 스케줄 배치** (Celery Beat cron) | **1시간 유휴 이벤트 감지** (Celery Beat 5분 스캔) |
| 역할 | 하루 학습 이벤트를 요약해 `session_summary` + `learning_status` append | 최근 100건 이벤트 + 기존 L2/L3를 읽어 전면 재작성 |
| 에이전트 | DailySummarizer | Dream Agent (Claude Sonnet) |
| 범위 | 요약만 (채점 없음) | L2 포인터 인덱스 + L3 도메인 파일 전체 |

Daily Consolidation은 하루 학습을 기록하는 요약 데몬이고, autoDream은 메모리를 다시 짜는 재작성 데몬이다. 둘이 동시에 돌 수 있어 파일 단위 락(S3 조건부 put)으로 충돌을 막는다.

### 동작 원리 3가지

#### 1. 자가 재작성

에이전트가 L2 포인터 인덱스와 L3 도메인 파일을 스스로 다시 쓴다. autoDream 유휴 시점에 Dream Agent(Sonnet)가 최근 학습 이벤트를 반영해 재작성한다.

#### 2. 힌트 취급과 재검증

에이전트는 memory를 기준 데이터로 믿지 않는다. 행동하기 전에 실제 원본(Postgres 학습 이벤트 로그, Qdrant 검색 결과)을 다시 확인한다. 오래된 사실이 섞이면 스스로 고친다.

#### 3. 세션 중간 자가 재작성은 범위 밖

비용 때문에 autoDream 유휴 시점 재작성만 한다. 세션 중 실시간 재작성은 하지 않는다.

---

## 5. autoDream (KAIROS) 데몬

유휴 감지 기반 백그라운드 메모리 통합 데몬이다.

| 항목 | 값 | 결정 주체 |
|---|---|---|
| 유휴 임계값 | **1시간** | 2026-04-14 사용자 확정 |
| Celery Beat 체크 주기 | **5분 간격** | 2026-04-14 사용자 확정 |
| 통합 범위 | 사용자별 **최근 100건** 학습 이벤트 | 2026-04-14 사용자 확정 |
| 통합 에이전트 모델 | **Claude Sonnet 고정** | Opus 비용 과다로 제외 |
| 완료 통지 | 폴링 또는 다음 요청 piggyback | AI 파이프라인 내 SSE 금지 정책 적용 |

### 동작 순서 5단계

```
① Redis에 마지막 활동 시각 기록
        │
        ▼
② Celery Beat가 5분마다 스캔 — 1시간 이상 유휴 사용자 식별
        │
        ▼
③ autodream.consolidate_memory task 발행
        │
        ▼
④ Sonnet이 최근 100건 이벤트 + 기존 L2/L3 읽고 재작성
        │
        ▼
⑤ Postgres memory 테이블(L2) + S3/MinIO(L3) commit
```

완료 통지는 SSE를 사용하지 않는다. 클라이언트가 폴링으로 확인하거나, 다음 AI 요청 응답에 piggyback으로 포함한다.

---

## 6. Qdrant 분리 원칙

| 시스템 | 용도 | 데이터 성격 |
|---|---|---|
| **Qdrant** | 의미 검색 (RAG) | 교재 내용, 개념 설명 등 일반 지식 |
| **Self-Healing Memory** | 구조화된 사실·상태 | 학생별 프로파일, 취약 개념 리스트, 선호 설명 스타일 |

Qdrant 임베딩은 에이전트 메모리 레이어에 넣지 않는다. RAG 검색 결과는 L1 컨텍스트를 만들 때 참고 자료로만 쓰고 L2/L3에 기록하지 않는다.

---

## 7. FastAPI 구현 체크리스트 (12항목)

> 스펙 기준 체크리스트다. 구현하지 않았다.

### 핵심 (7항목)

- [ ] **SHM-1** 유휴 감지 — Redis에 마지막 활동 시각 기록, Celery Beat가 **1시간 유휴** 사용자 스캔 (5분 간격)  
  위치: `app/autodream/idle_scheduler.py`

- [ ] **SHM-2** `autodream.consolidate_memory` Celery task — 사용자별 **최근 100건** 이벤트 + 기존 L2/L3 통합  
  위치: `app/autodream/tasks.py`

- [ ] **SHM-3** Dream Agent — **Claude Sonnet** 고정, memory.md 인덱스 + 도메인 파일 재작성  
  위치: `app/agents/dream_agent.py`

- [ ] **SHM-4** `MemoryPointerService` (L2) — `memory` 테이블 CRUD, ~150자/라인 포인터 규격 강제  
  위치: `app/memory/pointer_service.py`

- [ ] **SHM-5** `MemoryFileStore` (L3) — S3/MinIO `.md` 파일 read/write/list (grep-like 검색 포함)  
  위치: `app/memory/file_store.py`

- [ ] **SHM-6** `MemoryVerifier` — hint 취급 + 행동 전 원본 재검증 로직 (memory와 실제 DB/Qdrant 비교)  
  위치: `app/memory/verifier.py`

- [ ] **SHM-7** `ContextBuilder` — 매 턴 L2 포인터만 로드 → 필요 시 on-demand L3 조회 → L1 활성 컨텍스트 조립  
  위치: `app/agents/context_builder.py`

### API (2항목)

- [ ] **SHM-8** Agent 호출 미들웨어 — 튜터 AI 요청 진입점에서 자동 memory injection + verifier 호출  
  위치: `app/api/middleware/memory_injector.py`

- [ ] **SHM-9** 관리용 디버그 API (개발 환경 전용) — `GET /admin/memory/{user_id}` 상태 조회  
  위치: `app/api/routes/admin.py`

### 관측·정책 (3항목)

- [ ] **SHM-10** Dream 비용 per user, task 성공률, memory 재검증 불일치 비율 메트릭  
  위치: `app/monitoring/`

- [ ] **SHM-11** L3 S3 파일 보존/삭제 정책 (개인정보·학생 계정 만료 대응)  
  위치: `docs/data-retention-policy.md`

- [ ] **SHM-12** `memory.md` 포인터 용량 상한 + GC (너무 커지면 가장 오래된 포인터 삭제)  
  위치: `app/memory/gc.py`

---

> Spaced Review System 범위 (이 스펙 밖)  
> 기존 계획에서 뺀 항목이다.
> - ~~취약점 기반 맞춤 출제 API~~ → Spaced Review System 범위
> - ~~학부모/교사 리포트 시각화~~ → Spaced Review System 범위

---

## 8. 제약·결정 기록

| 항목 | 내용 | 결정일 |
|---|---|---|
| 도메인 경계 | FastAPI만. Spring은 프록시, Rust 관여 없음 | — |
| SSE 금지 | 완료 통지 불가. 폴링 또는 piggyback 사용 | 2026-04-14 |
| Python 버전 | Python 3.12 | — |
| FastAPI 버전 | 0.115.5 | — |
| Celery 버전 | 5.4.0 | — |
| 세션 중간 자가 재작성 | 이번 범위 제외 (비용). autoDream 유휴 시점만 | — |
| 퀴즈 채점 방식 | 실시간 소크라테스 대화(②c) 처리. 별도 배치 없음 | 2026-04-14 |
| Daily Consolidation | 매일 자정 스케줄 배치, 요약만 (채점 X). autoDream과 별개 데몬 | 2026-04-14 |
| 유휴 임계값 | 1시간 | 2026-04-14 사용자 확정 |
| 통합 범위 | 최근 100건 학습 이벤트 | 2026-04-14 사용자 확정 |
| 에이전트 모델 | Claude Sonnet 고정 | 2026-04-14 사용자 확정 |
| 네이밍 분리 | Self-Healing Memory / Spaced Review System | 2026-04-14 |

---

## 9. 별도 스펙으로 빠진 것 — Spaced Review System

학생 복습 시스템은 이 스펙 범위 밖이다. 아래 항목은 별도 스펙에서 다룬다.

#### 데이터

- `error_pattern` 테이블 — 오답 패턴 기록
- `weakness_snapshot` 테이블 — 취약 개념 스냅샷

#### 기능 목록

- 취약점 기반 맞춤 출제 API
- 학부모·교사 리포트 시각화

> 2026-04-20 결정: SM-2 망각곡선 기반 복습 간격 스케줄링(Ebbinghaus/SM-2 알고리즘)은 전면 제거되었다. `review_schedule` 테이블 및 관련 API(`GET /calendar/review-schedule`)도 함께 삭제되었다. Spaced Review System 스펙에서 이 기능은 더 이상 다루지 않는다.

#### API rename 필요

- 기존 `GET /reports/self-healing-memory`는 Spaced Review System에 속하는 엔드포인트이므로 `GET /reports/spaced-review`로 rename 대상이다 (섹션 2 참조).

Spaced Review System 스펙은 후속 작업으로 남겼다.
