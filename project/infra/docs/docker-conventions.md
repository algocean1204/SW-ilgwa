# Docker 관례

SW-EZ-Team 전체 서비스에 적용되는 Docker 규칙이다. 모든 Dockerfile과 compose 파일은 이 기준을 따른다.

---

## 베이스 이미지

`slim-bookworm` 계열(Debian 12 glibc)로 통일한다.

```
금지  node:20-alpine
금지  python:3.12-distroless
사용  node:20-slim  (bookworm 기반)
사용  python:3.12-slim-bookworm
```

alpine과 distroless는 금지한다. glibc 의존 네이티브 바이너리가 alpine musl과 충돌하는 경우가 잦고, distroless는 디버깅이 불가능하다.

예외로 `flyway/flyway:10-alpine`만 허용한다. 1회성 실행 컨테이너이고 공식 이미지가 alpine만 제공한다.

---

## Dockerfile 전략

- 파일 하나(`Dockerfile`)만 사용한다. dev/prod 분기 타겟은 두지 않는다.
- 멀티스테이지 빌드로 `builder` → `runtime` 분리한다.
- 로컬 hot-reload는 `docker-compose.yml`의 `volumes` 마운트와 `command` override로 해결한다. Dockerfile에 개발 전용 로직을 넣지 않는다.

```dockerfile
# 예시 구조
FROM node:20-slim AS builder
...

FROM node:20-slim AS runtime
...
```

---

## 포트 할당

| 서비스 | 포트 |
|---|---|
| backend-spring | 8080 |
| backend-fastapi | 8082 |
| postgres | 5432 |
| redis | 6379 |
| qdrant (HTTP) | 6333 |
| qdrant (gRPC) | 6334 |

포트 변경이 필요하면 이 표를 먼저 수정하고 관련 팀에 공지한다.

---

## 공용 네트워크

네트워크명: `sw-ez-shared` (external)

infra의 `./scripts/dev-up.sh`가 없으면 자동 생성한다. 각 백엔드 레포의 compose에서는 직접 생성하지 않고 `external: true`로 참조만 한다.

```yaml
networks:
  sw-ez-shared:
    external: true
```

---

## 컨테이너 이름 규칙

`sw-ez-<service>` 형식으로 고정한다.

```
sw-ez-postgres
sw-ez-redis
sw-ez-qdrant
sw-ez-flyway
sw-ez-backend-spring
sw-ez-backend-fastapi
```

---

## 이미지 버전 고정

| 이미지 | 고정 태그 |
|---|---|
| postgres | `17-bookworm` |
| redis | `7.4-bookworm` |
| qdrant | `v1.12.5` |
| flyway | `10-alpine` |

`latest` 태그는 어떤 이미지에도 사용하지 않는다. 재빌드 시 의도치 않은 업그레이드가 발생한다.

---

## 비루트 실행 규칙

모든 런타임 스테이지에서 루트로 실행하지 않는다.

```dockerfile
RUN useradd -r -u 1001 appuser
USER appuser
```

UID는 1001로 고정한다. 서비스별로 다른 UID를 쓰지 않는다.

---

## 공통 템플릿

`docker-templates/` 하위에 언어별 참고용 Dockerfile이 있다.

- `docker-templates/java.Dockerfile`
- `docker-templates/python.Dockerfile`

신규 서비스 Dockerfile 작성 시 해당 템플릿을 기반으로 시작한다.
