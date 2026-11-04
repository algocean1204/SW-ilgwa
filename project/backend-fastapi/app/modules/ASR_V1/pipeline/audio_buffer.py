"""스트리밍 음성 누적 버퍼.

음성 청크를 순서대로 모으고, 턴 커밋 때 버퍼 전체를 꺼내 ASR 배치 호출에 넘긴다.
"""
from __future__ import annotations

import numpy as np

from app.modules.ASR_V1.pipeline.config import BUFFER_MAX_SEC, SAMPLE_RATE


class AudioBuffer:
    """음성 청크 누적 버퍼.

    최대 누적 시간을 넘으면 가장 오래된 청크부터 버린다.
    """

    def __init__(
        self,
        sample_rate: int = SAMPLE_RATE,
        max_duration_sec: float = BUFFER_MAX_SEC,
    ) -> None:
        self._sr = sample_rate
        self._max_samples = int(max_duration_sec * sample_rate)
        self._chunks: list[np.ndarray] = []
        self._total_samples: int = 0

    def append(self, chunk: np.ndarray) -> None:
        """음성 청크를 버퍼에 추가하고 최대 길이를 유지한다."""
        self._chunks.append(chunk.astype(np.float32, copy=False))
        self._total_samples += len(chunk)
        # 하드캡 초과 시 가장 오래된 청크부터 제거
        while self._total_samples > self._max_samples and self._chunks:
            removed = self._chunks.pop(0)
            self._total_samples -= len(removed)

    def extract_speech(self) -> np.ndarray:
        """버퍼 전체를 이어 붙여 반환한다. 버퍼는 그대로 둔다."""
        if not self._chunks:
            return np.zeros(0, dtype=np.float32)
        return np.concatenate(self._chunks, axis=0)

    def consume_speech(self) -> np.ndarray:
        """버퍼 전체를 반환하고 비운다."""
        audio = self.extract_speech()
        self.clear()
        return audio

    def clear(self) -> None:
        """버퍼를 비운다."""
        self._chunks = []
        self._total_samples = 0

    def duration_sec(self) -> float:
        """현재 누적 오디오 길이(초)."""
        return self._total_samples / self._sr

    def is_empty(self) -> bool:
        return self._total_samples == 0
