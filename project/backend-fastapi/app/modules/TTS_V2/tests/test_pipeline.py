"""TTS V2 전체 파이프라인 통합 테스트.

LangGraph 오디오북 파이프라인을 end-to-end 로 검증한다.
레퍼런스 음성은 ref_7.m4a (존댓말 톤, 약 14초) 를 사용한다.
"""
from __future__ import annotations

import importlib
import re

import pytest

from pathlib import Path

# mlx_audio 가 없으면 실제 TTS 합성 테스트를 건너뛴다
_has_mlx_audio = importlib.util.find_spec("mlx_audio") is not None

from app.modules.TTS_V2.pipeline.graph import get_compiled_graph
from app.modules.TTS_V2.schemas.state import AudiobookState

# 레퍼런스 음성 파일 (테스트 파일 기준 상대 경로)
_VOICE_DIR = Path(__file__).parent.parent / "voice_samples"
_REF_AUDIO_PATH = _VOICE_DIR / "ref_7.m4a"
_REF_TRANSCRIPT_PATH = _VOICE_DIR / "ref_7_transcript.txt"


@pytest.fixture(scope="module")
def ref_audio_bytes() -> bytes:
    """ref_7.m4a 를 bytes 로 읽는다. 모듈당 한 번만 읽는다."""
    return _REF_AUDIO_PATH.read_bytes()


@pytest.fixture(scope="module")
def ref_transcript() -> str:
    """ref_7_transcript.txt 내용을 읽는다. 모듈당 한 번만 읽는다."""
    return _REF_TRANSCRIPT_PATH.read_text(encoding="utf-8").strip()


def _build_state(
    text: str,
    ref_bytes: bytes,
    ref_text: str,
) -> AudiobookState:
    """TypedDict 필수 필드를 모두 채운 AudiobookState 초기값을 만든다."""
    return AudiobookState(
        input_type="text",
        raw_text=text,
        file_content=None,
        ref_audio_bytes=ref_bytes,
        ref_text=ref_text,
        ref_sample_rate=0,
        language="ko",
        sections=[],
        chunks=[],
        current_phase="init",
        max_retries=3,
        failed_chunks=[],
        merged_audio_bytes=None,
        total_duration_sec=0.0,
        pipeline_status="loading",
        error_message=None,
        timings={},
    )


def test_graph_compiles() -> None:
    """그래프가 컴파일되고 노드 9개에 __start__ 를 더한 10개를 가져야 한다."""
    graph = get_compiled_graph()
    # LangGraph CompiledStateGraph 는 graph.nodes dict 를 노출한다
    assert graph is not None
    node_names = list(graph.nodes)
    assert len(node_names) == 10, f"예상 노드 수: 10, 실제: {len(node_names)} — {node_names}"


@pytest.mark.slow
@pytest.mark.skipif(not _has_mlx_audio, reason="mlx_audio 미설치 — TTS 합성 불가")
def test_short_text_e2e(ref_audio_bytes: bytes, ref_transcript: str) -> None:
    """3문장 한국어 텍스트로 전체 파이프라인을 돌리고 주요 출력을 검증한다."""
    text = "안녕하세요. 오늘은 좋은 날씨입니다. 함께 공부해 볼까요?"
    state = _build_state(text, ref_audio_bytes, ref_transcript)

    result: AudiobookState = get_compiled_graph().invoke(state)

    assert result["merged_audio_bytes"] is not None
    assert len(result["merged_audio_bytes"]) > 0

    assert result["pipeline_status"] == "done"

    assert result["total_duration_sec"] > 0.0

    assert len(result["chunks"]) > 0

    failed_ids: set[str] = set(result["failed_chunks"])
    for chunk in result["chunks"]:
        assert chunk["qc_passed"] or chunk["chunk_id"] in failed_ids, (
            f"청크 {chunk['chunk_id']} 가 QC 실패 + failed_chunks 미등록 상태"
        )

    assert "synthesize" in result["timings"]
    assert "merge" in result["timings"]


@pytest.mark.slow
@pytest.mark.skipif(not _has_mlx_audio, reason="mlx_audio 미설치 — TTS 합성 불가")
def test_single_sentence_e2e(ref_audio_bytes: bytes, ref_transcript: str) -> None:
    """단일 문장 최소 입력으로 파이프라인이 정상 완료되어야 한다."""
    text = "테스트 문장입니다."
    state = _build_state(text, ref_audio_bytes, ref_transcript)

    result: AudiobookState = get_compiled_graph().invoke(state)

    assert result["merged_audio_bytes"] is not None
    assert len(result["merged_audio_bytes"]) > 0
    assert result["pipeline_status"] == "done"
    assert result["total_duration_sec"] > 0.0
    assert len(result["chunks"]) > 0

    failed_ids: set[str] = set(result["failed_chunks"])
    for chunk in result["chunks"]:
        assert chunk["qc_passed"] or chunk["chunk_id"] in failed_ids


@pytest.mark.slow
@pytest.mark.skipif(not _has_mlx_audio, reason="mlx_audio 미설치 — TTS 합성 불가")
def test_long_text_chunking(ref_audio_bytes: bytes, ref_transcript: str) -> None:
    """500자 초과 텍스트에서 청크 분할이 정상 동작하는지 검증한다."""
    text = (
        "인공지능 기술은 최근 몇 년 사이 눈부신 발전을 이루었습니다. "
        "특히 대규모 언어 모델의 등장은 자연어 처리 분야에 혁신을 가져왔습니다. "
        "이러한 모델들은 방대한 텍스트 데이터를 학습하여 인간과 유사한 수준의 언어 이해 능력을 보여줍니다. "
        "음성 합성 기술 또한 빠르게 발전하고 있으며, 자연스러운 억양과 감정 표현이 가능해졌습니다. "
        "이제 컴퓨터가 생성한 음성은 많은 경우 사람의 음성과 구별하기 어려울 정도입니다. "
        "이러한 기술들이 결합되면 교육, 엔터테인먼트, 접근성 향상 등 다양한 분야에서 활용될 수 있습니다. "
        "오디오북 자동 생성은 그 대표적인 응용 사례 중 하나입니다. "
        "시각 장애인을 포함한 다양한 사용자가 텍스트 콘텐츠를 음성으로 더 쉽게 접근할 수 있게 됩니다. "
        "딥러닝 기반 음성 합성 모델은 화자의 목소리 특성을 학습하여 자연스러운 음성을 생성할 수 있습니다. "
        "이를 통해 다양한 언어와 억양으로 고품질 오디오 콘텐츠를 자동으로 제작할 수 있습니다. "
        "향후 실시간 번역과 결합하면 언어 장벽 없는 글로벌 콘텐츠 접근이 가능해질 것으로 기대됩니다."
    )
    assert len(text) > 500, f"테스트 텍스트가 500자 미만: {len(text)}자"

    state = _build_state(text, ref_audio_bytes, ref_transcript)
    result: AudiobookState = get_compiled_graph().invoke(state)

    assert len(result["chunks"]) > 1, (
        f"500자 초과 텍스트에서 청크가 1개만 생성됨: {len(result['chunks'])}개"
    )

    chunk_id_pattern = re.compile(r"^ch_\d{3}")
    for chunk in result["chunks"]:
        assert chunk_id_pattern.match(chunk["chunk_id"]), (
            f"chunk_id 형식 불일치: {chunk['chunk_id']}"
        )

    assert result["merged_audio_bytes"] is not None
    assert len(result["merged_audio_bytes"]) > 0


@pytest.mark.slow
def test_pipeline_state_fields(ref_audio_bytes: bytes, ref_transcript: str) -> None:
    """파이프라인 최종 상태에 모든 예상 필드가 올바르게 설정되어 있는지 검증한다."""
    text = "상태 필드 검증을 위한 테스트 문장입니다."
    state = _build_state(text, ref_audio_bytes, ref_transcript)

    result: AudiobookState = get_compiled_graph().invoke(state)

    expected_timing_keys = {"synthesize", "merge"}
    missing_keys = expected_timing_keys - set(result["timings"].keys())
    assert not missing_keys, f"timings 에 누락된 키: {missing_keys}"

    assert isinstance(result["failed_chunks"], list)

    assert result["current_phase"] != ""
    assert isinstance(result["current_phase"], str)
