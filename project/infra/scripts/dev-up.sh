#!/usr/bin/env bash
# 팀 공용 인프라 기동 스크립트
#
# 사용법:
#   ./dev-up.sh          DB 전용 (postgres + redis + qdrant + flyway)
#   ./dev-up.sh --full   풀스택 (DB + Spring + FastAPI + Frontend + Nginx)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

FULL_STACK=false
if [[ "${1:-}" == "--full" ]]; then
  FULL_STACK=true
fi

# 공용 네트워크 확인 및 생성
if ! docker network inspect sw-ez-shared >/dev/null 2>&1; then
  echo "[infra] sw-ez-shared 네트워크를 생성한다"
  docker network create sw-ez-shared
fi

# .env 확인
if [ ! -f .env ]; then
  echo "[infra] .env가 없다. .env.example을 복사해서 비밀번호를 설정해라"
  exit 1
fi

if $FULL_STACK; then
  echo "[infra] 풀스택 모드 — DB + Spring + FastAPI + Frontend + Nginx를 단일 compose로 기동한다"

  # backend-fastapi .env 확인
  if [ ! -f ../backend-fastapi/.env ]; then
    echo "[infra][경고] backend-fastapi/.env 없음. 통합 compose는 infra/.env 값을 우선 사용한다"
    echo "  cp ../backend-fastapi/.env.example ../backend-fastapi/.env"
  fi

  # backend-spring .env 확인
  if [ ! -f ../backend-spring/.env ]; then
    echo "[infra][경고] backend-spring/.env 없음. 통합 compose는 infra/.env 값을 우선 사용한다"
    echo "  cp ../backend-spring/.env.example ../backend-spring/.env"
  fi

  # frontend-web .env 확인 (.env 없으면 .env.example 복사)
  if [ ! -f ../frontend-web/.env ]; then
    echo "[infra][경고] frontend-web/.env 없음. .env.example을 기본값으로 사용한다"
  fi

  echo "[infra] 통합 compose up -d (DB + app + nginx)"
  docker compose -f docker-compose.yml -f docker-compose.local.yml up -d

  echo "[infra] 풀스택 기동 완료."
  echo "  포트 정리:"
  echo "    http://localhost:80   — Nginx (통합 진입점)"
  echo "    http://localhost:3000 — Frontend (직접)"
  echo "    http://localhost:8080 — Spring Boot (직접)"
  echo "    http://localhost:8082 — FastAPI (직접)"
  echo "    http://localhost:5432 — PostgreSQL"
  echo "    http://localhost:6379 — Redis"
  echo "    http://localhost:6333 — Qdrant"
else
  echo "[infra] DB compose up -d (postgres + redis + qdrant + flyway + minio)"
  docker compose -f docker-compose.yml up -d

  echo "[infra] DB-only 기동 완료. docker compose ps로 상태 확인해라"
  echo "  풀스택이 필요하면: ./scripts/dev-up.sh --full"
fi
