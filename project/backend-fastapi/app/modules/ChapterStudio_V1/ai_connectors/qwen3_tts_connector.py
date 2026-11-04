"""ChapterStudio 레슨 TTS용 Modal Qwen3-TTS 어댑터.

시스템 A의 `qwen3-tts-modal` 커넥터는 `synthesize(TTSRequest) -> TTSResponse`를 쓰고,
ChapterStudio TTSConnector는 `synthesize(text, voice) -> dict`를 쓴다.
gemini_tts_connector와 같은 방식으로 감싼다. 참조 음성은 TTS_REF_AUDIO_PATH가 있으면
그 파일을, 없으면 중립 preset_voice1을 쓴다.
"""
from __future__ import annotations

from ai_connectors.registry import get_tts_connector
from ai_connectors.tts_schemas import TTSRequest

from app.modules.ChapterStudio_V1.ai_connectors.tts_audio_storage import save_tts_wav_bytes
from app.modules.ChapterStudio_V1.common.config import tts_ref_audio_path
from app.modules.ChapterStudio_V1.pipeline.tts_routing import default_modal_ref_path


class Qwen3TTSChapterConnector:
    """시스템 A Modal Qwen3-TTS를 ChapterStudio TTSConnector 프로토콜로 변환한다."""

    name = "qwen3_tts_modal"

    def __init__(self) -> None:
        self._inner = get_tts_connector("qwen3-tts-modal")
        self._ref_audio = (tts_ref_audio_path() or default_modal_ref_path()).read_bytes()

    async def synthesize(self, text: str, voice: str = "f1") -> dict[str, str | float]:
        response = await self._inner.synthesize(TTSRequest(text=text, ref_audio_bytes=self._ref_audio))
        return {
            "audio_url": await save_tts_wav_bytes(response.audio_bytes, voice),
            "duration_sec": float(response.duration_sec),
        }

    def supports(self, feature: str) -> bool:
        return self._inner.supports(feature)
