"""V1 _mlx_audio_runtime 을 감싼 TTS 엔진 세션.

레퍼런스 오디오는 세션을 만들 때 한 번만 numpy로 디코딩하고 청크 합성마다 재사용한다.
"""
from __future__ import annotations

import threading
from typing import Any

import numpy as np

from ai_connectors.tts._generation_profile import GenerationProfile
from common.audio_io import ensure_mono, load_audio_from_bytes
from common.logging import get_logger

_LOG = get_logger(__name__)

# Qwen3-TTS ICL 모드가 기대하는 레퍼런스 샘플레이트
_DEFAULT_REF_SR = 24000

# 기본 생성 파라미터. temperature는 더 자연스럽게 들리도록 0.8로 맞춘다
_DEFAULT_PROFILE = GenerationProfile(
    temperature=0.8,
    top_k=50,
    top_p=1.0,
    repetition_penalty=1.05,
)

# 모델 싱글톤. global 문 없이 쓰려고 dict에 담는다
_model_lock = threading.Lock()
_STATE: dict[str, Any] = {}


def _get_or_load_model() -> Any:
    """모델 싱글톤을 돌려준다. 첫 호출에만 build_model 을 실행한다."""
    if _STATE.get("model") is not None:
        return _STATE["model"]
    with _model_lock:
        # 락을 기다리는 동안 다른 스레드가 이미 로드했을 수 있다
        if _STATE.get("model") is None:
            from ai_connectors.tts._mlx_audio_runtime import build_model
            _LOG.info("Qwen3-TTS 모델 최초 로드 시작")
            _STATE["model"] = build_model()
            _LOG.info("Qwen3-TTS 모델 로드 완료 (싱글톤)")
    return _STATE["model"]


class TTSEngineSession:
    """단일 레퍼런스 화자에 대한 TTS 합성 세션.

    context manager 로 사용한다:
        with TTSEngineSession(ref_bytes, ref_text, ref_sr) as session:
            audio, sr = session.synthesize_chunk("안녕하세요.")

    모델은 첫 합성 요청 때 로드한다.
    """

    def __init__(
        self,
        ref_audio_bytes: bytes,
        ref_text: str,
        ref_sr: int = _DEFAULT_REF_SR,
        language: str = "ko",
    ) -> None:
        """레퍼런스 오디오를 디코딩해 둔다."""
        self._ref_text = ref_text
        self._language = language
        effective_sr = ref_sr if ref_sr > 0 else _DEFAULT_REF_SR
        ref_np, actual_sr = load_audio_from_bytes(ref_audio_bytes, target_sr=effective_sr)
        self._ref_sr = actual_sr
        self._ref_audio: np.ndarray = ensure_mono(ref_np)
        _LOG.debug(
            "TTSEngineSession 초기화: ref_audio shape=%s sr=%d lang=%s",
            self._ref_audio.shape,
            self._ref_sr,
            self._language,
        )

    def __enter__(self) -> TTSEngineSession:
        return self

    def __exit__(self, *args: object) -> None:
        # 정리할 자원이 없다
        pass

    def synthesize_chunk(
        self,
        text: str,
        speed: float = 1.0,
        profile: GenerationProfile | None = None,
    ) -> tuple[np.ndarray, int]:
        """청크 하나를 합성해 (mono float32 numpy, sample_rate) 를 돌려준다."""
        from ai_connectors.tts._mlx_audio_runtime import run_generate
        p = profile or _DEFAULT_PROFILE
        model = _get_or_load_model()
        return run_generate(
            model=model,
            text=text,
            ref_audio=self._ref_audio,
            ref_sr=self._ref_sr,
            ref_text=self._ref_text,
            language=self._language,
            speed=speed,
            temperature=p.temperature,
            top_k=p.top_k,
            top_p=p.top_p,
            repetition_penalty=p.repetition_penalty,
        )
