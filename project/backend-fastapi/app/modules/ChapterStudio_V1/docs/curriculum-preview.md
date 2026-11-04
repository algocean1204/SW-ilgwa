# 커리큘럼 미리보기 연결

## 목적
`/demo/chapter-studio/curriculum`은 frontend-web의 `CourseDetail` 흐름을 미리 보는 화면이다. 커리큘럼은 채팅 화면에서 만들지 않고, `POST /tutoring` 이후 `GET /tutoring/{id}/curriculum`에서 확인하는 별도 단계다.

## 운영 흐름
Spring은 과외 생성, 권한, 상태 전이를 관리한다. FastAPI ChapterStudio_V1은 운영에서 Claude Opus 4.6 Planner를 1회 호출해 10~15개 강의 초안을 만들고 DB에 저장한다. 사용자가 확정하면 강의 1개 단위로 슬라이드 10~15장, 퀴즈, 핵심노트, 과제, 음성대본을 만든다.

## 로컬 검증
데모 화면의 `gemini` 엔진은 Opus 자리를 로컬에서 대신 확인하는 경로다. 운영 모델 라우팅은 바꾸지 않으며, `mock`은 빠른 회귀 테스트용이다.

## IN·OUT
IN은 제목, 주제, 과목, 난이도, 강의 수, 튜터, 엔진이다. OUT은 `CURRICULUM_READY` 상태, 확정 전 커리큘럼, 강의 목록, source analysis다.
