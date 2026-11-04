#!/usr/bin/env bash
# API 컨테이너가 뜰 때 Modal 앱(강의 qwen27b, kanana2 오타 교정, qwen3-tts, asr-whisper, ocr-bge-m3)을 deploy 한다.
# celery worker/beat 이거나 Modal 토큰이 없으면 건너뛴다.

# deploy 가 실패해도 컨테이너는 떠야 해서 set -e 를 쓰지 않는다.

_should_run_modal_ensure_deploy() {
  # api 컨테이너(uvicorn)에서만 돈다
  if [[ "${1:-}" != "uvicorn" ]]; then
    return 1
  fi

  # MODAL_ENSURE_DEPLOY: 비우면 켜짐, 0/false 면 꺼짐
  local toggle
  toggle="$(printf '%s' "${MODAL_ENSURE_DEPLOY:-}" | tr '[:upper:]' '[:lower:]')"
  case "${toggle}" in
    0 | false) return 1 ;;
  esac

  # Modal 인증 토큰이 둘 다 있어야 deploy 한다
  if [[ -z "${MODAL_TOKEN_ID:-}" || -z "${MODAL_TOKEN_SECRET:-}" ]]; then
    return 1
  fi

  return 0
}

_resolve_modal_deploy_paths() {
  # 우선순위: MODAL_DEPLOY_APP_PATHS, MODAL_DEPLOY_APP_PATH, 기본 5개
  if [[ -n "${MODAL_DEPLOY_APP_PATHS:-}" ]]; then
    echo "${MODAL_DEPLOY_APP_PATHS}"
    return
  fi
  if [[ -n "${MODAL_DEPLOY_APP_PATH:-}" ]]; then
    echo "${MODAL_DEPLOY_APP_PATH}"
    return
  fi
  echo "app/modules/ChapterStudio_V1/deploy/modal_app.py app/modules/ChapterStudio_V1/deploy/modal_kanana_app.py app/modules/TTS_V2/deploy/modal_tts_app.py app/modules/ASR_V1/deploy/modal_whisper_app.py app/modules/OCR_v1/deploy/modal_embed_app.py"
}

_deploy_single_modal_app() {
  local app_path="$1"
  local timeout_sec="$2"

  if command -v timeout >/dev/null 2>&1; then
    if timeout "${timeout_sec}" modal deploy "${app_path}"; then
      echo "[modal-ensure-deploy] 성공: ${app_path}"
    else
      echo "[modal-ensure-deploy] 실패(타임아웃 또는 오류): ${app_path} — 서비스는 폴백 모델로 계속 기동합니다."
    fi
  else
    if modal deploy "${app_path}"; then
      echo "[modal-ensure-deploy] 성공: ${app_path}"
    else
      echo "[modal-ensure-deploy] 실패: ${app_path} — 서비스는 폴백 모델로 계속 기동합니다."
    fi
  fi
}

_run_modal_ensure_deploy() {
  local paths timeout_sec app_path
  paths="$(_resolve_modal_deploy_paths)"
  timeout_sec="${MODAL_DEPLOY_TIMEOUT:-180}"

  for app_path in ${paths}; do
    _deploy_single_modal_app "${app_path}" "${timeout_sec}" || true
  done
}

if _should_run_modal_ensure_deploy "${1:-}"; then
  _run_modal_ensure_deploy || true
fi

exec "$@"