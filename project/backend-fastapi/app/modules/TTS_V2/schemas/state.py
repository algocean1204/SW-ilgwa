"""TTS V2 파이프라인 상태 타입.

AudiobookState 는 LangGraph 노드들이 주고받는 전체 상태, ChunkRecord 는 청크 하나의 처리 결과다.
"""
from __future__ import annotations

import numpy as np
from typing import NotRequired, TypedDict


class ChunkRecord(TypedDict):
    """청크 하나의 처리 상태와 결과.

    chunk_id 는 "ch_000_001" 처럼 섹션 번호와 청크 번호를 3자리로 붙이며, 병합 순서도 이 값을 따른다.
    qc_reason 은 QC 통과 여부와 실패 원인을 구분하는 식별자다.
    """

    chunk_id: str
    # 청크가 속한 섹션 번호와 제목
    chapter_idx: int
    section_title: str

    # 정제·정규화는 청크 분할 전에 끝나므로 세 값은 같은 텍스트에서 시작한다
    original_text: str
    normalized_text: str
    planned_text: str       # LLM 낭독 재작성 후

    # 합성 결과. audio_np 가 None 이면 아직 합성 전이다
    audio_np: np.ndarray | None
    sample_rate: int
    duration_sec: float

    # QC 측정값. None 이면 QC 전이다
    cer: float | None
    wer: float | None
    qc_passed: bool
    retry_count: int
    # "pending", "ok", "high_cer", "high_wer", "speed_anomaly", "repetition",
    # "excessive_silence", "synthesis_error" 등
    qc_reason: str


class AudiobookState(TypedDict):
    """LangGraph StateGraph 의 파이프라인 전체 상태.

    각 노드는 이 상태를 읽고 바꿀 필드만 담아 반환한다.
    """

    # 입력. 요청 값으로 채우고 input_type 은 load_input_node 가 정한다
    input_type: str          # "text", "markdown", "file"
    raw_text: str | None
    file_content: str | None  # 파일에서 읽은 텍스트. 있으면 raw_text 보다 먼저 쓴다
    ref_audio_bytes: bytes
    ref_text: str
    ref_sample_rate: int
    language: str

    # load_input_node 가 나누고 clean_text_node 가 정제한 섹션
    # 항목 형식: {"title": str, "text": str, "level": int, "skip": bool}
    sections: list[dict]

    # chunk_text_node 가 만들고 이후 노드가 갱신한다
    chunks: list[ChunkRecord]

    # 파이프라인 제어
    current_phase: str   # 마지막으로 실행한 단계 이름
    max_retries: int
    failed_chunks: list[str]   # 재시도 한도까지 QC 를 통과하지 못한 청크 ID

    # 최종 출력. merge_node 가 채운다
    merged_audio_bytes: bytes | None
    total_duration_sec: float
    # pipeline_status 값:
    #   - "loading", "synthesizing" : 진행 중
    #   - "done"                    : 전체 청크 병합 완료
    #   - "partial"                 : QC 통과 청크만 병합
    #   - "error"                   : 진행 불가. 이후 노드는 바로 반환한다
    pipeline_status: str
    error_message: str | None
    timings: dict[str, float]   # 단계별 소요 시간 (ms 단위)

    # 요청별 옵션. 없으면 config 기본값을 쓴다
    skip_planner: NotRequired[bool]
    skip_postfx: NotRequired[bool]
    qc_engine: NotRequired[str]
    voice_profile_id: NotRequired[str]
    speed: NotRequired[float]
