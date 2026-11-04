"""Silero VAD 래퍼 모듈.

torch.hub에서 Silero VAD 모델을 싱글톤으로 로드하고,
512 샘플(32ms) 청크 단위 음성 확률을 계산한다.
"""
from __future__ import annotations

import numpy as np
import torch

from app.modules.ASR_V1.pipeline.config import VAD_THRESHOLD

# Silero VAD 권장 청크 크기(16kHz에서 32ms)
_CHUNK_SIZE: int = 512


class SileroVAD:
    """Silero VAD 래퍼. 모델은 클래스 단위로 한 번만 로드한다."""

    _model: torch.nn.Module | None = None

    def __init__(self, threshold: float = VAD_THRESHOLD) -> None:
        self._threshold = threshold
        self._h: torch.Tensor | None = None
        self._c: torch.Tensor | None = None
        self._ensure_model()
        self.reset_state()

    @classmethod
    def _ensure_model(cls) -> None:
        """모델이 아직 없을 때만 로드한다."""
        if cls._model is None:
            # Silero VAD v5 jit 모델. torch.hub가 받아서 캐시한다
            cls._model, _ = torch.hub.load(
                "snakers4/silero-vad",
                "silero_vad",
                trust_repo=True,
            )
            # eval 모드로 둔다. gradient는 process_chunk의 no_grad에서 끈다
            cls._model.train(False)

    def reset_state(self) -> None:
        """h/c 상태 텐서를 0으로 되돌린다. 세션 시작과 종료 때 호출한다."""
        self._h = torch.zeros(2, 1, 64)
        self._c = torch.zeros(2, 1, 64)

    def process_chunk(self, chunk: np.ndarray) -> float:
        """512샘플 청크의 음성 확률을 반환한다.

        Silero는 float32 텐서와 h/c 상태를 요구한다.
        배치 차원 1을 추가해 (1, 512) 형태로 전달한다.
        """
        x = torch.from_numpy(chunk).float().unsqueeze(0)
        with torch.no_grad():
            prob, self._h, self._c = self._model(x, 16000, self._h, self._c)
        return float(prob.item())

    def is_speech(self, chunk: np.ndarray) -> bool:
        """음성 확률이 임계값 이상이면 True를 반환한다."""
        return self.process_chunk(chunk) >= self._threshold

    @property
    def chunk_size(self) -> int:
        """VAD 처리 단위 샘플 수."""
        return _CHUNK_SIZE
