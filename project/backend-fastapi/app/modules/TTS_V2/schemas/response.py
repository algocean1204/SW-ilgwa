"""TTS V2 합성 응답 Pydantic 모델."""
from __future__ import annotations

from pydantic import BaseModel, Field


class TTSV2Response(BaseModel):
    """TTS V2 파이프라인 응답 모델.

    failed_chunk_ids 가 비어 있으면 전체 청크 QC를 통과한 것이다.
    avg_cer, avg_wer 는 QC 값이 있는 청크가 없으면 None 이다.
    """

    # base64 인코딩한 WAV
    audio_base64: str
    total_duration_sec: float
    # 합성 샘플레이트. qwen3-tts-1.7b 출력은 24000 Hz 다
    sample_rate: int = Field(default=24000)
    chunk_count: int
    failed_chunk_count: int
    # 재시도 한도까지 QC 를 통과하지 못한 청크 ID
    failed_chunk_ids: list[str]
    # QC 값이 있는 청크가 없으면 None
    avg_cer: float | None
    avg_wer: float | None
    # QC 사유별 청크 수 (반복, 환각, 높은 CER 등)
    qc_reason_counts: dict[str, int] = Field(default_factory=dict)
    # 청크별 QC 사유. 프론트 디버깅용
    chunk_qc_reasons: list[dict[str, str]] = Field(default_factory=list)
    # 단계별 소요 ms ("load_input", "clean_text", "synthesize" 등)
    timings: dict[str, float]
    model: str = Field(default="qwen3-tts-1.7b-base")
    # 실제 QC 에 쓴 엔진: "whisperx", "v1_asr", "both", "none"
    qc_engine_used: str
