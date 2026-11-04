"""TTS V2 파이프라인 설정값 (청크, 재시도, QC, 후처리, 서버).

모든 값은 같은 이름의 환경변수로 바꿀 수 있다.
순환 import 를 피하려고 os 와 logging 만 import 한다.
"""
from __future__ import annotations

import logging
import os

_LOG = logging.getLogger(__name__)


def _safe_int(key: str, default: int) -> int:
    """환경변수를 int 로 읽는다. 비었으면 default, 변환이 안 되면 경고 후 default."""
    raw = os.getenv(key, "")
    if not raw:
        return default
    try:
        return int(raw)
    except (ValueError, TypeError):
        _LOG.warning("환경변수 %s 파싱 실패 (%r) — 기본값 %d 사용", key, raw, default)
        return default


def _safe_float(key: str, default: float) -> float:
    """환경변수를 float 로 읽는다. 비었으면 default, 변환이 안 되면 경고 후 default."""
    raw = os.getenv(key, "")
    if not raw:
        return default
    try:
        return float(raw)
    except (ValueError, TypeError):
        _LOG.warning("환경변수 %s 파싱 실패 (%r) — 기본값 %f 사용", key, raw, default)
        return default


# 청크 설정
# 청크 최소 글자 수. 너무 짧으면 TTS 가 어색한 묵음을 넣는다
TTS_V2_CHUNK_MIN_CHARS: int = _safe_int("TTS_V2_CHUNK_MIN_CHARS", 40)
# 청크 최대 글자 수. 길수록 합성이 느려지고 QC 정확도가 떨어진다
TTS_V2_CHUNK_MAX_CHARS: int = _safe_int("TTS_V2_CHUNK_MAX_CHARS", 120)

# 재시도 설정
# QC 실패 시 해당 청크를 재합성하는 최대 횟수
TTS_V2_MAX_RETRIES: int = _safe_int("TTS_V2_MAX_RETRIES", 3)

# QC 임계값
# CER 상한. 넘으면 해당 청크를 재합성한다
TTS_V2_CER_THRESHOLD: float = _safe_float("TTS_V2_CER_THRESHOLD", 0.20)
# WER 상한. CER 보조 지표로 쓴다
TTS_V2_WER_THRESHOLD: float = _safe_float("TTS_V2_WER_THRESHOLD", 0.25)
# 발화 속도 정상 범위 하한 (기대 속도 대비 배율)
TTS_V2_SPEED_MIN_RATIO: float = _safe_float("TTS_V2_SPEED_MIN_RATIO", 0.7)
# 발화 속도 정상 범위 상한 (기대 속도 대비 배율). 넘으면 반복 출력이나 속사로 본다
TTS_V2_SPEED_MAX_RATIO: float = _safe_float("TTS_V2_SPEED_MAX_RATIO", 1.5)
# 묵음 비율 상한. 넘으면 excessive_silence 로 판정한다
TTS_V2_SILENCE_MAX_RATIO: float = _safe_float("TTS_V2_SILENCE_MAX_RATIO", 0.3)

# 오디오 후처리
# 청크 사이 크로스페이드 길이 (ms). 0이면 그냥 이어 붙인다
TTS_V2_CROSSFADE_MS: int = _safe_int("TTS_V2_CROSSFADE_MS", 50)
# 목표 loudness (LUFS). 팟캐스트·스트리밍에서 흔히 쓰는 -16 이다
TTS_V2_TARGET_LUFS: float = _safe_float("TTS_V2_TARGET_LUFS", -16.0)
# 피크 상한 (dBFS). 클리핑이 나지 않게 -2 dBFS 이하로 누른다
TTS_V2_PEAK_DBFS: float = _safe_float("TTS_V2_PEAK_DBFS", -2.0)
# 앞뒤 묵음 트림 임계값 (절대 진폭). 이 값 이하를 묵음으로 본다
TTS_V2_TRIM_THRESHOLD: float = _safe_float("TTS_V2_TRIM_THRESHOLD", 0.01)

# QC 엔진 선택
# "whisperx", "v1_asr", "both". both 는 더 엄격하지만 느리다
TTS_V2_QC_ENGINE: str = os.getenv("TTS_V2_QC_ENGINE", "whisperx")
# WhisperX 모델 크기. large-v3 가 가장 정확하고 tiny 가 가장 빠르다
TTS_V2_WHISPERX_MODEL: str = os.getenv("TTS_V2_WHISPERX_MODEL", "large-v3")

# LLM 플래너
# 낭독 재작성 MLX 모델. 기본값은 Mac 로컬용 Qwen3-4B-Instruct 4bit 다
TTS_V2_LLM_PLANNER_MODEL_PATH: str = os.getenv(
    "TTS_V2_LLM_PLANNER_MODEL_PATH",
    "mlx-community/Qwen3-4B-Instruct-2507-4bit",
)

# 서버
# uvicorn 포트. 8000 은 기존 ASR 서버가 써서 8010 을 쓴다
TTS_V2_PORT: int = _safe_int("TTS_V2_PORT", 8010)

# 메모리 관리
# True 면 노드 실행 후 바로 모델을 언로드한다 (VRAM 이 부족할 때)
TTS_V2_AGGRESSIVE_UNLOAD: bool = (
    os.getenv("TTS_V2_AGGRESSIVE_UNLOAD", "false").lower() == "true"
)

# 한국어 발화 속도 기준
# 정상 발화 속도 범위 (초당 글자 수). 벗어나면 speed_anomaly 로 판정한다
TTS_V2_KO_CHARS_PER_SEC_MIN: float = _safe_float("TTS_V2_KO_CHARS_PER_SEC_MIN", 3.0)
TTS_V2_KO_CHARS_PER_SEC_MAX: float = _safe_float("TTS_V2_KO_CHARS_PER_SEC_MAX", 10.0)
