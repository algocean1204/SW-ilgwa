"""Chat_V1 음성 파이프라인의 LangGraph 노드. Gemini Flash Live로 음성 질문에 음성으로 답한다."""
from __future__ import annotations

from ai_connectors.errors import AIConnectorError

from app.modules.Chat_V1.pipeline.state import VoiceChatState


def validate_voice(state: VoiceChatState) -> VoiceChatState:
    """audio_input이나 system_prompt가 비어 있으면 error_message를 채운다."""
    if not state.get("audio_input", "").strip():
        return {
            **state,
            "error_message": "음성 데이터가 비어 있어요.",
            "pipeline_status": "invalid_input",
        }
    if not state.get("system_prompt", "").strip():
        return {
            **state,
            "error_message": "강의 컨텍스트가 없어요.",
            "pipeline_status": "invalid_input",
        }
    return {**state, "pipeline_status": "validated"}


async def generate_voice_answer(state: VoiceChatState) -> VoiceChatState:
    """Gemini Flash Live를 불러 음성 응답과 트랜스크립트를 채운다.

    커넥터 오류는 error_message에 남기고 상태를 failed로 둔다.
    """
    if state.get("error_message"):
        # 이전 노드에서 오류가 났으면 그대로 통과
        return state

    from app.modules.Chat_V1.app.voice_service import call_gemini_flash_live

    try:
        audio_b64, transcript_in, transcript_out = await call_gemini_flash_live(
            audio_data=state["audio_input"],
            audio_format=state.get("audio_format", "webm"),
            sample_rate=state.get("sample_rate", 16000),
            system_prompt=state["system_prompt"],
        )
    except AIConnectorError as exc:
        return {
            **state,
            "error_message": str(exc),
            "pipeline_status": "failed",
        }

    return {
        **state,
        "raw_audio_output": audio_b64,
        "transcript_input": transcript_in,
        "transcript_output": transcript_out,
        "pipeline_status": "answered",
    }


def format_voice_response(state: VoiceChatState) -> VoiceChatState:
    """트랜스크립트에서 슬라이드 인용을 뽑고 최종 음성 응답을 정한다.

    오류 상태면 빈 응답 대신 무음 WAV와 안내 문구를 돌려준다.
    """
    if state.get("error_message"):
        return {
            **state,
            "audio_output": _empty_wav_b64(),
            "audio_format_out": "wav",
            "transcript_input": "",
            "transcript_output": (
                "죄송해요, 답변을 생성하는 중에 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
            ),
            "referenced_slides": [],
            "pipeline_status": "error_handled",
        }

    from app.modules.Chat_V1.app.service import extract_referenced_slides

    transcript_out = state.get("transcript_output", "")
    refs = extract_referenced_slides(transcript_out)

    return {
        **state,
        "audio_output": state.get("raw_audio_output", ""),
        "audio_format_out": "wav",
        "referenced_slides": refs,
        "pipeline_status": "done",
    }


def _empty_wav_b64() -> str:
    """오류 응답에 쓸 0.1초 무음 WAV(24kHz)를 base64로 만든다."""
    import base64
    import io
    import struct
    import wave

    sample_rate = 24000
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        silence = struct.pack(
            "<" + "h" * (sample_rate // 10), *([0] * (sample_rate // 10))
        )
        wf.writeframes(silence)
    return base64.b64encode(buf.getvalue()).decode("utf-8")
