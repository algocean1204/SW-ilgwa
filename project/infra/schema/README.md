# schema/

Postgres 스키마의 기준 위치다. DDL 변경은 이 폴더에서만 한다.

---

## 폴더 구조

```
schema/
├── init/              # 컨테이너 최초 기동 시 1회 실행되는 초기화 SQL
├── migrations/        # Flyway 버전 마이그레이션
└── reference-models/  # 각 서비스 ORM 매핑 참고용
    ├── java/
    ├── python/
    └── rust/
```

---

## 폴더별 설명

### `init/`

Postgres 컨테이너가 처음 올라올 때 단 한 번 실행된다. 확장 설치(`CREATE EXTENSION`) 같은 DB 레벨 초기화에 사용한다. Flyway 체인과 독립적으로 동작한다.

### `migrations/`

Flyway가 관리하는 버전 마이그레이션 파일들이다. 네이밍 규칙은 아래를 따른다.

```
V{N}__{설명}.sql

예시:
V1__create_users_table.sql
V2__add_refresh_token_to_users.sql
```

### `reference-models/`

각 서비스가 테이블을 ORM으로 매핑할 때 참고하는 플레이스홀더 파일들이다. 실제 ORM 코드는 각 서비스 레포에서 관리한다. 여기 있는 파일은 참고용이므로 직접 실행하지 않는다.

---

## Flyway 동작 원리

infra `docker-compose.yml`의 `flyway` 서비스가 postgres 기동 후 `migrate` 명령을 1회 실행한다. 이미 적용된 마이그레이션은 체크섬으로 검증하고 건너뛴다.

---

## 마이그레이션 파일 작성 규칙

**V{N} 번호는 반드시 증가만 한다.** 기존 번호를 재사용하거나 건너뛰지 않는다. Flyway는 번호 순서로 체인을 구성하므로 중간 빈자리가 생기면 이후 마이그레이션이 실행되지 않는다.

**이미 배포된 마이그레이션 파일은 수정하지 않는다.** 파일 내용이 바뀌면 Flyway가 체크섬 불일치를 감지하고 전체 마이그레이션을 중단한다. 복구하려면 `flyway repair`가 필요하다.

기존 내용을 수정해야 하면 새 버전 파일에 `ALTER` 구문으로 추가한다.

```sql
-- 금지: 기존 V1__create_users_table.sql 내용 수정
-- 올바른 방법: V3__alter_users_add_column.sql 신규 추가
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
```

---

## ChapterStudio_V1 DB 계약

ChapterStudio_V1은 프론트가 빈 학습 프레임에 DB 저장 콘텐츠를 그대로 끼워 넣는 방식을 사용한다. 따라서 슬라이드 테이블에는 후처리와 검증이 끝난 `iframe_html`을 저장한다. 이 값은 iframe 태그가 아니라 프론트 iframe `srcDoc`에 들어갈 완성 HTML 문서다.

#### 원칙

- 도메인 소유권, 결제, 진도, 잠금은 Core/Spring 도메인에서 다룬다.
- AI 생성 산출물은 `chapter_studio` schema 안에서 관리한다.
- 두 영역은 `tutoring_id`, `lesson_id`, `slide_id`로 연결한다.
- `V2__chapter_studio.sql`은 초기 7개 산출물 테이블이다.
- `V3__chapter_studio_frontend_contract.sql`은 프론트 삽입형 렌더링 계약, 커리큘럼 초안, 강의 생성 상태를 보강한다.
- `V4__chapter_studio_generation_context.sql`은 Spring이 저장하고 FastAPI가 읽을 강의 생성 입력 스냅샷을 보강한다.

#### 강의 생성 입력 원천

| 입력 | 원천 |
|---|---|
| 주제/PDF 모드 | `curriculum_plan.source_type` |
| 챕터 정보 | `curriculum_unit.title`, `summary`, `learning_goal`, `slide_count` |
| 튜터·말투·속도·깊이·소크라테스·취약점 | `lesson_generation_status.generation_context` |
| 템플릿 | `lesson_generation_status.requested_template` |

#### 프론트 렌더링 경로

```
ChapterStudio_V1 생성
→ 후처리·검증·sanitize
→ slide.iframe_html 저장(srcDoc 문서 HTML)
→ 프론트 sandbox iframe srcDoc으로 렌더링
```
