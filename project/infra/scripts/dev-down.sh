#!/usr/bin/env bash
# 팀 공용 인프라 종료 스크립트 (볼륨은 보존)
#
# 사용법:
#   ./dev-down.sh          DB 전용 종료
#   ./dev-down.sh --full   풀스택 종료 (Nginx + FastAPI + Frontend + DB)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

FULL_STACK=false
if [[ "${1:-}" == "--full" ]]; then
  FULL_STACK=true
fi

if $FULL_STACK; then
  echo "[infra] 풀스택 종료 (DB + Spring + FastAPI + Frontend + Nginx)..."
  docker compose -f docker-compose.yml -f docker-compose.local.yml down
  echo "[infra] 풀스택 종료 완료. 볼륨은 유지된다."
  echo "  볼륨 삭제가 필요하면 별도 확인 후 명시적으로 실행해라"
else
  docker compose -f docker-compose.yml down
  echo "[infra] DB 종료 완료. 볼륨은 유지된다."
  echo "  볼륨 삭제가 필요하면 별도 확인 후 명시적으로 실행해라"
fi
