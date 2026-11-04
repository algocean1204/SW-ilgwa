"""Llama 싱글톤 로더. Celery 워커 프로세스당 1회만 로드한다."""
from __future__ import annotations

import os
import threading
from pathlib import Path

from .config import CONTEXT_SIZE, N_GPU_LAYERS, THREADS

# LoRA SFT 모델(aisamdasu/algocean-2.1B-nano-friend)을 병합해 Q4 GGUF로 바꾼 파일을 쓴다.
# 그 저장소에는 LoRA 어댑터만 있고 GGUF가 없어, 파일명은 기존 로컬 이름을 두고 경로는 env로 바꾼다
_MODEL_PATH_ENV = "KANANA_MODEL_PATH"
_DEFAULT_MODEL = (
    Path(__file__).parent / "models" / "kanana-nano-2.1b-instruct.Q4_K_M.gguf"
)

# double-checked locking 싱글톤. global 문 없이 쓰려고 dict에 담는다
_lock = threading.Lock()
_STATE: dict[str, Llama | None] = {"llm": None}


def get_llama() -> Llama:
    """Llama 싱글톤을 돌려준다. 첫 호출에 1.3GB 모델을 올린다."""
    if _STATE["llm"] is not None:
        return _STATE["llm"]
    with _lock:
        # 락을 기다리는 동안 다른 스레드가 이미 로드했을 수 있다
        if _STATE["llm"] is not None:
            return _STATE["llm"]
        model_path = os.environ.get(_MODEL_PATH_ENV, str(_DEFAULT_MODEL))
        if not Path(model_path).is_file():
            raise FileNotFoundError(
                f"Kanana 모델 파일을 찾을 수 없음: {model_path}. "
                f"{_MODEL_PATH_ENV} 환경변수로 경로를 지정하거나 scripts/setup.sh 실행."
            )
        from llama_cpp import Llama

        _STATE["llm"] = Llama(
            model_path=model_path,
            n_ctx=CONTEXT_SIZE,
            n_threads=THREADS,
            n_gpu_layers=N_GPU_LAYERS,
            verbose=False,
        )
        return _STATE["llm"]
