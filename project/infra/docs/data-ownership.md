# 데이터 소유권

---

## 원칙

스키마 기준은 infra 레포다. 테이블 생성·변경은 Flyway 마이그레이션으로만 한다.

각 서비스는 자신의 ORM으로 테이블을 매핑해서 읽는다. 단, **쓰기 권한은 테이블별로 하나의 서비스에만 부여**한다. 다른 서비스는 해당 테이블을 읽기 전용으로만 접근한다.

---

## 테이블별 소유권

테이블별 쓰기 소유자는 아직 이 표에 정리하지 않았다.

| 테이블 | 쓰기 소유자 | 읽기 권한 | 비고 |
|---|---|---|---|
| *(미정)* | - | - | - |

---

## 스키마 변경 프로세스

1. `infra/schema/migrations/`에 `V{N}__{설명}.sql` 파일 추가
2. PR 생성 후 **모든 서비스 팀** 리뷰 완료
3. main 머지 후 `./scripts/dev-up.sh` 재실행 → Flyway가 자동 적용
4. 각 서비스는 자기 레포에서 ORM 엔티티를 수정하고 별도 PR을 올린다

---

## 금지 사항

서비스 애플리케이션 코드에서 DDL을 직접 실행하지 않는다.

```sql
-- 금지
CREATE TABLE users (...);
ALTER TABLE orders ADD COLUMN ...;
```

JPA `ddl-auto: create`, `ddl-auto: update`, SQLAlchemy `create_all()`, Diesel 마이그레이션 자동 적용 등 ORM의 스키마 자동 생성 기능도 비활성화한다. 스키마 변경 권한은 infra 레포의 Flyway에만 있다.
