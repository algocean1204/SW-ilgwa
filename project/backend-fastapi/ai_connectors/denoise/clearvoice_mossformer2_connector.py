"""로컬 Mac용 MossFormer2 SE/SR (clearvoice) 커넥터.

SE_48K 디노이즈 → (선택) SR_48K 슈퍼해상도 순서로 처리한다.
SE/SR 모델은 처음 쓸 때 한 번 올린다. clearvoice 가 파일 경로를 받아서 임시 WAV 를 거친다.
블로킹 호출은 asyncio.to_thread 로 돌린다.
"""
from __future__ import annotations

import asyncio
import tempfile
import time
from pathlib import Path
from typing import Any

import numpy as np
from scipy.io import wavfile

from common.audio_io import (
    duration_sec,
    encode_wav_bytes,
    ensure_mono,
    load_audio_from_bytes,
)
from common.config import (
    get_mossformer2_se_checkpoint_dir,
    get_mossformer2_sr_checkpoint_dir,
)
from common.logging import get_logger

from ..errors import InferenceError, ModelLoadError
from ..schemas import DenoiseRequest, DenoiseResponse

_LOG = get_logger(__name__)

# MossFormer2_SE_48K / SR_48K 모두 48kHz mono 입출력을 기본으로 한다
_TARGET_SR = 48000


class ClearVoiceMossFormer2Connector:
    """MossFormer2_SE_48K 기반 디노이즈 커넥터 (선택적 SR_48K 후처리)."""

    # registry 키
    name: str = "mossformer2-se-48k"

    # SE 모델. 프로세스당 한 번 로드한다
    _se_instance: Any | None = None
    # SR 모델. apply_super_resolution=True 요청이 처음 올 때 로드한다
    _sr_instance: Any | None = None
    # Lock 은 첫 호출 때 만든다
    _se_lock: asyncio.Lock | None = None
    _sr_lock: asyncio.Lock | None = None

    async def denoise(self, request: DenoiseRequest) -> DenoiseResponse:
        """오디오 바이트를 정제된 WAV 바이트로 바꾼다."""
        # SR 입력도 48kHz 라서 48kHz mono 로 읽는다
        audio, sr = await asyncio.to_thread(
            load_audio_from_bytes, request.audio_bytes, _TARGET_SR
        )
        t0 = time.perf_counter()
        try:
            cleaned = await self._run_pipeline(
                audio, sr, request.apply_super_resolution
            )
        except ModelLoadError:
            # 로드 실패는 상위에서 503으로 매핑되게 그대로 올린다
            raise
        except Exception as exc:
            # clearvoice 추론 오류는 공통 예외로 바꾼다
            _LOG.exception("clearvoice 디노이즈 추론 실패")
            raise InferenceError(f"Denoise inference failed: {exc}") from exc
        latency_ms = (time.perf_counter() - t0) * 1000.0
        wav_bytes = await asyncio.to_thread(encode_wav_bytes, cleaned, _TARGET_SR)
        out_dur = duration_sec(ensure_mono(cleaned), _TARGET_SR)
        return DenoiseResponse(
            audio_bytes=wav_bytes,
            sample_rate=_TARGET_SR,
            content_type="audio/wav",
            duration_sec=out_dur,
            latency_ms=latency_ms,
            super_resolution_applied=request.apply_super_resolution,
            model=self.name,
        )

    def supports(self, feature: str) -> bool:
        """지원 기능 플래그."""
        return feature in {"denoise", "super_resolution"}

    # 내부 파이프라인

    async def _run_pipeline(
        self, audio: np.ndarray, sr: int, apply_sr: bool
    ) -> np.ndarray:
        """SE(필수) → SR(선택) 순서로 처리해 mono 결과를 돌려준다."""
        se_model = await self._ensure_se()
        cleaned = await asyncio.to_thread(_infer_with_tempfile, se_model, audio, sr)
        if not apply_sr:
            return cleaned
        # SR 이 실패하면 SE 결과로 대신하지 않고 에러를 올린다. 부분 성공을 숨기면 원인을 찾기 어렵다.
        sr_model = await self._ensure_sr()
        upscaled = await asyncio.to_thread(
            _infer_with_tempfile, sr_model, cleaned, _TARGET_SR
        )
        return upscaled

    @classmethod
    async def _ensure_se(cls) -> Any:
        """MossFormer2_SE_48K 인스턴스를 한 번만 로드한다."""
        if cls._se_instance is not None:
            return cls._se_instance
        if cls._se_lock is None:
            cls._se_lock = asyncio.Lock()
        async with cls._se_lock:
            if cls._se_instance is not None:
                return cls._se_instance
            cls._se_instance = await asyncio.to_thread(
                _build_clearvoice,
                "speech_enhancement",
                "MossFormer2_SE_48K",
                get_mossformer2_se_checkpoint_dir(),
            )
            return cls._se_instance

    @classmethod
    async def _ensure_sr(cls) -> Any:
        """MossFormer2_SR_48K 인스턴스를 한 번만 로드한다(선택)."""
        if cls._sr_instance is not None:
            return cls._sr_instance
        if cls._sr_lock is None:
            cls._sr_lock = asyncio.Lock()
        async with cls._sr_lock:
            if cls._sr_instance is not None:
                return cls._sr_instance
            cls._sr_instance = await asyncio.to_thread(
                _build_clearvoice,
                "speech_super_resolution",
                "MossFormer2_SR_48K",
                get_mossformer2_sr_checkpoint_dir(),
            )
            return cls._sr_instance


def _build_clearvoice(task: str, model_name: str, checkpoint_dir: str) -> Any:
    """ClearVoice 인스턴스를 만든다. 실패하면 ModelLoadError 를 올린다."""
    # 모듈 import 때 PyTorch/MPS 가 초기화되지 않게 여기서 import 한다
    try:
        from clearvoice import ClearVoice
    except ImportError as exc:
        raise ModelLoadError(
            "clearvoice 라이브러리가 설치되어 있지 않음. "
            "`uv sync` 실행 후 다시 시도하세요.",
        ) from exc
    # 체크포인트가 없으면 clearvoice 가 HF 에서 받아 오지만, 네트워크에 기대는 걸 드러내려고 먼저 확인한다.
    ckpt = Path(checkpoint_dir)
    if not ckpt.exists() or not any(ckpt.iterdir()):
        raise ModelLoadError(
            f"체크포인트 디렉터리가 비어있음: {checkpoint_dir}. "
            "`bash scripts/download_models.sh` 를 실행해 가중치를 내려받으세요.",
        )
    _LOG.info(
        "clearvoice 로드 시작: task=%s, model=%s, ckpt=%s",
        task, model_name, checkpoint_dir,
    )
    try:
        instance = ClearVoice(task=task, model_names=[model_name])
    except Exception as exc:
        raise ModelLoadError(
            f"clearvoice({model_name}) 로드 실패: {exc}",
        ) from exc
    _LOG.info("clearvoice 로드 완료: %s", model_name)
    return instance


def _infer_with_tempfile(
    cv_instance: Any, audio: np.ndarray, sr: int
) -> np.ndarray:
    """임시 WAV 를 거쳐 clearvoice 로 추론하고 결과를 1D mono 로 돌려준다."""
    mono = ensure_mono(audio)
    # 16-bit PCM 으로 쓴다(clearvoice 는 soundfile 로 float32 로 다시 읽는다)
    clipped = np.clip(mono, -1.0, 1.0).astype(np.float32, copy=False)
    pcm16 = (clipped * 32767.0).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=True) as tmp:
        wavfile.write(tmp.name, sr, pcm16)
        tmp.flush()
        result = cv_instance(tmp.name)
    # 단일 모델 호출은 ndarray 를 돌려준다(형상은 버전마다 다르다)
    if not isinstance(result, np.ndarray):
        raise InferenceError(
            f"clearvoice 반환 타입이 예상과 다름: {type(result)}",
        )
    return ensure_mono(result.astype(np.float32, copy=False))
