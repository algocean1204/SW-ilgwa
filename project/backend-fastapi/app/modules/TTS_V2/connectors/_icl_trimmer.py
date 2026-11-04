"""Qwen3-TTS ICL 모드 레퍼런스 꼬리 트리밍.

ICL 모드 합성 결과 앞에 ref_text 끝부분이 붙어 나오는 경우가 있어,
mlx_whisper 단어 타임스탬프로 문장 시작점을 찾아 그 앞을 잘라낸다.
"""
from __future__ import annotations

import numpy as np

from common.logging import get_logger
from app.modules.TTS_V2.connectors._qc_utils import ensure_float32_mono, resample_to_16k
from app.modules.TTS_V2.connectors.whisperx_qc import _MODEL_REPO_MAP

_LOG = get_logger(__name__)

# 첫 음절이 잘리지 않게 트림 지점 앞에 남기는 여유 (초)
_MARGIN_SEC: float = 0.05
# 한국어 발화 속도 추정값 (글자/초). 휴리스틱 폴백에서 발화 시간을 추정한다
_KO_CPS_DEFAULT: float = 6.0


def _resolve_model_repo(model_size: str) -> str:
    """모델 크기 키를 HF 레포 이름으로 변환한다. 알 수 없으면 turbo 로 폴백한다."""
    return _MODEL_REPO_MAP.get(model_size, _MODEL_REPO_MAP["large-v3-turbo"])


def _transcribe_with_words(
    audio_np: np.ndarray,
    sample_rate: int,
    model_repo: str,
) -> list[dict]:
    """mlx_whisper 로 단어 수준 타임스탬프를 추출한다.

    mlx_whisper 가 없거나 전사가 실패하면 빈 리스트를 돌려 휴리스틱 폴백으로 넘긴다.
    """
    try:
        import mlx_whisper
    except ImportError:
        _LOG.warning("mlx_whisper 미설치 — ICL 트리밍 불가, 원본 반환")
        return []
    audio_f32 = ensure_float32_mono(audio_np)
    audio_f32 = resample_to_16k(audio_f32, sample_rate)
    try:
        result: dict = mlx_whisper.transcribe(
            audio_f32,
            path_or_hf_repo=model_repo,
            language="ko",
            word_timestamps=True,
            verbose=None,
        )
    except Exception as exc:
        _LOG.warning("mlx_whisper 전사 실패 — ICL 트리밍 건너뜀: %s", exc)
        return []
    word_segs: list[dict] = []
    for seg in result.get("segments", []):
        word_segs.extend(seg.get("words", []))
    return word_segs


def _find_target_start_sec(
    word_segs: list[dict],
    expected_text: str,
) -> float | None:
    """단어 타임스탬프 목록에서 expected_text 첫 단어의 시작 시각을 찾는다.

    양끝 공백을 떼고 소문자로 맞춘 뒤 부분 문자열로 비교해 단어 경계 차이를 허용한다.
    """
    if not word_segs or not expected_text:
        return None
    first_word = expected_text.split()[0].strip().lower()
    for entry in word_segs:
        word = entry.get("word", "").strip().lower()
        if first_word in word or word in first_word:
            start = entry.get("start")
            if isinstance(start, (int, float)):
                return float(start)
    return None


def _heuristic_start_sec(
    total_samples: int,
    sample_rate: int,
    expected_text: str,
) -> float:
    """단어 탐색이 실패하면 전체 길이에서 expected_text 예상 발화 시간을 빼 트림 지점을 추정한다."""
    total_sec = total_samples / max(sample_rate, 1)
    expected_sec = len(expected_text) / _KO_CPS_DEFAULT
    return max(0.0, total_sec - expected_sec)


def _trim_audio(
    audio_np: np.ndarray,
    sample_rate: int,
    start_sec: float,
) -> np.ndarray:
    """start_sec 에서 여유 구간만큼 앞부터 잘라낸다."""
    margin = max(0.0, start_sec - _MARGIN_SEC)
    start_idx = int(margin * sample_rate)
    if start_idx >= len(audio_np):
        return audio_np
    return audio_np[start_idx:]


def trim_icl_prefix(
    audio_np: np.ndarray,
    sample_rate: int,
    expected_text: str,
    model_size: str = "large-v3",
) -> tuple[np.ndarray, bool]:
    """ICL 합성 결과 앞의 ref_text 꼬리를 잘라 (오디오, 트림 여부)를 돌려준다.

    mlx_whisper 단어 타임스탬프로 expected_text 시작점을 찾고, 못 찾으면 글자 수로 추정한다.
    시작점이 여유 구간 안이면 원본을 그대로 돌려준다.
    """
    if not expected_text:
        return audio_np, False
    model_repo = _resolve_model_repo(model_size)
    word_segs = _transcribe_with_words(audio_np, sample_rate, model_repo)
    start_sec = _find_target_start_sec(word_segs, expected_text)
    if start_sec is not None and start_sec > _MARGIN_SEC:
        trimmed = _trim_audio(audio_np, sample_rate, start_sec)
        _LOG.info(
            "ICL 트리밍 (word-timestamp): %.2fs → %.2fs",
            len(audio_np) / max(sample_rate, 1),
            len(trimmed) / max(sample_rate, 1),
        )
        return trimmed, True
    h_start = _heuristic_start_sec(len(audio_np), sample_rate, expected_text)
    if h_start > _MARGIN_SEC:
        trimmed = _trim_audio(audio_np, sample_rate, h_start)
        _LOG.info(
            "ICL 트리밍 (heuristic): %.2fs → %.2fs",
            len(audio_np) / max(sample_rate, 1),
            len(trimmed) / max(sample_rate, 1),
        )
        return trimmed, True
    _LOG.debug("ICL 트리밍 미적용 (레퍼런스 꼬리 감지 안 됨 또는 여유 범위 내)")
    return audio_np, False
