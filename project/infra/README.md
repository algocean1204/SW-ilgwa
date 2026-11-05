# infra

로컬 개발용 DB 서버 묶음 + 로컬 풀스택 통합 compose다.

---

## 현재 역할

PostgreSQL·Redis·Qdrant를 docker-compose로 묶어 로컬 개발 환경에 제공한다.  
선택적으로 FastAPI·Frontend·Nginx를 포함한 풀스택을 한 번에 기동할 수 있다.

| 컴포넌트 | 이미지 | 역할 |
|---|---|---|
| PostgreSQL | `postgres:16-bookworm` | 주 관계형 데이터베이스 |
| Redis | `redis:7.4-bookworm` | 캐시·큐 |
| Qdrant | `qdrant/qdrant:v1.12.5` | 벡터 검색 |
| Flyway | `flyway/flyway:10-alpine` | DB 스키마 마이그레이션 |
| Nginx | `nginx:1.27-bookworm` | 리버스 프록시 (--full 모드) |

---

## 로컬 실행

### DB 전용 (기본값)

```bash
cp .env.example .env
# .env에서 비밀번호 설정 후 저장
./scripts/dev-up.sh
docker compose ps
```

`sw-ez-postgres`, `sw-ez-redis`, `sw-ez-qdrant`, `sw-ez-flyway` 4개 서비스가 healthy 상태면 준비 완료다.

---

### 풀스택 (프로덕션 구조 근사)

FastAPI + Frontend + Nginx 리버스 프록시를 한 번에 기동한다.

```bash
# 1. 각 레포 .env 준비
cp .env.example .env
cp ../backend-fastapi/.env.example ../backend-fastapi/.env
# 각 .env에서 비밀번호·API 키 설정

# 2. 풀스택 기동
./scripts/dev-up.sh --full

# 3. 종료
./scripts/dev-down.sh --full
```

| 진입점 | 설명 |
|---|---|
| `http://localhost:80` | Nginx 통합 진입점 (프로덕션 구조 근사) |
| `http://localhost:3000` | Frontend 직접 접근 |
| `http://localhost:8082` | FastAPI 직접 접근 |
| `http://localhost:5432` | PostgreSQL |
| `http://localhost:6379` | Redis |
| `http://localhost:6333` | Qdrant |

주의: `backend-spring`(포트 8080)은 이 compose에 들어 있지 않아 별도 compose로 띄워야 한다.  
Nginx의 `/api/*` 경로는 backend-spring이 떠 있어야 동작한다.

---

### Nginx 라우팅 규칙 (풀스택 모드)

| 경로 | 업스트림 | 비고 |
|---|---|---|
| `/api/*` | `sw-ez-backend-spring:8080` | Spring REST |
| `/fastapi/api/*` | `sw-ez-backend-fastapi-api:8082` | FastAPI `/api/*` 라우트. Spring `/api/*`와 충돌하지 않는 기본 경로 |
| `/fastapi/media/*` | `sw-ez-backend-fastapi-api:8082` | FastAPI `/media/*` 정적 파일 |
| `/ai/*` | `sw-ez-backend-fastapi-api:8082` | 호환 경로. `/ai/curriculum/*` → `/api/curriculum/*` |
| `/ocr/*` | `sw-ez-backend-fastapi-api:8082` | 호환 경로. `/ocr/v1/*` → `/api/ocr/v1/*` |
| `/media/*` | `sw-ez-backend-fastapi-api:8082` | 기존 미디어 직접 접근 경로 |
| `/telegram/*` | `sw-ez-backend-fastapi-api:8082` | Telegram 웹훅. `/telegram/*` prefix 보존 |
| `/internal/*` | `sw-ez-backend-fastapi-api:8082` | 내부 통신. `/internal/*` prefix 보존 |
| `/*` | `sw-ez-frontend-web:3000` | React SPA (폴백) |

프론트 컨테이너 빌드 기준 FastAPI base는 `VITE_API_BASE_FASTAPI=http://localhost/fastapi`다.

---

## 하지 않는 것

- 프로덕션 인프라 자동화(IaC)는 하지 않는다
- 배포·CI/CD 파이프라인은 없다
- k8s·Terraform 구성은 없다

---

## 프로덕션 구성

프로덕션에서는 로컬 compose의 DB와 프록시를 AWS 자원으로 나눠 운영했다. 이 레포는 로컬 compose와 프로덕션 override(`docker-compose.prod.yml`)만 관리한다.

| 로컬 | 프로덕션 |
|---|---|
| PostgreSQL docker | RDS PostgreSQL 16 |
| Redis docker | Redis (세션·캐시) |
| Qdrant docker | Qdrant (FastAPI EC2, Dense+Sparse) |
| Nginx docker | CloudFront(S3 정적 배포·OAC) + Nginx(80/443, EC2) |

---

## 폴더 구조

```
infra/
├── .env.production.example  # 운영 환경변수 예시 (자동화 파일 아님)
├── nginx/                   # Nginx 리버스 프록시 설정
│   └── nginx.conf           # 로컬 풀스택용 라우팅 설정
├── schema/                  # PostgreSQL DDL 기준 위치
│   ├── init/                # 컨테이너 최초 기동 시 1회 실행
│   ├── migrations/          # Flyway 버전 마이그레이션
│   └── reference-models/    # ORM 매핑 참고용 (java/python)
├── docker-templates/        # 서비스별 Dockerfile 참고 템플릿
├── scripts/                 # dev-up.sh, dev-down.sh 등
├── docs/                    # 운영 가이드 문서
│   ├── dev-setup.md
│   ├── docker-conventions.md
│   └── data-ownership.md
├── contracts/               # 서비스 간 API 계약 (OpenAPI)
├── docker-compose.yml       # DB 전용 compose (postgres + redis + qdrant + flyway)
├── docker-compose.local.yml # 풀스택 통합 compose (FastAPI + Frontend + Nginx)
└── docker-compose.prod.yml  # 프로덕션 값 override
```

자격증명·포트 설정은 `.env.example`을 참고한다.

---

## 문서

- [개발 환경 설정](docs/dev-setup.md)
- [Docker 관례](docs/docker-conventions.md)
- [데이터 소유권](docs/data-ownership.md)
- [스키마 가이드](schema/README.md)

---

## 관련 저장소

| 레포 | 역할 |
|---|---|
| [backend-spring](https://github.com/SW-EZ-Team/backend-spring) | 메인 백엔드 |
| [backend-fastapi](https://github.com/SW-EZ-Team/backend-fastapi) | AI 추론·OCR·채점 |
| [frontend-web](https://github.com/SW-EZ-Team/frontend-web) | 웹 프론트엔드 |
