#!/usr/bin/env bash
# 화자 유사도 재측정. 새로 합성하지 않고 데모 사이트 음성과 원본 음성만 비교한다. 필요: uv, ffmpeg.
set -euo pipefail
cd "$(dirname "$0")"
export HF_HOME="$PWD/work/hf" UV_CACHE_DIR="$PWD/work/uvcache"
mkdir -p work
uv venv -q -p 3.12 work/venv && uv pip install -q -p work/venv/bin/python "torch==2.7.1" "torchaudio==2.7.1" speechbrain numpy
work/venv/bin/python measure.py
echo "결과: results/summary.json"
