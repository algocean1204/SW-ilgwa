#!/usr/bin/env bash
# Qdrant 적재 전 정규화·하이브리드 검색 측정. Qdrant는 인메모리 모드라 서버가 필요 없다.
# 필요: uv, Google Chrome(교재 PDF 렌더링), Pretendard 글꼴.
set -euo pipefail
cd "$(dirname "$0")"
export WORK="$PWD/work" HF_HOME="$PWD/work/hf" MODEL_CACHE_DIR="$PWD/work/hf/datalab" UV_CACHE_DIR="$PWD/work/uvcache"
mkdir -p work/data work/out/marker

uv venv -q -p 3.12 work/vmarker && uv pip install -q -p work/vmarker/bin/python marker-pdf
uv venv -q -p 3.12 work/vbench && uv pip install -q -p work/vbench/bin/python FlagEmbedding qdrant-client tiktoken pymupdf numpy pillow rapidfuzz pydantic pypdfium2 "transformers<5"

work/vbench/bin/python build_book.py "$WORK/data" "$@"   # 인자로 본문 md 폴더를 줄 수 있다 (기본 project/Docs)
work/vmarker/bin/marker_single work/data/book.pdf --output_dir work/out/marker --paginate_output --output_format markdown
SRC=marker work/vbench/bin/python bench.py
SRC=textlayer work/vbench/bin/python bench.py
echo "결과: work/out/result_marker.json, work/out/result_textlayer.json"
