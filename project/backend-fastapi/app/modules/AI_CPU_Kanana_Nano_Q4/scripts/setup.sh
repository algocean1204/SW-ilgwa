#!/usr/bin/env bash
# llama.cpp를 설치하고 Kanana Nano 2.1B LoRA SFT 모델의 Q4_K_M GGUF를 받는다.
# 이미 설치·다운로드돼 있으면 건너뛰므로 다시 실행해도 된다.
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC}   $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERR]${NC}  $*"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODEL_DIR="${SCRIPT_DIR}/models"
# 서비스 모델은 Kanana Nano 2.1B를 친구 대화로 LoRA SFT한 aisamdasu/algocean-2.1B-nano-friend 다.
# 이 저장소에는 LoRA 어댑터만 있고 GGUF는 아직 없다. 병합·Q4 양자화한 GGUF를 올린 저장소와
# 파일명을 KANANA_GGUF_REPO, KANANA_GGUF_FILE로 지정한다.
MODEL_REPO="${KANANA_GGUF_REPO:-aisamdasu/algocean-2.1B-nano-friend}"
MODEL_FILE="${KANANA_GGUF_FILE:-kanana-nano-2.1b-instruct.Q4_K_M.gguf}"
MODEL_PATH="${MODEL_DIR}/${MODEL_FILE}"

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=true
    info "DRY-RUN 모드: 실제 설치/다운로드 없이 경로·명령만 출력함"
fi

echo ""
echo "========================================================"
echo "  Kanana Nano 2.1B 벤치마크 환경 셋업"
echo "========================================================"
echo ""

ARCH=$(uname -m)
info "아키텍처: ${ARCH}"
if [[ "$ARCH" == "arm64" ]]; then
    success "Apple Silicon 감지 — Metal GPU 가속 지원 빌드 설치 예정"
else
    warn "x86_64 감지 — Metal 미지원, CPU 전용 실행"
fi

info "Homebrew 확인 중..."
if ! command -v brew &>/dev/null; then
    error "Homebrew가 설치되어 있지 않음"
    echo ""
    echo "  Homebrew 설치 방법:"
    echo "  /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
    echo ""
    exit 1
fi
success "Homebrew 확인: $(brew --version | head -1)"

info "llama.cpp 설치 상태 확인 중..."
if command -v llama-bench &>/dev/null; then
    success "llama-bench 이미 설치됨: $(which llama-bench)"
else
    echo ""
    warn "llama-bench가 없음. brew install llama.cpp 를 실행합니다."
    echo ""
    echo "  설치 대상: llama.cpp (Homebrew formula)"
    echo "  예상 설치 시간: 1~3분 (네트워크 속도에 따라 다름)"
    echo "  시스템 변경: /opt/homebrew/bin/ 아래 바이너리 추가"
    echo ""

    if [[ "$DRY_RUN" == true ]]; then
        info "[DRY-RUN] 실행 예정 명령: brew install llama.cpp"
    else
        read -r -p "  계속하시겠습니까? (y/N): " CONFIRM
        if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
            warn "설치 취소됨. 수동으로 'brew install llama.cpp' 를 실행한 뒤 다시 setup.sh를 실행하세요."
            exit 0
        fi
        echo ""
        info "llama.cpp 설치 중..."
        brew install llama.cpp
        success "llama.cpp 설치 완료"
    fi
fi

if [[ "$DRY_RUN" == false ]]; then
    if ! command -v llama-bench &>/dev/null; then
        error "설치 후에도 llama-bench를 찾을 수 없음"
        echo "  PATH에 /opt/homebrew/bin 이 포함되어 있는지 확인하세요:"
        echo "  export PATH=\"/opt/homebrew/bin:\$PATH\""
        exit 1
    fi
    success "llama-bench 경로: $(which llama-bench)"
fi

echo ""
info "모델 다운로드 확인 중..."
info "대상 파일: ${MODEL_FILE}"
info "저장 위치: ${MODEL_PATH}"

if [[ -f "$MODEL_PATH" ]]; then
    FILE_SIZE=$(du -sh "$MODEL_PATH" | cut -f1)
    success "모델 이미 존재함 (${FILE_SIZE}) — 다운로드 건너뜀"
else
    echo ""
    warn "모델 파일 없음. 다운로드를 시작합니다."
    echo ""
    echo "  소스: HuggingFace ${MODEL_REPO}"
    echo "  파일: ${MODEL_FILE}"
    echo "  예상 크기: ~1.3 GB"
    echo "  예상 시간: 수 분 (네트워크 속도에 따라 다름)"
    echo ""

    if [[ "$DRY_RUN" == true ]]; then
        info "[DRY-RUN] 실행 예정 명령 (huggingface-cli 방식):"
        echo "    huggingface-cli download ${MODEL_REPO} ${MODEL_FILE} --local-dir ${MODEL_DIR}"
        info "[DRY-RUN] 또는 직접 다운로드 방식:"
        echo "    curl -L -o '${MODEL_PATH}' \\"
        echo "      'https://huggingface.co/${MODEL_REPO}/resolve/main/${MODEL_FILE}'"
    else
        read -r -p "  계속하시겠습니까? (y/N): " CONFIRM
        if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
            warn "다운로드 취소됨."
            echo "  수동 다운로드 명령:"
            echo "  huggingface-cli download ${MODEL_REPO} ${MODEL_FILE} --local-dir ${MODEL_DIR}"
            exit 0
        fi
        echo ""

        # 다운로드 우선순위: huggingface-cli → python3 huggingface_hub → curl
        if command -v huggingface-cli &>/dev/null; then
            info "huggingface-cli 방식으로 다운로드 중..."
            huggingface-cli download "${MODEL_REPO}" "${MODEL_FILE}" \
                --local-dir "${MODEL_DIR}"
        elif python3 -c "import huggingface_hub" &>/dev/null 2>&1; then
            warn "huggingface-cli 없음 — python3 huggingface_hub 방식 사용"
            info "다운로드 중..."
            python3 - <<PYEOF
from huggingface_hub import hf_hub_download
import os
path = hf_hub_download(
    repo_id="${MODEL_REPO}",
    filename="${MODEL_FILE}",
    local_dir="${MODEL_DIR}",
    token=os.environ.get('HF_TOKEN')
)
print(f"저장 위치: {path}")
PYEOF
        else
            warn "huggingface-cli / python3 huggingface_hub 없음 — curl 직접 다운로드 방식 사용"
            info "다운로드 중... (진행률 표시)"
            HF_HEADERS=()
            if [[ -n "${HF_TOKEN:-}" ]]; then
                HF_HEADERS=(-H "Authorization: Bearer ${HF_TOKEN}")
            fi
            curl -L --progress-bar "${HF_HEADERS[@]}" \
                -o "${MODEL_PATH}" \
                "https://huggingface.co/${MODEL_REPO}/resolve/main/${MODEL_FILE}"
        fi

        if [[ -f "$MODEL_PATH" ]]; then
            FILE_SIZE=$(du -sh "$MODEL_PATH" | cut -f1)
            success "모델 다운로드 완료 (${FILE_SIZE})"
        else
            error "다운로드 실패. 아래 명령을 수동으로 실행하세요:"
            echo "  curl -L -o '${MODEL_PATH}' \\"
            echo "    'https://huggingface.co/${MODEL_REPO}/resolve/main/${MODEL_FILE}'"
            echo ""
            echo "  대안: HuggingFace에서 다른 Q4_K_M GGUF 검색"
            echo "  https://huggingface.co/search/full-text?q=kanana-nano+GGUF&type=model"
            exit 1
        fi
    fi
fi

echo ""
echo "========================================================"
if [[ "$DRY_RUN" == true ]]; then
    success "DRY-RUN 완료 — 실제 변경 없음"
else
    success "셋업 완료!"
    echo ""
    echo "  다음 단계: ./run_bench.sh 를 실행하세요"
fi
echo "========================================================"
echo ""
