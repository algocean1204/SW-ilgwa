# frontend-web 프론트엔드 API 연동 명세서

> 기준 시점: 2026-04-22  
> 대상 코드: `frontend-web` 현재 스냅샷  
> 참조 문서: `Docs/02_API/Spring_API.md`, `Docs/02_API/엔드포인트_분류표.md`, `Docs/00_개요/기능명세서.md`, `Docs/00_개요/페이지별_기능정리.md`

`frontend-web` 코드가 기대는 목업 데이터와 로컬 상태, 백엔드 API 매핑, 연동 전에 정리할 블로커를 적는다.

---

## 1. 현재 상태 요약

| 항목 | 현재 상태 |
|---|---|
| 라우팅 방식 | `src/App.tsx` 내부 상태 기반 라우터다. URL path 기반 React Router는 아직 없다. |
| 선언된 라우트 키 | 22개 (`src/shared/types/route.ts`) |
| 실제 존재하는 뷰 파일 | 22개 (`src/views/*.tsx`) |
| 추가된 테스트용 뷰 | `StudyCalendar`, `TelegramHub`, `AdminConsole` |
| 실제 API 호출 코드 | 없음. `fetch`, `axios`, 전용 API client 검색 결과 0건이다. |
| base URL 상태 | `src/core/env.ts`에 `apiSpring`, `apiFastapi`, `apiRust`가 정의되어 있으나 현재 import/사용 코드가 없다. |
| 공유 mock 데이터 | `src/lib.tsx`의 `COURSES_DATA`, `TWEAKS_DEFAULTS` |
| 로컬 영속 상태 | `App.tsx`의 `route`, `activeCourseId`, `coins`, `plan`, `tweaks_state` / `TutorCustomize.tsx`의 `tutors_v1` |
| 빌드 상태 | `npm run build` 성공 (2026-04-22 확인) |
| 린트 상태 | `npm run lint` 성공 (2026-04-22 확인) |
| 브라우저 검증 | Playwright로 `calendar → telegram`, `admin` 화면 전환 확인 |

### 현재 남은 구조 리스크

| 구분 | 현재 문제 |
|---|---|
| 실제 API 호출 부재 | 모든 화면이 `setRoute()` + mock/local state만으로 동작한다. API client 계층이 없다. |
| React Router 미도입 | 문서상 표준은 React Router 기반 SPA지만 현재는 URL이 바뀌지 않는 상태 라우팅이다. |
| 데이터 산재 | 화면별 목업 데이터가 각 뷰 파일 내부에 흩어져 있어 API 교체 시 수정 범위가 넓다. |
| env 미사용 | `.env.example`와 `env.ts` 값은 맞췄지만, 현재 어떤 화면도 이를 실제로 쓰지 않는다. |
| Tailwind/shadcn 적용 편차 | 신규 테스트용 뷰는 shadcn + Motion을 사용하지만 기존 디자인 시안 포팅 뷰 다수는 인라인 스타일 비중이 높다. |

### 결론

현재 프론트는 "화면 초안 + 목업 데이터 + 로컬 상태" 단계다. 다만 라우팅 오류와 빌드 문제는 정리돼서, 이제는 화면 검증과 API 계약 고정까지는 가능한 상태다. 다음 단계는 이 문서를 기준으로 실제 API client를 붙이는 것이다.

---

## 2. 프론트 용어와 백엔드 리소스 매핑

현재 UI 용어와 실제 백엔드 문서의 리소스명은 다르다. 백엔드 전달 시 아래 표를 기준으로 통일해야 한다.

| 프론트 용어 | 실제 백엔드 리소스/엔드포인트 |
|---|---|
| 과외 | `/tutoring*` |
| 과외 상세/커리큘럼 | `/tutoring/{id}`, `/tutoring/{id}/curriculum`, `/tutoring/{id}/curriculum/confirm` |
| 강의/슬라이드/학습 | `/tutoring/{id}/lessons*` |
| AI 튜터 | `/tutors*` |
| 과제 | `/assignments*` |
| 암기노트 | `/slides/{id}/memo-note`, `/courses/{id}/memo-notes` |
| 학습 리포트 | `/reports*` |
| 학습 캘린더 | `/calendar*` |
| 구독 | `/subscription*` |
| AI 코인 | `/coins*` |

---

## 3. 라우트별 현황

### 3-1. 현재 구현된 뷰 22개

| routeKey | 화면 파일 | 현재 상태 | 주요 mock/로컬 상태 위치 | 연결할 백엔드 API |
|---|---|---|---|---|
| `landing` | `src/views/Landing.tsx` | UI만 구현됨. CTA는 모두 내부 `setRoute()`만 호출한다. | 로컬 `mode` | 직접 API 호출 없음. 로그인 이후 `POST /tutoring` 진입을 유도하는 랜딩 역할이다. |
| `auth` | `src/views/Auth.tsx` | 소셜 로그인 UI만 있음. Google/Kakao 버튼은 실제 OAuth 호출 없이 `onboarding`으로 이동한다. | 없음 | `POST /auth/login/google`, `POST /auth/login/kakao`, `POST /auth/refresh` |
| `onboarding` | `src/views/Onboarding.tsx` | 4단계 UI만 구현됨. 저장 API는 없지만 완료 이동은 `home`으로 정리됐다. | 로컬 `subjects`, `goal`, `when` | `GET /users/me`, `PUT /users/me`, `POST /users/onboarding` |
| `home` | `src/views/StudentHome.tsx` | 대시보드 UI는 구현됐지만 전부 목업이다. 잘못된 라우트 키는 정리됐고 일정 카드도 고정 mock 데이터로 바뀌었다. | 로컬 `COURSES`, 복습 리스트, 일정 리스트, 하드코딩 통계 | `GET /users/me`, `GET /tutoring`, `GET /assignments`, `GET /calendar`, `GET /reports/study-time` |
| `explore` | `src/views/CourseExplore.tsx` | 새 과외 생성 입력 UI다. 업로드/주제 입력 모두 목업이다. | 로컬 `teachers`, `topic`, `fileName`, `duration`, `depth`, `teacher` | `GET /tutors`, `POST /tutoring` |
| `courseDetail` | `src/views/CourseDetail.tsx` | 커리큘럼 검토/수정/확정 UI만 구현됨. | 로컬 `title`, `phase`, `chapters` | `GET /tutoring/{id}`, `GET /tutoring/{id}/curriculum`, `PUT /tutoring/{id}/curriculum/confirm` |
| `myCourses` | `src/views/MyCourses.tsx` | 목록 카드 UI 구현. 공통 mock 데이터에만 의존한다. | `src/lib.tsx` `COURSES_DATA` | `GET /tutoring` |
| `learning` | `src/views/StudentLearning.tsx` | 학습 화면 UI는 구현됐지만 채팅/과제/커리큘럼이 모두 목업이다. AI 응답은 `setTimeout()` 시뮬레이션이다. | `useCourse(COURSES_DATA)`, 로컬 `chat`, 인라인 `chapters`, 인라인 `hw`, `setTimeout()` | `GET /tutoring/{id}/lessons`, `GET /tutoring/{id}/lessons/{lessonId}`, `GET /tutoring/{id}/lessons/{lessonId}/slides`, `POST /tutoring/{id}/lessons/{lessonId}/qa`, `POST /tutoring/{id}/lessons/{lessonId}/socratic/generate`, `POST /tutoring/{id}/lessons/{lessonId}/socratic`, `POST /tutoring/{id}/lessons/{lessonId}/comprehension-check`, `POST /assignments/generate` |
| `aiNote` | `src/views/AINoteViewer.tsx` | AI 노트/질문/북마크 UI만 구현됨. 실제 노트 조회/저장 없음. | 로컬 `bookmarks`, `qaMessages`, `aiNotes`, `chapters`, `steps`, `setTimeout()` | `GET /tutoring/{id}/lessons/{lessonId}/slides`, `POST /tutoring/{id}/lessons/{lessonId}/qa`, `POST /tutoring/{id}/lessons/{lessonId}/bookmark`, `GET /tutoring/{id}/lessons/{lessonId}/notes`, `POST /tutoring/{id}/lessons/{lessonId}/notes`, `PUT /tutoring/{id}/lessons/{lessonId}/notes/{noteId}/star` |
| `notes` | `src/views/MemoryNotes.tsx` | 암기노트 모아보기 UI만 구현됨. 텔레그램 패널도 전부 로컬 상태다. | 로컬 `TOPICS`, `NOTES`, `telegramOn`, `autoSend`, `sendTime` | `GET /courses/{id}/memo-notes`, `GET /slides/{id}/memo-note`, `GET /users/telegram/status`, `POST /users/telegram/link`, `PUT /users/settings` |
| `calendar` | `src/views/StudyCalendar.tsx` | 문서 기준 PAGE 8을 테스트용 일정 보드로 추가했다. | 로컬 `agenda`, 주간 카드 mock | `GET /calendar`, `POST /calendar/events`, `PUT /calendar/events/{id}`, `POST /calendar/reschedule` |
| `telegram` | `src/views/TelegramHub.tsx` | 문서 기준 PAGE 11을 테스트용 연동 보드로 추가했다. | 로컬 이벤트 타임라인, 허용 파일 형식 안내, mock 딥링크 | `POST /users/telegram/link`, `GET /users/telegram/status`, `POST /assignments/{id}/submit/telegram` |
| `report` | `src/views/Report.tsx` | 통계 시각화 UI만 구현됨. | 로컬 `week`, 인라인 강점/약점 데이터 | `GET /reports/study-time`, `GET /reports/assignment-completion`, `GET /reports/error-patterns`, `GET /reports/weakness-trend` |
| `myHomework` | `src/views/MyHomework.tsx` | 과제 목록/상세 UI만 구현됨. 실제 제출/조회 API 없다. | 로컬 `ALL_HW`, 공유 `COURSES_DATA` | `GET /assignments`, `GET /assignments/{id}`, `POST /assignments/{id}/submit`, `GET /assignments/{id}/result`, `GET /assignments/{id}/weakness-report` |
| `mockExam` | `src/views/MockExam.tsx` | 모의고사 UI만 구현됨. 코인 차감은 여전히 로컬 `appState`지만, 최신 문서 기준으로 비용은 1코인으로 맞췄다. | 로컬 `history`, `freeUsed`, `COST = 1`, 공유 `COURSES_DATA` | `POST /mock-exams`, `GET /mock-exams`, `GET /mock-exams/{id}`, `POST /mock-exams/{id}/submit`, `GET /mock-exams/{id}/result`, `GET /coins/balance`, `POST /coins/use/exam` |
| `tutor` | `src/views/TutorCustomize.tsx` | 튜터 CRUD UI는 있으나 저장 대상이 서버가 아니라 `localStorage`다. | `DEFAULT_TUTORS`, `PERSONA_BASES`, `localStorage('tutors_v1')` | `GET /tutors`, `POST /tutors`, `GET /tutors/{id}`, `PATCH /tutors/{id}`, `DELETE /tutors/{id}`, `POST /tutors/{id}/reset`, `POST /tutors/{id}/preview` |
| `pricing` | `src/views/Pricing.tsx` | 요금제/코인 UI는 구현됐지만 데이터는 아직 상수다. 다만 플랜 enum은 `free/pro/premium`으로 최신 문서와 맞췄다. | 로컬 `plans`, `coinPacks`, `selected`, `tab`, `cycle` | `GET /subscription/plans`, `GET /subscription/current`, `POST /subscription/checkout`, `POST /subscription/cancel`, `GET /subscription/history`, `GET /coins/balance`, `POST /coins/purchase`, `GET /coins/history` |
| `settings` | `src/views/Settings.tsx` | 설정 탭 구조만 구현됨. 폼 저장/조회 연결 없음. | 로컬 `tab`, 각 input 기본값, toggle 로컬 state | `GET /users/me`, `PUT /users/me`, `PUT /users/settings`, `GET /users/telegram/status`, `POST /users/telegram/link`, `GET /subscription/current`, `GET /subscription/history`, `GET /coins/history` |
| `editor` | `src/views/LessonEditor.tsx` | 강사용 편집 UI mock이다. 현재 문서 기준 전용 API 계약은 정리되지 않았다. | 로컬 `items`, drag/drop 상태 | 우선 `GET /tutoring/{id}/lessons`, `GET /tutoring/{id}/curriculum` 재사용 검토. 강사 전용 쓰기 API는 별도 정의가 필요하다. |
| `admin` | `src/views/AdminConsole.tsx` | 문서 기준 PAGE 16을 테스트용 운영 보드로 추가했다. 현재는 관리자 API 연결 전 프론트 검수용 화면이다. | 로컬 운영 지표 mock | `POST /admin/users/{userId}/telegram/chat-id`, `GET /admin/telegram/messages`, `GET /admin/telegram/sessions`, `GET/PATCH /admin/telegram-credential`, `POST /admin/content`(기능 문서 기준) |

---

## 4. 주요 mock 데이터 위치

### 4-1. 공통/전역

| 파일 | 심볼/키 | 설명 |
|---|---|---|
| `src/lib.tsx` | `COURSES_DATA` | 내 과외, 사이드바, 과제 필터, 모의고사 선택의 공통 데이터 소스다. |
| `src/lib.tsx` | `TWEAKS_DEFAULTS` | UI 조정 패널 기본값이다. API 연동 대상은 아니다. |
| `src/App.tsx` | `localStorage(route, activeCourseId, coins, plan, tweaks_state)` | 앱 전역 상태를 모두 브라우저 로컬에 저장한다. |

### 4-2. 화면별 주요 mock

| 파일 | 주요 mock/시뮬레이션 |
|---|---|
| `src/views/StudentHome.tsx` | 로컬 `COURSES`, 복습 챕터 배열, 일정 배열, 하드코딩 암기노트/완료 강의 수치 |
| `src/views/CourseExplore.tsx` | 로컬 `teachers`, `topic`, `fileName`, `duration`, `depth`, `teacher` |
| `src/views/CourseDetail.tsx` | 로컬 `chapters`, `title`, `phase` |
| `src/views/StudentLearning.tsx` | 로컬 `chat`, 인라인 `chapters`, 인라인 `hw`, AI 응답 `setTimeout()` |
| `src/views/AINoteViewer.tsx` | 로컬 `bookmarks`, `qaMessages`, `aiNotes`, `chapters`, `steps`, QA `setTimeout()` |
| `src/views/MyHomework.tsx` | 로컬 `ALL_HW` |
| `src/views/MemoryNotes.tsx` | 로컬 `TOPICS`, `NOTES`, 텔레그램 설정 state |
| `src/views/TutorCustomize.tsx` | `DEFAULT_TUTORS`, `PERSONA_BASES`, `localStorage('tutors_v1')` |
| `src/views/MockExam.tsx` | 로컬 `history`, `freeUsed`, `COST = 1`, 생성 대기 `setTimeout()` |
| `src/views/Report.tsx` | 로컬 `week`, 인라인 강점/약점 카드 데이터 |
| `src/views/Pricing.tsx` | 로컬 `plans`, `coinPacks` |
| `src/views/LessonEditor.tsx` | 로컬 `items` drag/drop 편집 상태 |

### 4-3. 실제 API 호출 부재

- `frontend-web/src` 전체 검색 기준 `fetch(`, `axios`, `apiSpring`, `apiFastapi`, `apiRust` 사용처가 없다.
- 즉, 현재 화면 간 연결은 `setRoute()`와 local state만으로 동작한다.

---

## 5. 백엔드 연동 전에 먼저 정리해야 할 블로커

### P0

| 항목 | 정리 내용 |
|---|---|
| API 계층 부재 | 실제 `fetch`/client가 0건이라 백엔드 연동을 시작하면 바로 붙일 공통 API 계층이 먼저 필요하다. |
| 상태 관리 재설계 | `COURSES_DATA`, `coins`, `plan`, `tutor`, 화면별 mock 배열이 각 파일에 분산돼 있어 API 응답으로 교체하기 어렵다. |
| 라우팅 표준화 | 현재는 상태 기반 라우터라 새로고침/URL 공유가 불가능하다. 백엔드 연동 직전 React Router 전환 여부를 결정해야 한다. |
| 주요 화면 계약 확정 | `learning`, `aiNote`, `myHomework`, `pricing`, `tutor`, `calendar`, `telegram`의 req/res shape를 프론트 기준으로 먼저 고정해야 한다. |

### P1

| 항목 | 정리 내용 |
|---|---|
| API 계층 생성 | `src/api/` 또는 `src/services/`에 도메인별 client를 만들고, 화면 컴포넌트에서 직접 호출하지 않도록 분리해야 한다. |
| 응답 DTO 표준화 | `Docs/02_API/Spring_API.md`의 공통 응답 래퍼(`success`, `data`, `message`, `timestamp`) 기준으로 프론트 타입을 먼저 정의해야 한다. |
| 전역 앱 상태 재설계 | `COURSES_DATA`, `coins`, `plan`, `tutor`는 API 응답을 받아 저장하는 전역 store가 필요하다. |
| 환경변수 정리 | `.env.example`에 `VITE_API_BASE_RUST`는 추가했다. 다음 단계는 실제 사용 여부를 결정해 `env.ts`를 호출 계층과 연결하는 것이다. |

### 정책/명세 불일치

| 항목 | 현재 코드 | 최신 문서 기준 |
|---|---|---|
| 구독 플랜 값 | `free/pro/premium`으로 정리됨 | `free/pro/premium` |
| 모의고사 코인 비용 | `MockExam.tsx`에서 `COST = 1`로 정리됨 | `POST /mock-exams` 및 `POST /coins/use/exam` 기준 1코인 |
| 슬라이드 잠금 정책 | 코드에는 별도 명확한 반영 없음 | `POST /coins/use/course`는 삭제됐고, 현재는 `POST /coins/use/slide`만 유지 |

---

## 6. 백엔드 연동 우선순위

### 1차 연동

아래 API는 현재 구현된 22개 화면 중 실제 사용 빈도가 높고, 목업 제거 효과가 큰 우선 연결 대상이다.

| 도메인 | 우선 API |
|---|---|
| 인증/유저 | `POST /auth/login/google`, `POST /auth/login/kakao`, `POST /auth/refresh`, `GET /users/me`, `POST /users/onboarding`, `PUT /users/me`, `PUT /users/settings` |
| 과외/강의 | `GET /tutoring`, `POST /tutoring`, `GET /tutoring/{id}`, `GET /tutoring/{id}/curriculum`, `PUT /tutoring/{id}/curriculum/confirm`, `GET /tutoring/{id}/lessons`, `GET /tutoring/{id}/lessons/{lessonId}`, `GET /tutoring/{id}/lessons/{lessonId}/slides` |
| 학습 Q&A/노트 | `POST /tutoring/{id}/lessons/{lessonId}/qa`, `POST /tutoring/{id}/lessons/{lessonId}/bookmark`, `GET /tutoring/{id}/lessons/{lessonId}/notes`, `POST /tutoring/{id}/lessons/{lessonId}/notes`, `PUT /tutoring/{id}/lessons/{lessonId}/notes/{noteId}/star` |
| 과제 | `POST /assignments/generate`, `GET /assignments`, `GET /assignments/{id}`, `POST /assignments/{id}/submit`, `GET /assignments/{id}/result`, `GET /assignments/{id}/weakness-report` |
| 튜터 | `GET /tutors`, `POST /tutors`, `PATCH /tutors/{id}`, `DELETE /tutors/{id}`, `POST /tutors/{id}/reset`, `POST /tutors/{id}/preview` |
| 결제/코인 | `GET /subscription/plans`, `GET /subscription/current`, `POST /subscription/checkout`, `POST /subscription/cancel`, `GET /subscription/history`, `GET /coins/balance`, `POST /coins/purchase`, `GET /coins/history` |

### 2차 연동

| 도메인 | 후속 API |
|---|---|
| 암기노트 | `GET /slides/{id}/memo-note`, `GET /courses/{id}/memo-notes` |
| 리포트/캘린더 | `GET /reports/study-time`, `GET /reports/assignment-completion`, `GET /reports/error-patterns`, `GET /reports/weakness-trend`, `GET /calendar` |
| 모의고사 | `POST /mock-exams`, `GET /mock-exams`, `GET /mock-exams/{id}`, `POST /mock-exams/{id}/submit`, `GET /mock-exams/{id}/result`, `POST /coins/use/exam` |
| 확장 화면 | `POST /users/telegram/link`, `GET /users/telegram/status`, `POST /assignments/{id}/submit/telegram`, 관리자 API |

---

## 7. 문서 기준 최종 판단

1. 현재 `frontend-web`은 "화면 목업 확인 단계"이며, 백엔드와의 실제 HTTP 연결은 아직 0건이다.
2. 백엔드에 넘겨야 할 API 기준은 기존 문서의 `/courses/*`, `/ai-tutor/*`, `/subscriptions/*` 식 임시 명칭이 아니라, 최신 계약 문서의 `/tutoring/*`, `/tutors/*`, `/subscription/*`, `/assignments/*`, `/coins/*`다.
3. 빌드/린트/화면 전환 검증은 통과했으므로, 지금부터는 "계약 고정 → API client 추가 → mock 제거" 순서로 넘어가면 된다.
4. 이 문서는 백엔드 연동 요청에 쓸 초안이다. 다음 작업은 도메인별 응답 스키마를 프론트 타입으로 옮기는 일이다.
