#!/usr/bin/env bash
# Modal 앱 두 개(Whisper V3 Turbo, BGE-M3·reranker)의 추론 코드를 Apple Silicon MPS에서 확인한다.
# 필요: uv, ffmpeg, macOS say(Yuna 음성). 모델은 work/hf 에 받는다(약 4GB).
set -euo pipefail
cd "$(dirname "$0")"
export HF_HOME="$PWD/work/hf" UV_CACHE_DIR="$PWD/work/uvcache" PYTORCH_ENABLE_MPS_FALLBACK=1 TOKENIZERS_PARALLELISM=false
mkdir -p work/audio
uv venv -q -p 3.12 work/venv && uv pip install -q -p work/venv/bin/python FlagEmbedding==1.3.5 torch==2.7.1 transformers==4.51.3
say -v Yuna -o work/audio/short.aiff "안녕하세요. 오늘은 분수의 덧셈을 배워 보겠습니다."
say -v Yuna -r 150 -f lecture_ko.txt -o work/audio/lecture.aiff
for n in short lecture; do ffmpeg -loglevel error -y -i "work/audio/$n.aiff" -ar 16000 -ac 1 "work/audio/${n}16k.wav"; done
work/venv/bin/python smoke.py work/audio work/result_mps.json
