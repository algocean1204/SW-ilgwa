"""ASRConnector 래퍼.

커넥터는 처음 쓸 때 만들고, numpy 오디오를 WAV 바이트로 바꿔
ASRRequest로 배치 호출한다.
"""
from __future__ import annotations

import numpy as np

from ai_connectors.base import ASRConnector
from ai_connectors.registry import get_asr_connector
from ai_connectors.schemas import ASRRequest, ASRResponse
from common.audio_io import encode_wav_bytes
from app.modules.ASR_V1.pipeline.config import ASR_MODEL, SAMPLE_RATE


class ASREngine:
    """numpy 오디오를 받아 ASRResponse를 돌려준다."""

    def __init__(self, model_name: str = ASR_MODEL) -> None:
        self._model_name = model_name
        self._connector: ASRConnector | None = None

    def _ensure_connector(self) -> ASRConnector:
        """처음 호출될 때 레지스트리에서 커넥터를 만든다."""
        if self._connector is None:
            self._connector = get_asr_connector(self._model_name)
        return self._connector

    async def transcribe(
        self,
        audio_np: np.ndarray,
        sr: int = SAMPLE_RATE,
        lang: str = "ko",
    ) -> ASRResponse:
        """numpy 오디오를 WAV 바이트로 바꿔 ASR 추론을 실행한다.

        Args:
            audio_np: float32 mono numpy 배열
            sr: 샘플레이트 (기본 16000Hz)
            lang: 전사 언어 코드

        Returns:
            text, latency_ms, duration_sec 등을 담은 ASRResponse.
        """
        connector = self._ensure_connector()
        wav_bytes = encode_wav_bytes(audio_np, sr)
        request = ASRRequest(
            audio_bytes=wav_bytes,
            language=lang,
            sample_rate=sr,
        )
        return await connector.generate(request)
