"""음성 네이티브 AI 커넥터 패키지.

ASR, 생성, TTS 를 모델 하나로 처리하는 커넥터를 둔다.
지원: Gemini Flash Live
"""
from ai_connectors.voice.gemini_flash_live import (
    GeminiFlashLiveConnector,
    VoiceRequest,
    VoiceResponse,
)

__all__ = ["GeminiFlashLiveConnector", "VoiceRequest", "VoiceResponse"]
