"""애플리케이션 공용 설정 모듈.

.env 값을 여기서 읽고 다른 모듈은 이 함수들을 쓴다.
테스트가 monkeypatch 할 곳이 한 군데로 모이고, 커넥터마다 os.getenv()가 흩어지지 않는다.
"""
from __future__ import annotations

import os
from pathlib import Path

# 프로젝트 루트(common/config.py 의 부모의 부모)
_PROJECT_ROOT = Path(__file__).resolve().parent.parent

# 모델 캐시 기본 경로. 모델 다운로드 스크립트가 여기에 저장한다.
_DEFAULT_MODELS_DIR = _PROJECT_ROOT / "models_cache"


def get_asr_model_name() -> str:
    """ASR 레지스트리 키를 반환한다. 미지정 시 로컬 Mac 기본값 mlx-qwen3-asr."""
    return os.getenv("AI_MODEL_ASR", "mlx-qwen3-asr")


def get_models_cache_dir() -> Path:
    """모델 가중치 로컬 캐시 디렉터리 경로."""
    # 다운로드 스크립트처럼 없으면 만든다
    _DEFAULT_MODELS_DIR.mkdir(parents=True, exist_ok=True)
    return _DEFAULT_MODELS_DIR


def get_denoise_model_name() -> str:
    """Denoise 커넥터 레지스트리 키. 미지정 시 mossformer2-se-48k(clearvoice + PyTorch MPS)."""
    return os.getenv("AI_MODEL_DENOISE", "mossformer2-se-48k")


def get_mossformer2_se_checkpoint_dir() -> str:
    """MossFormer2_SE_48K 체크포인트 디렉터리 (clearvoice가 읽는 경로).

    clearvoice 는 args.checkpoint_dir 기본값으로 CWD 기준 "checkpoints/<Model>"을 쓴다.
    .env 에 값이 있으면 그 경로를 쓴다.
    """
    local = os.getenv("MOSSFORMER2_SE_MODEL_PATH", "").strip()
    if local:
        return str(Path(local).expanduser().resolve())
    # 기본값: 프로젝트 루트의 checkpoints/MossFormer2_SE_48K
    return str(_PROJECT_ROOT / "checkpoints" / "MossFormer2_SE_48K")


def get_mossformer2_sr_checkpoint_dir() -> str:
    """MossFormer2_SR_48K 체크포인트 디렉터리 (선택적 슈퍼해상도)."""
    local = os.getenv("MOSSFORMER2_SR_MODEL_PATH", "").strip()
    if local:
        return str(Path(local).expanduser().resolve())
    return str(_PROJECT_ROOT / "checkpoints" / "MossFormer2_SR_48K")


def get_tts_model_name() -> str:
    """로컬 TTS 커넥터 레지스트리 키. 미지정 시 mlx-audio-qwen3-tts(Qwen3-TTS 12Hz bf16)."""
    return os.getenv("AI_MODEL_TTS", "mlx-audio-qwen3-tts")


def get_qwen3_tts_model_path() -> str:
    """Qwen3-TTS(MLX 커뮤니티 bf16) 가중치 경로.

    QWEN3_TTS_MODEL_PATH 가 있으면 그 경로를, 없으면 HF 허브 repo id 를 돌려주고
    mlx_audio.tts.utils.load 가 내려받아 캐시한다.
    mlx-community 레포에 speech_tokenizer/ 와 text tokenizer 가 들어 있어 따로 받을 필요가 없다.
    """
    # 지정 경로는 절대경로로 바꾼다
    local = os.getenv("QWEN3_TTS_MODEL_PATH", "").strip()
    if local:
        return str(Path(local).expanduser().resolve())
    # 기본값: HF 허브 repo id
    return "mlx-community/Qwen3-TTS-12Hz-1.7B-Base-bf16"


def get_postproc_model_name() -> str:
    """OCR 후처리 커넥터 레지스트리 키. 미지정 시 로컬 Mac 기본값 kanana2-mlx."""
    return os.getenv("AI_MODEL_POSTPROC", "kanana2-mlx")


def get_rate_limit_rpm() -> int:
    """분당 최대 요청 수. 미지정 시 60."""
    # 내부 서비스라 넉넉히 두되, GPU를 많이 쓰는 엔드포인트가 몰리지 않게 한다
    return int(os.getenv("RATE_LIMIT_RPM", "60"))


def get_rate_limit_burst() -> int:
    """순간 허용 버스트 요청 수. 미지정 시 10."""
    # 순간 몰림은 받아 주고 계속되는 과부하는 막는다
    return int(os.getenv("RATE_LIMIT_BURST", "10"))


