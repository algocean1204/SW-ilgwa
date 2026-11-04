"""ChapterStudio 레슨 TTS 용 Gemini 어댑터.

시스템 A(ai_connectors/tts/gemini_tts_connector.py)의 GeminiTTSConnector는
`synthesize(TTSRequest) -> TTSResponse`를 쓰고, ChapterStudio TTSConnector(base.py)는
`synthesize(text, voice) -> dict`를 쓴다. 이 어댑터가 둘을 이어 준다.
ACTIVE_TTS_MODEL=gemini_tts로 명시할 때만 쓴다(기본은 qwen3_tts_modal).

합성 결과 WAV 바이트는 tts_audio_storage.save_tts_wav_bytes로 저장하고
브라우저가 GET할 수 있는 URL과 재생 시간을 dict로 돌려준다.
"""
from __future__ import annotations

from ai_connectors.tts.gemini_tts_connector import GeminiTTSConnector
from ai_connectors.tts_schemas import TTSRequest

from app.modules.ChapterStudio_V1.ai_connectors.tts_audio_storage import save_tts_wav_bytes


class GeminiTTSChapterConnector:
    """시스템 A Gemini TTS를 ChapterStudio TTSConnector 프로토콜로 감싼다."""

    name = "gemini_tts"

    def __init__(self) -> None:
        """시스템 A Gemini TTS 커넥터를 만든다.

        Gemini는 사전 정의 음성(prebuilt voice)을 써서 참조 음성 클로닝이 필요 없다.
        모델·음성은 시스템 A 커넥터가 .env에서 읽는다.
        """
        self._inner = GeminiTTSConnector()

    async def synthesize(self, text: str, voice: str = "f1") -> dict[str, str | float]:
        """텍스트 하나를 Gemini로 합성해 WAV로 저장하고 URL·길이를 돌려준다.

        voice는 ChapterStudio 튜터 식별자다. Gemini는 prebuilt voice를 쓰므로
        voice를 말투 힌트(style)로 넘긴다. ref_audio_bytes는 시스템 A 스키마의
        필수 필드지만 Gemini는 쓰지 않아 빈 바이트를 넣는다.
        """
        response = await self._inner.synthesize(
            TTSRequest(text=text, ref_audio_bytes=b"", style=voice)
        )
        return {
            "audio_url": await save_tts_wav_bytes(response.audio_bytes, voice),
            "duration_sec": float(response.duration_sec),
        }

    def supports(self, feature: str) -> bool:
        """지원 기능 플래그를 시스템 A 커넥터에 위임한다."""
        return self._inner.supports(feature)
