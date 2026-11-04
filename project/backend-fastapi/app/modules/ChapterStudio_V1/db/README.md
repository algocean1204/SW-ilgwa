# db

## 모듈 목적
DB 참조 모델과 저장 로직을 둔다. 스키마 기준은 `infra/schema/migrations/V2__chapter_studio.sql`이다.

## IN·OUT
IN은 schema 정의이며 OUT은 SQLAlchemy ORM 참조 모델이다.

## 환경변수
`DATABASE_URL`, `DATABASE_SCHEMA`를 사용한다.

## 의존성
`sqlalchemy`, `common.config`에 의존한다.

## 사용 예시
```python
from db.models import Base
```

## 에러 정책
실제 DB 작업 실패는 asyncpg 커넥션을 쓰는 persistence 모듈에서 처리한다.
