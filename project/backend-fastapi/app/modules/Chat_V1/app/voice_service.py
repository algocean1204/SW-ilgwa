"""Chat_V1 음성 채팅. Gemini Flash Live 커넥터를 부른다.

강의 컨텍스트를 시스템 프롬프트에 넣어 음성 질문에 답한다. 프롬프트 조립과
슬라이드 인용 추출은 텍스트 채팅 코드(build_system_prompt, extract_referenced_slides)를 쓴다.
"""
from __future__ import annotations

from ai_connectors.voice.gemini_flash_live.connector import GeminiFlashLiveConnector
from ai_connectors.voice.gemini_flash_live.schemas import VoiceRequest

from app.modules.Chat_V1.app.schemas import VoiceChatRequest, VoiceChatResponse
from app.modules.Chat_V1.pipeline.voice_graph import run_voice_pipeline


async def answer_voice_question(request: VoiceChatRequest) -> VoiceChatResponse:
    """음성 파이프라인을 돌리고 최종 상태로 VoiceChatResponse를 만든다.

    순서: validate_voice → generate_voice_answer → format_voice_response.
    """
    final_state = await run_voice_pipeline(request)
    return VoiceChatResponse(
        session_id=request.session_id,
        audio_data=final_state["audio_output"],
        audio_format=final_state.get("audio_format_out", "wav"),
        transcript_input=final_state.get("transcript_input", ""),
        transcript_output=final_state.get("transcript_output", ""),
        referenced_slides=final_state.get("referenced_slides", []),
    )


async def call_gemini_flash_live(
    audio_data: str,
    audio_format: str,
    sample_rate: int,
    system_prompt: str,
) -> tuple[str, str, str]:
    """Gemini Flash Live를 불러 (audio_b64, transcript_in, transcript_out)을 돌려준다."""
    connector = GeminiFlashLiveConnector()
    req = VoiceRequest(
        audio_data=audio_data,
        audio_format=audio_format,
        sample_rate=sample_rate,
        system_prompt=system_prompt,
    )
    response = await connector.voice_chat(req)
    return response.audio_data, response.transcript_input, response.transcript_output
