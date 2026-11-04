from __future__ import annotations

import asyncio
from pathlib import Path

import pytest

from ai_connectors.tts_schemas import TTSRequest, TTSResponse
from app.modules.ChapterStudio_V1.pipeline import voice_audio
from app.modules.ChapterStudio_V1.pipeline import tts_routing as tts_routing_module
from app.modules.ChapterStudio_V1.pipeline.tts_routing import (
    TutorVoiceProfile,
    is_custom_tutor,
    is_preset_voice2_tutor,
    resolve_tts_fallback_chain,
    resolve_tts_plan,
)


class FakeRootTTSConnector:
    def __init__(self, name: str, *, fail: bool = False) -> None:
        self.name = name
        self.fail = fail
        self.requests: list[TTSRequest] = []

    async def synthesize(self, request: TTSRequest) -> TTSResponse:
        self.requests.append(request)
        if self.fail:
            raise RuntimeError(f"{self.name}-offline")
        return TTSResponse(
            audio_bytes=b"RIFFfakeWAVE",
            sample_rate=24000,
            content_type="audio/wav",
            duration_sec=1.25,
            latency_ms=10.0,
            char_count=len(request.text),
            model=self.name,
        )

    def supports(self, feature: str) -> bool:
        return feature in {"tts", "voice_cloning"}


class FakeLegacyTTSConnector:
    def __init__(self) -> None:
        self.texts: list[str] = []

    async def synthesize(self, text: str, voice: str = "f1") -> dict[str, object]:
        self.texts.append(text)
        return {"audio_url": f"mock://audio/{voice}", "duration_sec": 1.0}


class SlowLegacyTTSConnector:
    def __init__(self) -> None:
        self.active = 0
        self.max_active = 0

    async def synthesize(self, text: str, voice: str = "f1") -> dict[str, object]:
        self.active += 1
        self.max_active = max(self.max_active, self.active)
        await asyncio.sleep(0.02)
        self.active -= 1
        return {"audio_url": f"mock://audio/{text}", "duration_sec": 1.0}


def _preset_voice2_path() -> str:
    return str(
        (
            Path(__file__).resolve().parents[3]
            / "TTS_V2"
            / "assets"
            / "voices"
            / "preset_voice2.wav"
        ).resolve()
    )


def _modal_fallback_path() -> str:
    return str(
        (
            Path(__file__).resolve().parents[3]
            / "TTS_V2"
            / "assets"
            / "voices"
            / "preset_voice1.wav"
        ).resolve()
    )


def test_resolve_tts_plan_routes_custom_and_preset_voice2_modal_first() -> None:
    cat = resolve_tts_plan(_profile("tut_00000000000000PRESET_CAT01"))
    bear = resolve_tts_plan(_profile("tut_0000000000000PRESET_BEAR01"))
    custom = resolve_tts_plan(_profile("custom-1", voice_sample_url="https://cdn.local/ref.wav"))

    assert set(tts_routing_module._PRESET_VOICES) == {"tut_00000000000000PRESET_CAT01"}
    assert is_preset_voice2_tutor(_profile("tut_00000000000000PRESET_CAT01"))
    assert cat.engine == "qwen3-tts-modal"
    assert cat.ref_source == _preset_voice2_path()
    assert bear.engine == "qwen3-tts-modal"
    assert bear.ref_source == _modal_fallback_path()
    assert custom.engine == "qwen3-tts-modal"
    assert custom.ref_source == "https://cdn.local/ref.wav"


def test_resolve_tts_fallback_chain_orders_by_tutor_kind(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("TTS_CLOUD_FALLBACK_ENABLED", "true")

    cat_chain = resolve_tts_fallback_chain(_profile("tut_00000000000000PRESET_CAT01"))
    assert [plan.engine for plan in cat_chain[:2]] == ["qwen3-tts-modal", "gemini-tts"]
    assert cat_chain[0].ref_source == _preset_voice2_path()

    bear_chain = resolve_tts_fallback_chain(_profile("tut_0000000000000PRESET_BEAR01"))
    assert [plan.engine for plan in bear_chain[:2]] == ["qwen3-tts-modal", "gemini-tts"]
    assert bear_chain[0].ref_source == _modal_fallback_path()


def test_resolve_tts_fallback_chain_excludes_cloud_tts_by_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("TTS_CLOUD_FALLBACK_ENABLED", raising=False)
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")

    for profile in (
        _profile("tut_0000000000000PRESET_BEAR01"),
        _profile("tut_00000000000000PRESET_CAT01"),
        _profile("custom-1", voice_sample_url="https://cdn.local/ref.wav"),
    ):
        assert [plan.engine for plan in resolve_tts_fallback_chain(profile)] == ["qwen3-tts-modal"]


def test_is_custom_tutor_requires_uploaded_sample_not_preset() -> None:
    assert is_custom_tutor(_profile("custom-1", voice_sample_url="https://cdn.local/ref.wav"))
    assert not is_custom_tutor(_profile("tut_00000000000000PRESET_CAT01"))
    assert not is_custom_tutor(_profile("custom-2", formal=False))


def test_cloud_fallback_gemini_keeps_speech_style(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("TTS_CLOUD_FALLBACK_ENABLED", "true")
    formal = resolve_tts_fallback_chain(_profile("tut_00000000000000PRESET_OWL01", formal=True))[1]
    casual = resolve_tts_fallback_chain(_profile("custom-2", formal=False))[1]

    assert formal.engine == "gemini-tts"
    assert formal.style == "존댓말 과외톤"
    assert casual.engine == "gemini-tts"
    assert casual.style == "반말 친근 과외톤"


@pytest.mark.asyncio
async def test_synthesize_voice_audio_uses_modal_first_for_cat_preset(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    modal = FakeRootTTSConnector("qwen3-tts-modal")
    requested_names: list[str] = []

    def fake_get_connector(name: str) -> FakeRootTTSConnector:
        requested_names.append(name)
        return modal

    async def fake_load_ref_audio(ref_source: str) -> bytes:
        assert ref_source == _preset_voice2_path()
        return b"RIFFrefWAVE"

    monkeypatch.setattr(voice_audio, "get_root_tts_connector", fake_get_connector)
    monkeypatch.setattr(voice_audio, "_load_ref_audio", fake_load_ref_audio)
    monkeypatch.setattr(voice_audio, "tts_output_dir", lambda: tmp_path)

    result = await voice_audio.synthesize_voice_audio(
        [{"slide_idx": 0, "script_text": "냥 튜터 대본입니다."}],
        tutor_profile=_profile("tut_00000000000000PRESET_CAT01"),
    )

    assert requested_names == ["qwen3-tts-modal"]
    assert result[0]["voice"] == "qwen3-tts-modal"
    assert str(result[0]["audio_url"]).startswith("/media/tts/")


@pytest.mark.asyncio
async def test_synthesize_voice_audio_cat_falls_back_to_gemini_when_modal_fails(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    monkeypatch.setenv("TTS_CLOUD_FALLBACK_ENABLED", "true")
    modal = FakeRootTTSConnector("qwen3-tts-modal", fail=True)
    gemini = FakeRootTTSConnector("gemini-tts")
    requested_names: list[str] = []

    def fake_get_connector(name: str) -> FakeRootTTSConnector:
        requested_names.append(name)
        return modal if name == "qwen3-tts-modal" else gemini

    async def fake_load_ref_audio(ref_source: str) -> bytes:
        return b"RIFFrefWAVE"

    monkeypatch.setattr(voice_audio, "get_root_tts_connector", fake_get_connector)
    monkeypatch.setattr(voice_audio, "_load_ref_audio", fake_load_ref_audio)
    monkeypatch.setattr(voice_audio, "tts_output_dir", lambda: tmp_path)

    result = await voice_audio.synthesize_voice_audio(
        [{"slide_idx": 0, "script_text": "폴백 대본입니다."}],
        tutor_profile=_profile("tut_00000000000000PRESET_CAT01", formal=False),
    )

    assert requested_names == ["qwen3-tts-modal", "gemini-tts"]
    assert gemini.requests[0].style == "반말 친근 과외톤"
    assert result[0]["voice"] == "gemini-tts"


@pytest.mark.asyncio
async def test_synthesize_voice_audio_owl_uses_modal_with_neutral_voice(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    monkeypatch.delenv("TTS_CLOUD_FALLBACK_ENABLED", raising=False)
    modal = FakeRootTTSConnector("qwen3-tts-modal")
    requested_names: list[str] = []

    def fake_get_connector(name: str) -> FakeRootTTSConnector:
        requested_names.append(name)
        return modal

    async def fake_load_ref_audio(ref_source: str) -> bytes:
        assert ref_source == _modal_fallback_path()
        return b"RIFFrefWAVE"

    monkeypatch.setattr(voice_audio, "get_root_tts_connector", fake_get_connector)
    monkeypatch.setattr(voice_audio, "_load_ref_audio", fake_load_ref_audio)
    monkeypatch.setattr(voice_audio, "tts_output_dir", lambda: tmp_path)

    result = await voice_audio.synthesize_voice_audio(
        [{"slide_idx": 0, "script_text": "부엉이 튜터 대본입니다."}],
        tutor_profile=_profile("tut_00000000000000PRESET_OWL01"),
    )

    assert requested_names == ["qwen3-tts-modal"]
    assert result[0]["voice"] == "qwen3-tts-modal"


@pytest.mark.asyncio
async def test_synthesize_voice_audio_uses_modal_first_for_custom_tutor(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    modal = FakeRootTTSConnector("qwen3-tts-modal")
    requested_names: list[str] = []

    def fake_get_connector(name: str) -> FakeRootTTSConnector:
        requested_names.append(name)
        return modal

    async def fake_load_ref_audio(ref_source: str) -> bytes:
        assert ref_source == "https://cdn.local/ref.wav"
        return b"RIFFrefWAVE"

    monkeypatch.setattr(voice_audio, "get_root_tts_connector", fake_get_connector)
    monkeypatch.setattr(voice_audio, "_load_ref_audio", fake_load_ref_audio)
    monkeypatch.setattr(voice_audio, "tts_output_dir", lambda: tmp_path)

    result = await voice_audio.synthesize_voice_audio(
        [{"slide_idx": 0, "script_text": "커스텀 튜터 대본입니다."}],
        tutor_profile=_profile("custom-9", voice_sample_url="https://cdn.local/ref.wav"),
    )

    assert requested_names == ["qwen3-tts-modal"]
    assert result[0]["voice"] == "qwen3-tts-modal"


@pytest.mark.asyncio
async def test_synthesize_voice_audio_uploads_to_s3_when_enabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    connector = FakeRootTTSConnector("qwen3-tts-modal")
    uploaded: list[dict[str, object]] = []

    def fake_get_connector(name: str) -> FakeRootTTSConnector:
        assert name == "qwen3-tts-modal"
        return connector

    async def fake_load_ref_audio(ref_source: str) -> bytes:
        return b"RIFFrefWAVE"

    async def fake_put_object(data: bytes, key: str, content_type: str) -> str:
        uploaded.append({"data": data, "key": key, "content_type": content_type})
        return f"https://cdn.example/{key}"

    monkeypatch.setattr(voice_audio, "get_root_tts_connector", fake_get_connector)
    monkeypatch.setattr(voice_audio, "_load_ref_audio", fake_load_ref_audio)
    monkeypatch.setattr(voice_audio, "s3_enabled", lambda: True)
    monkeypatch.setattr(voice_audio, "put_object", fake_put_object)

    result = await voice_audio.synthesize_voice_audio(
        [{"slide_idx": 3, "script_text": "S3 업로드 대본입니다."}],
        tutor_profile=_profile("custom-2", formal=False),
    )

    assert str(uploaded[0]["key"]).startswith("tts/")
    assert str(uploaded[0]["key"]).endswith(".wav")
    assert uploaded[0]["content_type"] == "audio/wav"
    assert result[0]["audio_url"] == f"https://cdn.example/{uploaded[0]['key']}"


@pytest.mark.asyncio
async def test_synthesize_voice_audio_keeps_original_when_selective_tilde_disabled(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    connector = FakeLegacyTTSConnector()
    source = "안녕하세요. 오른쪽이 더 커요. 천천히 따라와 보세요."
    monkeypatch.setattr(voice_audio, "tts_selective_tilde_enabled", lambda: False)

    result = await voice_audio.synthesize_voice_audio(
        [{"slide_idx": 0, "script_text": source}],
        connector=connector,
    )

    assert connector.texts == [source]
    assert result[0]["script_text"] == source


@pytest.mark.asyncio
async def test_synthesize_voice_audio_uses_selective_tilde_for_routed_tts_only(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    connector = FakeRootTTSConnector("gemini-tts")
    source = "안녕하세요. 오른쪽이 더 커요. 천천히 따라와 보세요."

    def fake_get_connector(name: str) -> FakeRootTTSConnector:
        assert name == "gemini-tts"
        return connector

    async def fake_load_ref_audio(ref_source: str) -> bytes:
        return b"RIFFrefWAVE"

    monkeypatch.setattr(voice_audio, "get_root_tts_connector", fake_get_connector)
    monkeypatch.setattr(voice_audio, "_load_ref_audio", fake_load_ref_audio)
    monkeypatch.setattr(voice_audio, "tts_output_dir", lambda: tmp_path)
    monkeypatch.setattr(voice_audio, "tts_selective_tilde_enabled", lambda: True)
    monkeypatch.setattr(
        voice_audio,
        "resolve_tts_fallback_chain",
        lambda profile: [
            tts_routing_module.TtsPlan(engine="gemini-tts", ref_source="", style="존댓말 과외톤")
        ],
    )

    result = await voice_audio.synthesize_voice_audio(
        [{"slide_idx": 0, "script_text": source}],
        tutor_profile=_profile("tut_00000000000000PRESET_CAT01"),
    )

    expected = "안녕하세요~ 오른쪽이 더 커요. 천천히 따라와 보세요~"
    assert connector.requests[0].text == expected
    assert result[0]["script_text"] == source


@pytest.mark.asyncio
async def test_synthesize_voice_audio_reads_tts_synth_concurrency_env(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    connector = SlowLegacyTTSConnector()
    monkeypatch.setenv("TTS_SYNTH_CONCURRENCY", "2")
    monkeypatch.setattr(voice_audio, "tts_selective_tilde_enabled", lambda: False)

    result = await voice_audio.synthesize_voice_audio(
        [
            {"slide_idx": 0, "script_text": "0번 대본"},
            {"slide_idx": 1, "script_text": "1번 대본"},
            {"slide_idx": 2, "script_text": "2번 대본"},
        ],
        connector=connector,
    )

    assert [record["slide_idx"] for record in result] == [0, 1, 2]
    assert connector.max_active == 2


def _profile(tutor_id: str, *, formal: bool = True, voice_sample_url: str = "") -> TutorVoiceProfile:
    return TutorVoiceProfile(
        tutor_id=tutor_id,
        is_default_tutor=tutor_id.startswith("tut_"),
        voice_sample_url=voice_sample_url,
        use_formal_speech=formal,
        tutor_tagline="테스트 튜터",
    )