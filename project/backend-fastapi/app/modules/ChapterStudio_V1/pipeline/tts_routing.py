from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

_VOICE_ASSET_DIR = Path(__file__).resolve().parents[2] / "TTS_V2" / "assets" / "voices"
_PRESET_VOICES = {
    "tut_00000000000000PRESET_CAT01": "preset_voice2.wav",
}
# 클론 음성이 없는 튜터가 Modal TTS에 쓰는 중립 참조 음성이다.
_DEFAULT_MODAL_VOICE = "preset_voice1.wav"


@dataclass(frozen=True)
class TutorVoiceProfile:
    """튜터 음성 선택에 필요한 최소 프로필이다."""

    tutor_id: str
    is_default_tutor: bool
    voice_sample_url: str
    use_formal_speech: bool
    tutor_tagline: str


@dataclass(frozen=True)
class TtsPlan:
    """TTS 실행 전에 확정된 엔진·참조음성·스타일 계획이다."""

    engine: str
    ref_source: str
    style: str


def is_custom_tutor(profile: TutorVoiceProfile) -> bool:
    """사용자 업로드 보이스 샘플이 있고 프리셋 ID가 아니면 커스텀 튜터로 본다."""
    custom_ref = profile.voice_sample_url.strip()
    return bool(custom_ref) and profile.tutor_id not in _PRESET_VOICES


def is_preset_voice2_tutor(profile: TutorVoiceProfile) -> bool:
    """기본튜터2(냥, preset_voice2.wav) 여부를 반환한다."""
    return profile.tutor_id in _PRESET_VOICES


def resolve_tts_fallback_chain(profile: TutorVoiceProfile) -> list[TtsPlan]:
    """튜터 프로필을 순서대로 시도할 TTS 엔진 체인으로 변환한다.

    기본 체인은 Modal Qwen3-TTS 하나다. 참조 음성은 커스텀 튜터=업로드 샘플,
    기본튜터2(CAT01)=preset_voice2, 그 외=중립 preset_voice1이다.
    Gemini → OpenAI TTS는 TTS_CLOUD_FALLBACK_ENABLED=true일 때만 Modal 뒤에 붙는다.
    """
    from app.modules.ChapterStudio_V1.common.config import tts_cloud_fallback_enabled

    style = _speech_style(profile.use_formal_speech)
    chain = [TtsPlan(engine="qwen3-tts-modal", ref_source=_modal_ref_source(profile), style="")]
    if tts_cloud_fallback_enabled():
        chain.append(TtsPlan(engine="gemini-tts", ref_source="", style=style))
        chain.extend(_openai_plan_if_available(style))
    return chain


def resolve_tts_plan(profile: TutorVoiceProfile) -> TtsPlan:
    """튜터 프로필의 1순위 TTS 엔진 계획을 반환한다."""
    return resolve_tts_fallback_chain(profile)[0]


def default_modal_ref_path() -> Path:
    """클론 음성이 없는 튜터가 Modal TTS에 넘길 중립 참조 음성 경로다."""
    return _safe_asset_path(_DEFAULT_MODAL_VOICE)


def _modal_ref_source(profile: TutorVoiceProfile) -> str:
    if is_custom_tutor(profile):
        return profile.voice_sample_url.strip()
    if is_preset_voice2_tutor(profile):
        return str(_safe_asset_path(_PRESET_VOICES[profile.tutor_id]))
    try:
        return str(default_modal_ref_path())
    except (FileNotFoundError, ValueError):
        # 참조 파일이 없으면 Modal 단계가 실패하고, 폴백이 켜져 있으면 다음 엔진으로 넘어간다.
        return ""


def _openai_plan_if_available(style: str) -> list[TtsPlan]:
    """OpenAI TTS 키가 있을 때만 Gemini 다음 폴백 계획을 붙인다."""
    from common.text_config import openai_api_key, openai_fallback_enabled

    if openai_fallback_enabled() and openai_api_key() is not None:
        return [TtsPlan(engine="openai-tts", ref_source="", style=style)]
    return []


def _speech_style(use_formal_speech: bool) -> str:
    if use_formal_speech:
        return "존댓말 과외톤"
    return "반말 친근 과외톤"


def _safe_asset_path(file_name: str) -> Path:
    """프리셋 파일은 지정된 assets/voices 내부의 실제 파일만 허용한다."""
    base = _VOICE_ASSET_DIR.resolve()
    path = (base / file_name).resolve()
    try:
        path.relative_to(base)
    except ValueError as exc:
        raise ValueError("프리셋 음성 경로가 assets/voices 밖을 가리킨다.") from exc
    if not path.is_file():
        raise FileNotFoundError(f"프리셋 음성 파일을 찾을 수 없다: {path}")
    return path


__all__ = [
    "TtsPlan",
    "TutorVoiceProfile",
    "default_modal_ref_path",
    "is_custom_tutor",
    "is_preset_voice2_tutor",
    "resolve_tts_fallback_chain",
    "resolve_tts_plan",
]
