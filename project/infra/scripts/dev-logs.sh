#!/usr/bin/env bash
# 인프라 전체 로그 follow
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
docker compose logs -f "${@:-}"
