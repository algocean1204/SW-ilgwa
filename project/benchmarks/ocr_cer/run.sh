#!/usr/bin/env bash
# 교재 OCR CER 측정. 엔진마다 의존성이 달라 가상환경을 셋으로 나눈다. 결과는 work/ 아래에 쌓인다.
# 필요: uv, Google Chrome(테스트 PDF 렌더링), Pretendard 글꼴. Kanana 후처리는 Apple Silicon(MLX)용이다.
set -euo pipefail
cd "$(dirname "$0")"
export WORK="$PWD/work" HF_HOME="$PWD/work/hf" MODEL_CACHE_DIR="$PWD/work/hf/datalab" UV_CACHE_DIR="$PWD/work/uvcache"
export MINERU_MODEL_SOURCE=huggingface MINERU_TOOLS_CONFIG_JSON="$PWD/work/mineru.json"
mkdir -p work/data work/data_hard work/out/marker work/out/mineru work/out_hard/marker work/out_hard/mineru

uv venv -q -p 3.12 work/vmarker && uv pip install -q -p work/vmarker/bin/python marker-pdf
uv venv -q -p 3.12 work/vmineru && uv pip install -q -p work/vmineru/bin/python "mineru[core]>=2.5,<3"
uv venv -q -p 3.12 work/vtool && uv pip install -q -p work/vtool/bin/python mlx-lm pymupdf rapidfuzz pillow numpy pydantic pypdfium2 tiktoken "transformers<5"

work/vtool/bin/python build_set.py "$WORK/data" "$@"   # 인자로 본문 md 폴더를 줄 수 있다 (기본 project/Docs)
work/vtool/bin/python degrade_hard.py

for SET in "" _hard; do
  work/vmarker/bin/marker_single "work/data$SET/testset.pdf" --output_dir "work/out$SET/marker" --paginate_output --output_format markdown
  work/vmineru/bin/mineru -p "work/data$SET/testset.pdf" -o "work/out$SET/mineru" -b pipeline -m auto -l korean
  SFX=$SET work/vtool/bin/python evaluate.py > "work/out$SET/evaluate.json"
done
work/vtool/bin/python postproc.py > work/out/kanana.json
echo "결과: work/out/evaluate.json, work/out_hard/evaluate.json, work/out/kanana.json"
