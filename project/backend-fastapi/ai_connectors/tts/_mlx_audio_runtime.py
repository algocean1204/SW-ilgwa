"""mlx-audio Qwen3-TTS 실행 유틸(로컬 Mac 전용).

모델 빌드, 1회 추론, GenerationResult 수집만 한다.
"""
from __future__ import annotations

import tempfile
from typing import Any

import numpy as np
from scipy.io import wavfile

from common.audio_io import ensure_mono
from common.config import get_qwen3_tts_model_path
from common.logging import get_logger

from ..errors import InferenceError, ModelLoadError

_LOG = get_logger(__name__)

# Qwen3-TTS 기본 sample_rate. 모델이 load_audio 로 리샘플링하지만 32kHz 이상 참조 음성은
# 느려져서 24kHz 로 맞춰 쓴다.
_TARGET_SR = 24000


def build_model() -> Any:
    """mlx_audio.tts.utils.load 로 모델을 만든다. 실패하면 ModelLoadError 를 올린다."""
    # 모듈 import 때 MLX 디바이스가 초기화되지 않게 여기서 import 한다
    try:
        from mlx_audio.tts.utils import load
    except ImportError as exc:
        raise ModelLoadError(
            "mlx-audio 라이브러리가 설치되어 있지 않음. "
            "`uv sync` 로 pyproject 의존성을 설치하세요.",
        ) from exc
    model_path = get_qwen3_tts_model_path()
    _LOG.info("Qwen3-TTS 모델 로드 시작: %s", model_path)
    try:
        model = load(model_path)
    except Exception as exc:
        # 실패 원인(bf16 역직렬화, HF 다운로드 실패, 캐시 경로 오타 등)은 메시지에 담고
        # 예외 타입은 ModelLoadError 하나로 맞춘다.
        raise ModelLoadError(
            f"Qwen3-TTS 모델 생성 실패 (path={model_path}): {exc}",
        ) from exc
    _LOG.info("Qwen3-TTS 모델 로드 완료")
    return model


def run_generate(
    model: Any,
    text: str,
    ref_audio: np.ndarray,
    ref_sr: int,
    ref_text: str | None,
    language: str,
    speed: float,
    temperature: float = 0.8,
    top_k: int = 50,
    top_p: float = 1.0,
    repetition_penalty: float = 1.05,
) -> tuple[np.ndarray, int]:
    """임시 WAV 를 거쳐 Qwen3-TTS.generate 를 호출하고 (mono float32, sample_rate)를 돌려준다.

    mlx-audio 의 load_audio 는 str 경로나 mx.array 만 받아서, 참조 음성을 임시 WAV 로
    써서 넘긴다.

    ref_text 가 없으면 x_vector_only_mode=True 로 돌고 화자 유사도가 0.89에서 0.75 정도로 떨어진다.
    temperature(기본 0.8)/top_k/top_p/repetition_penalty 는 호출부에서 바꿀 수 있다.
    """
    # float32 mono 를 16-bit PCM 으로 쓴다
    mono = ensure_mono(ref_audio)
    clipped = np.clip(mono, -1.0, 1.0).astype(np.float32, copy=False)
    pcm16 = (clipped * 32767.0).astype(np.int16)
    # ref_text 가 없으면 x_vector_only_mode 로 돌린다
    use_x_vector_only = ref_text is None or not ref_text.strip()
    # mlx-audio 가 파일을 다 읽은 뒤 with 블록이 끝나면 임시 파일이 지워진다
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=True) as tmp:
        wavfile.write(tmp.name, ref_sr, pcm16)
        tmp.flush()
        # 스트리밍을 끄면 ICL 모드는 GenerationResult 하나를 낸다
        # (qwen3_tts._generate_icl 의 non-streaming 분기).
        chunks: list[Any] = []
        generate_kwargs: dict[str, Any] = {
            "text": text,
            "ref_audio": tmp.name,
            "lang_code": language,
            "speed": speed,
            "stream": False,
            "verbose": False,
            "temperature": temperature,
            "top_k": top_k,
            "top_p": top_p,
            "repetition_penalty": repetition_penalty,
        }
        if use_x_vector_only:
            # ref_text 가 없으면 화자 벡터만 참조한다
            generate_kwargs["x_vector_only_mode"] = True
        else:
            generate_kwargs["ref_text"] = ref_text
        for result in model.generate(**generate_kwargs):
            chunks.append(result)
    if not chunks:
        raise InferenceError("Qwen3-TTS 가 빈 GenerationResult 를 반환함")
    return _collect_audio(chunks, _resolve_sample_rate(model, chunks))


def _collect_audio(chunks: list[Any], sample_rate: int) -> tuple[np.ndarray, int]:
    """GenerationResult 리스트를 (float32 mono numpy, sample_rate) 로 합친다.

    GenerationResult.audio 는 1D mx.array(mono)이고, chunk 가 여럿이면 순서대로 잇는다.
    """
    arrays: list[np.ndarray] = []
    for result in chunks:
        audio_mx = getattr(result, "audio", None)
        if audio_mx is None:
            continue
        arrays.append(np.asarray(audio_mx, dtype=np.float32))
    if not arrays:
        raise InferenceError("Qwen3-TTS 결과에 audio 필드가 비어있음")
    audio_np = np.concatenate(arrays) if len(arrays) > 1 else arrays[0]
    return ensure_mono(audio_np), sample_rate


def _resolve_sample_rate(model: Any, chunks: list[Any]) -> int:
    """출력 샘플레이트. 첫 chunk 의 sample_rate, 없으면 model.sample_rate 를 쓴다."""
    first = chunks[0]
    sr = getattr(first, "sample_rate", None)
    if isinstance(sr, int) and sr > 0:
        return sr
    # Qwen3-TTS 는 config.sample_rate(기본 24000)를 쓴다
    model_sr = getattr(model, "sample_rate", None)
    if isinstance(model_sr, int) and model_sr > 0:
        return model_sr
    return _TARGET_SR
