# 일과 (ilkwa) API 명세서

> AI 1:1 과외 플랫폼 API 명세서  
> 버전: 2.8.0 | 최종 수정일: 2026-05-25

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [공통 규격](#2-공통-규격)
3. [인증/사용자 API (Spring Boot)](#3-인증사용자-api)
4. [AI 선생님 API (Spring Boot + FastAPI)](#4-ai-선생님-api)
4b. [AI 튜터 API (Spring Boot + FastAPI)](#4b-ai-튜터-api)
5. [과외/커리큘럼 API (Spring Boot + FastAPI)](#5-과외커리큘럼-api)
6. [과제 API (FastAPI)](#6-과제-api)
7. [캘린더 API (Spring Boot)](#7-캘린더-api)
8. [리포트/통계 API](#8-리포트통계-api)
9. [모의고사 API (FastAPI)](#9-모의고사-api)
10. [구독/결제 API (Spring Boot)](#10-구독결제-api)
11. [알림 API](#11-알림-api)
12. [텔레그램 웹훅 (FastAPI)](#12-텔레그램-웹훅)
13. [대시보드 API](#13-대시보드-api)
14. [내부 엔드포인트 (Spring↔FastAPI)](#14-내부-엔드포인트-springfastapi)
15. [관리자 전용 API](#15-관리자-전용-api-슈퍼-관리자-한정)
16. [AI 코인 API (Spring Boot)](#16-ai-코인-api)

---

## 1. 아키텍처 개요

### 서버 구성

| 서버 | 기술 스택 | 역할 | Base URL |
|------|-----------|------|----------|
| Core Server | Spring Boot 3.x (Java 21) | 인증, 사용자, CRUD, 결제, 구독, 캘린더, 과외 소유권·상태 전이 REST API | `https://api.ilkwa.com` |
| AI Server | FastAPI (Python 3.12) | 커리큘럼 생성, 강의 생성, 과제 채점, OCR(OCR_V1), 추천, 텔레그램 봇 | `https://ai.ilkwa.com` |

### 기술 스택

| 항목 | 기술 |
|------|------|
| 프론트엔드 | React 18 + TypeScript (strict) + Vite + React Router v6 (SPA) + Tailwind CSS + shadcn/ui + Framer Motion(현 Motion). 패턴: shadcn/ui 골격 + Tailwind/Motion 시각 효과 |
| 인증 | JWT (Access + Refresh Token) |
| 결제 | 토스페이먼츠 |
| 외부 연동 | Telegram Bot API, Google OAuth, Kakao OAuth |
| 저장소 | AWS S3 + CloudFront CDN |
| AI 모델 | Claude Opus 4.6 (커리큘럼 설계), Qwen3.6-27B FP8·vLLM (강의 내용 생성, Modal GPU), Qwen3-TTS(H100)·Whisper V3 Turbo·BGE-M3·reranker (Modal GPU), Claude Sonnet 4.6 (검증·채팅·채점, 과제 이미지 VLM), OCR_V1 (Marker + 품질 게이트 + MinerU 재추출 + Kanana-2 교정), LangGraph (FastAPI). 폴백은 커리큘럼 외 텍스트 작업 전부 Modal → Claude |
| 검색 | RAG + Qdrant 벡터 DB |
| 데이터베이스 | PostgreSQL 16 (RDS) |
| 캐시 | Redis 7 |
| 작업 큐 | Celery Beat |

### 아키텍처 원칙

FastAPI 한 서버에 AI 진입점을 모으고 LangGraph로 확장한다.

- FastAPI가 모든 AI 진입점을 맡는다. OCR, Claude 채점, TTS 호출, 임베딩, LangGraph 에이전트를 FastAPI 레이어에 모은다.
- 교재 OCR은 OCR_V1 한 경로다. Marker로 1차 추출하고 품질 게이트에 걸린 페이지만 MinerU로 다시 뽑아 Kanana-2로 교정한다. 과제 이미지·PDF는 Sonnet 4.6 VLM이 직접 읽는다.
- SSE는 쓰지 않는다. ②c 소크라테스 토큰 스트리밍은 FastAPI StreamingResponse로 처리한다(SSE 프로토콜 아님). 예외는 FastAPI Chat_V1 `POST /api/chat/v1/ask/stream` 하나로, 채팅 답변을 SSE로 흘려보낸다.
- Modal GPU는 FastAPI가 호출하는 외부 compute runtime이다. Qwen3.6-27B FP8·vLLM, Qwen3-TTS, Whisper V3 Turbo, BGE-M3·reranker를 돌린다.

---

## 2. 공통 규격

### 2.1 Base URL

```
Production Core:   https://api.ilkwa.com/api
Production AI:     https://ai.ilkwa.com/api
Development:       http://localhost:{port}/api
```

### 2.2 인증 헤더

모든 인증 필요 API에 아래 헤더를 포함해야 한다.

```http
Authorization: Bearer {access_token}
Content-Type: application/json
```

- **Access Token**: JWT, 만료 30분 (`1800초`)
- **Refresh Token**: HTTP-Only Secure Cookie (`ilkwa_refresh`), 만료 14일
- **공개 엔드포인트** (인증 불필요): `/api/auth/refresh`, `/api/auth/login/google`, `/api/auth/login/kakao`, `/api/subscription/plans`

### 2.3 공통 응답 래퍼

모든 API 응답은 아래 래퍼 구조를 따른다.

#### 성공 응답

```json
{
  "success": true,
  "data": { ... },
  "message": "요청이 성공적으로 처리되었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| success | boolean | 요청 성공 여부 (항상 `true`) |
| data | object / array / null | 응답 데이터 본문 |
| message | string | 사용자에게 표시할 메시지 (선택) |
| timestamp | string (ISO 8601) | 서버 응답 시각 |

#### 에러 응답

```json
{
  "success": false,
  "error": {
    "code": "AUTH_001",
    "message": "인증 토큰이 만료되었습니다.",
    "details": null
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| success | boolean | 항상 `false` |
| error.code | string | 에러 코드 (카테고리_번호 형식) |
| error.message | string | 사람이 읽을 수 있는 에러 설명 |
| error.details | object / null | 필드별 유효성 검증 오류 등 추가 정보 |
| timestamp | string (ISO 8601) | 서버 응답 시각 |

#### 유효성 검증 에러 (details 포함)

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_001",
    "message": "입력값 유효성 검증에 실패했습니다.",
    "details": {
      "email": "올바른 이메일 형식이 아닙니다.",
      "password": "비밀번호는 8자 이상이어야 합니다."
    }
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

### 2.4 에러 코드 체계

| 코드 범위 | 카테고리 | 에러 코드 상세 |
|-----------|----------|---------------|
| `AUTH_001` | 인증/인가 | 토큰 만료 |
| `AUTH_002` | 인증/인가 | 권한 없음 (해당 리소스에 대한 접근 권한이 없음) |
| `AUTH_003` | 인증/인가 | 리프레시 토큰이 유효하지 않거나 만료됨 |
| `AUTH_004` | 인증/인가 | 이미 사용된 리프레시 토큰 (재사용 공격 탐지) |
| `AUTH_005` | 인증/인가 | Google 인증 실패 |
| `AUTH_006` | 인증/인가 | Kakao 인증 실패 |
| `AUTH_007` | 인증/인가 | OAuth 이메일 제공 동의 필요 |
| `USER_001` | 사용자 | 사용자를 찾을 수 없음 |
| `USER_002` | 사용자 | 이메일 중복 |
| `USER_004` | 사용자 | 온보딩 미완료 |
| `USER_005` | 사용자 | 텔레그램 연동 실패 |
| `TUTOR_001` | 과외/강의 | 과외를 찾을 수 없음 |
| `TUTOR_002` | 과외/강의 | 커리큘럼 미생성 (아직 생성 중) |
| `TUTOR_003` | 과외/강의 | 강의를 찾을 수 없음 |
| `TUTOR_004` | 과외/강의 | PDF 분석 실패 |
| `TUTOR_005` | 과외/강의 | 실시간형 상태 조회 실패 |
| `ASSIGN_001` | 과제 | 과제를 찾을 수 없음 |
| `ASSIGN_002` | 과제 | 제출 기한 초과 |
| `ASSIGN_003` | 과제 | 이미 제출됨 (중복 제출 불가) |
| `ASSIGN_004` | 과제 | 채점 중 (아직 결과 없음) |
| `PAY_001` | 결제/구독 | 결제 실패 (토스페이먼츠 오류) |
| `PAY_002` | 결제/구독 | 구독 없음 |
| `PAY_003` | 결제/구독 | 포인트 부족 |
| `PAY_004` | 결제/구독 | 이미 해당 플랜 구독 중 |
| `PAY_005` | 결제/구독 | 결제 금액 불일치 |
| `AI_001` | AI 처리 | AI 생성 실패 |
| `AI_002` | AI 처리 | 모델 응답 시간 초과 |
| `AI_003` | AI 처리 | OCR 인식 실패 |
| `AI_004` | AI 처리 | TTS 생성 실패 |
| `AI_005` | AI 처리 | Claude API 호출 실패 (AI 생성 엔드포인트에서 Claude API 타임아웃/에러 반환 시, HTTP 503) |
| `TUTOR_006` | 과외/강의 | 튜터링 세션 만료 (세션 장시간 미응답 → 자동 종료 시, HTTP 410) |
| `PLAN_001` | 플랜 권한 | Pro 이상 전용 기능 (subscription_status IN ('pro','premium') 필요) |
| `QUOTA_001` | 쿼터 초과 | 강의 생성 한도 초과 (플랜별 max_courses 기준) |
| `QUOTA_002` | 쿼터 초과 | 슬라이드 접근 한도 초과 (Free 플랜 2개 제한) |
| `VALIDATION_001` | 유효성 | 입력값 유효성 검증 실패 |
| `SYSTEM_001` | 시스템 | 내부 서버 오류 |
| `SYSTEM_002` | 시스템 | 서비스 점검 중 |
| `SYSTEM_003` | 시스템 | 요청 제한 초과 (Rate Limit) |

### 2.5 페이지네이션

모든 목록 조회 API는 아래 쿼리 파라미터를 지원한다.

#### 요청 쿼리 파라미터

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| page | integer | X | 0 | 페이지 번호 (0부터 시작) |
| size | integer | X | 20 | 페이지 당 항목 수 (최대 100) |
| sort | string | X | `createdAt,desc` | 정렬 기준 (필드명,방향) |

```http
GET /api/resource?page=0&size=20&sort=createdAt,desc
```

#### 응답 페이지네이션 래퍼

```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| content | array | 현재 페이지의 데이터 배열 |
| page | integer | 현재 페이지 번호 |
| size | integer | 페이지 당 항목 수 |
| totalElements | integer | 전체 항목 수 |
| totalPages | integer | 전체 페이지 수 |
| hasNext | boolean | 다음 페이지 존재 여부 |
| hasPrevious | boolean | 이전 페이지 존재 여부 |

### 2.6 공통 HTTP 상태 코드

| 코드 | 의미 | 사용 상황 |
|------|------|----------|
| 200 | OK | 조회/수정 성공 |
| 201 | Created | 리소스 생성 성공 |
| 204 | No Content | 삭제 성공 (응답 본문 없음) |
| 400 | Bad Request | 요청 형식 오류, 유효성 검증 실패 |
| 401 | Unauthorized | 인증 토큰 없음/만료/유효하지 않음 |
| 403 | Forbidden | 권한 없음 (프리미엄 전용, 다른 사용자 리소스 등) |
| 404 | Not Found | 리소스를 찾을 수 없음 |
| 409 | Conflict | 중복 리소스 (이메일 중복 등) |
| 429 | Too Many Requests | 요청 제한 초과 (1분 당 60회) |
| 500 | Internal Server Error | 서버 내부 오류 |
| 503 | Service Unavailable | AI 모델 과부하 또는 서비스 점검 중 |

### 2.7 파일 업로드 규격

파일 업로드가 필요한 API는 `multipart/form-data` 형식을 사용한다.

| 제한 사항 | 값 |
|-----------|-----|
| 최대 파일 크기 (PDF) | 1.5GB |
| 최대 파일 크기 (이미지) | 10MB |
| 최대 파일 크기 (오디오) | 25MB |
| 허용 PDF 형식 | `.pdf` |
| 허용 이미지 형식 | `.jpg`, `.jpeg`, `.png`, `.webp` |
| 허용 오디오 형식 | `.webm`, `.wav`, `.mp3`, `.m4a` |

### 2.8 Rate Limiting

| 엔드포인트 그룹 | 제한 |
|----------------|------|
| 인증 API (`/auth/*`) | 10회/분 |
| AI 생성 API (`/tutoring`, `/assignments/generate`, `/exam-forges`) | 5회/분 |
| 일반 API | 60회/분 |
| 실시간형 상태 조회 API | 30회/분/사용자 |

Rate Limit 초과 시 응답:
```json
{
  "success": false,
  "error": {
    "code": "SYSTEM_003",
    "message": "요청 제한을 초과했습니다. 잠시 후 다시 시도해주세요.",
    "details": {
      "retryAfter": 30
    }
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

---

## 3. 인증/사용자 API

> 서버: Spring Boot (Core)  
> Base URL: `https://api.ilkwa.com/api`
>
> 인증 방식: 구글/카카오 소셜 로그인 전용 (2026-04-20 확정). 자체 이메일/비밀번호 로그인과 회원가입 엔드포인트(`/auth/login`, `/auth/signup`)는 삭제됨. 소셜 최초 로그인 시 계정이 자동 생성된다.

---

### POST /auth/login/google

Google OAuth ID Token으로 로그인 또는 자동 가입한다. 기존 계정이 있으면 로그인, 없으면 자동으로 회원가입 후 로그인된다.

요청 형식: `application/json`

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| idToken | string | O | Google OAuth에서 발급받은 ID Token |

#### 요청 예시

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.accessToken | string | JWT 액세스 토큰 |
| data.userId | string (ULID) | 사용자 고유 ID (`usr_` prefix) |
| data.email | string | Google 계정 이메일 |
| data.nickname | string | 닉네임 (신규 시 이메일 앞부분으로 자동 설정) |
| data.onboardingCompleted | boolean | 온보딩 완료 여부 (신규 가입 시 항상 `false`) |
| data.subscriptionStatus | string | 구독 상태 (`"free"` / `"premium"`) |

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "userId": "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV",
    "email": "user@gmail.com",
    "nickname": "user",
    "onboardingCompleted": false,
    "subscriptionStatus": "free"
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

> 참고: Refresh Token은 응답 본문에 포함되지 않는다. HTTP-Only Cookie `ilkwa_refresh`로 자동 설정된다.  
> 쿠키 보안 설정은 서버 환경변수(`AUTH_COOKIE_SECURE`, `AUTH_COOKIE_SAME_SITE`)로 제어된다.

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | ID Token이 비어있음 |
| 401 | AUTH_005 | Google ID Token 검증 실패 (만료 또는 변조) |
| 401 | AUTH_007 | Google 계정의 이메일 제공 동의 필요 |
| 409 | USER_002 | 이미 다른 방법으로 가입된 이메일 |

---

### POST /auth/login/kakao

Kakao 인가 코드로 로그인 또는 자동 가입한다.

요청 형식: `application/json`

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| authorizationCode | string | O | Kakao OAuth 인가 코드 |

> 참고: Redirect URI는 서버 환경변수(`KAKAO_REDIRECT_URI`)로 고정 관리한다. 프론트에서 전달하지 않는다.

#### 요청 예시

```json
{
  "authorizationCode": "Ks_d8f9a7sd6f8a7sd6f8a7sd6f8a7"
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.accessToken | string | JWT 액세스 토큰 |
| data.userId | string (ULID) | 사용자 고유 ID (`usr_` prefix) |
| data.email | string | Kakao 계정 이메일 |
| data.nickname | string | 닉네임 (신규 시 이메일 앞부분으로 자동 설정) |
| data.onboardingCompleted | boolean | 온보딩 완료 여부 (신규 가입 시 항상 `false`) |
| data.subscriptionStatus | string | 구독 상태 (`"free"` / `"premium"`) |

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "userId": "usr_01ARZ3NDEKTSV4RRFFQ69G5FAV",
    "email": "user@kakao.com",
    "nickname": "user",
    "onboardingCompleted": false,
    "subscriptionStatus": "free"
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

> 참고: Refresh Token은 응답 본문에 포함되지 않는다. HTTP-Only Cookie `ilkwa_refresh`로 자동 설정된다.

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 인가 코드 누락 |
| 401 | AUTH_006 | Kakao 인가 코드 검증 실패 (만료 또는 유효하지 않음) |
| 401 | AUTH_007 | Kakao 계정의 이메일 제공 동의 필요 |
| 409 | USER_002 | 이미 다른 방법으로 가입된 이메일 |

---

### POST /auth/refresh

Refresh Token으로 Access Token을 갱신한다.

요청 형식: 본문 없음 (HTTP-Only Cookie `ilkwa_refresh`에서 Refresh Token 자동 추출)

인증: 불필요 (Cookie 기반)

#### 요청 예시

```http
POST /api/auth/refresh HTTP/1.1
Host: api.ilkwa.com
Cookie: ilkwa_refresh=eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.accessToken | string | 새로 발급된 JWT 액세스 토큰 |
| data.tokenType | string | 토큰 타입 (`"Bearer"`) |
| data.expiresIn | integer | 액세스 토큰 만료 시간 (초, 1800) |

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...(새 토큰)",
    "tokenType": "Bearer",
    "expiresIn": 1800
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

> 참고: 새로운 Refresh Token도 Cookie로 재발급된다 (Refresh Token Rotation).

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_003 | Refresh Token이 유효하지 않거나 만료됨 |
| 401 | AUTH_004 | 이미 사용된 Refresh Token (5분 내 재사용 시 탐지) |

---

### GET /users/me

현재 로그인한 사용자의 프로필을 조회한다.

요청 형식: 쿼리 파라미터 없음

인증: 필요 (`Authorization: Bearer {access_token}`)

#### 요청 예시

```http
GET /api/users/me HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 사용자 고유 ID |
| data.email | string | 사용자 이메일 |
| data.nickname | string | 닉네임 |
| data.profileImageUrl | string / null | 프로필 이미지 URL |
| data.grade | string / null | 학년 (예: `"고등학교 2학년"`, `"대학교 3학년"`) |
| data.major | string / null | 전공 (대학생인 경우) |
| data.education | string / null | 학교명 |
| data.interests | string[] | 관심 과목 배열 |
| data.learningGoal | string / null | 학습 목표 (예: `"수능 대비"`) |
| data.preferredTone | string / null | 선호 말투 (`"존댓말"`, `"반말"`, `"격려형"`, `"도전형"`) |
| data.subscriptionTier | string | 구독 등급 (`"FREE"` / `"PRO"` / `"PREMIUM"` / `"EXPIRED"`) |
| data.subscriptionExpiresAt | string (ISO 8601) / null | 구독 만료일 (FREE는 null) |
| data.telegramLinked | boolean | 텔레그램 연동 여부 |
| data.onboardingCompleted | boolean | 온보딩 완료 여부 |
| data.createdAt | string (ISO 8601) | 계정 생성일 |
| data.settings | object | 사용자 설정 |
| data.settings.highContrast | boolean | 고대비 모드 |
| data.settings.keyboardNavigation | boolean | 키보드 네비게이션 활성화 |
| data.settings.notifications | object | 알림 설정 |
| data.settings.notifications.lesson | boolean | 강의 알림 |
| data.settings.notifications.assignment | boolean | 과제 알림 |
| data.settings.notifications.marketing | boolean | 마케팅 알림 |

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "nickname": "학습왕",
    "profileImageUrl": "https://cdn.ilkwa.com/profiles/550e8400.jpg",
    "grade": "고등학교 2학년",
    "major": null,
    "education": "서울고등학교",
    "interests": ["수학", "물리"],
    "learningGoal": "수능 대비",
    "preferredTone": "격려형",
    "subscriptionTier": "PREMIUM",
    "subscriptionExpiresAt": "2026-05-12T00:00:00Z",
    "telegramLinked": true,
    "onboardingCompleted": true,
    "createdAt": "2026-03-01T09:00:00Z",
    "settings": {
      "highContrast": false,
      "keyboardNavigation": false,
      "notifications": {
        "lesson": true,
        "assignment": true,
        "marketing": false
      }
    }
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 401 | AUTH_003 | 유효하지 않은 토큰 |

---

### PUT /users/me

프로필 정보를 수정한다. 변경할 필드만 포함하면 된다 (Partial Update).

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| nickname | string | X | 닉네임 (2~20자) |
| profileImageUrl | string | X | 프로필 이미지 URL (S3 업로드 후 URL) |
| grade | string | X | 학년 (예: `"고등학교 3학년"`) |
| major | string | X | 전공 |
| education | string | X | 학교명 |
| interests | string[] | X | 관심 과목 배열 |
| learningGoal | string | X | 학습 목표 |
| preferredTone | string | X | 선호 말투 (`"존댓말"` / `"반말"` / `"격려형"` / `"도전형"`) |

#### 요청 예시

```json
{
  "nickname": "수학마스터",
  "grade": "고등학교 3학년",
  "interests": ["수학", "물리", "화학"],
  "learningGoal": "의대 입시 대비"
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

수정된 전체 사용자 프로필 객체 반환 (`GET /users/me`와 동일한 구조).

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "nickname": "수학마스터",
    "profileImageUrl": "https://cdn.ilkwa.com/profiles/550e8400.jpg",
    "grade": "고등학교 3학년",
    "major": null,
    "education": "서울고등학교",
    "interests": ["수학", "물리", "화학"],
    "learningGoal": "의대 입시 대비",
    "preferredTone": "격려형",
    "subscriptionTier": "PREMIUM",
    "subscriptionExpiresAt": "2026-05-12T00:00:00Z",
    "telegramLinked": true,
    "onboardingCompleted": true,
    "createdAt": "2026-03-01T09:00:00Z",
    "settings": {
      "highContrast": false,
      "keyboardNavigation": false,
      "notifications": {
        "lesson": true,
        "assignment": true,
        "marketing": false
      }
    }
  },
  "message": "프로필이 수정되었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 닉네임 길이 초과, 유효하지 않은 학년 형식 등 |
| 401 | AUTH_001 | 토큰 만료 |
| 409 | USER_002 | 닉네임 중복 |

---

### POST /users/onboarding

최초 가입 후 온보딩 정보를 입력한다. 온보딩 완료 후 추천 선생님을 반환한다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| interests | string[] | O | 관심 과목 배열 (최소 1개, 예: `["수학", "영어"]`) |
| grade | string | O | 학년 (예: `"고등학교 2학년"`, `"대학교 1학년"`) |
| learningGoal | string | O | 학습 목표 (예: `"수능 대비"`, `"내신 향상"`, `"자격증 준비"`) |
| preferredTone | string | O | 선호 말투 (`"존댓말"` / `"반말"` / `"격려형"` / `"도전형"`) |

#### 요청 예시

```json
{
  "interests": ["수학", "과학", "영어"],
  "grade": "고등학교 2학년",
  "learningGoal": "수능 대비",
  "preferredTone": "격려형"
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.onboardingCompleted | boolean | 온보딩 완료 여부 (항상 `true`) |
| data.recommendedTeachers | array | 추천 프리셋 선생님 목록 |
| data.recommendedTeachers[].id | string | 선생님 ID |
| data.recommendedTeachers[].name | string | 선생님 이름 |
| data.recommendedTeachers[].emoji | string | 선생님 대표 이모지 |
| data.recommendedTeachers[].specialties | string[] | 전문 과목 |
| data.recommendedTeachers[].matchReason | string | 추천 이유 |

```json
{
  "success": true,
  "data": {
    "onboardingCompleted": true,
    "recommendedTeachers": [
      {
        "id": "preset-friendly",
        "name": "다정한 수리샘",
        "emoji": "🤗",
        "specialties": ["수학", "과학"],
        "matchReason": "수학과 과학에 관심이 있으시군요! 격려하는 스타일의 수리샘이 잘 맞을 거예요."
      },
      {
        "id": "preset-strict",
        "name": "엄격한 진쌤",
        "emoji": "📐",
        "specialties": ["국어", "사회"],
        "matchReason": "체계적인 학습을 원한다면 진쌤도 좋은 선택이에요."
      }
    ]
  },
  "message": "온보딩이 완료되었습니다. 추천 선생님을 확인해보세요!",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락 또는 관심 과목 빈 배열 |
| 401 | AUTH_001 | 토큰 만료 |
| 409 | USER_004 | 이미 온보딩이 완료된 사용자 |

---

### POST /users/telegram/link

텔레그램 계정을 연동한다. 프론트엔드에서 토큰을 생성하고 사용자가 `t.me/ilgwa_EZ_Team_bot?start={token}` 링크를 통해 텔레그램 봇과 대화를 시작하면 자동 연동된다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| - | - | - | 본문 없음. 서버에서 연동 토큰을 생성하여 반환합니다. |

#### 요청 예시

```http
POST /api/users/telegram/link HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.linkToken | string | 텔레그램 연동용 일회성 토큰 (5분 만료) |
| data.telegramBotUrl | string | 텔레그램 봇 딥링크 URL |
| data.expiresAt | string (ISO 8601) | 토큰 만료 시각 |

```json
{
  "success": true,
  "data": {
    "linkToken": "ILKWA-ABCD-1234-EFGH",
    "telegramBotUrl": "https://t.me/ilgwa_EZ_Team_bot?start=ILKWA-ABCD-1234-EFGH",
    "expiresAt": "2026-04-12T09:35:00Z"
  },
  "message": "텔레그램 봇 링크를 열어 연동을 완료해주세요.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 409 | USER_005 | 이미 텔레그램이 연동된 계정 |

---

### GET /users/telegram/status

텔레그램 연동 상태를 확인한다.

요청 형식: 쿼리 파라미터 없음

인증: 필요

#### 요청 예시

```http
GET /api/users/telegram/status HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.linked | boolean | 연동 여부 |
| data.telegramUsername | string / null | 텔레그램 사용자명 (@xxx) |
| data.telegramChatId | string / null | 텔레그램 채팅 ID (내부용) |
| data.linkedAt | string (ISO 8601) / null | 연동 일시 |
| data.lastActiveAt | string (ISO 8601) / null | 마지막 텔레그램 활동 일시 |

```json
{
  "success": true,
  "data": {
    "linked": true,
    "telegramUsername": "@student_user",
    "telegramChatId": "123456789",
    "linkedAt": "2026-04-01T12:00:00Z",
    "lastActiveAt": "2026-04-12T08:30:00Z"
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |

---

### PUT /users/settings

사용자 설정을 변경한다. 변경할 필드만 포함하면 된다 (Partial Update).

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| highContrast | boolean | X | 고대비 모드 활성화 |
| keyboardNavigation | boolean | X | 키보드 네비게이션 활성화 |
| notifications | object | X | 알림 설정 객체 |
| notifications.lesson | boolean | X | 강의 알림 수신 |
| notifications.assignment | boolean | X | 과제 알림 수신 |
| notifications.marketing | boolean | X | 마케팅 알림 수신 |

#### 요청 예시

```json
{
  "highContrast": true,
  "keyboardNavigation": true,
  "notifications": {
    "lesson": true,
    "assignment": true,
    "marketing": false
  }
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.highContrast | boolean | 고대비 모드 |
| data.keyboardNavigation | boolean | 키보드 네비게이션 |
| data.notifications | object | 알림 설정 |
| data.notifications.lesson | boolean | 강의 알림 |
| data.notifications.assignment | boolean | 과제 알림 |
| data.notifications.marketing | boolean | 마케팅 알림 |

```json
{
  "success": true,
  "data": {
    "highContrast": true,
    "keyboardNavigation": true,
    "notifications": {
      "lesson": true,
      "assignment": true,
      "marketing": false
    }
  },
  "message": "설정이 변경되었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 유효하지 않은 설정 값 |
| 401 | AUTH_001 | 토큰 만료 |

---

## 4. AI 선생님 API

> 서버: Spring Boot (CRUD) + FastAPI (AI 기능)  
> Base URL: Core `https://api.ilkwa.com/api` / AI `https://ai.ilkwa.com/api`

---

### GET /teachers/presets

기본 제공되는 프리셋 선생님 3종을 조회한다.

요청 형식: 쿼리 파라미터 없음

인증: 필요

서버: Core

#### 요청 예시

```http
GET /api/teachers/presets HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data | array | 프리셋 선생님 배열 (3개) |
| data[].id | string | 선생님 고유 ID |
| data[].name | string | 선생님 이름 |
| data[].emoji | string | 대표 이모지 |
| data[].personality | string | 성격 설명 |
| data[].tone | string | 말투 스타일 (`"존댓말"` / `"반말"` / `"격려형"` / `"도전형"`) |
| data[].specialties | string[] | 전문 과목 배열 |
| data[].description | string | 상세 소개 |
| data[].avatarUrl | string | 프로필 이미지 URL |
| data[].voiceType | string | 기본 음성 타입 |
| data[].isPreset | boolean | 프리셋 여부 (항상 `true`) |
| data[].isPremium | boolean | 프리미엄 전용 여부 (항상 `false`) |

```json
{
  "success": true,
  "data": [
    {
      "id": "preset-friendly",
      "name": "다정한 수리샘",
      "emoji": "🤗",
      "personality": "친근하고 격려하는 스타일. 학생의 작은 성장도 칭찬해주며, 틀려도 괜찮다고 다독여줍니다.",
      "tone": "격려형",
      "specialties": ["수학", "과학"],
      "description": "수학과 과학을 쉽고 재미있게 가르쳐요. 어려운 개념도 일상의 예시로 풀어드립니다.",
      "avatarUrl": "https://cdn.ilkwa.com/teachers/friendly.png",
      "voiceType": "warm-female",
      "isPreset": true,
      "isPremium": false
    },
    {
      "id": "preset-strict",
      "name": "엄격한 진쌤",
      "emoji": "📐",
      "personality": "체계적이고 꼼꼼한 스타일. 정확한 표현을 중시하며, 논리적 사고를 유도합니다.",
      "tone": "존댓말",
      "specialties": ["국어", "사회"],
      "description": "정확하고 체계적인 학습을 지향합니다. 기본부터 탄탄하게 쌓아갈 수 있도록 도와드립니다.",
      "avatarUrl": "https://cdn.ilkwa.com/teachers/strict.png",
      "voiceType": "calm-male",
      "isPreset": true,
      "isPremium": false
    },
    {
      "id": "preset-fun",
      "name": "유쾌한 빛나쌤",
      "emoji": "✨",
      "personality": "유머있고 재미있는 스타일. 밈과 유행어를 활용해 쉽게 설명합니다.",
      "tone": "반말",
      "specialties": ["영어", "코딩"],
      "description": "영어와 코딩을 재미있게! 게임과 퀴즈를 활용한 인터랙티브 수업을 진행합니다.",
      "avatarUrl": "https://cdn.ilkwa.com/teachers/fun.png",
      "voiceType": "bright-female",
      "isPreset": true,
      "isPremium": false
    }
  ],
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |

---

### POST /teachers

커스텀 AI 선생님 캐릭터를 생성한다. (Premium 전용)

요청 형식: `application/json`

인증: 필요 (Premium 구독 필수)

서버: Core

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | O | 선생님 이름 (2~20자) |
| personality | string | O | 성격 설명 (최대 200자) |
| toneStyle | string | O | 말투 스타일 (`"존댓말"` / `"반말"` / `"격려형"` / `"도전형"`) |
| specialties | string[] | O | 전문 과목 배열 (최소 1개) |
| teachingStyle | string | X | 교수법 (`"lecture"`: 강의형 / `"socratic"`: 소크라테스식, 기본값: `"lecture"`) |
| description | string | X | 상세 소개 (최대 500자) |
| avatarConfig | object | X | 아바타 커스터마이징 설정 |
| avatarConfig.baseStyle | string | X | 아바타 스타일 (`"anime"` / `"realistic"` / `"cartoon"`) |
| avatarConfig.hairColor | string | X | 머리 색상 (hex, 예: `"#3B3B3B"`) |
| avatarConfig.eyeColor | string | X | 눈 색상 (hex) |

#### 요청 예시

```json
{
  "name": "나만의 쌤",
  "personality": "차분하면서도 열정적인. 학생이 스스로 답을 찾도록 유도합니다.",
  "toneStyle": "격려형",
  "specialties": ["물리", "화학"],
  "teachingStyle": "socratic",
  "description": "물리와 화학의 원리를 실생활 예시로 쉽게 풀어드립니다.",
  "avatarConfig": {
    "baseStyle": "anime",
    "hairColor": "#3B3B3B",
    "eyeColor": "#2196F3"
  }
}
```

응답 형식: `application/json`

##### 성공 응답 (201 Created)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 생성된 선생님 ID |
| data.name | string | 선생님 이름 |
| data.personality | string | 성격 설명 |
| data.toneStyle | string | 말투 스타일 |
| data.specialties | string[] | 전문 과목 |
| data.teachingStyle | string | 교수법 |
| data.description | string | 상세 소개 |
| data.avatarUrl | string | 생성된 아바타 이미지 URL |
| data.isPreset | boolean | 프리셋 여부 (항상 `false`) |
| data.isPremium | boolean | 프리미엄 여부 (항상 `true`) |
| data.createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "id": "883g1733-h52e-74g7-d049-779988773333",
    "name": "나만의 쌤",
    "personality": "차분하면서도 열정적인. 학생이 스스로 답을 찾도록 유도합니다.",
    "toneStyle": "격려형",
    "specialties": ["물리", "화학"],
    "teachingStyle": "socratic",
    "description": "물리와 화학의 원리를 실생활 예시로 쉽게 풀어드립니다.",
    "avatarUrl": "https://cdn.ilkwa.com/teachers/custom/883g1733.png",
    "isPreset": false,
    "isPremium": true,
    "createdAt": "2026-04-12T09:00:00Z"
  },
  "message": "커스텀 선생님이 생성되었습니다.",
  "timestamp": "2026-04-12T09:00:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락, 이름 길이 초과 등 |
| 401 | AUTH_001 | 토큰 만료 |
| 403 | PREMIUM_001 | 프리미엄 구독 필요 |

---

### GET /teachers

내가 사용할 수 있는 선생님 목록 (프리셋 + 커스텀) 을 조회한다.

요청 형식: 쿼리 파라미터 없음

인증: 필요

서버: Core

#### 요청 예시

```http
GET /api/teachers HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.selectedTeacherId | string | 현재 선택된 선생님 ID |
| data.teachers | array | 선생님 목록 (프리셋 3종 + 커스텀) |
| data.teachers[].id | string | 선생님 ID |
| data.teachers[].name | string | 이름 |
| data.teachers[].emoji | string | 대표 이모지 |
| data.teachers[].personality | string | 성격 설명 |
| data.teachers[].tone | string | 말투 스타일 |
| data.teachers[].specialties | string[] | 전문 과목 |
| data.teachers[].avatarUrl | string | 아바타 이미지 URL |
| data.teachers[].isPreset | boolean | 프리셋 여부 |
| data.teachers[].isPremium | boolean | 프리미엄 여부 |
| data.teachers[].isSelected | boolean | 현재 선택 여부 |

```json
{
  "success": true,
  "data": {
    "selectedTeacherId": "preset-friendly",
    "teachers": [
      {
        "id": "preset-friendly",
        "name": "다정한 수리샘",
        "emoji": "🤗",
        "personality": "친근하고 격려하는 스타일",
        "tone": "격려형",
        "specialties": ["수학", "과학"],
        "avatarUrl": "https://cdn.ilkwa.com/teachers/friendly.png",
        "isPreset": true,
        "isPremium": false,
        "isSelected": true
      },
      {
        "id": "preset-strict",
        "name": "엄격한 진쌤",
        "emoji": "📐",
        "personality": "체계적이고 꼼꼼한 스타일",
        "tone": "존댓말",
        "specialties": ["국어", "사회"],
        "avatarUrl": "https://cdn.ilkwa.com/teachers/strict.png",
        "isPreset": true,
        "isPremium": false,
        "isSelected": false
      },
      {
        "id": "883g1733-h52e-74g7-d049-779988773333",
        "name": "나만의 쌤",
        "emoji": "🔬",
        "personality": "차분하면서도 열정적인",
        "tone": "격려형",
        "specialties": ["물리", "화학"],
        "avatarUrl": "https://cdn.ilkwa.com/teachers/custom/883g1733.png",
        "isPreset": false,
        "isPremium": true,
        "isSelected": false
      }
    ]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |

---

### GET /teachers/{id}

선생님의 상세 정보를 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string | 선생님 ID (프리셋 ID 또는 UUID) |

#### 요청 예시

```http
GET /api/teachers/preset-friendly HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string | 선생님 ID |
| data.name | string | 이름 |
| data.emoji | string | 대표 이모지 |
| data.personality | string | 성격 설명 |
| data.tone | string | 말투 스타일 |
| data.specialties | string[] | 전문 과목 |
| data.teachingStyle | string | 교수법 (`"lecture"` / `"socratic"`) |
| data.description | string | 상세 소개 |
| data.avatarUrl | string | 아바타 이미지 URL |
| data.voiceType | string | 음성 타입 |
| data.voiceProfile | object / null | 커스텀 음성 프로필 (설정 시) |
| data.voiceProfile.profileType | string | 프로필 타입 (`"preset"` / `"natural"` / `"clone"`) |
| data.voiceProfile.voiceId | string | 음성 ID |
| data.voiceProfile.sampleUrl | string / null | 음성 샘플 URL |
| data.isPreset | boolean | 프리셋 여부 |
| data.isPremium | boolean | 프리미엄 여부 |
| data.tutoringCount | integer | 이 선생님으로 진행한 과외 수 |
| data.createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "id": "preset-friendly",
    "name": "다정한 수리샘",
    "emoji": "🤗",
    "personality": "친근하고 격려하는 스타일. 학생의 작은 성장도 칭찬해주며, 틀려도 괜찮다고 다독여줍니다.",
    "tone": "격려형",
    "specialties": ["수학", "과학"],
    "teachingStyle": "lecture",
    "description": "수학과 과학을 쉽고 재미있게 가르쳐요.",
    "avatarUrl": "https://cdn.ilkwa.com/teachers/friendly.png",
    "voiceType": "warm-female",
    "voiceProfile": null,
    "isPreset": true,
    "isPremium": false,
    "tutoringCount": 3,
    "createdAt": "2026-01-01T00:00:00Z"
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 해당 ID의 선생님이 존재하지 않음 |

---

### PUT /teachers/{id}

커스텀 선생님 정보를 수정한다. (Premium 전용, 커스텀 선생님만 수정 가능)

요청 형식: `application/json`

인증: 필요 (Premium)

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 커스텀 선생님 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| name | string | X | 이름 (2~20자) |
| personality | string | X | 성격 설명 (최대 200자) |
| toneStyle | string | X | 말투 스타일 |
| specialties | string[] | X | 전문 과목 배열 |
| teachingStyle | string | X | 교수법 |
| description | string | X | 상세 소개 (최대 500자) |

#### 요청 예시

```json
{
  "name": "업그레이드 쌤",
  "specialties": ["물리", "화학", "생물"],
  "teachingStyle": "lecture"
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

수정된 선생님 전체 객체 반환 (`GET /teachers/{id}`와 동일한 구조)

```json
{
  "success": true,
  "data": {
    "id": "883g1733-h52e-74g7-d049-779988773333",
    "name": "업그레이드 쌤",
    "emoji": "🔬",
    "personality": "차분하면서도 열정적인",
    "tone": "격려형",
    "specialties": ["물리", "화학", "생물"],
    "teachingStyle": "lecture",
    "description": "물리와 화학의 원리를 실생활 예시로 쉽게 풀어드립니다.",
    "avatarUrl": "https://cdn.ilkwa.com/teachers/custom/883g1733.png",
    "voiceType": "gentle-male",
    "voiceProfile": null,
    "isPreset": false,
    "isPremium": true,
    "tutoringCount": 1,
    "createdAt": "2026-04-12T09:00:00Z"
  },
  "message": "선생님 정보가 수정되었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 유효하지 않은 필드 값 |
| 401 | AUTH_001 | 토큰 만료 |
| 403 | PREMIUM_001 | 프리미엄 구독 필요 |
| 403 | AUTH_002 | 프리셋 선생님은 수정 불가 |
| 404 | TUTOR_001 | 선생님을 찾을 수 없음 |

---

### DELETE /teachers/{id}

커스텀 선생님을 삭제한다. (커스텀만 삭제 가능, 프리셋 삭제 불가)

요청 형식: Path Parameter만 사용

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 삭제할 커스텀 선생님 ID |

#### 요청 예시

```http
DELETE /api/teachers/883g1733-h52e-74g7-d049-779988773333 HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

##### 성공 응답 (204 No Content)

응답 본문 없음

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 403 | AUTH_002 | 프리셋 선생님은 삭제 불가 |
| 404 | TUTOR_001 | 선생님을 찾을 수 없음 |

---

### PUT /teachers/{id}/select

현재 활성 선생님을 변경한다.

요청 형식: Path Parameter만 사용 (본문 없음)

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string | 선택할 선생님 ID |

#### 요청 예시

```http
PUT /api/teachers/preset-friendly/select HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.selectedTeacherId | string | 선택된 선생님 ID |
| data.selectedTeacherName | string | 선택된 선생님 이름 |

```json
{
  "success": true,
  "data": {
    "selectedTeacherId": "preset-friendly",
    "selectedTeacherName": "다정한 수리샘"
  },
  "message": "선생님이 변경되었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 403 | PREMIUM_001 | 커스텀 선생님 선택 시 프리미엄 구독 필요 |
| 404 | TUTOR_001 | 선생님을 찾을 수 없음 |

---

### POST /teachers/{id}/voice-profile

선생님의 음성 프로필을 커스텀 설정한다. (Premium 전용)

요청 형식: `application/json`

인증: 필요 (Premium)

서버: AI (`https://ai.ilkwa.com/api`)

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string | 선생님 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| profileType | string | O | 프로필 타입 (`"preset"`: 기본 제공 / `"natural"`: 자연스러운 변형 / `"clone"`: 음성 복제) |
| voiceId | string | 조건부 | 프리셋 음성 ID (profileType이 `"preset"`일 때 필수) |
| sampleUrl | string | 조건부 | 음성 샘플 URL (profileType이 `"clone"`일 때 필수, 10~30초 분량) |
| pitch | number | X | 음높이 조절 (0.5~2.0, 기본값: 1.0) |
| speed | number | X | 속도 조절 (0.5~2.0, 기본값: 1.0) |

#### 요청 예시

```json
{
  "profileType": "natural",
  "voiceId": "warm-female-v2",
  "pitch": 1.1,
  "speed": 1.0
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.voiceProfileId | string (UUID) | 생성된 음성 프로필 ID |
| data.profileType | string | 프로필 타입 |
| data.status | string | 처리 상태 (`"PROCESSING"` / `"READY"`) |
| data.estimatedCompletionTime | string (ISO 8601) / null | 예상 완료 시각 (clone인 경우) |

```json
{
  "success": true,
  "data": {
    "voiceProfileId": "voice-994h2844-i63f-85h8-e150-880099884444",
    "profileType": "natural",
    "status": "READY",
    "estimatedCompletionTime": null
  },
  "message": "음성 프로필이 설정되었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

> 참고: `profileType: "clone"`인 경우 음성 복제에 약 5분이 소요되며, status가 `"PROCESSING"`으로 반환된다.

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | profileType에 따른 필수 필드 누락, pitch/speed 범위 초과 |
| 401 | AUTH_001 | 토큰 만료 |
| 403 | PREMIUM_001 | 프리미엄 구독 필요 |
| 404 | TUTOR_001 | 선생님을 찾을 수 없음 |
| 503 | AI_004 | TTS 모델 오류 |

---

### POST /teachers/{id}/preview

선택한 AI 선생님의 음성과 성격을 미리듣기한다. 체험용 짧은 음성 샘플(10~15초)과 선생님 소개 메시지를 반환한다.

요청 형식: `application/json`

인증: 필요 (FREE/PREMIUM 모두 가능, 레이트리밋 3회/시간)

서버: AI (FastAPI) `https://ai.ilkwa.com/api`

#### Path 파라미터

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| id | string (UUID) | 선생님 ID |

#### 요청 본문

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|------|------|------|--------|------|
| sampleText | string | X | `"안녕하세요, 저는 여러분의 AI 선생님입니다."` | 미리듣기에 사용할 텍스트 |
| includePersonality | boolean | X | `true` | 성격 정보 포함 여부 |

#### 요청 예시

```http
POST /api/teachers/teacher-uuid-001/preview HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: application/json

{
  "sampleText": "안녕하세요! 오늘은 미적분을 함께 공부해봐요.",
  "includePersonality": true
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.teacherId | string (UUID) | 선생님 ID |
| data.name | string | 선생님 이름 |
| data.personality | string | 성격 설명 |
| data.toneStyle | string | 말투 스타일 |
| data.specialties | string[] | 전문 과목 배열 |
| data.description | string | 선생님 소개 |
| data.avatarUrl | string | 프로필 이미지 URL |
| data.voiceProfileId | string (UUID) | 음성 프로필 ID |
| data.audioPreviewUrl | string | 미리듣기 오디오 URL (S3/CloudFront) |
| data.audioDuration | number | 오디오 길이 (초) |
| data.voiceType | string | 음성 타입 |
| data.isPremium | boolean | 프리미엄 전용 여부 |

```json
{
  "success": true,
  "data": {
    "teacherId": "teacher-uuid-001",
    "name": "다정한 수리샘",
    "personality": "친근하고 격려하는 스타일. 학생의 작은 성장도 칭찬해주며, 틀려도 괜찮다고 다독여줍니다.",
    "toneStyle": "격려형",
    "specialties": ["수학", "과학"],
    "description": "수학과 과학을 쉽고 재미있게 가르쳐요. 어려운 개념도 일상의 예시로 풀어드립니다.",
    "avatarUrl": "https://cdn.ilkwa.com/teachers/friendly.png",
    "voiceProfileId": "voice-994h2844-i63f-85h8-e150-880099884444",
    "audioPreviewUrl": "https://cdn.ilkwa.com/previews/teacher-uuid-001-sample.mp3",
    "audioDuration": 12.5,
    "voiceType": "warm-female",
    "isPremium": false
  },
  "message": "미리듣기 샘플이 생성되었습니다.",
  "timestamp": "2026-04-14T09:30:00Z"
}
```

> 특이사항: Modal GPU TTS로 실시간 생성 후 S3/CloudFront에 업로드한다. Redis에 3시간 TTL로 캐시하여 동일 선생님 반복 요청 시 다시 생성하지 않는다. ERD 참조: ai_teacher, voice_profile (읽기 전용).

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 선생님을 찾을 수 없음 |
| 429 | SYSTEM_003 | 레이트리밋 초과 (3회/시간) |
| 500 | AI_004 | TTS 생성 실패 |
| 503 | AI_005 | Claude API 호출 실패 |

---

## 4b. AI 튜터 API

> 서버: Spring Boot (CRUD·리셋) + FastAPI (라이브 프리뷰)  
> Base URL: Core `https://api.ilkwa.com/api` / AI `https://ai.ilkwa.com/api`  
> 신규 (2026-04-20): PAGE 18 AI 튜터 관리 전용 엔드포인트.

### T1. GET /tutors — 내 튜터 목록

기본 제공 2종 + 사용자 생성 튜터 전체를 반환한다.

Request: 없음

#### Response 200

```json
{
  "tutors": [
    {
      "tutorId": "tut_PRESET_OWL_PROFESSOR_000001",
      "name": "올빼미 교수",
      "tagline": "차분하고 단단한 설명 · 소크라테스식",
      "avatarStyle": "owl",
      "backgroundColor": "#EEF3F4",
      "isDefault": true,
      "toneSlider": 20,
      "speedSlider": 50,
      "depthSlider": 80,
      "questionStyleSlider": 80,
      "useEmoji": false,
      "useFormalSpeech": true
    }
  ]
}
```

---

### T2. POST /tutors — 신규 튜터 생성

#### Request Body

```json
{
  "name": "string (max 20)",
  "tagline": "string (max 100, optional)",
  "avatarStyle": "owl | cat | fox | bear | cloud | sparkle",
  "backgroundColor": "#RRGGBB",
  "toneSlider": 0,
  "speedSlider": 50,
  "depthSlider": 50,
  "questionStyleSlider": 50,
  "useEmoji": false,
  "useFormalSpeech": true
}
```

#### Response 201

```json
{ "tutorId": "tut_..." }
```

---

### T3. GET /tutors/{id} — 튜터 단건 조회

Request: 없음

#### Response 200

```json
{
  "tutorId": "tut_...",
  "name": "string",
  "tagline": "string",
  "avatarStyle": "owl | cat | fox | bear | cloud | sparkle",
  "backgroundColor": "#RRGGBB",
  "isDefault": false,
  "toneSlider": 0,
  "speedSlider": 50,
  "depthSlider": 50,
  "questionStyleSlider": 50,
  "useEmoji": false,
  "useFormalSpeech": true
}
```

Response 404: 존재하지 않거나 다른 사용자 소유

---

### T4. PATCH /tutors/{id} — 튜터 수정

Request Body: T2와 동일한 필드 중 변경할 것만 포함 (부분 업데이트)

Response 200: 수정된 튜터 전체 필드 반환

---

### T5. DELETE /tutors/{id} — 튜터 삭제

기본 제공 튜터(`is_default=true`)는 삭제 불가 — 403 반환.

Response 204: 삭제 성공

---

### T6. POST /tutors/{id}/reset — 기본값 리셋

편집 중인 튜터를 최초 생성 시 기본값으로 초기화한다.

Response 200: 리셋된 튜터 전체 필드 반환

---

### T7. POST /tutors/{id}/preview — 라이브 프리뷰

> 서버: FastAPI. 현재 슬라이더 값을 기반으로 LLM에 즉시 요청하고 예시 응답을 반환한다.

#### Request Body

```json
{
  "toneSlider": 20,
  "speedSlider": 50,
  "depthSlider": 80,
  "questionStyleSlider": 80,
  "useEmoji": false,
  "useFormalSpeech": true,
  "sampleQuestion": "응집도와 결합도가 자꾸 헷갈려요. 어떻게 구분하나요?"
}
```

#### Response 200

```json
{
  "previewText": "string",
  "settingTags": ["#균형", "#표준속도", "#심화", "#힌트주기"]
}
```

---

## 5. 과외/커리큘럼 API

> 서버: Spring Boot (공개 REST·인증·권한·상태 전이) + FastAPI ChapterStudio_V1 (AI 생성·후처리·DB 저장)
> Base URL: AI `https://ai.ilkwa.com/api` / Core `https://api.ilkwa.com/api`

> ChapterStudio_V1 계약: 프론트엔드는 슬라이드 내부 템플릿을 조립하지 않는다. ChapterStudio_V1이 템플릿 75종(JSON) 중 하나에 내용을 채우고 HTML/CSS 렌더, 후처리, 검증, sanitize, 프론트 삽입용 문서 조립까지 끝낸 `iframeHtml`을 DB에 저장하고, 프론트는 빈 학습 프레임에 `iframe srcDoc`으로 끼워 넣는다. `iframeHtml`은 iframe 태그가 아니라 `srcDoc`에 들어갈 완성 HTML 문서다. 공개 REST 진입점과 소유권 검증은 Spring이 담당하며, 모델 호출은 FastAPI에서만 수행한다. 운영 FastAPI는 프론트 쿼리값이 아니라 `lessonId`로 `chapter_studio.curriculum_unit`, `curriculum_plan`, `lesson_generation_status.generation_context`를 읽어 생성 입력을 구성한다.

---

### POST /tutoring

새 과외를 생성한다. PDF 파일 업로드 또는 주제 텍스트를 입력하여 AI 커리큘럼을 자동 생성한다.

요청 형식: `multipart/form-data`

인증: 필요

서버: Core(Spring) 공개 진입점 + AI(FastAPI) 커리큘럼 생성

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| teacherId | string | O | 선생님 ID |
| language | string | X | 강의 언어 (기본값: `"ko"`, 옵션: `"en"`) |
| mode | string | X | 수업 방식 (`"lecture"`: 강의형 / `"socratic"`: 소크라테스식, 기본값: `"lecture"`) |
| tone | string | X | 말투 오버라이드 (선생님 기본 설정 대신 사용) |
| availableTime | integer | X | 가용 학습 시간 (분 단위, 과제 유형 결정에 사용) |
| examDate | string (ISO 8601) | X | 시험 날짜 (있으면 시험까지의 일정에 맞춰 커리큘럼 생성) |
| type | string | O | 입력 유형 (`"pdf"` / `"topic"`) |
| file | file (PDF) | 조건부 | PDF 파일 (type이 `"pdf"`일 때 필수, 최대 1.5GB, `.pdf` 형식) |
| title | string | O | 과외 제목 (최대 100자) |
| topicText | string | 조건부 | 주제 텍스트 (type이 `"topic"`일 때 필수, 최대 2000자) |
| subject | string | O | 과목 (예: `"수학"`, `"물리"`, `"영어"`) |
| difficulty | string | X | 난이도 (`"easy"` / `"medium"` / `"hard"`, 기본값: `"medium"`) |
| lessonCount | integer | X | 희망 강의 수 (AI가 참고, 최종 결정은 AI) |

#### 요청 예시 (PDF 업로드)

```http
POST /api/tutoring HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="teacherId"

preset-friendly
------WebKitFormBoundary
Content-Disposition: form-data; name="type"

pdf
------WebKitFormBoundary
Content-Disposition: form-data; name="title"

미적분 기초
------WebKitFormBoundary
Content-Disposition: form-data; name="subject"

수학
------WebKitFormBoundary
Content-Disposition: form-data; name="difficulty"

medium
------WebKitFormBoundary
Content-Disposition: form-data; name="availableTime"

30
------WebKitFormBoundary
Content-Disposition: form-data; name="mode"

lecture
------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="미적분_교재.pdf"
Content-Type: application/pdf

(바이너리 PDF 데이터)
------WebKitFormBoundary--
```

#### 요청 예시 (주제 텍스트)

```http
POST /api/tutoring HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="teacherId"

preset-friendly
------WebKitFormBoundary
Content-Disposition: form-data; name="type"

topic
------WebKitFormBoundary
Content-Disposition: form-data; name="title"

미적분 기초
------WebKitFormBoundary
Content-Disposition: form-data; name="topicText"

미적분의 개념부터 기본 문제풀이까지. 극한, 미분, 적분의 기초를 다룹니다.
------WebKitFormBoundary
Content-Disposition: form-data; name="subject"

수학
------WebKitFormBoundary
Content-Disposition: form-data; name="difficulty"

medium
------WebKitFormBoundary--
```

응답 형식: `application/json`

##### 성공 응답 (201 Created)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 생성된 과외 ID |
| data.title | string | 과외 제목 |
| data.subject | string | 과목 |
| data.teacherId | string | 선생님 ID |
| data.teacherName | string | 선생님 이름 |
| data.status | string | 상태 (`"CURRICULUM_GENERATING"`) |
| data.difficulty | string | 난이도 |
| data.sourceType | string | 입력 유형 (`"pdf"` / `"topic"`) |
| data.mode | string | 수업 방식 |
| data.createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "id": "aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
    "title": "미적분 기초",
    "subject": "수학",
    "teacherId": "preset-friendly",
    "teacherName": "다정한 수리샘",
    "status": "CURRICULUM_GENERATING",
    "difficulty": "medium",
    "sourceType": "topic",
    "mode": "lecture",
    "createdAt": "2026-04-12T09:00:00Z"
  },
  "message": "과외가 생성되었습니다. 커리큘럼을 생성 중입니다.",
  "timestamp": "2026-04-12T09:00:00Z"
}
```

> 참고: 커리큘럼 생성은 비동기로 진행된다 (약 30초~2분). Spring은 `tutoring` 소유권과 상태를 관리하고, FastAPI ChapterStudio_V1은 Opus 4.6으로 커리큘럼 초안을 생성해 DB에 저장한다. `GET /tutoring/{id}`에서 `status`가 `"CURRICULUM_READY"`로 변경되면 `GET /tutoring/{id}/curriculum`으로 커리큘럼을 조회할 수 있다.

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락, type에 따른 조건부 필드 누락 |
| 400 | TUTOR_004 | PDF 파일 분석 실패 (손상된 파일, 비밀번호 보호 등) |
| 401 | AUTH_001 | 토큰 만료 |
| 403 | PREMIUM_001 | Free 플랜의 최대 과외 수 (2개) 초과 |
| 413 | VALIDATION_001 | 파일 크기 초과 (1.5GB) |
| 429 | SYSTEM_003 | AI 생성 요청 제한 초과 |

---

### GET /tutoring

내 과외 목록을 조회한다.

요청 형식: Query Parameter

인증: 필요

서버: Core

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| page | integer | X | 0 | 페이지 번호 |
| size | integer | X | 20 | 페이지 당 항목 수 |
| status | string | X | - | 상태 필터 (`"ACTIVE"` / `"COMPLETED"` / `"PAUSED"` / `"CURRICULUM_GENERATING"` / `"CURRICULUM_READY"`) |
| subject | string | X | - | 과목 필터 |
| sort | string | X | `createdAt,desc` | 정렬 기준 |

#### 요청 예시

```http
GET /api/tutoring?page=0&size=10&status=ACTIVE HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.content | array | 과외 목록 |
| data.content[].id | string (UUID) | 과외 ID |
| data.content[].title | string | 과외 제목 |
| data.content[].subject | string | 과목 |
| data.content[].teacherName | string | 선생님 이름 |
| data.content[].teacherAvatarUrl | string | 선생님 아바타 URL |
| data.content[].status | string | 상태 |
| data.content[].progress | number | 진행률 (0.0~1.0) |
| data.content[].totalLessons | integer | 전체 강의 수 |
| data.content[].completedLessons | integer | 완료한 강의 수 |
| data.content[].lastStudiedAt | string (ISO 8601) / null | 마지막 학습 일시 |
| data.content[].createdAt | string (ISO 8601) | 생성 시각 |
| data.page | integer | 현재 페이지 |
| data.size | integer | 페이지 크기 |
| data.totalElements | integer | 전체 항목 수 |
| data.totalPages | integer | 전체 페이지 수 |
| data.hasNext | boolean | 다음 페이지 존재 여부 |
| data.hasPrevious | boolean | 이전 페이지 존재 여부 |

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
        "title": "미적분 기초",
        "subject": "수학",
        "teacherName": "다정한 수리샘",
        "teacherAvatarUrl": "https://cdn.ilkwa.com/teachers/friendly.png",
        "status": "ACTIVE",
        "progress": 0.4,
        "totalLessons": 5,
        "completedLessons": 2,
        "lastStudiedAt": "2026-04-11T20:00:00Z",
        "createdAt": "2026-04-01T09:00:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 3,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |

---

### GET /tutoring/{id}

과외의 상세 정보를 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |

#### 요청 예시

```http
GET /api/tutoring/aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 과외 ID |
| data.title | string | 제목 |
| data.subject | string | 과목 |
| data.teacherId | string | 선생님 ID |
| data.teacherName | string | 선생님 이름 |
| data.teacherAvatarUrl | string | 선생님 아바타 URL |
| data.status | string | 상태 (`"CURRICULUM_GENERATING"` / `"CURRICULUM_READY"` / `"ACTIVE"` / `"COMPLETED"` / `"PAUSED"`) |
| data.difficulty | string | 난이도 |
| data.sourceType | string | 입력 유형 |
| data.mode | string | 수업 방식 |
| data.progress | number | 진행률 (0.0~1.0) |
| data.totalLessons | integer | 전체 강의 수 |
| data.completedLessons | integer | 완료 강의 수 |
| data.totalEstimatedMinutes | integer | 예상 총 소요 시간 (분) |
| data.lastStudiedAt | string (ISO 8601) / null | 마지막 학습 시각 |
| data.createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "id": "aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
    "title": "미적분 기초",
    "subject": "수학",
    "teacherId": "preset-friendly",
    "teacherName": "다정한 수리샘",
    "teacherAvatarUrl": "https://cdn.ilkwa.com/teachers/friendly.png",
    "status": "ACTIVE",
    "difficulty": "medium",
    "sourceType": "topic",
    "mode": "lecture",
    "progress": 0.4,
    "totalLessons": 5,
    "completedLessons": 2,
    "totalEstimatedMinutes": 150,
    "lastStudiedAt": "2026-04-11T20:00:00Z",
    "createdAt": "2026-04-01T09:00:00Z"
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 과외를 찾을 수 없음 |

---

### GET /tutoring/{id}/curriculum

AI가 생성한 커리큘럼을 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: Core(Spring) 조회·권한 검증 + AI 생성 산출물 조회

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |

#### 요청 예시

```http
GET /api/tutoring/aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d/curriculum HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.tutoringId | string (UUID) | 과외 ID |
| data.title | string | 커리큘럼 제목 |
| data.status | string | 상태 (`"CURRICULUM_GENERATING"` / `"CURRICULUM_READY"`) |
| data.confirmed | boolean | 사용자 확인 여부 |
| data.estimatedTotalMinutes | integer | 예상 총 학습 시간 (분) |
| data.lessons | array | 강의 목록 |
| data.lessons[].order | integer | 강의 순서 |
| data.lessons[].title | string | 강의 제목 |
| data.lessons[].description | string | 강의 설명 |
| data.lessons[].estimatedMinutes | integer | 예상 소요 시간 (분) |
| data.lessons[].keyTopics | string[] | 핵심 주제 배열 |
| data.lessons[].prerequisite | string / null | 선수 강의 제목 (없으면 null) |
| data.sourceAnalysis | object | PDF/주제 분석 결과 |
| data.sourceAnalysis.detectedTopics | string[] | 감지된 주제 |
| data.sourceAnalysis.difficultyAssessment | string | 난이도 평가 |
| data.sourceAnalysis.recommendedPrerequisites | string[] | 추천 선수 학습 |

```json
{
  "success": true,
  "data": {
    "tutoringId": "aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
    "title": "미적분 기초",
    "status": "CURRICULUM_READY",
    "confirmed": false,
    "estimatedTotalMinutes": 150,
    "lessons": [
      {
        "order": 1,
        "title": "극한의 개념",
        "description": "함수의 극한과 수렴의 정의를 학습합니다.",
        "estimatedMinutes": 30,
        "keyTopics": ["함수의 극한", "좌극한/우극한", "수렴과 발산"],
        "prerequisite": null
      },
      {
        "order": 2,
        "title": "미분의 정의",
        "description": "미분계수와 도함수의 기본 개념을 배웁니다.",
        "estimatedMinutes": 30,
        "keyTopics": ["미분계수", "도함수", "미분법"],
        "prerequisite": "극한의 개념"
      },
      {
        "order": 3,
        "title": "미분의 활용",
        "description": "접선의 방정식, 증가/감소, 극값을 다룹니다.",
        "estimatedMinutes": 35,
        "keyTopics": ["접선", "증가감소", "극대극소"],
        "prerequisite": "미분의 정의"
      },
      {
        "order": 4,
        "title": "적분의 개념",
        "description": "부정적분과 정적분의 기초를 학습합니다.",
        "estimatedMinutes": 30,
        "keyTopics": ["부정적분", "정적분", "적분법"],
        "prerequisite": "미분의 활용"
      },
      {
        "order": 5,
        "title": "적분의 활용",
        "description": "넓이, 부피 등 적분의 실생활 응용을 배웁니다.",
        "estimatedMinutes": 25,
        "keyTopics": ["넓이 계산", "부피 계산", "실생활 응용"],
        "prerequisite": "적분의 개념"
      }
    ],
    "sourceAnalysis": {
      "detectedTopics": ["미적분", "극한", "미분", "적분"],
      "difficultyAssessment": "중급",
      "recommendedPrerequisites": ["함수의 기초", "방정식"]
    }
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 과외를 찾을 수 없음 |
| 409 | TUTOR_002 | 커리큘럼이 아직 생성 중 |

---

### PUT /tutoring/{id}/curriculum/confirm

사용자가 커리큘럼을 확인하고 강의 생성을 시작한다. 필요 시 수정 사항을 전달할 수 있다.

요청 형식: `application/json`

인증: 필요

서버: Core(Spring) 확정·상태 전이 + AI(FastAPI) 강의 생성 큐 등록

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| confirmed | boolean | O | 확인 여부 (항상 `true`) |
| adjustments | object | X | 수정 사항 (없으면 생략 가능) |
| adjustments.removeLessons | integer[] | X | 제거할 강의 order 배열 |
| adjustments.addTopics | string[] | X | 추가할 주제 배열 |
| adjustments.reorderLessons | object[] | X | 순서 변경 (from/to) |

#### 요청 예시

```json
{
  "confirmed": true,
  "adjustments": {
    "removeLessons": [],
    "addTopics": ["치환 적분"],
    "reorderLessons": []
  }
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.status | string | 상태 (`"LESSONS_GENERATING"`) |
| data.estimatedReadyAt | string (ISO 8601) | 강의 생성 예상 완료 시각 |

```json
{
  "success": true,
  "data": {
    "status": "LESSONS_GENERATING",
    "estimatedReadyAt": "2026-04-12T09:10:00Z"
  },
  "message": "커리큘럼이 확정되었습니다. 강의를 생성 중입니다.",
  "timestamp": "2026-04-12T09:05:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | confirmed가 false이거나 누락 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 과외를 찾을 수 없음 |
| 409 | TUTOR_002 | 커리큘럼이 아직 생성 중이거나 이미 확정됨 |

---

### GET /tutoring/{id}/lessons

해당 과외의 강의 목록을 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |

#### 요청 예시

```http
GET /api/tutoring/aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d/lessons HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data | array | 강의 목록 |
| data[].id | string (UUID) | 강의 ID |
| data[].order | integer | 강의 순서 |
| data[].title | string | 강의 제목 |
| data[].description | string | 강의 설명 |
| data[].estimatedMinutes | integer | 예상 소요 시간 (분) |
| data[].status | string | 강의 상태 (`"LOCKED"` / `"AVAILABLE"` / `"IN_PROGRESS"` / `"COMPLETED"`) |
| data[].slideCount | integer | 슬라이드 수 |
| data[].completedAt | string (ISO 8601) / null | 완료 시각 |

```json
{
  "success": true,
  "data": [
    {
      "id": "lesson-001-uuid",
      "order": 1,
      "title": "극한의 개념",
      "description": "함수의 극한과 수렴의 정의를 학습합니다.",
      "estimatedMinutes": 30,
      "status": "COMPLETED",
      "slideCount": 15,
      "completedAt": "2026-04-05T20:30:00Z"
    },
    {
      "id": "lesson-002-uuid",
      "order": 2,
      "title": "미분의 정의",
      "description": "미분계수와 도함수의 기본 개념을 배웁니다.",
      "estimatedMinutes": 30,
      "status": "COMPLETED",
      "slideCount": 18,
      "completedAt": "2026-04-08T21:00:00Z"
    },
    {
      "id": "lesson-003-uuid",
      "order": 3,
      "title": "미분의 활용",
      "description": "접선의 방정식, 증가/감소, 극값을 다룹니다.",
      "estimatedMinutes": 35,
      "status": "AVAILABLE",
      "slideCount": 20,
      "completedAt": null
    },
    {
      "id": "lesson-004-uuid",
      "order": 4,
      "title": "적분의 개념",
      "description": "부정적분과 정적분의 기초를 학습합니다.",
      "estimatedMinutes": 30,
      "status": "LOCKED",
      "slideCount": 16,
      "completedAt": null
    }
  ],
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 과외를 찾을 수 없음 |

---

### GET /tutoring/{id}/lessons/{lessonId}

강의의 상세 정보를 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

#### 요청 예시

```http
GET /api/tutoring/aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d/lessons/lesson-001-uuid HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 강의 ID |
| data.order | integer | 순서 |
| data.title | string | 제목 |
| data.description | string | 설명 |
| data.estimatedMinutes | integer | 예상 소요 시간 |
| data.status | string | 상태 |
| data.slideCount | integer | 슬라이드 수 |
| data.keyTopics | string[] | 핵심 주제 |
| data.bookmarks | array | 북마크 목록 |
| data.bookmarks[].slideIndex | integer | 북마크한 슬라이드 인덱스 |
| data.bookmarks[].note | string / null | 북마크 메모 |
| data.bookmarks[].createdAt | string (ISO 8601) | 생성 시각 |
| data.completedAt | string (ISO 8601) / null | 완료 시각 |

```json
{
  "success": true,
  "data": {
    "id": "lesson-001-uuid",
    "order": 1,
    "title": "극한의 개념",
    "description": "함수의 극한과 수렴의 정의를 학습합니다.",
    "estimatedMinutes": 30,
    "status": "COMPLETED",
    "slideCount": 15,
    "keyTopics": ["함수의 극한", "좌극한/우극한", "수렴과 발산"],
    "bookmarks": [
      {
        "slideIndex": 5,
        "note": "좌극한 우극한 개념 중요",
        "createdAt": "2026-04-05T20:15:00Z"
      }
    ],
    "completedAt": "2026-04-05T20:30:00Z",
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 과외를 찾을 수 없음 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |

---

### GET /tutoring/{id}/lessons/{lessonId}/slides

강의의 슬라이드 목록을 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: Core(Spring) 조회·권한 검증 + AI 생성 산출물 조회

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

#### 요청 예시

```http
GET /api/tutoring/aa1b2c3d/lessons/lesson-001-uuid/slides HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.lessonId | string (UUID) | 강의 ID |
| data.totalSlides | integer | 전체 슬라이드 수 |
| data.slides | array | 슬라이드 배열 |
| data.slides[].slideId | string (UUID) | 슬라이드 ID |
| data.slides[].index | integer | 슬라이드 인덱스 (0부터) |
| data.slides[].title | string | 슬라이드 제목 |
| data.slides[].category | string | ChapterStudio_V1 슬라이드 카테고리 |
| data.slides[].templateId | string | 적용된 템플릿 ID |
| data.slides[].iframeHtml | string | 후처리·검증·sanitize가 끝난 iframe `srcDoc` HTML |
| data.slides[].audioUrl | string / null | TTS 오디오 URL. 생성 전이면 null |
| data.slides[].audioDuration | number / null | 오디오 길이 (초). 생성 전이면 null |
| data.slides[].cueStartSec | number / null | 통합 오디오 내 슬라이드 시작 시각 |
| data.slides[].cueEndSec | number / null | 통합 오디오 내 슬라이드 종료 시각 |
| data.slides[].validationStatus | string | 렌더링 검증 상태 (`"passed"` / `"pending"` / `"failed"`) |

```json
{
  "success": true,
  "data": {
    "lessonId": "lesson-001-uuid",
    "totalSlides": 15,
    "slides": [
      {
        "slideId": "sld-001-uuid",
        "index": 0,
        "title": "극한의 정의",
        "category": "math",
        "templateId": "math-concept-v1",
        "iframeHtml": "<!doctype html><html><head><style>...</style></head><body>...</body></html>",
        "audioUrl": "https://cdn.ilkwa.com/audio/lesson-001/slide-0.mp3",
        "audioDuration": 45.2,
        "cueStartSec": 0,
        "cueEndSec": 45.2,
        "validationStatus": "passed"
      },
      {
        "slideId": "sld-002-uuid",
        "index": 1,
        "title": "극한의 표기법",
        "category": "formula",
        "templateId": "formula-proof-v1",
        "iframeHtml": "<!doctype html><html><head><style>...</style></head><body>...</body></html>",
        "audioUrl": null,
        "audioDuration": null,
        "cueStartSec": null,
        "cueEndSec": null,
        "validationStatus": "passed"
      }
    ]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

> 프론트 렌더링 주의: `iframeHtml`은 `iframe srcDoc`에만 넣는다. `dangerouslySetInnerHTML`로 앱 DOM에 직접 삽입하지 않는다. iframe sandbox 토큰은 `allow-scripts`만 허용한다.

> 생성 입력 주의: 주제/PDF 모드, 학습 기간, 깊이, 튜터, tone/pace/depth/socratic, 대상 수준, 학습 목표, 취약점, 챕터 정보, 템플릿은 Spring이 DB에 저장한 스냅샷을 FastAPI가 `lessonId` 기준으로 읽는다. 데모 화면의 동일 필드는 mock 검증용이다.

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |

---

### GET /tutoring/{id}/lessons/{lessonId}/stream

강의 생성·학습 진행 상태를 조회한다. ChapterStudio_V1은 강의 1개 단위로 슬라이드를 생성하고 DB에 저장한다. 프론트는 이 엔드포인트를 polling하거나, FastAPI 내부 생성 이벤트를 별도 개발 환경에서 직접 구독해 실시간처럼 보이는 진행률을 표시한다. Spring은 SseEmitter를 구현하지 않는다.

요청 형식: HTTP GET

인증: 필요

서버: Core(Spring) `https://api.ilkwa.com/api`

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| resumeFrom | integer | X | `0` | 이어보기 시작 슬라이드 인덱스 |

#### 요청 예시

```http
GET /api/tutoring/aa1b2c3d/lessons/lesson-001-uuid/stream?resumeFrom=0 HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGci...
```

#### 성공 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| data.lessonId | string | 강의 ID |
| data.status | string | 생성 상태 (`"queued"` / `"generating"` / `"ready"` / `"failed"`) |
| data.currentNode | string / null | 현재 실행 중인 ChapterStudio 노드 |
| data.completedNodes | integer | 완료 노드 수 |
| data.totalNodes | integer | 전체 노드 수 |
| data.progressPercent | integer | 진행률 |
| data.readySlideCount | integer | DB에 저장 완료된 슬라이드 수 |
| data.totalSlides | integer | 전체 슬라이드 수 |
| data.resumeFrom | integer | 이어보기 시작 슬라이드 인덱스 |
| data.errorMessage | string / null | 실패 사유 |

```json
{
  "success": true,
  "data": {
    "lessonId": "lesson-001-uuid",
    "status": "generating",
    "currentNode": "slide_generator",
    "completedNodes": 4,
    "totalNodes": 11,
    "progressPercent": 36,
    "readySlideCount": 3,
    "totalSlides": 12,
    "resumeFrom": 0,
    "errorMessage": null
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

> 특이사항: Spring은 생성 상태 조회와 권한 검증만 한다. FastAPI ChapterStudio_V1은 내부적으로 LangGraph 진행 이벤트를 관리하고 `lesson_generation_status`를 갱신한다. 프론트 운영 기본값은 REST polling이다.

##### 에러 응답 (연결 실패 시)

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |
| 403 | TUTOR_001 | 강의가 LOCKED 상태 |
| 429 | SYSTEM_003 | 상태 조회 요청 과다 |
| 503 | TUTOR_005 | 상태 조회 실패 |

---

### POST /tutoring/{id}/qa/stream

사용자 질문에 대해 Claude Sonnet으로 실시간 스트리밍 응답한다. 입력이 음성이면 Modal ASR(Whisper Turbo), 출력이 음성이면 Modal TTS(Qwen3-TTS)로 분기한다.

요청 형식: `application/json`

인증: 필요

서버: AI (FastAPI) `https://ai.ilkwa.com/api`

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |

#### 요청 바디

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| input_type | string enum(`text`, `audio`) | O | 입력 형식 |
| text | string | X | `input_type=text`일 때 필수 |
| audio_url | string (URL) | X | `input_type=audio`일 때 필수 |
| response_format | string enum(`text`, `audio`, `both`) | O | 응답 형식 |
| course_id | string (UUID) | X | 맥락 전달용 |
| slide_id | string (UUID) | X | 현재 슬라이드 (질문빈도 집계용) |

#### 요청 예시

```json
{
  "input_type": "text",
  "text": "극한값이 존재하지 않는 경우를 예를 들어 설명해 줘",
  "response_format": "text",
  "course_id": "course-uuid-001",
  "slide_id": "slide-uuid-005"
}
```

응답 형식: `application/x-ndjson` (HTTP chunked StreamingResponse, SSE 미사용)

> 2026-04-17 확정: FastAPI `StreamingResponse` (HTTP chunked transfer) 방식. SSE 프로토콜(`text/event-stream`) 미사용.

#### 청크 이벤트 타입

| 이벤트 타입 | 페이로드 필드 | 설명 |
|------------|-------------|------|
| `token` | `delta`: string, `index`: int | 텍스트 토큰 청크 순차 전송 |
| `audio_chunk` | `seq`: int, `mime`: `"audio/mpeg"`, `data_b64`: string | Base64 인코딩 오디오 청크 (`response_format`이 `audio` 또는 `both`일 때) |
| `done` | `qa_id`: string, `tokens_in`: int, `tokens_out`: int, `duration_ms`: int | 스트리밍 완료 및 메타 정보 |
| `error` | `error_code`: string, `message`: string | 스트리밍 중 오류 발생 |

#### 청크 예시

```
{"type": "token", "delta": "극한값이 존재하지 않는 대표적인 예는", "index": 0}
{"type": "token", "delta": " sin(1/x)에서 x→0인 경우야.", "index": 1}
{"type": "audio_chunk", "seq": 0, "mime": "audio/mpeg", "data_b64": "//uQxAAA..."}
{"type": "done", "qa_id": "qa-uuid-001", "tokens_in": 42, "tokens_out": 87, "duration_ms": 1230}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 과외를 찾을 수 없음 |
| 503 | AI_002 | Sonnet 타임아웃 |
| 503 | AI_003 | ASR 실패 |
| 503 | AI_004 | TTS 실패 |
| 503 | AI_005 | Modal 콜드 스타트 초과 |

---

### POST /tutoring/{id}/lessons/{lessonId}/chat/stream

학습 화면 우측 채팅에서 현재 강의·슬라이드·사용자 취약점에 맞춘 과외 답변을 스트리밍한다. 일반 Q&A가 아니라 ChapterStudio_V1 산출물에 붙는 **강의 맞춤 채팅**이다. FastAPI는 `lessonId`와 `slideId`로 DB의 슬라이드, 음성대본, 핵심노트, 강의 끝 퀴즈, 튜터 설정, 사용자 약점을 읽어 컨텍스트를 구성한다.

요청 형식: `application/json`

인증: 필요

서버: AI (FastAPI) `https://ai.ilkwa.com/api`

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

#### 요청 바디

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| slideId | string (UUID) | O | 현재 보고 있는 슬라이드 ID |
| slideIdx | integer | O | 현재 슬라이드 인덱스 (0부터) |
| message | string | O | 사용자 질문 |
| selectedText | string | X | 사용자가 드래그한 슬라이드 문장 |
| playbackSec | number | X | 오디오 재생 위치. 음성대본 구간 추정에 사용 |
| responseFormat | string enum(`text`) | X | 현재는 텍스트 스트리밍만 확정 |

#### 요청 예시

```json
{
  "slideId": "sld-005-uuid",
  "slideIdx": 4,
  "message": "이 퀴즈에서 왜 2번이 정답인지 다시 설명해줘",
  "selectedText": "p-value는 귀무가설 기준의 극단성이다",
  "playbackSec": 42.5,
  "responseFormat": "text"
}
```

#### 컨텍스트 구성 규칙

| 컨텍스트 | 출처 | 설명 |
|---------|------|------|
| 튜터 설정 | `lesson_generation_status.generation_context` | teacher, tone, pace, tutor_depth, socratic |
| 사용자 수준 | `lesson_generation_status.generation_context` | audience_level, weak_points, learning_goal |
| 현재 슬라이드 | `slide` | title, category, template_id, iframe_html 요약, validation_status |
| 음성대본 | `voice_script` | 현재 슬라이드 설명 톤과 말투의 기준 |
| 핵심노트 | `note` | 강의 단위 요약. 답변 근거로 사용 |
| 강의 끝 퀴즈 | `quiz` | 문제와 정답·해설은 분리해서 설명 |

> 모델 정책: FastAPI connector 설정에 따라 Sonnet 계열 실시간 모델을 쓰며, Spring과 프론트는 모델명·OAuth 토큰을 전달하지 않는다.

응답 형식: `application/x-ndjson` (HTTP chunked StreamingResponse)

#### 청크 이벤트 타입

| 이벤트 타입 | 페이로드 필드 | 설명 |
|------------|-------------|------|
| `token` | `delta`: string, `index`: int | 텍스트 토큰 청크 순차 전송 |
| `done` | `chat_id`: string, `tokens_in`: int, `tokens_out`: int, `duration_ms`: int | 스트리밍 완료 및 메타 정보 |
| `error` | `error_code`: string, `message`: string | 스트리밍 중 오류 발생 |

#### 청크 예시

```ndjson
{"type":"token","delta":"지금 헷갈린 지점은 p-value를 확률 자체로 읽은 데 있어요.","index":0}
{"type":"token","delta":" 이 슬라이드에서는 귀무가설 기준의 극단성으로 봐야 합니다.","index":1}
{"type":"done","chat_id":"chat-uuid-001","tokens_in":420,"tokens_out":180,"duration_ms":1860}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 403 | TUTOR_001 | 과외 또는 강의 접근 권한 없음 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |
| 404 | SLIDE_001 | 슬라이드를 찾을 수 없음 |
| 429 | SYSTEM_003 | 채팅 요청 과다 |
| 503 | AI_001 | AI 채팅 생성 실패 |

---

### GET /tutoring/{id}/lessons/{lessonId}/socratic/stream

소크라테스 follow-up 질문 상태를 조회한다. Spring은 SseEmitter를 구현하지 않으며, 질문 생성은 FastAPI에서 하고 결과는 DB 상태로 조회한다.

요청 형식: HTTP GET

인증: 필요

서버: Core(Spring) `https://api.ilkwa.com/api`

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

| 쿼리 파라미터 | 타입 | 필수 | 설명 |
|--------------|------|------|------|
| userId | string (UUID) | O | 사용자 ID |

#### 요청 예시

```http
GET /api/tutoring/aa1b2c3d/lessons/lesson-001-uuid/socratic/stream?userId=user-aa1b2c3d-uuid HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGci...
```

#### 성공 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| data.status | string | `"idle"` / `"question_ready"` / `"answered"` / `"closed"` |
| data.questionId | string / null | 질문 ID |
| data.text | string / null | 질문 본문 |
| data.context | string / null | 기준 슬라이드·맥락 |
| data.expiresAt | string / null | 만료 시각 |

```json
{
  "success": true,
  "data": {
    "status": "question_ready",
    "questionId": "q-7f8e9d0c-uuid",
    "text": "그럼 연속이 아닌데도 극한이 존재하는 예를 하나 들어볼래?",
    "context": "슬라이드 5 - 극한의 존재 조건",
    "expiresAt": "2026-04-13T10:17:30Z"
  },
  "timestamp": "2026-04-13T10:15:00Z"
}
```

> 클라이언트 구현 참고: 프론트는 학습 중 짧은 polling으로 상태를 갱신한다. 사용자 응답은 기존 `POST /socratic`로 제출한다.

##### 에러 응답 (연결 실패 시)

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 인증 실패 (토큰 만료/무효) |
| 410 | TUTOR_006 | 세션 만료 (강의 세션이 이미 종료됨) |

---

### POST /tutoring/{id}/lessons/{lessonId}/qa

강의 중 인터랙티브 Q&A를 처리한다. 텍스트 질문 또는 음성 질문(ASR)을 받아 RAG로 관련 자료를 검색하고, TTS 음성 답변을 생성한다.

요청 형식: `multipart/form-data` (음성 포함 시) 또는 `application/json` (텍스트만)

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

#### 텍스트 질문 시 (`application/json`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| type | string | O | 질문 유형 (`"text"`) |
| content | string | O | 질문 내용 (최대 1000자) |
| currentSlideIndex | integer | X | 현재 슬라이드 인덱스 (맥락 파악용) |

#### 음성 질문 시 (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| type | string | O | 질문 유형 (`"voice"`) |
| audioFile | file | O | 음성 파일 (`.webm` / `.wav` / `.m4a`, 최대 25MB) |
| currentSlideIndex | integer | X | 현재 슬라이드 인덱스 |

#### 요청 예시 (텍스트)

```json
{
  "type": "text",
  "content": "근의 공식에서 판별식이 0이면 어떻게 되나요?",
  "currentSlideIndex": 3
}
```

#### 요청 예시 (음성)

```http
POST /api/tutoring/aa1b2c3d/lessons/lesson-001-uuid/qa HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: multipart/form-data; boundary=----Boundary

------Boundary
Content-Disposition: form-data; name="type"

voice
------Boundary
Content-Disposition: form-data; name="currentSlideIndex"

3
------Boundary
Content-Disposition: form-data; name="audioFile"; filename="question.webm"
Content-Type: audio/webm

(바이너리 오디오 데이터)
------Boundary--
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.answer | string | AI 답변 텍스트 |
| data.audioUrl | string | TTS 생성 오디오 URL |
| data.audioDuration | number | 오디오 길이 (초) |
| data.transcribedQuestion | string / null | 음성 인식 결과 (voice 타입일 때만) |
| data.relatedSlides | integer[] | 관련 슬라이드 인덱스 배열 |
| data.ragSources | array | RAG 검색 출처 |
| data.ragSources[].documentTitle | string | 문서 제목 |
| data.ragSources[].pageNumber | integer / null | 페이지 번호 |
| data.ragSources[].relevanceScore | number | 관련도 점수 (0.0~1.0) |
| data.followUpSuggestions | string[] | 추천 후속 질문 |

```json
{
  "success": true,
  "data": {
    "answer": "판별식(D = b² - 4ac)이 0이면 중근을 가져요! 즉, 두 근이 같은 값이 되는 거야. 예를 들어 x² - 4x + 4 = 0 이면 D = 16 - 16 = 0이고, 근은 x = 2 (중근)이야.",
    "audioUrl": "https://cdn.ilkwa.com/audio/qa/resp-bb2c3d4e.mp3",
    "audioDuration": 12.5,
    "transcribedQuestion": null,
    "relatedSlides": [3, 5],
    "ragSources": [
      {
        "documentTitle": "이차방정식 교재",
        "pageNumber": 42,
        "relevanceScore": 0.95
      }
    ],
    "followUpSuggestions": [
      "판별식이 음수면 어떻게 되나요?",
      "중근의 예시를 더 보여주세요"
    ]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | type에 따른 필수 필드 누락 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |
| 503 | AI_002 | AI 모델 응답 시간 초과 |
| 503 | AI_003 | 음성 인식(ASR) 실패 |

---

### POST /tutoring/{id}/lessons/{lessonId}/socratic/generate

사용자 학습 진행 중 맥락 기반 follow-up 질문을 생성한다. Claude를 호출하여 질문을 만들고 DB에 저장한다. 프론트는 `GET /socratic/stream` 상태 조회로 질문을 가져온다.

요청 형식: `application/json`

인증: 필요

서버: AI (FastAPI, 생성 전용) `https://ai.ilkwa.com/api`

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

#### 요청 바디

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| userId | string (UUID) | O | 사용자 ID |
| 진행상태 | object | O | 현재 학습 진행 상태 |
| 진행상태.slideIndex | integer | O | 현재 보고 있는 슬라이드 인덱스 |
| 진행상태.dwellTime | number | O | 현재 슬라이드 체류 시간 (초) |
| 진행상태.comprehensionScore | number | O | 누적 이해도 점수 (0.0~1.0) |

#### 요청 예시

```json
{
  "userId": "user-aa1b2c3d-uuid",
  "진행상태": {
    "slideIndex": 5,
    "dwellTime": 42.7,
    "comprehensionScore": 0.62
  }
}
```

#### 처리 흐름

1. Claude 호출 → 현재 슬라이드/체류시간/이해도 기반 맥락 분석
2. follow-up 질문 생성 (소크라테스식 유도)
3. 질문 상태를 DB에 저장
4. 클라이언트는 상태 조회로 질문을 수신

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.questionId | string (UUID) | 생성된 질문 ID |
| data.storedForPolling | boolean | 상태 조회로 가져갈 수 있게 저장됐는지 여부 |
| data.generatedAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "questionId": "q-7f8e9d0c-uuid",
    "storedForPolling": true,
    "generatedAt": "2026-04-13T10:15:30Z"
  },
  "timestamp": "2026-04-13T10:15:30Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 인증 실패 (토큰 만료/무효) |
| 404 | TUTOR_003 | 수업을 찾을 수 없음 |
| 502 | AI_004 | Claude 호출 실패 (외부 모델 응답 오류) |

---

### POST /tutoring/{id}/lessons/{lessonId}/socratic

소크라테스식 대화로 학습자의 이해를 이끌어낸다. 학생의 답변을 평가하고 유도 질문을 생성한다.

요청 형식: `multipart/form-data` (음성) 또는 `application/json` (텍스트)

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

#### 텍스트 답변 시 (`application/json`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| questionContext | string | O | 원래 질문 내용 |
| studentAnswer | string | O | 학생의 텍스트 답변 |

#### 음성 답변 시 (`multipart/form-data`)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| questionContext | string | O | 원래 질문 내용 |
| audioFile | file | O | 학생 답변 음성 파일 (`.webm` / `.wav`, 최대 25MB) |

#### 요청 예시 (텍스트)

```json
{
  "questionContext": "극한값이 존재하려면 어떤 조건이 필요할까?",
  "studentAnswer": "함수가 연속이면 극한이 존재해요"
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.judgment | string | 판정 결과 (`"correct"` / `"partial"` / `"incorrect"`) |
| data.response | string | AI 응답 텍스트 (유도 질문 포함) |
| data.audioUrl | string | TTS 응답 오디오 URL |
| data.audioDuration | number | 오디오 길이 (초) |
| data.hint | string / null | 힌트 (incorrect/partial일 때) |
| data.partialCredit | number | 부분 점수 (0.0~1.0) |
| data.guidingQuestion | string / null | 유도 질문 (소크라테스식 후속 질문) |
| data.transcribedAnswer | string / null | 음성 인식 결과 (음성 답변일 때) |

```json
{
  "success": true,
  "data": {
    "judgment": "partial",
    "response": "좋은 생각이야! 그런데 조금 더 생각해볼까? 연속이 아니어도 극한이 존재할 수 있을까?",
    "audioUrl": "https://cdn.ilkwa.com/audio/socratic/resp-cc3d4e5f.mp3",
    "audioDuration": 6.8,
    "hint": "x=0에서 정의되지 않지만 극한은 존재하는 함수를 떠올려봐",
    "partialCredit": 0.4,
    "guidingQuestion": "예를 들어 f(x) = sin(x)/x 에서 x->0일 때는 어떨까?",
    "transcribedAnswer": null
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |
| 503 | AI_002 | AI 모델 응답 시간 초과 |

---

### POST /tutoring/{id}/lessons/{lessonId}/comprehension-check

상태 조회로 전달된 이해도 체크 질문에 대한 사용자 응답을 제출한다.

요청 형식: `application/json`

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| questionId | string | O | 상태 조회에서 전달받은 질문 ID |
| selectedOption | integer | O | 선택한 답변 인덱스 (0부터) |
| responseTimeMs | integer | X | 응답까지 걸린 시간 (밀리초) |

#### 요청 예시

```json
{
  "questionId": "check-001",
  "selectedOption": 0,
  "responseTimeMs": 5200
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.correct | boolean | 정답 여부 |
| data.correctOption | integer | 정답 인덱스 |
| data.explanation | string | 해설 텍스트 |
| data.audioUrl | string | TTS 해설 오디오 URL |
| data.comprehensionScore | number | 누적 이해도 점수 (0.0~1.0) |

```json
{
  "success": true,
  "data": {
    "correct": true,
    "correctOption": 0,
    "explanation": "맞아! 좌극한과 우극한이 같아야 극한값이 존재해. 잘 이해하고 있구나!",
    "audioUrl": "https://cdn.ilkwa.com/audio/check/resp-dd4e5f6g.mp3",
    "comprehensionScore": 0.85,
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | questionId가 유효하지 않거나 selectedOption 범위 초과 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |

---

### POST /tutoring/{id}/lessons/{lessonId}/bookmark

강의 중 특정 슬라이드에 북마크를 추가한다.

요청 형식: `application/json`

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| slideIndex | integer | O | 북마크할 슬라이드 인덱스 |
| note | string | X | 북마크 메모 (최대 200자) |

#### 요청 예시

```json
{
  "slideIndex": 5,
  "note": "좌극한 우극한 개념 중요 - 시험에 자주 나옴"
}
```

응답 형식: `application/json`

##### 성공 응답 (201 Created)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 북마크 ID |
| data.slideIndex | integer | 슬라이드 인덱스 |
| data.note | string / null | 메모 |
| data.createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "id": "bookmark-ee5f6g7h",
    "slideIndex": 5,
    "note": "좌극한 우극한 개념 중요 - 시험에 자주 나옴",
    "createdAt": "2026-04-12T09:20:00Z"
  },
  "message": "북마크가 추가되었습니다.",
  "timestamp": "2026-04-12T09:20:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | slideIndex가 범위를 벗어남 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |

---

### GET /tutoring/{id}/lessons/{lessonId}/notes

강의에 대한 AI 자동 생성 노트와 사용자 메모를 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

#### 요청 예시

```http
GET /api/tutoring/aa1b2c3d/lessons/lesson-001-uuid/notes HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.aiNotes | object | AI 자동 생성 노트 |
| data.aiNotes.summary | string | 강의 요약 |
| data.aiNotes.keyPoints | string[] | 핵심 포인트 배열 |
| data.aiNotes.formulas | array | 수식 목록 |
| data.aiNotes.formulas[].latex | string | LaTeX 수식 |
| data.aiNotes.formulas[].description | string | 수식 설명 |
| data.aiNotes.generatedAt | string (ISO 8601) | 노트 생성 시각 |
| data.userNotes | array | 사용자 메모 목록 |
| data.userNotes[].id | string (UUID) | 메모 ID |
| data.userNotes[].content | string | 메모 내용 |
| data.userNotes[].type | string | 메모 타입 (`"key-point"` / `"summary"` / `"formula"` / `"memo"`) |
| data.userNotes[].slideIndex | integer / null | 관련 슬라이드 인덱스 |
| data.userNotes[].timestamp | number / null | 강의 내 시간 위치 (초) |
| data.userNotes[].starred | boolean | 별표 여부 |
| data.userNotes[].userMemo | string / null | 추가 사용자 메모 |
| data.userNotes[].createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "aiNotes": {
      "summary": "이번 강의에서는 극한의 정의와 좌극한/우극한 개념을 학습했습니다.",
      "keyPoints": [
        "극한값이 존재하려면 좌극한 = 우극한",
        "함수값과 극한값은 다를 수 있음",
        "수렴과 발산의 정의"
      ],
      "formulas": [
        {"latex": "\\lim_{x \\to a} f(x) = L", "description": "극한의 정의"},
        {"latex": "\\lim_{x \\to a^-} f(x) = \\lim_{x \\to a^+} f(x) = L", "description": "극한값 존재 조건"}
      ],
      "generatedAt": "2026-04-05T20:35:00Z"
    },
    "userNotes": [
      {
        "id": "note-ff6g7h8i",
        "content": "이 부분 다시 복습하기",
        "type": "memo",
        "slideIndex": 5,
        "timestamp": 120.5,
        "starred": true,
        "userMemo": "시험 전에 반드시 재확인",
        "createdAt": "2026-04-05T20:20:00Z"
      }
    ]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |

---

### POST /tutoring/{id}/lessons/{lessonId}/notes

사용자 메모를 추가한다.

요청 형식: `application/json`

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| content | string | O | 메모 내용 (최대 1000자) |
| slideIndex | integer | X | 관련 슬라이드 인덱스 |
| timestamp | number | X | 강의 내 시간 위치 (초) |

#### 요청 예시

```json
{
  "content": "판별식 부분 헷갈림 - 나중에 다시 보기",
  "slideIndex": 7,
  "timestamp": 210.3
}
```

응답 형식: `application/json`

##### 성공 응답 (201 Created)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 생성된 메모 ID |
| data.content | string | 메모 내용 |
| data.type | string | 메모 타입 (자동 분류됨) |
| data.slideIndex | integer / null | 슬라이드 인덱스 |
| data.timestamp | number / null | 시간 위치 |
| data.starred | boolean | 별표 여부 (기본값: `false`) |
| data.createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "id": "note-gg7h8i9j",
    "content": "판별식 부분 헷갈림 - 나중에 다시 보기",
    "type": "memo",
    "slideIndex": 7,
    "timestamp": 210.3,
    "starred": false,
    "createdAt": "2026-04-12T09:25:00Z"
  },
  "message": "메모가 추가되었습니다.",
  "timestamp": "2026-04-12T09:25:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 내용이 비어있거나 길이 초과 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |

---

### PUT /tutoring/{id}/lessons/{lessonId}/notes/{noteId}/star

노트에 별표를 토글한다.

요청 형식: Path Parameter만 사용 (본문 없음)

인증: 필요

서버: Core

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과외 ID |
| lessonId | string (UUID) | 강의 ID |
| noteId | string (UUID) | 메모 ID |

#### 요청 예시

```http
PUT /api/tutoring/aa1b2c3d/lessons/lesson-001-uuid/notes/note-ff6g7h8i/star HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.noteId | string (UUID) | 메모 ID |
| data.starred | boolean | 토글된 별표 상태 |

```json
{
  "success": true,
  "data": {
    "noteId": "note-ff6g7h8i",
    "starred": true
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_003 | 메모를 찾을 수 없음 |

---

## 6. 과제 API

> 서버: FastAPI (AI)  
> Base URL: `https://ai.ilkwa.com/api`

---

### POST /assignments/generate

과외 강의 완료 후 과제를 자동 생성한다. `availableTime`에 따라 문제 유형이 결정된다 (10분: 객관식, 30분: 혼합, 60분+: 서술형 포함).

> 모드 구분: 기본 호출(강의 완료 자동 트리거)은 `tutoringId` + `lessonId`만 필요하다. 관리자가 수동으로 과제를 생성할 경우 `axes` 파라미터를 추가해 출제 축(핵심/응용/약점)을 직접 지정할 수 있다. 수동 생성 결과는 텔레그램 DM으로 학생에게 즉시 발송된다(`telegram_sent: true`). 자동 트리거 경로에서는 `axes` 생략 시 기본값(`["core"]`)이 적용된다.

요청 형식: `application/json`

인증: 필요

서버: AI

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| tutoringId | string (UUID) | O | 과외 ID |
| lessonId | string (UUID) | O | 강의 ID |
| questionCount | integer | X | 문제 수 (기본값: 10, 최대: 30) |
| availableTime | integer | X | 풀이 가용 시간 (분, 기본값: 30) |
| difficultyDistribution | object | X | 난이도 분배 |
| difficultyDistribution.easy | integer | X | 쉬운 문제 수 |
| difficultyDistribution.medium | integer | X | 중간 문제 수 |
| difficultyDistribution.hard | integer | X | 어려운 문제 수 |
| axes | array\<string\> | X | 출제 축 (관리자 수동 생성 전용). 값: `"core"` / `"practical"` / `"weak"`. 기본값: `["core"]` |
| weightOverride | object | X | 출제 가중치 수동 튜닝 (관리자 전용) |
| weightOverride.alpha | float | X | 질문밀도 가중치 (기본값 0.4, 범위 0~1) |
| weightOverride.beta | float | X | 되감기 비율 가중치 (기본값 0.3, 범위 0~1) |
| weightOverride.gamma | float | X | 오답률 가중치 (기본값 0.3, 범위 0~1) |
| expectedInputType | string | O | 기대 제출 유형 (L2 검증 기준). 값: `"code_python"` / `"text_essay"` / `"handwritten_solution"` / `"audio_oral"` / `"project_archive"` |
| allowedExtensions | array\<string\> | X | 허용 확장자 목록. `expectedInputType`에서 자동 파생되나 교사가 override 가능. 예: `[".py"]`, `[".png", ".jpg", ".pdf"]` |

#### 요청 예시

```json
{
  "tutoringId": "aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "lessonId": "lesson-001-uuid",
  "questionCount": 10,
  "availableTime": 30,
  "difficultyDistribution": {
    "easy": 3,
    "medium": 4,
    "hard": 3
  },
  "expectedInputType": "handwritten_solution",
  "allowedExtensions": [".png", ".jpg", ".jpeg", ".pdf"],
  "weightOverride": {
    "alpha": 0.5,
    "beta": 0.3,
    "gamma": 0.2
  }
}
```

응답 형식: `application/json`

##### 성공 응답 (201 Created)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 생성된 과제 ID |
| data.tutoringId | string (UUID) | 과외 ID |
| data.lessonId | string (UUID) | 강의 ID |
| data.title | string | 과제 제목 |
| data.questionCount | integer | 문제 수 |
| data.totalPoints | integer | 총 배점 |
| data.status | string | 상태 (`"PENDING"`) |
| data.dueDate | string (ISO 8601) | 제출 마감일 |
| data.createdAt | string (ISO 8601) | 생성 시각 |
| data.selectionSignals | object | 출제 가중치 근거 |
| data.selectionSignals.qaWeight | float | 질문빈도 기여도 (0~1) |
| data.selectionSignals.rewindWeight | float | 되감기 기여도 (0~1) |
| data.selectionSignals.wrongWeight | float | 오답률 기여도 (0~1) |
| data.selectionSignals.topSlides | array\<string\> | 출제 후보로 선별된 상위 슬라이드 ID 목록 |
| data.telegramSent | boolean | 텔레그램 DM 발송 여부. 관리자 수동 생성(`axes` 지정) 시 true, 자동 트리거 시 false |
| data.expectedInputType | string | 지정된 기대 제출 유형 |
| data.allowedExtensions | array\<string\> | 이 과제에서 허용하는 확장자 목록 |

```json
{
  "success": true,
  "data": {
    "id": "assign-hh8i9j0k",
    "tutoringId": "aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
    "lessonId": "lesson-001-uuid",
    "title": "극한의 개념 - 과제",
    "questionCount": 10,
    "totalPoints": 100,
    "status": "PENDING",
    "dueDate": "2026-04-14T23:59:59Z",
    "createdAt": "2026-04-12T09:40:00Z",
    "telegramSent": false,
    "expectedInputType": "handwritten_solution",
    "allowedExtensions": [".png", ".jpg", ".jpeg", ".pdf"],
    "selectionSignals": {
      "qaWeight": 0.42,
      "rewindWeight": 0.31,
      "wrongWeight": 0.27,
      "topSlides": ["slide-003", "slide-007", "slide-012", "slide-015", "slide-021"]
    }
  },
  "message": "과제가 생성되었습니다.",
  "timestamp": "2026-04-12T09:40:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락, `expectedInputType` 미지정, difficultyDistribution 합계 불일치 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 과외를 찾을 수 없음 |
| 404 | TUTOR_003 | 강의를 찾을 수 없음 |
| 503 | AI_001 | AI 과제 생성 실패 |

---

### GET /assignments

내 과제 목록을 조회한다.

요청 형식: Query Parameter

인증: 필요

서버: AI

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| page | integer | X | 0 | 페이지 번호 |
| size | integer | X | 20 | 페이지 크기 |
| status | string | X | - | 상태 필터 (`"PENDING"` / `"SUBMITTED"` / `"GRADING"` / `"GRADED"`) |
| tutoringId | string | X | - | 과외 ID로 필터 |
| sort | string | X | `dueDate,asc` | 정렬 기준 |

#### 요청 예시

```http
GET /api/assignments?page=0&size=10&status=PENDING HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.content | array | 과제 목록 |
| data.content[].id | string (UUID) | 과제 ID |
| data.content[].title | string | 과제 제목 |
| data.content[].tutoringTitle | string | 과외 제목 |
| data.content[].lessonTitle | string | 강의 제목 |
| data.content[].questionCount | integer | 문제 수 |
| data.content[].totalPoints | integer | 총 배점 |
| data.content[].status | string | 상태 |
| data.content[].score | integer / null | 점수 (채점 완료 시) |
| data.content[].dueDate | string (ISO 8601) | 마감일 |
| data.content[].createdAt | string (ISO 8601) | 생성 시각 |
| data.page / size / totalElements / totalPages / hasNext / hasPrevious | - | 페이지네이션 |

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "assign-hh8i9j0k",
        "title": "극한의 개념 - 과제",
        "tutoringTitle": "미적분 기초",
        "lessonTitle": "극한의 개념",
        "questionCount": 10,
        "totalPoints": 100,
        "status": "PENDING",
        "score": null,
        "dueDate": "2026-04-14T23:59:59Z",
        "createdAt": "2026-04-12T09:40:00Z"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 5,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |

---

### GET /assignments/{id}

과제의 상세 정보와 문제를 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과제 ID |

#### 요청 예시

```http
GET /api/assignments/assign-hh8i9j0k HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 과제 ID |
| data.title | string | 과제 제목 |
| data.status | string | 상태 |
| data.dueDate | string (ISO 8601) | 마감일 |
| data.totalPoints | integer | 총 배점 |
| data.questions | array | 문제 배열 |
| data.questions[].id | string | 문제 ID |
| data.questions[].questionNumber | integer | 문제 번호 |
| data.questions[].type | string | 문제 유형 (`"multiple-choice"` / `"short-answer"` / `"essay"`) |
| data.questions[].content | string | 문제 내용 (마크다운) |
| data.questions[].imageUrl | string / null | 문제 이미지 URL |
| data.questions[].options | string[] / null | 객관식 선택지 (multiple-choice일 때) |
| data.questions[].difficulty | string | 난이도 |
| data.questions[].topic | string | 관련 주제 |
| data.questions[].points | integer | 배점 |

```json
{
  "success": true,
  "data": {
    "id": "assign-hh8i9j0k",
    "title": "극한의 개념 - 과제",
    "status": "PENDING",
    "dueDate": "2026-04-14T23:59:59Z",
    "totalPoints": 100,
    "questions": [
      {
        "id": "q-1",
        "questionNumber": 1,
        "type": "multiple-choice",
        "content": "다음 중 $\\lim_{x \\to 2} \\frac{x^2-4}{x-2}$의 값은?",
        "imageUrl": null,
        "options": ["2", "4", "0", "존재하지 않음"],
        "difficulty": "easy",
        "topic": "극한 계산",
      },
      {
        "id": "q-2",
        "questionNumber": 2,
        "type": "short-answer",
        "content": "$\\lim_{x \\to 0} \\frac{\\sin x}{x}$의 값을 구하시오.",
        "imageUrl": null,
        "options": null,
        "difficulty": "medium",
        "topic": "삼각함수 극한",
      },
      {
        "id": "q-3",
        "questionNumber": 3,
        "type": "essay",
        "content": "극한값이 존재하지 않는 경우를 3가지 이상 서술하고, 각각의 예시를 제시하시오.",
        "imageUrl": null,
        "options": null,
        "difficulty": "hard",
        "topic": "극한의 이해",
      }
    ]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | ASSIGN_001 | 과제를 찾을 수 없음 |

---

### POST /assignments/{id}/submit

과제 풀이를 제출한다. 객관식은 선택 인덱스, 주관식/서술형은 텍스트 답변을 전송한다. 서술형에 풀이 이미지를 첨부할 수 있다. 이미지 첨부 시 `[Spring Boot → S3 업로드] → [FastAPI (Sonnet 4.6 VLM 직접 판독)] → [FastAPI 채점(Claude)]` 순서로 처리한다. 재제출은 `queued` 또는 `grading` 상태 구간에서만 허용되며, 공식 점수는 첫 제출(공식 제출) 기준으로 확정된다. 재제출 이력은 `submission_retry` 테이블에 별도 저장된다.

> 과제(텔레그램 채점): `.py` / `.txt` / `.md` / 이미지 / `.pdf` / `.zip` / 음성 파일 제출은 `POST /assignments/{id}/submit/telegram` 경로를 통해 FastAPI `app/telegram/` 라우터가 직접 처리한다. 텔레그램 웹훅 수신 즉시 비동기 채점(AssignmentGrader)이 실행되므로 배치 스케줄을 기다리지 않는다.

요청 형식: `multipart/form-data`

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과제 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| answers | string (JSON) | O | 답변 배열의 JSON 문자열 |
| answers[].questionNumber | integer | O | 문제 번호 |
| answers[].answer | string / integer | O | 답변 (객관식: 선택 인덱스 숫자, 주관식/서술형: 텍스트) |
| answers[].steps | string[] | X | 풀이 과정 (단계별 기록, Step-Trace용) |
| imageFile_q{번호} | file | X | 풀이 이미지 (문제별, `.jpg`/`.png`, 최대 10MB). 필드명 예: `imageFile_q3` |

#### 요청 예시

```http
POST /api/assignments/assign-hh8i9j0k/submit HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Content-Type: multipart/form-data; boundary=----Boundary

------Boundary
Content-Disposition: form-data; name="answers"

[
  {"questionNumber": 1, "answer": 1},
  {"questionNumber": 2, "answer": "1"},
  {"questionNumber": 3, "answer": "1. 좌극한과 우극한이 다른 경우: 예) |x|/x에서 x->0\n2. 함수값이 무한대로 발산: 예) 1/x에서 x->0\n3. 진동하는 경우: 예) sin(1/x)에서 x->0", "steps": ["좌극한≠우극한 사례 분석", "발산 사례 분석", "진동 사례 분석"]}
]
------Boundary
Content-Disposition: form-data; name="imageFile_q3"; filename="solution_q3.jpg"
Content-Type: image/jpeg

(바이너리 이미지 데이터)
------Boundary--
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.submissionId | string (UUID) | 제출 ID |
| data.status | string | 상태 (`"submitted"` — 제출 완료) |
| data.submittedAt | string (ISO 8601) | 제출 시각 |

```json
{
  "success": true,
  "data": {
    "submissionId": "sub-ii9j0k1l",
    "status": "submitted",
    "submittedAt": "2026-04-12T10:00:00Z"
  },
  "message": "제출이 완료되었습니다.",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | answers 형식 오류, 필수 문제 미답변 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | ASSIGN_001 | 과제를 찾을 수 없음 |
| 409 | ASSIGN_003 | 이미 제출된 과제 |
| 410 | ASSIGN_002 | 제출 기한 초과 |

#### `.zip` 업로드 검증 규칙

풀이 파일을 제출할 때 FastAPI 수신 레이어에서 Python stdlib `zipfile`로 아래 검증을 순서대로 한다. 전부 통과한 경우에만 압축 해제 후 후속 파이프라인으로 전달된다. 검증 파라미터는 설정값으로 관리하며 MVP 기본값은 아래 표와 같다.

##### 검증 파라미터 (설정값)

| 항목 | MVP 기본값 | 비고 |
|------|-----------|------|
| 허용 외부 확장자 | `.zip`, `.pdf`, `.png`, `.jpg`/`.jpeg`, `.webp` | Content-Type 검증 병행 |
| 최대 업로드 용량 | 20 MB | 경로 무관 동일 |
| 최대 해제 후 총 용량 (zip 한정) | 100 MB | zip bomb 방어 |
| 압축률 상한 (zip 한정) | 100:1 | bomb 탐지 |
| 내부 파일 수 상한 (zip 한정) | 30개 | 통째 업로드 차단 |
| 허용 내부 확장자 (zip 한정) | `.pdf`, `.png`, `.jpg`/`.jpeg`, `.webp` | 화이트리스트 |
| 중첩 zip | 금지 | 즉시 거절 |
| 단건 업로드 | 허용 | `.pdf`/`.png`/`.jpg`/`.jpeg`/`.webp` 단일 파일 직접 제출 |

##### 검증 순서 (Python `zipfile` 기반)

1. Content-Type + 외부 확장자 검사 → 허용 5종(`.zip`/`.pdf`/`.png`/`.jpg`/`.jpeg`/`.webp`)에 해당하지 않으면 **415** 반환
2. 업로드 용량 ≤ 20 MB → 초과 시 **413** 반환
3. `.zip` 경로이면 4~9 진행 / 단건 직접 업로드이면 10으로 점프
4. `zipfile.ZipFile` 헤더 파싱 (해제 전)
5. 내부 엔트리 수 ≤ 30 → 초과 시 **422** 반환 (`too_many_files`)
6. 엔트리별 확장자가 내부 화이트리스트(`.pdf`/`.png`/`.jpg`/`.jpeg`/`.webp`)에 있는지 확인 → 1건이라도 없으면 **422** 반환 (`unsupported_inner_extension`)
7. `ZipInfo.file_size` 합계 ≤ 100 MB → 초과 시 **422** 반환 (`total_uncompressed_too_large`)
8. 엔트리 압축률(`file_size / compress_size`) ≤ 100 → 초과 시 **422** 반환 (`compression_ratio_too_high`)
9. 디렉터리 순회(`../`), 절대경로 엔트리, 중첩 zip 탐지 → 발견 시 **422** 반환 (`path_traversal` 또는 `nested_zip_forbidden`)
10. (단건/zip 통과분 공통) 추출된 개별 파일이 `.pdf`이면 PDF 처리 경로로, 이미지(`.png`/`.jpg`/`.jpeg`/`.webp`)이면 OCR 파이프라인으로 전달

##### 검증 관련 HTTP 에러 코드

| 코드 | 사유 |
|------|------|
| 413 Payload Too Large | 업로드 파일 용량 초과 (zip 해제 후 총량 초과 포함) |
| 415 Unsupported Media Type | 허용 5종 외 확장자·Content-Type |
| 422 Unprocessable Entity | 내부 파일 수 초과 / 허용 외 내부 확장자 / 압축률 비정상 / 경로 공격 / 중첩 zip |

##### 에러 응답 바디 예시

```json
{
  "error_code": "INVALID_SUBMISSION_ARCHIVE",
  "reason": "unsupported_inner_extension",
  "detail": "zip 내부 허용 확장자는 .pdf/.png/.jpg/.jpeg/.webp 입니다. 업로드된 zip에 허용되지 않는 확장자가 포함돼 있습니다.",
  "allowed_extensions": [".pdf", ".png", ".jpg", ".jpeg", ".webp"],
  "file_count": 17,
  "max_file_count": 30
}
```

`reason` enum 값: `unsupported_outer_extension`, `outer_too_large`, `too_many_files`, `unsupported_inner_extension`, `total_uncompressed_too_large`, `compression_ratio_too_high`, `path_traversal`, `nested_zip_forbidden`, `ocr_all_failed`

- `unsupported_outer_extension`: 허용 외부 확장자 5종(`.zip`/`.pdf`/`.png`/`.jpg`/`.jpeg`/`.webp`) 외 파일 제출 시 반환
- `unsupported_inner_extension`: zip 내부에 허용 내부 확장자(`.pdf`/`.png`/`.jpg`/`.jpeg`/`.webp`) 외 파일이 포함된 경우 반환
- `ocr_all_failed`: 전체 페이지 OCR 인식 실패 (0/N 페이지). submission.status가 `failed`로 전이됨

##### PDF 처리 경로

PDF 렌더링·텍스트 추출은 Python `pypdfium2`에서 한다.

- 디지털 PDF(텍스트 레이어 존재) 감지 시 텍스트 레이어를 우선 추출하여 OCR을 스킵한다. 토큰·비용 절감 효과.
- 스캔 PDF(이미지만 있음)나 텍스트 레이어가 빈 페이지는 300 DPI로 래스터화한 뒤 Sonnet 4.6 VLM에 넘긴다.
- 일부 페이지만 텍스트가 있는 혼합 PDF는 페이지별로 분기 처리한다. 최종 출력은 페이지 순서대로 병합.
- 텍스트 추출에 실패하거나 전체 빈 페이지인 경우 해당 페이지만 OCR 경로로 폴백.

##### 검증 경계

검증은 Python stdlib `zipfile`로만 한다. 검증을 통과한 이미지는 Sonnet 4.6 VLM이 직접 읽는다. 2026-04-19 이전에는 PaddleOCR ONNX로 읽었다.

---

### POST /assignments/{id}/submit/telegram

텔레그램을 통해 과제 파일을 제출한다. FastAPI `app/telegram/` 라우터가 전담 처리하며 Spring Boot는 관여하지 않는다.

허용 파일 형식: `.py`, `.txt`, `.md`, `.png`, `.jpg`, `.jpeg`, `.webp`, `.pdf`, `.zip`, `.mp3`, `.m4a`, `.wav`, `.docx`, `.pptx`, `.xlsx`

> 오피스 문서 계열(`.docx`/`.pptx`/`.xlsx`)은 MarkItDown으로 Markdown 정규화 후 채점 LLM 입력으로 사용한다. 상세 규칙은 계약결정사항의 §14 MarkItDown 전처리 섹션 참조.
>
> 한글 파일(`.hwp`)은 향후 확장 후보이며 현재는 미허용이다.

AssignmentGrader는 파일 타입을 판단하여 아래와 같이 분기 처리한다.

| 파일 타입 | 처리 경로 |
|----------|----------|
| 이미지 (`.png` / `.jpg` / `.jpeg` / `.webp`), `.pdf` | ①a Sonnet 4.6 VLM 직접 처리 (OCR 레이어 없음) → 채점 |
| `.py` | AST 파싱 or 실행 결과 검증 → 채점 |
| `.txt`, `.md` | 자유서술 평가 → 채점 |
| `.docx` / `.pptx` / `.xlsx` | MarkItDown → Markdown 정규화 → 채점 |
| `.zip` | 압축 해제 → 포함 파일 타입별 위 분기 재적용 |
| 음성 (`.mp3` / `.m4a` / `.wav`) | Whisper Turbo → 텍스트 변환 → 채점 |

파이프라인은 아래 단계로 처리된다.

1. **[텔레그램 봇]** 학생으로부터 파일 수신
2. **[FastAPI `app/telegram/`]** Telegram getFile → S3 업로드 → Submission 행 생성(`status=queued`)
3. **[FastAPI AssignmentGrader]** 파일 타입 판단 → 처리 분기 → 채점 → 즉시 해설 생성
4. **[FastAPI → Spring]** `POST /internal/submissions/{id}/ai-result` 채점 완료 콜백
5. **[Spring]** DB 저장 → 텔레그램 DM 채점 결과 회신

요청 형식: `application/json` (내부 API — FastAPI `app/telegram/` 라우터가 웹훅 수신 후 자체 처리)

인증: Webhook Secret (`X-Telegram-Bot-Api-Secret-Token` 헤더)

서버: AI (FastAPI)

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과제 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| telegramFileId | string | O | 텔레그램 파일 ID |
| mimeType | string | O | MIME 타입 (예: `"text/x-python"`, `"text/plain"`, `"image/jpeg"`, `"application/pdf"`, `"audio/mpeg"`) |
| questionNumbers | integer[] | O | 이 파일에 해당하는 문제 번호 배열 |

#### 요청 예시 (이미지 파일)

```json
{
  "telegramFileId": "AgACAgIAAxkBAAI...",
  "mimeType": "image/jpeg",
  "questionNumbers": [1, 2, 3]
}
```

#### 요청 예시 (파이썬 파일)

```json
{
  "telegramFileId": "BgACAgIAAxkBAAJ...",
  "mimeType": "text/x-python",
  "questionNumbers": [4, 5]
}
```

응답 형식: `application/json`

##### 성공 응답 (202 Accepted)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.submissionId | string (UUID) | 제출 ID |
| data.status | string | 상태 (`"grading"` — AssignmentGrader 채점 진행 중) |

```json
{
  "success": true,
  "data": {
    "submissionId": "sub-jj0k1l2m",
    "status": "grading"
  },
  "message": "텔레그램 제출이 접수되었습니다. 채점 완료 후 텔레그램 DM으로 결과를 전달합니다.",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락 또는 허용되지 않는 파일 형식 |
| 415 | REJECTED_TYPE_MISMATCH | L2 검증 실패 — 제출 파일 유형이 과제의 `expected_input_type`과 일치하지 않음. 응답 바디에 `rejection_reason: "REJECTED_TYPE_MISMATCH"`, `expected_input_type`, `allowed_extensions` 포함 |
| 404 | ASSIGN_001 | 과제를 찾을 수 없음 |
| 503 | AI_003 | AssignmentGrader 처리 실패 |

##### L2 거절 응답 예시 (415)

```json
{
  "success": false,
  "rejection_reason": "REJECTED_TYPE_MISMATCH",
  "expected_input_type": "handwritten_solution",
  "allowed_extensions": [".png", ".jpg", ".jpeg", ".pdf"],
  "submitted_extension": ".py",
  "message": "이 과제는 손풀이 제출 과제입니다. .png / .jpg / .jpeg / .pdf 파일만 제출할 수 있습니다."
}
```

---

### GET /assignments/{id}/result

채점 결과를 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과제 ID |

#### 요청 예시

```http
GET /api/assignments/assign-hh8i9j0k/result HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.assignmentId | string (UUID) | 과제 ID |
| data.score | integer | 획득 점수 |
| data.totalPoints | integer | 총 배점 |
| data.correctCount | integer | 맞은 문제 수 |
| data.totalQuestions | integer | 전체 문제 수 |
| data.grade | string | 등급 (`"A+"` ~ `"F"`) |
| data.gradedAt | string (ISO 8601) | 채점 완료 시각 |
| data.questionResults | array | 문제별 결과 |
| data.questionResults[].questionNumber | integer | 문제 번호 |
| data.questionResults[].isCorrect | boolean | 정답 여부 |
| data.questionResults[].score | integer | 획득 점수 |
| data.questionResults[].maxPoints | integer | 배점 |
| data.questionResults[].userAnswer | string | 학생 답변 |
| data.questionResults[].correctAnswer | string | 정답 |
| data.questionResults[].explanation | string | 해설 |
| data.questionResults[].errorType | string / null | 오류 유형 (`"계산"` / `"개념"` / `"공식"` / `"해석"` / null) |
| data.overallFeedback | string | 종합 피드백 |
| data.status | string | 채점 상태. `queued` / `grading` / `done` / `failed` |
| data.weakConceptsAdded | array\<string\> | 이번 채점 결과로 `weakness_profile`에 새로 추가된 약점 개념 ID 목록. 채점 중이거나 오답 없는 경우 빈 배열 |

> 채점 경로 안내: 퀴즈(소크라테스 세션)와 과제 채점은 별도 경로로 처리된다. 퀴즈 오답은 ②c 실시간 소크라테스(QAResponder)가 즉시 `weakness_profile`을 갱신하고, 과제 오답은 ⑥ 텔레그램 웹훅 비동기 채점(AssignmentGrader) 완료 시점에 갱신된다.

```json
{
  "success": true,
  "data": {
    "assignmentId": "assign-hh8i9j0k",
    "score": 75,
    "totalPoints": 100,
    "correctCount": 7,
    "totalQuestions": 10,
    "grade": "B",
    "gradedAt": "2026-04-12T10:05:00Z",
    "status": "done",
    "weakConceptsAdded": ["concept-극한-좌우극한구분", "concept-미적분-연속성정의"],
    "questionResults": [
      {
        "questionNumber": 1,
        "isCorrect": true,
        "score": 10,
        "maxPoints": 10,
        "userAnswer": "4",
        "correctAnswer": "4",
        "explanation": "정답입니다! (x^2-4)/(x-2) = (x+2)(x-2)/(x-2) = x+2이므로 x->2일 때 4",
        "errorType": null
      },
      {
        "questionNumber": 3,
        "isCorrect": false,
        "score": 12,
        "maxPoints": 20,
        "userAnswer": "1. 좌극한≠우극한 2. 발산",
        "correctAnswer": "1. 좌극한≠우극한 2. 발산 3. 진동",
        "explanation": "2가지만 서술했습니다. 진동하는 경우(예: sin(1/x))를 추가로 알아보세요.",
        "errorType": "개념"
      }
    ],
    "overallFeedback": "극한 계산은 잘 하고 있어요! 극한이 존재하지 않는 다양한 경우를 더 공부하면 좋겠어요."
  },
  "timestamp": "2026-04-12T10:05:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | ASSIGN_001 | 과제를 찾을 수 없음 |
| 409 | ASSIGN_004 | 아직 채점 중 (status=grading) |

---

### GET /assignments/{id}/step-trace

풀이 과정을 단계별로 추적한 결과를 조회한다. 서술형 문제에서 학생이 어느 단계에서 틀렸는지 파악한다.

요청 형식: Path Parameter

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과제 ID |

#### 요청 예시

```http
GET /api/assignments/assign-hh8i9j0k/step-trace HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.assignmentId | string (UUID) | 과제 ID |
| data.questionTraces | array | 문제별 풀이 추적 |
| data.questionTraces[].questionNumber | integer | 문제 번호 |
| data.questionTraces[].steps | array | 풀이 단계 |
| data.questionTraces[].steps[].stepNumber | integer | 단계 번호 |
| data.questionTraces[].steps[].studentWork | string | 학생의 풀이 |
| data.questionTraces[].steps[].evaluation | string | 평가 (`"CORRECT"` / `"INCORRECT"` / `"PARTIAL"` / `"MISSING"`) |
| data.questionTraces[].steps[].feedback | string | 단계별 피드백 |
| data.questionTraces[].ocrConfidence | number / null | OCR 인식 신뢰도 (이미지 제출 시) |

```json
{
  "success": true,
  "data": {
    "assignmentId": "assign-hh8i9j0k",
    "questionTraces": [
      {
        "questionNumber": 3,
        "steps": [
          {
            "stepNumber": 1,
            "studentWork": "좌극한과 우극한이 다른 경우: |x|/x에서 x->0",
            "evaluation": "CORRECT",
            "feedback": "정확한 설명과 적절한 예시입니다."
          },
          {
            "stepNumber": 2,
            "studentWork": "함수값이 무한대로 발산하는 경우: 1/x에서 x->0",
            "evaluation": "CORRECT",
            "feedback": "좋아요! 발산의 대표적인 예시입니다."
          },
          {
            "stepNumber": 3,
            "studentWork": "(누락)",
            "evaluation": "MISSING",
            "feedback": "진동하는 경우를 추가해야 합니다. 예: sin(1/x)에서 x->0일 때 함수값이 -1과 1 사이를 무한히 진동합니다."
          }
        ],
        "ocrConfidence": 0.92
      }
    ]
  },
  "timestamp": "2026-04-12T10:05:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | ASSIGN_001 | 과제를 찾을 수 없음 |
| 409 | ASSIGN_004 | 아직 채점 중 |

---

### GET /assignments/{id}/weakness-report

과제 결과 기반 약점 리포트를 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 과제 ID |

#### 요청 예시

```http
GET /api/assignments/assign-hh8i9j0k/weakness-report HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.assignmentId | string (UUID) | 과제 ID |
| data.weaknesses | array | 약점 목록 |
| data.weaknesses[].category | string | 약점 카테고리 |
| data.weaknesses[].count | integer | 관련 오답 수 |
| data.weaknesses[].trend | string | 추세 (`"improving"` / `"stable"` / `"declining"`) |
| data.weaknesses[].relatedConcepts | string[] | 관련 개념 |
| data.weaknesses[].severity | string | 심각도 (`"LOW"` / `"MODERATE"` / `"HIGH"`) |
| data.weaknesses[].recommendedAction | string | 추천 학습 방법 |
| data.weaknesses[].relatedLessonId | string (UUID) / null | 관련 강의 ID |
| data.strengths | array | 강점 목록 |
| data.strengths[].topic | string | 주제 |
| data.strengths[].description | string | 설명 |
| data.overallAssessment | string | 종합 평가 |

```json
{
  "success": true,
  "data": {
    "assignmentId": "assign-hh8i9j0k",
    "weaknesses": [
      {
        "category": "극한의 존재 조건",
        "count": 2,
        "trend": "stable",
        "relatedConcepts": ["좌극한", "우극한", "진동"],
        "severity": "MODERATE",
        "recommendedAction": "진동 함수의 극한 관련 강의 복습 추천",
        "relatedLessonId": "lesson-001-uuid"
      }
    ],
    "strengths": [
      {
        "topic": "극한 계산",
        "description": "인수분해를 이용한 극한 계산에 능숙"
      }
    ],
    "overallAssessment": "기본적인 극한 계산 능력은 우수하나, 극한의 존재 조건에 대한 종합적 이해 강화 필요"
  },
  "timestamp": "2026-04-12T10:05:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | ASSIGN_001 | 과제를 찾을 수 없음 |
| 409 | ASSIGN_004 | 아직 채점 중 |

---

### GET /slides/{id}/memo-note

특정 슬라이드에 생성된 암기노트를 조회한다.

요청 형식: Path Parameter

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string | 슬라이드 ID |

#### 요청 예시

```http
GET /api/slides/sld-abc123/memo-note HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string | 암기노트 ID |
| data.slideId | string | 슬라이드 ID |
| data.courseId | string | 과외(과목) ID |
| data.noteTitle | string | 암기노트 제목 |
| data.contentMd | string | 마크다운 본문 |
| data.imageUrl | string | 렌더링 이미지 S3 URL |
| data.sentToTelegramAt | string (ISO 8601) | 텔레그램 발송 시각 (미발송 시 null) |
| data.createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "id": "mmo-abc123",
    "slideId": "sld-abc123",
    "courseId": "crs-xyz789",
    "noteTitle": "극한의 정의 — 핵심 요약",
    "contentMd": "## 극한의 정의\n- lim(x→a) f(x) = L 의미: x가 a에 가까워질 때 f(x)가 L에 가까워짐\n- 좌극한 = 우극한 → 극한 존재",
    "imageUrl": "https://cdn.ilkwa.com/memo-notes/mmo-abc123.png",
    "sentToTelegramAt": "2026-04-12T10:05:00Z",
    "createdAt": "2026-04-12T10:00:00Z"
  },
  "timestamp": "2026-04-12T10:05:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | SLIDE_001 | 슬라이드를 찾을 수 없음 |
| 404 | MEMO_001 | 해당 슬라이드의 암기노트 없음 |

---

### GET /courses/{id}/memo-notes

과목(course) 단위로 해당 과목에 속한 슬라이드들의 암기노트를 모아 반환한다. 주제별 암기노트 모음 페이지에서 사용한다.

요청 형식: Path Parameter + Query Parameter

인증: 필요

서버: AI

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string | 과외(과목) ID |

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| page | integer | X | 1 | 페이지 번호 |
| size | integer | X | 20 | 페이지당 항목 수 |
| keyword | string | X | — | 제목 검색 키워드 |

#### 요청 예시

```http
GET /api/courses/crs-xyz789/memo-notes?page=1&size=20 HTTP/1.1
Host: ai.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.courseId | string | 과외 ID |
| data.courseTitle | string | 과외 제목 |
| data.memoNotes | array | 암기노트 배열 (슬라이드 순서 정렬) |
| data.memoNotes[].id | string | 암기노트 ID |
| data.memoNotes[].slideId | string | 슬라이드 ID |
| data.memoNotes[].slideTitle | string | 슬라이드 제목 |
| data.memoNotes[].noteTitle | string | 암기노트 제목 |
| data.memoNotes[].contentMd | string | 마크다운 본문 |
| data.memoNotes[].imageUrl | string | 렌더링 이미지 S3 URL |
| data.memoNotes[].sentToTelegramAt | string / null | 텔레그램 발송 시각 |
| data.memoNotes[].createdAt | string (ISO 8601) | 생성 시각 |
| data.total | integer | 전체 암기노트 수 |
| data.page | integer | 현재 페이지 |
| data.size | integer | 페이지당 항목 수 |

```json
{
  "success": true,
  "data": {
    "courseId": "crs-xyz789",
    "courseTitle": "미적분 기초",
    "memoNotes": [
      {
        "id": "mmo-abc123",
        "slideId": "sld-abc123",
        "slideTitle": "극한의 정의",
        "noteTitle": "극한의 정의 — 핵심 요약",
        "contentMd": "## 극한의 정의\n- lim(x→a) f(x) = L ...",
        "imageUrl": "https://cdn.ilkwa.com/memo-notes/mmo-abc123.png",
        "sentToTelegramAt": "2026-04-12T10:05:00Z",
        "createdAt": "2026-04-12T10:00:00Z"
      }
    ],
    "total": 12,
    "page": 1,
    "size": 20
  },
  "timestamp": "2026-04-12T10:05:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | COURSE_001 | 과외를 찾을 수 없음 |

---

## 7. 캘린더 API

> 서버: Spring Boot (Core)  
> Base URL: `https://api.ilkwa.com/api`  
> 예외: `POST /calendar/reschedule`은 자연어 의도 파싱을 위해 FastAPI (Claude)를 사용한다.

---

### GET /calendar

학습 캘린더를 조회한다. 월별 또는 주별 보기를 지원한다.

요청 형식: Query Parameter

인증: 필요

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| year | integer | O | - | 연도 (예: 2026) |
| month | integer | O | - | 월 (1~12) |
| view | string | X | `month` | 보기 모드 (`"month"` / `"week"`) |
| weekStart | string (ISO 8601 date) | X | - | 주간 보기 시작일 (view=week일 때) |

#### 요청 예시

```http
GET /api/calendar?year=2026&month=4&view=month HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.year | integer | 연도 |
| data.month | integer | 월 |
| data.events | array | 이벤트 배열 |
| data.events[].id | string (UUID) | 이벤트 ID |
| data.events[].date | string (ISO 8601 date) | 날짜 |
| data.events[].type | string | 이벤트 유형 (`"LESSON"` / `"ASSIGNMENT_DUE"` / `"REVIEW"` / `"EXAM"`) |
| data.events[].title | string | 이벤트 제목 |
| data.events[].status | string | 상태 (`"scheduled"` / `"completed"` / `"missed"`) |
| data.events[].tutoringId | string (UUID) / null | 관련 과외 ID |
| data.events[].lessonId | string (UUID) / null | 관련 강의 ID |
| data.events[].assignmentId | string (UUID) / null | 관련 과제 ID |
| data.events[].scheduledTime | string / null | 예정 시간 (HH:mm) |
| data.events[].duration | integer / null | 소요 시간 (분) |
| data.events[].reviewRound | integer / null | 복습 회차 (REVIEW 타입) |
| data.studyDays | integer[] | 학습한 일자 배열 |

```json
{
  "success": true,
  "data": {
    "year": 2026,
    "month": 4,
    "events": [
      {
        "id": "event-kk1l2m3n",
        "date": "2026-04-12",
        "type": "LESSON",
        "title": "미적분 기초 - 3강",
        "status": "scheduled",
        "tutoringId": "aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
        "lessonId": "lesson-003-uuid",
        "assignmentId": null,
        "scheduledTime": "19:00",
        "duration": 30,
        "reviewRound": null
      },
      {
        "id": "event-ll2m3n4o",
        "date": "2026-04-12",
        "type": "ASSIGNMENT_DUE",
        "title": "극한의 개념 - 과제 마감",
        "status": "scheduled",
        "tutoringId": null,
        "lessonId": null,
        "assignmentId": "assign-hh8i9j0k",
        "scheduledTime": "23:59",
        "duration": null,
        "reviewRound": null
      }
    ],
    "studyDays": [1, 3, 5, 7, 8, 10, 11]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | year/month 범위 오류 |
| 401 | AUTH_001 | 토큰 만료 |

---

### POST /calendar/events

학습 캘린더에 이벤트를 수동으로 추가한다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| title | string | O | 이벤트 제목 (최대 100자) |
| type | string | O | 이벤트 유형 (`"LESSON"` / `"ASSIGNMENT_DUE"` / `"REVIEW"` / `"EXAM"` / `"CUSTOM"`) |
| date | string (ISO 8601 date) | O | 날짜 |
| scheduledTime | string | X | 시간 (HH:mm 형식) |
| duration | integer | X | 소요 시간 (분) |
| tutoringId | string (UUID) | X | 관련 과외 ID |
| description | string | X | 설명 |

#### 요청 예시

```json
{
  "title": "수학 중간고사",
  "type": "EXAM",
  "date": "2026-04-25",
  "scheduledTime": "09:00",
  "duration": 90,
  "description": "미적분 범위"
}
```

응답 형식: `application/json`

##### 성공 응답 (201 Created)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 생성된 이벤트 ID |
| data.title | string | 제목 |
| data.type | string | 유형 |
| data.date | string | 날짜 |
| data.scheduledTime | string / null | 시간 |
| data.duration | integer / null | 소요 시간 |
| data.status | string | 상태 (`"scheduled"`) |
| data.createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "id": "event-nn4o5p6q",
    "title": "수학 중간고사",
    "type": "EXAM",
    "date": "2026-04-25",
    "scheduledTime": "09:00",
    "duration": 90,
    "status": "scheduled",
    "createdAt": "2026-04-12T09:30:00Z"
  },
  "message": "이벤트가 추가되었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락, 날짜 형식 오류 |
| 401 | AUTH_001 | 토큰 만료 |

---

### PUT /calendar/events/{id}

캘린더 이벤트를 수정한다.

요청 형식: `application/json`

인증: 필요

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 이벤트 ID |

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| title | string | X | 제목 |
| date | string | X | 날짜 |
| scheduledTime | string | X | 시간 |
| duration | integer | X | 소요 시간 |
| status | string | X | 상태 (`"scheduled"` / `"completed"` / `"cancelled"`) |

#### 요청 예시

```json
{
  "scheduledTime": "20:00",
  "date": "2026-04-26"
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

수정된 이벤트 전체 객체 반환

```json
{
  "success": true,
  "data": {
    "id": "event-nn4o5p6q",
    "title": "수학 중간고사",
    "type": "EXAM",
    "date": "2026-04-26",
    "scheduledTime": "20:00",
    "duration": 90,
    "status": "scheduled",
    "createdAt": "2026-04-12T09:30:00Z"
  },
  "message": "이벤트가 수정되었습니다.",
  "timestamp": "2026-04-12T09:35:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 유효하지 않은 필드 값 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | TUTOR_001 | 이벤트를 찾을 수 없음 |

---

### POST /calendar/reschedule

자연어로 일정을 변경한다. AI가 의도를 파악하여 자동 처리한다.

서버: FastAPI (Claude 자연어 의도 파싱)  
Base URL: `https://ai.ilkwa.com/api`

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| message | string | O | 자연어 일정 변경 요청 (최대 500자) |

#### 요청 예시

```json
{
  "message": "이번 주 수요일 수업을 금요일 저녁 8시로 옮겨줘"
}
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.interpretation | string | AI가 해석한 변경 내용 |
| data.changes | array | 변경 사항 배열 |
| data.changes[].eventId | string (UUID) | 변경된 이벤트 ID |
| data.changes[].before | object | 변경 전 (date, time) |
| data.changes[].after | object | 변경 후 (date, time) |
| data.confirmed | boolean | 자동 적용 여부 |

```json
{
  "success": true,
  "data": {
    "interpretation": "4월 15일(수) 과외 -> 4월 17일(금) 20:00으로 변경",
    "changes": [
      {
        "eventId": "event-kk1l2m3n",
        "before": {"date": "2026-04-15", "time": "19:00"},
        "after": {"date": "2026-04-17", "time": "20:00"}
      }
    ],
    "confirmed": true
  },
  "message": "일정이 변경되었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 메시지가 비어있음 |
| 400 | AI_001 | AI가 의도를 파악할 수 없음 (모호한 요청) |
| 401 | AUTH_001 | 토큰 만료 |

---

## 8. 리포트/통계 API

> 서버: Spring Boot (Core) + FastAPI (AI 분석)
> Base URL: `https://api.ilkwa.com/api`

각 엔드포인트의 상세 필드는 아래와 같다.

---

### GET /reports/study-time

학습 시간 통계를 조회한다.

요청 형식: Query Parameter

인증: 필요

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| period | string | O | - | 기간 (`"daily"` / `"weekly"` / `"monthly"`) |
| startDate | string (ISO 8601 date) | X | - | 시작 날짜 |
| endDate | string (ISO 8601 date) | X | - | 종료 날짜 |

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.period | string | 기간 |
| data.totalMinutes | integer | 총 학습 시간 (분) |
| data.averageMinutesPerDay | number | 일 평균 학습 시간 |
| data.breakdown | array | 일별 상세 |
| data.breakdown[].date | string | 날짜 |
| data.breakdown[].minutes | integer | 학습 시간 |
| data.breakdown[].subjects | object | 과목별 시간 (키: 과목명, 값: 분) |
| data.comparisonWithLastPeriod | object | 전 기간 대비 |
| data.comparisonWithLastPeriod.changePercent | number | 변화율 (%) |
| data.comparisonWithLastPeriod.direction | string | `"UP"` / `"DOWN"` / `"SAME"` |

```json
{
  "success": true,
  "data": {
    "period": "weekly",
    "totalMinutes": 420,
    "averageMinutesPerDay": 60,
    "breakdown": [
      {"date": "2026-04-06", "minutes": 45, "subjects": {"수학": 30, "영어": 15}},
      {"date": "2026-04-07", "minutes": 90, "subjects": {"수학": 60, "물리": 30}}
    ],
    "comparisonWithLastPeriod": {
      "changePercent": 15.5,
      "direction": "UP"
    }
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

---

### GET /reports/assignment-completion

과제 완료율 통계를 조회한다.

요청 형식: 쿼리 파라미터 없음

인증: 필요

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.totalAssignments | integer | 전체 과제 수 |
| data.completedAssignments | integer | 완료 과제 수 |
| data.completionRate | number | 완료율 (0.0~1.0) |
| data.averageScore | number | 평균 점수 |
| data.bySubject | object | 과목별 통계 |
| data.bySubject.{과목}.total | integer | 전체 수 |
| data.bySubject.{과목}.completed | integer | 완료 수 |
| data.bySubject.{과목}.avgScore | number | 평균 점수 |
| data.trend | array | 주간 추이 |

```json
{
  "success": true,
  "data": {
    "totalAssignments": 20,
    "completedAssignments": 16,
    "completionRate": 0.8,
    "averageScore": 78.5,
    "bySubject": {
      "수학": {"total": 12, "completed": 10, "avgScore": 82.0},
      "물리": {"total": 5, "completed": 4, "avgScore": 75.0}
    },
    "trend": [
      {"week": "2026-W14", "completionRate": 0.7, "avgScore": 72.0},
      {"week": "2026-W15", "completionRate": 0.8, "avgScore": 78.5}
    ]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

---

### GET /reports/error-patterns

AI가 분석한 오류 유형 분류를 조회한다.

요청 형식: 쿼리 파라미터 없음

인증: 필요

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.totalErrors | integer | 전체 오류 수 |
| data.patterns | array | 오류 패턴 배열 |
| data.patterns[].type | string | 오류 유형 (`"계산"` / `"개념"` / `"공식"` / `"해석"`) |
| data.patterns[].count | integer | 발생 횟수 |
| data.patterns[].percentage | number | 비율 (%) |
| data.patterns[].trend | string | 추세 (`"improving"` / `"stable"` / `"declining"`) |
| data.patterns[].examples | string[] | 예시 |
| data.patterns[].recommendation | string | 개선 추천 |

```json
{
  "success": true,
  "data": {
    "totalErrors": 45,
    "patterns": [
      {
        "type": "계산",
        "count": 15,
        "percentage": 33.3,
        "trend": "improving",
        "examples": ["부호 오류", "통분 실수"],
        "recommendation": "계산 과정을 단계별로 검증하는 습관을 추천합니다."
      },
      {
        "type": "개념",
        "count": 12,
        "percentage": 26.7,
        "trend": "stable",
        "examples": ["미분과 적분 공식 혼동"],
        "recommendation": "핵심 개념을 암기노트로 정리하고 반복 복습하세요."
      }
    ]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

---

### GET /reports/weakness-trend

사용자 약점 추이를 조회한다. 최근 30일 기본, 주간별 심각도 변화를 포함한다.

요청 형식: Query Parameter

인증: 필요

서버: Core (Spring Boot) `https://api.ilkwa.com/api`

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| days | integer | X | `30` | 조회 기간 (1~90) |
| topic | string | X | - | 특정 주제 필터 |
| resolved | boolean | X | - | 해결 여부 필터 |

#### 요청 예시

```http
GET /api/reports/weakness-trend?days=30 HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.period.startDate | string (ISO 8601 date) | 조회 시작일 |
| data.period.endDate | string (ISO 8601 date) | 조회 종료일 |
| data.totalWeaknesses | integer | 전체 약점 수 |
| data.resolvedCount | integer | 해결된 약점 수 |
| data.ongoingCount | integer | 진행 중인 약점 수 |
| data.weaknesses | array | 약점 배열 |
| data.weaknesses[].id | string (UUID) | 약점 ID |
| data.weaknesses[].topic | string | 주제 |
| data.weaknesses[].subtopic | string | 세부 주제 |
| data.weaknesses[].errorPattern | string | 오류 패턴 설명 |
| data.weaknesses[].frequency | integer | 발생 빈도 |
| data.weaknesses[].severity | integer | 심각도 (1~5) |
| data.weaknesses[].firstSeenAt | string (ISO 8601) | 최초 발생 시각 |
| data.weaknesses[].lastSeenAt | string (ISO 8601) | 최근 발생 시각 |
| data.weaknesses[].resolvedAt | string (ISO 8601) / null | 해결 시각 |
| data.weeklyTrend | array | 주간 추이 배열 |
| data.weeklyTrend[].week | string | ISO 주차 (예: `2026-W14`) |
| data.weeklyTrend[].avgSeverity | number | 평균 심각도 |
| data.weeklyTrend[].newCount | integer | 신규 약점 수 |

```json
{
  "success": true,
  "data": {
    "period": {
      "startDate": "2026-03-15",
      "endDate": "2026-04-14"
    },
    "totalWeaknesses": 8,
    "resolvedCount": 3,
    "ongoingCount": 5,
    "weaknesses": [
      {
        "id": "weakness-uuid-001",
        "topic": "미적분",
        "subtopic": "극한",
        "errorPattern": "좌극한과 우극한 구분 오류",
        "frequency": 5,
        "severity": 3,
        "firstSeenAt": "2026-03-20T10:00:00Z",
        "lastSeenAt": "2026-04-10T14:30:00Z",
        "resolvedAt": null
      }
    ],
    "weeklyTrend": [
      {"week": "2026-W12", "avgSeverity": 3.2, "newCount": 2},
      {"week": "2026-W13", "avgSeverity": 2.8, "newCount": 1},
      {"week": "2026-W14", "avgSeverity": 2.5, "newCount": 0},
      {"week": "2026-W15", "avgSeverity": 2.3, "newCount": 1}
    ]
  },
  "timestamp": "2026-04-14T09:30:00Z"
}
```

> 특이사항: Redis 6시간 TTL 캐시. severity는 frequency와 recency를 조합하여 계산한다. ERD 참조: weakness_profile (user_id + last_seen_at 범위), Grade (파생).

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | days 범위 초과 (1~90) |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | USER_001 | 사용자를 찾을 수 없음 |

---

### GET /reports/self-healing-memory

자동 복습 큐 상태를 조회한다. 핵심 구조: `totalItemsInQueue`, `itemsHealed`, `healingRate`, `currentQueue[]`, `recentlyHealed[]`.

> 명칭 안내: 이 엔드포인트는 **학생 복습 시스템(Spaced Review System)** 리포트다. "self-healing"이라는 명칭은 내부 도입 당시 사용하던 표현이며, 추후 `GET /reports/spaced-review`로 rename 예정이다. 에이전트 자체 메모리(`memory` 테이블, Self-Healing Memory 3계층)와 다른 개념이므로 혼동하지 않는다.

---

### GET /reports/replay-analysis

복습 행동을 분석한다. PlaybackEvent를 집계하고 AI 조언을 포함하여 반환한다.

요청 형식: Query Parameter

인증: 필요

서버: Core (Spring Boot) `https://api.ilkwa.com/api`

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| days | integer | X | `30` | 조회 기간 (7~180) |
| courseId | string (UUID) | X | - | 특정 코스 필터 |
| focusSlideId | string (UUID) | X | - | 특정 슬라이드 포커스 |

#### 요청 예시

```http
GET /api/reports/replay-analysis?days=30 HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.period.startDate | string (ISO 8601 date) | 조회 시작일 |
| data.period.endDate | string (ISO 8601 date) | 조회 종료일 |
| data.replayCount | integer | 총 복습 횟수 |
| data.totalPlaybackMinutes | number | 총 재생 시간 (분) |
| data.avgSessionDuration | number | 평균 세션 시간 (분) |
| data.slides | array | 슬라이드별 복습 데이터 |
| data.slides[].slideId | string (UUID) | 슬라이드 ID |
| data.slides[].lessonTitle | string | 강의 제목 |
| data.slides[].slideTitle | string | 슬라이드 제목 |
| data.slides[].replayCount | integer | 복습 횟수 |
| data.slides[].totalDuration | number | 누적 재생 시간 (분) |
| data.slides[].pauseCount | integer | 일시 정지 횟수 |
| data.slides[].jumpBackCount | integer | 되감기 횟수 |
| data.slides[].lastPlayedAt | string (ISO 8601) | 마지막 재생 시각 |
| data.slides[].difficulty | string | 난이도 판정 (`"easy"` / `"medium"` / `"hard"`) |
| data.slides[].associatedWeakness | string / null | 연관 약점 ID |
| data.insights.focusAreas | string[] | 집중 학습 추천 영역 |
| data.insights.estimatedMasteryLevel | number | 추정 숙련도 (0.0~1.0) |
| data.insights.recommendedFocusSlidesCount | integer | 추천 복습 슬라이드 수 |
| data.insights.recommendation | string | AI 조언 (FastAPI Claude 호출 또는 Spring 규칙 기반) |

```json
{
  "success": true,
  "data": {
    "period": {
      "startDate": "2026-03-15",
      "endDate": "2026-04-14"
    },
    "replayCount": 23,
    "totalPlaybackMinutes": 185.5,
    "avgSessionDuration": 8.1,
    "slides": [
      {
        "slideId": "slide-uuid-001",
        "lessonTitle": "극한의 개념",
        "slideTitle": "극한의 정의",
        "replayCount": 7,
        "totalDuration": 42.3,
        "pauseCount": 12,
        "jumpBackCount": 5,
        "lastPlayedAt": "2026-04-12T15:00:00Z",
        "difficulty": "hard",
        "associatedWeakness": "weakness-uuid-001"
      }
    ],
    "insights": {
      "focusAreas": ["극한 단원", "미분 공식"],
      "estimatedMasteryLevel": 0.62,
      "recommendedFocusSlidesCount": 4,
      "recommendation": "극한 단원 슬라이드를 반복적으로 복습하고 있습니다. 관련 연습 문제를 추가로 풀어보면 숙련도를 높일 수 있습니다."
    }
  },
  "timestamp": "2026-04-14T09:30:00Z"
}
```

> 특이사항: Redis 12시간 TTL 캐시. difficulty는 Spring 규칙 기반(재생 횟수 + 일시 정지 횟수 + 되감기 횟수)으로 계산한다. recommendation은 FastAPI Claude 호출 또는 Spring 규칙 기반으로 생성한다. ERD 참조: PlaybackEvent(신규) + Slide/Chapter/Course + weakness_profile 조인.
>
> 되감기 집계 기준: slide 이동(seek) 중 방향이 back인 이벤트를 되감기로 본다. 일시정지(pause)는 제외한다. SQL 조건: `event_type = 'seek' AND (payload->>'direction') = 'back'`

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | days 범위 초과 (7~180) |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | USER_001 | 사용자를 찾을 수 없음 |
| 404 | TUTOR_001 | 지정한 courseId를 찾을 수 없음 |

---

## 9. 모의고사 API

> 서버: FastAPI (AI)  
> Base URL: `https://ai.ilkwa.com/api`

---

### POST /exam-forges

모의고사를 생성한다.

- **신규 정책 (2026-06-07)**: 완강 여부와 무관하게 모의고사 1회 생성 시 **3코인** 소모. 구독 결제 시 결제 금액만큼 코인이 적립되고 해당 커리큘럼도 함께 지급된다.
- ~~**완강 강의 + Pro/Premium 구독자**: 첫 1회 코인 소모 0 (무료). `exam_forge.is_free_trial = true`.~~ (폐기)
- ~~**완강하지 않은 강의 대상 요청**: 403 EXAM_001.~~ (폐기 — 완강 조건 제거됨)

> 안내: AI 코인으로 슬라이드를 하나씩 해제하면 강의 생성 시간이 길어질 수 있다. 미리 필요한 슬라이드를 해제해두거나, 구독 플랜(Pro/Premium)을 이용하면 모든 슬라이드를 바로 이용할 수 있다.

요청 형식: `application/json`

인증: 필요 (Any authenticated user. 3코인 이상 보유 필요. 완강 조건 없음.)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| title | string | O | 모의고사 제목 |
| courseId | string | O | 대상 강의 ID (완강 여부 검증에 사용) |
| subject | string | O | 과목 |
| scope | object | O | 출제 범위 |
| scope.tutoringIds | string[] | X | 포함할 과외 ID 배열 |
| scope.customTopics | string[] | X | 직접 입력 주제 |
| scope.includeWeaknesses | boolean | X | 약점 기반 출제 포함 여부 (기본값: `true`) |
| questionCount | integer | X | 문제 수 (기본값: 25) |
| timeLimit | integer | X | 제한 시간 (분, 기본값: 60) |
| difficulty | string | X | 난이도 (`"easy"` / `"medium"` / `"hard"` / `"mixed"`, 기본값: `"mixed"`) |
| format | string | X | 시험 형식 (`"SUNEUNG_STYLE"`: 수능형 / `"SCHOOL_EXAM"`: 내신형 / `"CUSTOM"`, 기본값: `"SUNEUNG_STYLE"`) |
| useWeaknessProfile | boolean | X | 취약점 프로필 기반 출제 (기본값: `true`) |

#### 요청 예시

```json
{
  "title": "미적분 중간고사 대비",
  "subject": "수학",
  "scope": {
    "tutoringIds": ["aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d"],
    "customTopics": ["극한", "미분"],
    "includeWeaknesses": true
  },
  "questionCount": 25,
  "timeLimit": 60,
  "difficulty": "mixed",
  "format": "SUNEUNG_STYLE",
  "useWeaknessProfile": true
}
```

##### 성공 응답 (201 Created)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.id | string (UUID) | 모의고사 ID |
| data.title | string | 제목 |
| data.status | string | 상태 (`"GENERATING"`) |
| data.questionCount | integer | 문제 수 |
| data.timeLimit | integer | 제한 시간 (분) |
| data.estimatedReadyAt | string (ISO 8601) | 예상 완료 시각 |
| data.coinUsed | integer | 소모된 코인 수 (0 = 무료 제공, 1 = 코인 차감) |
| data.coinBalance | integer | 차감 후 코인 잔액 (무료 제공 시 변동 없음) |
| data.isFreeTrialUsed | boolean | 완강 무료 1회 제공 여부 |

```json
{
  "success": true,
  "data": {
    "id": "exam-vv2w3x4y",
    "title": "미적분 중간고사 대비",
    "status": "GENERATING",
    "questionCount": 25,
    "timeLimit": 60,
    "estimatedReadyAt": "2026-04-12T09:05:00Z"
  },
  "message": "모의고사를 생성 중입니다.",
  "timestamp": "2026-04-12T09:00:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | VALIDATION_001 | 필수 필드 누락 |
| 401 | AUTH_001 | 토큰 만료 |
| 403 | EXAM_001 | NOT_COMPLETED — 완강하지 않은 강의에 대한 모의고사 요청 |
| 409 | COIN_001 | AI 코인 잔액 부족 (코인 0개) |

---

### GET /exam-forges, GET /exam-forges/{id}, POST /exam-forges/{id}/submit, GET /exam-forges/{id}/result

이 엔드포인트들은 과제 API와 동일한 패턴을 따른다:

- **GET /exam-forges**: 페이지네이션 목록 (id, title, status, questionCount, timeLimit, score, createdAt)
- **GET /exam-forges/{id}**: 문제 배열 포함 (`questions[].id`, `.questionNumber`, `.type`, `.content`, `.options`, `.points`, `.difficulty`, `.topic`)
- **POST /exam-forges/{id}/submit**: `{ answers: [{questionNumber, answer}], timeSpent }` -> `{ submissionId, status: "GRADING" }`
- **GET /exam-forges/{id}/result**: 채점 결과 + 수능 등급 환산 (`score`, `totalPoints`, `percentile`, `grade`, `questionResults[]`, `analysis.strongTopics`, `analysis.weakTopics`, `analysis.estimatedSuneungScore`)

---

## 10. 구독/결제 API

> 서버: Spring Boot (Core)  
> Base URL: `https://api.ilkwa.com/api`  
> 결제 연동: 토스페이먼츠  
> v1.5: Free/Pro/Premium 3종 플랜. 상세 정책: `Docs/00_개요/기능명세서.md` § 10 요금제 참조

---

### GET /subscription/plans

요금제를 비교 조회한다. (인증 불필요)

요청 형식: 쿼리 파라미터 없음

인증: 불필요

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.plans | array | 요금제 배열 |
| data.plans[].id | string | 요금제 ID (`"FREE"` / `"PRO"` / `"PREMIUM"`) |
| data.plans[].name | string | 요금제 이름 |
| data.plans[].price | integer | 가격 (원) |
| data.plans[].currency | string | 통화 (`"KRW"`) |
| data.plans[].billingCycle | string / null | 결제 주기 (`"MONTHLY"` / null) |
| data.plans[].features | object | 기능 목록 |

```json
{
  "success": true,
  "data": {
    "plans": [
      {
        "id": "FREE",
        "name": "무료",
        "price": 0,
        "currency": "KRW",
        "billingCycle": null,
        "features": {
          "maxCourses": 1,
          "maxSlidesPerCourse": 2,
          "telegramBot": false,
          "canCustomizeTutor": false
        }
      },
      {
        "id": "PRO",
        "name": "Pro",
        "price": 14900,
        "currency": "KRW",
        "billingCycle": "MONTHLY",
        "features": {
          "maxCourses": 2,
          "maxSlidesPerCourse": null,
          "telegramBot": true,
          "canCustomizeTutor": true
        }
      },
      {
        "id": "PREMIUM",
        "name": "Premium",
        "price": 49900,
        "currency": "KRW",
        "billingCycle": "MONTHLY",
        "features": {
          "maxCourses": 10,
          "maxSlidesPerCourse": null,
          "telegramBot": true,
          "canCustomizeTutor": true
        }
      }
    ]
  },
  "timestamp": "2026-04-20T09:30:00Z"
}
```

---

### GET /subscription/current

현재 구독 상태를 조회한다.

요청 형식: 쿼리 파라미터 없음

인증: 필요

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.planId | string | 현재 요금제 ID |
| data.status | string | 상태 (`"ACTIVE"` / `"CANCELLED"` / `"EXPIRED"` / `"NONE"`) |
| data.startedAt | string (ISO 8601) / null | 구독 시작일 |
| data.expiresAt | string (ISO 8601) / null | 만료일 |
| data.autoRenew | boolean | 자동 갱신 여부 |
| data.paymentMethod | object / null | 결제 수단 |
| data.paymentMethod.type | string | 결제 유형 (`"CARD"`) |
| data.paymentMethod.last4 | string | 카드 번호 마지막 4자리 |
| data.paymentMethod.brand | string | 카드 브랜드 |
| data.nextBillingDate | string / null | 다음 결제일 |
| data.nextBillingAmount | integer / null | 다음 결제 금액 |

```json
{
  "success": true,
  "data": {
    "planId": "PRO",
    "status": "ACTIVE",
    "startedAt": "2026-04-20T00:00:00Z",
    "expiresAt": "2026-05-20T00:00:00Z",
    "autoRenew": true,
    "paymentMethod": {
      "type": "CARD",
      "last4": "1234",
      "brand": "VISA"
    },
    "nextBillingDate": "2026-05-20",
    "nextBillingAmount": 14900
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

---

### POST /subscription/checkout

토스페이먼츠 결제를 시작한다. 클라이언트에서 토스페이먼츠 SDK를 사용하여 결제 UI를 띄운다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| planId | string | O | 요금제 ID (`"PRO"` / `"PREMIUM"`) |
| successUrl | string | O | 결제 성공 시 리다이렉트 URL |
| failUrl | string | O | 결제 실패 시 리다이렉트 URL |

#### 요청 예시

```json
{
  "planId": "PREMIUM",
  "successUrl": "https://ilkwa.com/payment/success",
  "failUrl": "https://ilkwa.com/payment/fail"
}
```

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.orderId | string (UUID) | 주문 ID |
| data.tossPaymentsClientKey | string | 토스페이먼츠 클라이언트 키 |
| data.amount | integer | 결제 금액 |
| data.orderName | string | 주문 명 |
| data.customerName | string | 고객 이름 |
| data.customerEmail | string | 고객 이메일 |

```json
{
  "success": true,
  "data": {
    "orderId": "order-ww3x4y5z",
    "tossPaymentsClientKey": "test_ck_D5GePWvyJnrK0W0k6q8gmeYYookQ",
    "amount": 9900,
    "orderName": "일과 프리미엄 (월간)",
    "customerName": "학습왕",
    "customerEmail": "user@example.com"
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

> 클라이언트 흐름: 이 응답 데이터로 토스페이먼츠 SDK `requestPayment()`를 호출 -> 결제 완료 시 `successUrl?paymentKey=...&orderId=...&amount=...`로 리다이렉트 -> `POST /subscription/confirm`으로 승인

---

### POST /subscription/confirm

토스페이먼츠 결제를 승인한다. `successUrl` 리다이렉트에서 받은 파라미터를 전송한다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| paymentKey | string | O | 토스페이먼츠 결제 키 |
| orderId | string | O | 주문 ID |
| amount | integer | O | 결제 금액 (서버 검증용) |

#### 요청 예시

```json
{
  "paymentKey": "toss_5zyx4wvu3tsr2qpon1m",
  "orderId": "order-ww3x4y5z",
  "amount": 9900
}
```

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.subscriptionId | string (UUID) | 구독 ID |
| data.planId | string | 요금제 ID |
| data.status | string | 상태 (`"ACTIVE"`) |
| data.startedAt | string (ISO 8601) | 시작일 |
| data.expiresAt | string (ISO 8601) | 만료일 |
| data.receipt | object | 영수증 |
| data.receipt.receiptUrl | string | 영수증 URL |

```json
{
  "success": true,
  "data": {
    "subscriptionId": "sub-xx4y5z6a",
    "planId": "PREMIUM",
    "status": "ACTIVE",
    "startedAt": "2026-04-12T10:00:00Z",
    "expiresAt": "2026-05-12T10:00:00Z",
    "receipt": {
      "receiptUrl": "https://dashboard.tosspayments.com/receipt/redirection?transactionId=..."
    }
  },
  "message": "프리미엄 구독이 시작되었습니다!",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | PAY_005 | 결제 금액 불일치 |
| 400 | PAY_001 | 토스페이먼츠 결제 실패 |
| 401 | AUTH_001 | 토큰 만료 |
| 409 | PAY_004 | 이미 프리미엄 구독 중 |

---

### POST /subscription/cancel

구독을 취소한다. 즉시 해지가 아닌 만료일까지 유지된다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| reason | string | X | 해지 사유 |
| feedback | string | X | 추가 피드백 |

#### 요청 예시

```json
{
  "reason": "더 이상 사용하지 않음",
  "feedback": "가격이 부담됩니다"
}
```

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.status | string | 상태 (`"CANCELLED"`) |
| data.effectiveUntil | string (ISO 8601) | 유효 기간 종료일 |
| data.autoRenew | boolean | 자동 갱신 (false) |

```json
{
  "success": true,
  "data": {
    "status": "CANCELLED",
    "effectiveUntil": "2026-05-12T10:00:00Z",
    "autoRenew": false
  },
  "message": "구독이 취소되었습니다. 2026-05-12까지 현재 플랜 기능을 이용할 수 있습니다.",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 404 | PAY_002 | 활성 구독이 없음 |

---

### GET /subscription/history

결제 내역을 조회한다. 페이지네이션을 지원한다. 각 항목에 `id`, `orderId`, `planId`, `amount`, `currency`, `status` (`"PAID"` / `"REFUNDED"`), `paymentMethod`, `cardLast4`, `paidAt`, `receiptUrl`을 포함한다.

---

### POST /subscription/upgrade

플랜을 업그레이드한다. 즉시 적용되며 남은 기간을 일할 계산하여 차액만 청구(prorated)한다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| planId | string | O | 업그레이드 대상 플랜 (`"PREMIUM"`) |
| successUrl | string | O | 결제 성공 리다이렉트 URL |
| failUrl | string | O | 결제 실패 리다이렉트 URL |

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.orderId | string | 주문 ID |
| data.amount | integer | 청구 금액 (prorated, 원) |
| data.proratedDays | integer | 남은 일수 기준 |
| data.tossPaymentsClientKey | string | 토스페이먼츠 클라이언트 키 |

> Prorated 계산 예시: Pro(14,900원) 10일 사용 후 Premium(49,900원) 업그레이드 → 남은 20일 기준 차액 일할 계산 = (49,900 - 14,900) × 20/30 ≈ 23,334원 → 소숫점 올림 처리.

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | PAY_004 | 이미 해당 플랜 또는 상위 플랜 구독 중 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | PAY_002 | 활성 구독 없음 |

---

### POST /subscription/downgrade

플랜을 다운그레이드한다. 현재 구독 기간 종료 후 다음 결제 주기부터 적용된다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| planId | string | O | 다운그레이드 대상 플랜 (`"PRO"` / `"FREE"`) |

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.scheduledPlanId | string | 예약된 플랜 ID |
| data.effectiveFrom | string (ISO 8601) | 적용 시작일 (현재 구독 만료 후) |
| data.currentPlanActiveUntil | string (ISO 8601) | 현재 플랜 유효 기간 |

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 400 | PAY_004 | 이미 해당 플랜 또는 하위 플랜 구독 중 |
| 401 | AUTH_001 | 토큰 만료 |
| 404 | PAY_002 | 활성 구독 없음 |

---

## 11. 알림 API

> 서버: Spring Boot (Core)  
> Base URL: `https://api.ilkwa.com/api`

---

### GET /notifications

알림 목록을 조회한다.

요청 형식: Query Parameter

인증: 필요

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|--------------|------|------|--------|------|
| page | integer | X | 0 | 페이지 번호 |
| size | integer | X | 20 | 페이지 크기 |
| unreadOnly | boolean | X | false | 읽지 않은 알림만 |

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.content | array | 알림 목록 |
| data.content[].id | string (UUID) | 알림 ID |
| data.content[].type | string | 알림 유형 (`"assignment"` / `"lesson"` / `"schedule"` / `"grading"` / `"system"`) |
| data.content[].title | string | 알림 제목 |
| data.content[].body | string | 알림 본문 |
| data.content[].isRead | boolean | 읽음 여부 |
| data.content[].linkTo | string | 이동 경로 (프론트 라우트, 예: `"/tutoring/aa1b2c3d/lessons/lesson-001"`) |
| data.content[].createdAt | string (ISO 8601) | 생성 시각 |
| data.unreadCount | integer | 전체 읽지 않은 알림 수 |

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "notif-yy5z6a7b",
        "type": "assignment",
        "title": "과제 마감 임박",
        "body": "극한의 개념 - 과제 마감이 2시간 남았어요!",
        "isRead": false,
        "linkTo": "/assignments/assign-hh8i9j0k",
        "createdAt": "2026-04-12T22:00:00Z"
      }
    ],
    "unreadCount": 3,
    "page": 0,
    "size": 20,
    "totalElements": 15,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

---

### PUT /notifications/{id}/read

알림을 읽음 처리한다.

요청 형식: Path Parameter (본문 없음)

인증: 필요

| Path 파라미터 | 타입 | 설명 |
|--------------|------|------|
| id | string (UUID) | 알림 ID |

#### 성공 응답 (200 OK)

```json
{
  "success": true,
  "data": {
    "id": "notif-yy5z6a7b",
    "isRead": true
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

---

### PUT /notifications/read-all

모든 알림을 읽음 처리한다.

요청 형식: 본문 없음

인증: 필요

#### 성공 응답 (200 OK)

```json
{
  "success": true,
  "data": {
    "readCount": 3
  },
  "message": "모든 알림을 읽었습니다.",
  "timestamp": "2026-04-12T09:30:00Z"
}
```

---

### GET /notifications/stream

알림 목록의 최신 상태를 조회한다. 실시간형 UX가 필요하면 프론트는 짧은 polling 또는 다음 요청 piggyback 응답을 사용한다.

> 서버: Core (Spring Boot REST)
> 비고: Spring은 SseEmitter를 구현하지 않는다.

요청 형식: Query Parameter 없음

인증: Bearer 토큰 필수

성공 응답: `application/json`

```json
{
  "success": true,
  "data": {
    "content": [
      {"id":"...","type":"lesson","title":"강의 준비 완료","body":"새 강의를 바로 학습할 수 있습니다.","createdAt":"2026-04-12T09:30:00Z"}
    ],
    "unreadCount": 1
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| data.content[].id | string (UUID) | 알림 ID |
| data.content[].type | string | 알림 유형 (`"assignment"` / `"lesson"` / `"schedule"` / `"grading"` / `"system"`) |
| data.content[].title | string | 알림 제목 |
| data.content[].body | string | 알림 본문 |
| data.content[].createdAt | string (ISO 8601) | 생성 시각 |
| data.unreadCount | integer | 읽지 않은 알림 수 |

---

## 12. 텔레그램 웹훅

> 서버: FastAPI (AI)  
> Base URL: `https://ai.ilkwa.com/api`  
> 내부 API: 텔레그램 봇 서버에서만 호출

---

### POST /telegram/webhook

텔레그램 Bot API의 Webhook URL이다. 텔레그램에서 메시지가 수신되면 이 엔드포인트로 Update 객체가 전달된다.

> 서버 경계: 이 엔드포인트는 **FastAPI 내부 `app/telegram/` 라우터**가 전담 처리한다. Spring Boot는 관여하지 않으며, Spring으로의 콜백도 없다. 봇 토큰(`TELEGRAM_BOT_TOKEN`)은 환경변수로 관리하며 DB에 저장하지 않는다.

> 과제 파일 처리 흐름: 텔레그램에서 과제 파일을 수신하면 FastAPI가 즉시 200 ACK을 반환한 뒤, 비동기 큐(Celery)로 다음 단계를 처리한다. 허용 파일 형식: `.py`, `.txt`, `.md`, `.png`, `.jpg`, `.jpeg`, `.webp`, `.pdf`, `.zip`, `.mp3`, `.m4a`, `.wav`, `.docx`, `.pptx`, `.xlsx`.
> 1. Telegram Bot API `getFile`로 파일 URL 획득
> 2. S3에 업로드 (`submissions/{user_id}/{assignment_id}/telegram_{file_id}.{ext}`)
> 3. `submission` 테이블에 행 생성 (`submission_source=telegram`, `status=queued`)
> 4. AssignmentGrader가 파일 타입 판단 후 처리 분기: 이미지/PDF → ①a Sonnet 4.6 VLM 직접 처리 / `.py` → AST 파싱 or 실행 결과 검증 / `.txt`·`.md` → 자유서술 평가 / `.docx`·`.pptx`·`.xlsx` → MarkItDown → Markdown 정규화 → 채점 / `.zip` → 해제 후 포함 파일 타입별 분기 / 음성(`.mp3`·`.m4a`·`.wav`) → Whisper Turbo → 텍스트 변환 → 채점 → 즉시 해설 생성 → 채점 완료 콜백 → 텔레그램 DM 회신

요청 형식: `application/json` (Telegram Update 객체)

인증: Webhook Secret (`X-Telegram-Bot-Api-Secret-Token` 헤더). 텔레그램이 등록된 secret token을 전달하며, FastAPI가 불일치 시 400으로 즉시 차단한다.

| 필드 | 타입 | 설명 |
|------|------|------|
| update_id | integer | 업데이트 ID |
| message | object / null | 텍스트/사진/문서/음성 메시지 |
| message.from | object | 보낸 사람 정보 |
| message.chat | object | 채팅 정보 |
| message.text | string / null | 텍스트 내용 |
| message.photo | array / null | 사진 배열 |
| message.document | object / null | 문서 정보 |
| message.voice | object / null | 음성 메시지 정보 |

#### 처리되는 메시지 유형

| 메시지 유형 | 트리거 | 처리 |
|------------|--------|------|
| `/start {token}` | 연동 딥링크 | 사용자 계정 연동 완료 |
| 사진 (photo) | 카메라 질문 | OCR → 문제 인식 → AI 풀이 답변 |
| 문서 (document, `.py` / `.txt` / `.md` / `.pdf` / `.zip`) | 과제 제출 | AssignmentGrader 파일 타입 판단 → 처리 분기(OCR·AST·자유서술·zip 해제 등) → 즉시 채점 → DM 회신 |
| 음성 (voice, `.mp3` / `.m4a` / `.wav`) | 과제 제출(음성) | Whisper Turbo → 텍스트 변환 → AssignmentGrader 채점 → DM 회신 |
| `/선생님변경` | 텍스트 커맨드 | 선생님 변경 메뉴 표시 |
| 자연어 텍스트 | 일정 관련 | 자연어 파싱 → 일정 변경 |
| 음성 (voice) | 음성 Q&A | ASR → RAG → TTS 답변 |

#### 요청 예시 (사진 메시지)

```json
{
  "update_id": 123456789,
  "message": {
    "message_id": 100,
    "from": {
      "id": 987654321,
      "first_name": "학생",
      "username": "student_user"
    },
    "chat": {
      "id": 987654321,
      "type": "private"
    },
    "photo": [
      {
        "file_id": "AgACAgIAAxkBAAI...",
        "file_unique_id": "AQAD...",
        "width": 1280,
        "height": 960,
        "file_size": 120000
      }
    ],
    "caption": "이 문제 풀어줘"
  }
}
```

##### 응답 (200 OK)

텔레그램 봇이 직접 사용자에게 메시지를 보낸다.

```json
{
  "ok": true
}
```

---

## 13. 대시보드 API

> 서버: Spring Boot (Core) - 여러 서비스를 집계  
> Base URL: `https://api.ilkwa.com/api`

---

### GET /dashboard

메인 대시보드에 필요한 모든 데이터를 한 번에 집계하여 반환한다. 여러 API를 개별 호출하는 대신 이 단일 엔드포인트로 대시보드를 구성한다.

요청 형식: 쿼리 파라미터 없음

인증: 필요

#### 요청 예시

```http
GET /api/dashboard HTTP/1.1
Host: api.ilkwa.com
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

응답 형식: `application/json`

##### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.activeTutoring | array | 진행 중인 과외 (최대 5개) |
| data.activeTutoring[].id | string (UUID) | 과외 ID |
| data.activeTutoring[].title | string | 제목 |
| data.activeTutoring[].subject | string | 과목 |
| data.activeTutoring[].teacherName | string | 선생님 이름 |
| data.activeTutoring[].teacherAvatarUrl | string | 아바타 URL |
| data.activeTutoring[].progress | number | 진행률 |
| data.activeTutoring[].nextLessonTitle | string / null | 다음 강의 제목 |
| data.todayAssignments | array | 오늘 마감 과제 |
| data.todayAssignments[].id | string (UUID) | 과제 ID |
| data.todayAssignments[].title | string | 제목 |
| data.todayAssignments[].dueDate | string (ISO 8601) | 마감일 |
| data.todayAssignments[].status | string | 상태 |
| data.recommendations | array | AI 추천 활동 |
| data.recommendations[].type | string | 추천 유형 (`"review"` / `"assignment"` / `"lesson"`) |
| data.recommendations[].title | string | 제목 |
| data.recommendations[].reason | string | 추천 이유 |
| data.recommendations[].linkTo | string | 이동 경로 |
| data.currentTeacher | object | 현재 선생님 |
| data.currentTeacher.id | string | 선생님 ID |
| data.currentTeacher.name | string | 이름 |
| data.currentTeacher.avatarUrl | string | 아바타 URL |
| data.notifications | array | 최근 알림 (최대 5개) |
| data.notifications[].id | string (UUID) | 알림 ID |
| data.notifications[].type | string | 유형 |
| data.notifications[].title | string | 제목 |
| data.notifications[].isRead | boolean | 읽음 여부 |
| data.notifications[].createdAt | string (ISO 8601) | 생성 시각 |

```json
{
  "success": true,
  "data": {
    "activeTutoring": [
      {
        "id": "aa1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
        "title": "미적분 기초",
        "subject": "수학",
        "teacherName": "다정한 수리샘",
        "teacherAvatarUrl": "https://cdn.ilkwa.com/teachers/friendly.png",
        "progress": 0.4,
        "nextLessonTitle": "미분의 활용"
      }
    ],
    "todayAssignments": [
      {
        "id": "assign-hh8i9j0k",
        "title": "극한의 개념 - 과제",
        "dueDate": "2026-04-12T23:59:59Z",
        "status": "PENDING"
      }
    ],
    "recommendations": [
      {
        "type": "review",
        "title": "극한의 개념 복습",
        "reason": "최근 학습한 개념입니다. 복습을 권장합니다.",
        "linkTo": "/tutoring/aa1b2c3d/lessons/lesson-001"
      }
    ],
    "currentTeacher": {
      "id": "preset-friendly",
      "name": "다정한 수리샘",
      "avatarUrl": "https://cdn.ilkwa.com/teachers/friendly.png"
    },
    "notifications": [
      {
        "id": "notif-yy5z6a7b",
        "type": "assignment",
        "title": "과제 마감 임박",
        "isRead": false,
        "createdAt": "2026-04-12T22:00:00Z"
      }
    ]
  },
  "timestamp": "2026-04-12T09:30:00Z"
}
```

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 401 | AUTH_001 | 토큰 만료 |
| 503 | SYSTEM_001 | 일부 서비스 집계 실패 (부분 응답 반환될 수 있음) |

---

## 14. 내부 엔드포인트 (Spring↔FastAPI)

Spring Boot와 FastAPI 사이의 서버 간 통신 전용 엔드포인트다. 외부 인터넷에 노출되지 않으며 동일 VPC 내 프라이빗 서브넷에서만 호출된다.

### 공통 규격

인증: 모든 `/internal/*` 요청에 아래 헤더가 필수다.

```
X-Internal-Token: <공유 시크릿>
Content-Type: application/json
```

환경변수명: `ILKWA_INTERNAL_TOKEN` (Spring, FastAPI 동일)

네트워크: VPC 내부 전용. 로드 밸런서 레벨에서 외부 접근 차단. VPC 격리 + 토큰 검증 이중 방어 구조.

---

> 폐지 안내: `POST /internal/grading/batch-run` 엔드포인트는 2026-04-14 폐지되었다. 과제 채점(⑥)은 텔레그램 웹훅 수신 즉시 FastAPI `app/telegram/` → AssignmentGrader가 처리한다. Spring Scheduler 06/12/18/24시 Cron 배치 트리거 방식은 사용하지 않는다.

> **채점 경로 요약**
>
> | 구분 | 담당 | 실행 방식 |
> |---|---|---|
> | **과제 채점** | FastAPI `AssignmentGrader` | 텔레그램 웹훅 수신 즉시 비동기 Celery 큐 |
> | **Daily Consolidation** | `DailySummarizer` (Celery Beat) | 매일 자정 — 하루 학습 요약만 수행. 채점 로직 없음 |
> | **퀴즈 채점** | ②c `QAResponder` | 실시간 즉시 처리 |

---

### POST /internal/submissions/{submissionId}/ai-result

방향: FastAPI → Spring

용도: 채점 완료 콜백. FastAPI가 각 제출의 OCR → 채점을 완료할 때마다 Spring에 결과를 전달한다. Spring은 수신 즉시 DB 저장 후 200을 반환한다.

#### 요청 헤더

```
X-Internal-Token: <공유 시크릿>
Content-Type: application/json
```

#### 요청 바디 — 정상 채점

```json
{
  "ocr_text": "2026년 4월 14일\n문제 1. ...",
  "score": 87,
  "feedback": "전반적으로 논리 구조가 명확하나 2번 문항의 근거 제시가 부족하다.",
  "ai_confidence": 0.92,
  "ocr_failed_pages": "0/10",
  "is_retry": false,
  "status_transition": "done"
}
```

#### 요청 바디 — 전체 처리 실패 (OCR·ASR·파싱 전면 실패)

```json
{
  "ocr_text": null,
  "score": null,
  "feedback": null,
  "ai_confidence": 0.0,
  "ocr_failed_pages": "10/10",
  "is_retry": false,
  "status_transition": "failed"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `ocr_text` | string / null | OCR 추출 텍스트. 전체 실패 시 null |
| `score` | integer / null | AI 채점 점수. 전체 실패 시 null |
| `feedback` | string / null | AI 피드백. 전체 실패 시 null |
| `ai_confidence` | number | AI 신뢰도 (0.0~1.0) |
| `ocr_failed_pages` | string | 실패/전체 페이지 수 (예: `"2/10"`) |
| `is_retry` | boolean | 재제출 채점 여부 (true이면 submission_retry 테이블에 저장) |
| `status_transition` | string | Spring이 전이할 status 값. 값: `done` / `failed` / `grading` |

##### 응답 (200 OK)

```json
{
  "ack": true,
  "next_action": "notify_user"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `ack` | boolean | 수신 확인 |
| `next_action` | string | Spring이 수행할 후속 액션 (`notify_user` / `enqueue_dlq`) |

#### 에러 응답

| 상태 코드 | 설명 |
|-----------|------|
| 403 | X-Internal-Token 불일치 |
| 404 | submissionId 없음 |
| 409 | 이미 처리 완료된 제출 |

---

## 부록: 전체 엔드포인트 요약

| # | 메서드 | 엔드포인트 | 서버 | 인증 |
|---|--------|-----------|------|------|
| 1 | POST | `/auth/login` | Core | X |
| 2 | POST | `/auth/login/google` | Core | X |
| 3 | POST | `/auth/login/kakao` | Core | X |
| 4 | POST | `/auth/signup` | Core | X |
| 5 | POST | `/auth/refresh` | Core | X |
| 6 | GET | `/users/me` | Core | O |
| 7 | PUT | `/users/me` | Core | O |
| 8 | POST | `/users/onboarding` | Core | O |
| 9 | POST | `/users/telegram/link` | Core | O |
| 10 | GET | `/users/telegram/status` | Core | O |
| 11 | PUT | `/users/settings` | Core | O |
| 12 | GET | `/teachers/presets` | Core | O |
| 13 | POST | `/teachers` | Core | O (P) |
| 14 | GET | `/teachers` | Core | O |
| 15 | GET | `/teachers/{id}` | Core | O |
| 16 | PUT | `/teachers/{id}` | Core | O (P) |
| 17 | DELETE | `/teachers/{id}` | Core | O |
| 18 | PUT | `/teachers/{id}/select` | Core | O |
| 19 | POST | `/teachers/{id}/voice-profile` | AI | O (P) |
| 21 | POST | `/tutoring` | AI | O |
| 22 | GET | `/tutoring` | Core | O |
| 23 | GET | `/tutoring/{id}` | Core | O |
| 24 | GET | `/tutoring/{id}/curriculum` | AI | O |
| 25 | PUT | `/tutoring/{id}/curriculum/confirm` | AI | O |
| 26 | GET | `/tutoring/{id}/lessons` | Core | O |
| 27 | GET | `/tutoring/{id}/lessons/{lessonId}` | Core | O |
| 28 | GET | `/tutoring/{id}/lessons/{lessonId}/slides` | AI | O |
| 29 | GET | `/tutoring/{id}/lessons/{lessonId}/stream` | Core | O |
| 29-2 | GET | `/tutoring/{id}/lessons/{lessonId}/socratic/stream` | Core | O |
| 30 | POST | `/tutoring/{id}/lessons/{lessonId}/qa` | AI | O |
| 30-2 | POST | `/tutoring/{id}/lessons/{lessonId}/socratic/generate` | AI | O |
| 30-3 | POST | `/tutoring/{id}/qa/stream` | AI | O |
| 30-4 | POST | `/tutoring/{id}/lessons/{lessonId}/chat/stream` | AI | O |
| 31 | POST | `/tutoring/{id}/lessons/{lessonId}/socratic` | AI | O |
| 32 | POST | `/tutoring/{id}/lessons/{lessonId}/comprehension-check` | AI | O |
| 33 | POST | `/tutoring/{id}/lessons/{lessonId}/bookmark` | Core | O |
| 34 | GET | `/tutoring/{id}/lessons/{lessonId}/notes` | AI | O |
| 35 | POST | `/tutoring/{id}/lessons/{lessonId}/notes` | Core | O |
| 36 | PUT | `/tutoring/{id}/lessons/{lessonId}/notes/{noteId}/star` | Core | O |
| 37 | POST | `/assignments/generate` | AI | O |
| 38 | GET | `/assignments` | AI | O |
| 39 | GET | `/assignments/{id}` | AI | O |
| 40 | POST | `/assignments/{id}/submit` | AI | O |
| 41 | POST | `/assignments/{id}/submit/telegram` | AI | O |
| 42 | GET | `/assignments/{id}/result` | AI | O |
| 43 | GET | `/assignments/{id}/step-trace` | AI | O |
| 44 | GET | `/assignments/{id}/weakness-report` | AI | O |
| 45 | GET | `/slides/{id}/memo-note` | AI | O |
| 45b | GET | `/courses/{id}/memo-notes` | AI | O |
| 48 | GET | `/calendar` | Core | O |
| 49 | POST | `/calendar/events` | Core | O |
| 50 | PUT | `/calendar/events/{id}` | Core | O |
| 51 | POST | `/calendar/reschedule` | Core | O |
| 76 | GET | `/reports/study-time` | Core | O |
| 77 | GET | `/reports/assignment-completion` | Core | O |
| 78 | GET | `/reports/error-patterns` | Core | O |
| 79 | GET | `/reports/weakness-trend` | Core | O |
| 81 | GET | `/reports/self-healing-memory` | Core | O |
| 82 | POST | `/exam-forges` | AI | O (P) |
| 83 | GET | `/exam-forges` | AI | O |
| 84 | GET | `/exam-forges/{id}` | AI | O |
| 85 | POST | `/exam-forges/{id}/submit` | AI | O |
| 86 | GET | `/exam-forges/{id}/result` | AI | O |
| 87 | GET | `/subscription/plans` | Core | X |
| 88 | GET | `/subscription/current` | Core | O |
| 89 | POST | `/subscription/checkout` | Core | O |
| 90 | POST | `/subscription/confirm` | Core | O |
| 91 | POST | `/subscription/cancel` | Core | O |
| 92 | GET | `/subscription/history` | Core | O |
| 94 | GET | `/notifications` | Core | O |
| 95 | PUT | `/notifications/{id}/read` | Core | O |
| 96 | PUT | `/notifications/read-all` | Core | O |
| 97 | POST | `/telegram/webhook` | AI | Webhook |
| 98 | GET | `/dashboard` | Core | O |

> **O (P)** = 인증 필요 + Premium 구독 필요

---

## 15. 관리자 전용 API (슈퍼 관리자 한정)

> 주의: 이 섹션은 Spring 담당자 확정 후 수정 가능한 예비 명세다 (2026-04-15 stub).
> 엔드포인트 URL·응답 스키마·권한 검증 세부는 Spring 담당자가 최종 결정한다.

모든 엔드포인트는 **슈퍼 관리자 권한 필수 (role = SUPER_ADMIN)**이다. 일반 관리자·일반 유저는 접근할 수 없다.

---

### POST /admin/users/{userId}/telegram/chat-id

F16.1 — 유저 텔레그램 chat_id 편집·연동 해제·재연동.

인증: 슈퍼 관리자 권한 필수 (role = SUPER_ADMIN)

#### 요청 본문

| 필드 | 타입 | 설명 |
|---|---|---|
| chatId | number \| null | 설정할 chat_id. `action=unlink`일 때는 null로 전달 |
| action | string | `"set"` / `"unlink"` / `"relink"` |

```json
{
  "chatId": 123456789,
  "action": "set"
}
```

##### 성공 응답 (200 OK)

대상 유저의 텔레그램 연동 상태 요약 (linked, telegram_chat_id, linked_at 등 — 상세 스키마는 Spring 담당자 확정)

---

### GET /admin/telegram/messages

F16.2 — 텔레그램 DM 내역 조회. `telegram_message` 테이블 기반.

인증: 슈퍼 관리자 권한 필수 (role = SUPER_ADMIN)

#### 쿼리 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| chatId | number | 조회할 텔레그램 chat_id |
| from | string (ISO 8601) | 조회 시작 시각 |
| to | string (ISO 8601) | 조회 종료 시각 |

##### 성공 응답 (200 OK)

메시지 배열. 각 항목은 id, direction(in/out), text(content), sent_at(created_at) 등을 포함한다. 상세 스키마는 Spring 담당자 확정.

---

### GET /admin/telegram/sessions

F16.2 — 웹 AI 튜터·음성 Q&A·채점 세션 통합 조회.

인증: 슈퍼 관리자 권한 필수 (role = SUPER_ADMIN)

#### 쿼리 파라미터

| 파라미터 | 타입 | 설명 |
|---|---|---|
| userId | string (UUID) | 조회할 유저 ID |

##### 성공 응답 (200 OK)

세션 배열. 각 항목은 session_type, started_at, summary 등을 포함한다. 상세 스키마는 Spring 담당자 확정.

---

### GET /admin/telegram-credential

F16.3 — 봇 토큰(마스킹)·관리자 chat_id 조회. **봇 토큰 평문 응답 절대 금지.**

인증: 슈퍼 관리자 권한 필수 (role = SUPER_ADMIN)

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|---|---|---|
| botTokenFingerprint | string | 봇 토큰 해시 prefix (예: `abc123…`) — 평문 아님 |
| adminChatId | number | 관리자 알림 수신 chat_id |
| lastRotatedAt | string (ISO 8601) \| null | 마지막 토큰 교체 시각 |
| lastHealthCheckAt | string (ISO 8601) \| null | 마지막 `getMe` 헬스체크 성공 시각 |

```json
{
  "botTokenFingerprint": "abc123…",
  "adminChatId": 1234567890,
  "lastRotatedAt": "2026-04-10T09:00:00Z",
  "lastHealthCheckAt": "2026-04-15T08:00:00Z"
}
```

---

### PATCH /admin/telegram-credential

F16.3 — 봇 토큰·관리자 chat_id 수정. 저장 직후 `getMe` 헬스체크를 돌린다. **write-only 입력이므로 저장된 토큰 평문은 어느 응답에도 포함되지 않는다.**

인증: 슈퍼 관리자 권한 필수 (role = SUPER_ADMIN)

#### 요청 본문 (변경할 필드만 포함)

| 필드 | 타입 | 설명 |
|---|---|---|
| botToken | string (선택) | 새 봇 토큰 — write-only, 응답에 평문 미포함 |
| adminChatId | number (선택) | 새 관리자 chat_id |

```json
{
  "botToken": "your_bot_token_here",
  "adminChatId": 9876543210
}
```

#### 성공 응답 (200 OK)

fingerprint 갱신 결과만 포함. 상세 스키마는 Spring 담당자 확정.

```json
{
  "botTokenFingerprint": "xyz789…",
  "lastRotatedAt": "2026-04-15T12:00:00Z",
  "healthCheckPassed": true
}
```

---

## 16. AI 코인 API

> 서버: Spring Boot (Core)  
> Base URL: `https://api.ilkwa.com/api`  
> v1.5 신규: AI 코인 시스템 도입. 20코인 번들 = 49,900원. 사용처: 모의고사 생성(1코인), 슬라이드 해제(1코인), 강의 해제(pending).

---

### GET /coins/balance

현재 AI 코인 잔액을 조회한다.

인증: 필요

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.balance | integer | 현재 코인 잔액 |
| data.totalPurchased | integer | 누적 구매 코인 수 |
| data.totalUsed | integer | 누적 사용 코인 수 |

```json
{
  "success": true,
  "data": {
    "balance": 15,
    "totalPurchased": 20,
    "totalUsed": 5
  },
  "timestamp": "2026-04-20T10:00:00Z"
}
```

---

### POST /coins/purchase

AI 코인 번들을 구매한다. 토스페이먼츠로 결제한다.

요청 형식: `application/json`

인증: 필요 (Any authenticated user — Free 플랜 포함 모든 플랜에서 구매 가능)

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| bundleId | string | O | 번들 ID (`"COIN_20"` — 20코인 = 49,900원) |
| successUrl | string | O | 결제 성공 리다이렉트 URL |
| failUrl | string | O | 결제 실패 리다이렉트 URL |

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.orderId | string | 주문 ID |
| data.amount | integer | 결제 금액 (원) |
| data.coinAmount | integer | 충전될 코인 수 (20) |
| data.tossPaymentsClientKey | string | 토스페이먼츠 클라이언트 키 |

---

### GET /coins/history

AI 코인 이력을 조회한다. 페이지네이션 지원.

인증: 필요

응답 항목: `id`, `type` (`purchase`/`use_slide`/`use_exam`/`refund`), `amount`, `balanceAfter`, `refId`, `note`, `createdAt`

---

### POST /coins/use/slide

AI 코인 1개를 사용하여 슬라이드 1개를 해제한다.

> 안내: 슬라이드를 하나씩 해제하면 강의 생성 시간이 길어질 수 있다. 미리 필요한 슬라이드를 해제해두거나, Pro/Premium 구독을 이용하면 모든 슬라이드를 바로 이용할 수 있다.

요청 형식: `application/json`

인증: 필요

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| slideId | string | O | 해제할 슬라이드 ID |

#### 성공 응답 (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| data.slideId | string | 해제된 슬라이드 ID |
| data.coinUsed | integer | 소모 코인 (1) |
| data.coinBalance | integer | 차감 후 잔액 |

#### 에러 응답

| 상태 코드 | error_code | 설명 |
|-----------|------------|------|
| 409 | COIN_001 | 코인 잔액 부족 |
| 404 | RESOURCE_001 | 슬라이드를 찾을 수 없음 |
| 409 | COIN_002 | 이미 해제된 슬라이드 |

---

### POST /coins/use/exam

AI 코인 1개를 사용하여 모의고사를 생성한다. `POST /exam-forges`와 동일한 동작 — 코인 소모가 자동 처리됨.

인증: 필요

> 모의고사 생성은 `POST /exam-forges`를 직접 호출하면 코인이 자동 차감된다. 이 엔드포인트는 별도 코인 소모 확인용 stub이다.

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|-----------|
| 2026-04-15 | 2.0.8 | 봇 username 불일치 수정 (`ilkwa_bot` → `ilgwa_EZ_Team_bot`). 관리자 전용 API 섹션(§ 18) 신설 — F16.1·F16.2·F16.3 예비 stub 5개 엔드포인트. |
| 2026-04-12 | 2.0.0 | 초기 작성 |
| 2026-04-14 | 2.0.1 | AI 스트리밍 경계를 FastAPI 중심으로 정리 |
| 2026-04-14 | 2.0.2 | 과제 제출 `.zip` 업로드 검증 규칙 신설. 확장자 화이트리스트(`.ppx`)·용량·압축률·파일 수 상한·zip bomb 방어. Python `zipfile` 기반. |
| 2026-04-14 | 2.0.3 | 과제 제출 확장자 화이트리스트 구체화(`.pdf`/`.png`/`.jpg`/`.jpeg`/`.webp`), 단건 직접 업로드 허용, 검증 순서 경로 분기(zip/단건) 명시, PDF 처리 경로(`pypdfium2` 텍스트 레이어 우선·혼합 PDF 페이지별 분기) 신설, 텔레그램 봇 허용 확장자 5종 명시. |
| 2026-04-14 | 2.0.4 | 배치 채점 계약 확정 반영. § 17 내부 엔드포인트 신설 (`POST /internal/grading/batch-run`, `POST /internal/submissions/{submissionId}/ai-result`). 인증 방식 `X-Internal-Token` 명시. 과제 제출 엔드포인트 status를 `pending`(배치 대기)으로 변경, 채점 트리거 설명을 배치 채점(06/12/18/24시)으로 교체. 재제출 허용 구간(`pending`/`queued_for_batch`)·공식 점수 기준·`submission_retry` 테이블 명시. `rejected_reason` enum에 `ocr_all_failed` 추가. |
| 2026-04-14 | 2.0.5 | 과제 파이프라인 신규(⑤⑥) + 텔레그램 봇 경계 명시 반영. `POST /assignments/generate` — `axes` 파라미터·`telegramSent` 응답 필드 추가, 관리자 수동 생성 모드 설명 추가. `GET /assignments/{id}/result` — `status`·`weakConceptsAdded` 응답 필드 추가, 채점 경로 안내(퀴즈 소크라테스 vs 과제 텔레그램) 추가. `POST /telegram/webhook` — 서버 경계(FastAPI 전담, Spring 미관여) 및 과제 파일 처리 흐름 4단계 명시. `POST /internal/grading/batch-run` — Daily Consolidation 구분표 추가, 채점 책임 분리(퀴즈→QAResponder / 과제→AssignmentGrader) 명시. `GET /reports/self-healing-memory` — 학생 복습 시스템 rename 예정 주석, 에이전트 메모리와의 구분 명시. |
| 2026-04-14 | 2.0.6 | **P0 교정**: SSE 라인 재정의(FastAPI ②c 소크라테스 전용으로 한정). `POST /assignments/{id}/submit` 배치 채점 설명 삭제, `status=pending`·`nextBatchAt` 필드 삭제 → `status=submitted`로 변경. `POST /assignments/{id}/submit/telegram` 전면 재작성 — Spring Boot/OCR/photo/document/pdf/zip/png/jpg 참조 삭제, 허용 파일 `.py`/`.txt`, mimeType `text/x-python`/`text/plain`, 상태 `GRADING`으로 변경. `POST /telegram/webhook` 과제 파일 처리 흐름 4단계 재작성(OCR 삭제, AssignmentGrader 즉시 채점으로). 텔레그램 메시지 유형 표 교정. `POST /internal/grading/batch-run` 섹션 폐지 처리(엔드포인트 삭제, 폐지 안내로 대체). |
| 2026-04-14 | 2.0.7 | **P0-v2 재교정**: `POST /assignments/{id}/submit/telegram` 허용 파일 형식을 `.py`/`.txt` 2종에서 `.py`/`.txt`/`.md`/`.png`/`.jpg`/`.jpeg`/`.webp`/`.pdf`/`.zip`/`.mp3`/`.m4a`/`.wav` 다형식으로 원복·확장. AssignmentGrader 파일 타입별 처리 분기(이미지/PDF→①a OCR, 음성→Qwen3-ASR, .py→AST, .txt/.md→자유서술, .zip→해제 후 분기) 명시. 응답 `status` 값 `"GRADING"` → `"grading"` (소문자 enum 통일). `POST /telegram/webhook` 과제 파일 처리 흐름·메시지 유형 표 다형식으로 재교정. `status_transition` 필드 값 `rejected`/`ocr_running` 제거 → `done`/`failed`/`grading` 3종으로 교정. |
| 2026-04-17 | 2.0.9 | 문서 동기화 — OCR ONNX-only + SSE 전역 미사용 반영. 아키텍처 원칙 OCR/SSE/Modal 서술 갱신. 파일 타입 처리 경로 표·텔레그램 웹훅 흐름에서 Qwen-VL/PaddleOCR 혼용 표현 → ONNX Runtime CPU 단일 경로로 교체. |
| 2026-04-20 | 2.1.0 | 암기노트 슬라이드 기반 재설계 반영. `GET /assignments/{id}/memo-note` 삭제(과제 기반 플래시카드·에빙하우스 망각곡선 복습 시스템 전면 제거). `GET /slides/{id}/memo-note` + `GET /courses/{id}/memo-notes` 신설(§ 6 과제 API 절 내 배치). `GET /calendar/review-schedule` 전체 삭제. 캘린더 응답 예시에서 에빙하우스 1일차 이벤트 데이터 제거. 엔드포인트 목록 테이블 동기화. |
| 2026-04-20 | 2.2.0 | 요금제 v1.4 반영. § 13 구독/결제 API 전면 갱신 — Free/Plus/Pro 3종(9900원 폐기, 49900/99900원 신설), 플랜 features 응답 구조 교체(maxCourses/maxSlidesPerCourse/telegramBot/canCustomizeTutor). 에러코드 PAY_004 수정("이미 프리미엄 → 이미 해당 플랜"), PREMIUM_001 폐기 → PLAN_001·QUOTA_001·QUOTA_002 신설. subscriptionTier 응답 값 FREE/PLUS/PRO/EXPIRED로 교체. checkout planId 허용값 교체. |
| 2026-04-20 | 2.1.1 | 과제 형식 L2 검증 방식 확정 반영. `POST /assignments/generate` 요청에 `expectedInputType`(필수)·`allowedExtensions`(선택) 추가, 응답에 동일 두 필드 추가. `POST /assignments/{id}/submit/telegram` 에러 응답에 `415 REJECTED_TYPE_MISMATCH` 추가 + L2 거절 응답 예시 추가. 400 VALIDATION_001 설명에 `expectedInputType` 미지정 케이스 명시. |
| 2026-04-20 | 2.3.0 | 요금제 v1.5 반영. 플랜 명칭 전면 변경: Plus→Pro, Pro→Premium. subscriptionTier 응답값 FREE/PRO/PREMIUM/EXPIRED로 교체. 구독/결제 플랜 ID/이름 교체. AI 코인 기반 모의고사 사용 정책과 업그레이드/다운그레이드 엔드포인트를 추가. |
| 2026-04-20 | 2.4.0 | 요금제 v1.6 반영. `POST /coins/use/course` 엔드포인트 **제거** (강의 잠금 정책 폐지). `POST /coins/purchase` 권한 주석 "Any authenticated user — Free 포함"으로 업데이트. `POST /exam-forges` 완강+무료 1회 로직 명세 업데이트 — `courseId` 필드 추가, 완강 무료 첫 1회(coinUsed=0, isFreeTrialUsed=true) / 코인 차감(coinUsed=1) 분기 명시, EXAM_001 에러코드 신규 추가, 안내 문구 추가. `POST /coins/use/slide` 슬라이드 잠금 해제 안내 문구 추가. |
| 2026-04-23 | 2.5.0 | **ASR 최종 결정 반영 — Whisper Large V3 Turbo 채택**. `POST /tutoring/{id}/qa/stream` 설명의 "Modal ASR(Qwen3-ASR)" → "Modal ASR(Whisper Turbo)". `POST /telegram/webhook` 메시지 유형 표 음성 행 및 처리 흐름 4단계 "Qwen3-ASR" → "Whisper Turbo". `POST /assignments/{id}/submit/telegram` 음성 파일 처리 경로 "Qwen3-ASR" → "Whisper Turbo". |
| 2026-04-29 | 2.6.0 | ChapterStudio_V1 삽입형 렌더링 계약 반영. Spring Boot는 SseEmitter를 구현하지 않고 강의 생성 진행·소크라테스 질문·알림을 REST 상태 조회 또는 다음 요청 piggyback으로 제공한다. |
| 2026-04-29 | 2.7.0 | ChapterStudio_V1 학습 화면 채팅 계약 추가. `POST /tutoring/{id}/lessons/{lessonId}/chat/stream`는 `lessonId`와 `slideId`로 DB 컨텍스트를 읽어 강의 맞춤 답변을 StreamingResponse로 반환한다. |
| 2026-06-12 | 2.8.0 | 요금제 v1.7 가격 인하 반영. Pro 49,900→14,900원(강의 2개, 구독 코인 15,000), Premium 99,900→49,900원(강의 10개, 구독 코인 50,000). DB는 V19 마이그레이션으로 갱신. 코인 번들(COIN_5/20/60) 가격은 변경 없음. Prorated 예시·nextBillingAmount 동기화. |
| 2026-06-13 | 2.8.1 | **구독 코인 지급량 정정(V20)**. V19가 "코인 15,000원치/50,000원치"(상당액)를 코인 15,000개/50,000개로 잘못 입력 → 번들 단가 환산으로 정정: **Pro 5개(≈15,000원치=COIN_5), Premium 22개(≈50,000원치=COIN_20)**. `V20__fix_subscription_coin_grant.sql`. V17의 1원=1코인 환산 폐기. 코인 번들 가격·구독 KRW는 변경 없음. |
| 2026-05-25 | 2.8.0 | 최종 기능 범위 기준으로 확장 도메인 엔드포인트, 응답 필드, 알림 유형, 대시보드 요약 필드를 정리했다. |
| 2026-04-29 | 2.7.0 | ChapterStudio_V1 학습 화면 채팅 계약 추가. `POST /tutoring/{id}/lessons/{lessonId}/chat/stream`는 현재 강의·슬라이드·음성대본·핵심노트·퀴즈 맥락을 FastAPI가 DB에서 읽어 사용자 맞춤 답변을 스트리밍한다. 운영 모델은 FastAPI connector 설정으로 정한다. |
| 2026-04-20 | 2.3.0 | 요금제 v1.5 반영. 플랜 명칭 변경(Plus→Pro, Pro→Premium). subscriptionTier 응답값 FREE/PRO/PREMIUM/EXPIRED로 교체. § 13 구독/결제 플랜 ID/이름 교체. 모의고사 생성 AI 코인 1개 소모 명시. AI 코인 관련 엔드포인트를 추가했다. 업그레이드(prorated)/다운그레이드(다음 결제 주기) 플랜 변경 엔드포인트 추가. |
| 2026-04-20 | 2.2.0 | AI 튜터 CRUD (`/tutors/*`) 7개 + 암기노트 검색 (`/memo-notes/search`) 1개 신규 추가. (UI 결정 내용은 `00_개요/페이지별_기능정리.md` PAGE 18에 있음) |
| 2026-06-07 | — | OCR 아키텍처 확정: ①a 과제는 **Sonnet 4.6 VLM 직접 처리** (OCR 레이어 없음). ①c 교재·범용 PDF는 **OCR_V1(Marker+MinerU 이중 엔진)** 단일 경로. ①b(PaddleOCR ONNX 단일 경로 및 PaddleOCR-VL-1.5 Modal A10) 경로 폐기됨. Spring↔FastAPI 콜백 계약 (`ocr_text`, `ai_confidence` 등 필드)은 유지됨. |
| 2026-04-22 | — | 프론트엔드 기술 스택에 shadcn/ui를 공식 편입. |
| 2026-11-02 | — | 포트폴리오 정리: 상단 변경 배너를 이 표로 옮김. 아키텍처 개요와 과제 채점 경로의 OCR 서술을 현행(Sonnet VLM 직접 처리, OCR_V1)으로 맞춤. |
