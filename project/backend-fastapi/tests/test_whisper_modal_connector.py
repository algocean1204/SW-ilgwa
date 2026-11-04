"""whisper-modal 커넥터와 ASR 기본값·폴백 순서 테스트. Modal 호출은 가짜로 바꾼다."""
from __future__ import annotations

import asyncio
from typing import Any

import modal
import pytest

# 이 파일만 따로 돌릴 때 registry ↔ ChapterStudio 순환 import 를 피하려고 먼저 올린다
import app.modules.ChapterStudio_V1  # noqa: F401
from ai_connectors import _registry_asr
from ai_connectors._failover import FailoverASRConnector
from ai_connectors.asr.fallback.config import resolve_tier_models
from ai_connectors.asr.whisper_modal_connector import WhisperModalConnector
from ai_connectors.errors import InferenceError, ModelNotFoundError
from ai_connectors.registry import get_asr_connector
from ai_connectors.schemas import ASRRequest


class _FakeRemote:
    def __init__(self, result: Any) -> None:
        self.result = result
        self.calls: list[dict[str, Any]] = []

    async def aio(self, **kwargs: Any) -> Any:
        self.calls.append(kwargs)
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


def _patch_modal(monkeypatch: pytest.MonkeyPatch, result: Any) -> tuple[_FakeRemote, list[tuple[str, str]]]:
    remote = _FakeRemote(result)
    lookups: list[tuple[str, str]] = []

    class _Server:
        transcribe = type("M", (), {"remote": remote})()

    def _from_name(app_name: str, cls_name: str) -> Any:
        lookups.append((app_name, cls_name))
        return _Server

    monkeypatch.setattr(modal.Cls, "from_name", staticmethod(_from_name))
    return remote, lookups


@pytest.fixture(autouse=True)
def _clean_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in (
        "AI_MODEL_ASR",
        "ASR_GEMINI_FALLBACK",
        "ASR_FALLBACK_TIERS",
        "ASR_WHISPER_MODAL_APP_NAME",
        "OPENAI_FALLBACK_ENABLED",
    ):
        monkeypatch.delenv(key, raising=False)


def test_connector_parses_modal_response(monkeypatch: pytest.MonkeyPatch) -> None:
    remote, lookups = _patch_modal(monkeypatch, {"text": " 안녕하세요 ", "duration_sec": 1.5})
    resp = asyncio.run(WhisperModalConnector().generate(ASRRequest(audio_bytes=b"wav", language="ko")))
    assert resp.text == "안녕하세요"
    assert resp.duration_sec == 1.5
    assert resp.model == "whisper-modal"
    assert resp.language == "ko"
    assert lookups == [("asr-whisper-v3-turbo", "WhisperServer")]
    assert remote.calls == [{"audio_bytes": b"wav", "language": "ko"}]


def test_connector_app_name_from_env(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ASR_WHISPER_MODAL_APP_NAME", "my-whisper")
    _, lookups = _patch_modal(monkeypatch, {"text": "a", "duration_sec": 1.0})
    asyncio.run(WhisperModalConnector().generate(ASRRequest(audio_bytes=b"wav")))
    assert lookups[0][0] == "my-whisper"


def test_connector_maps_errors(monkeypatch: pytest.MonkeyPatch) -> None:
    _patch_modal(monkeypatch, {"duration_sec": 1.0})
    with pytest.raises(InferenceError):
        asyncio.run(WhisperModalConnector().generate(ASRRequest(audio_bytes=b"wav")))
    _patch_modal(monkeypatch, modal.exception.NotFoundError("no app"))
    with pytest.raises(ModelNotFoundError):
        asyncio.run(WhisperModalConnector().generate(ASRRequest(audio_bytes=b"wav")))


def test_registry_default_is_whisper_modal() -> None:
    assert get_asr_connector().name == "whisper-modal"
    assert get_asr_connector("whisper-modal").name == "whisper-modal"
    # 스텁은 등록만 유지한다
    assert get_asr_connector("qwen3-asr-modal").name == "qwen3-asr-modal"


def test_asr_v1_default_model_is_whisper_modal() -> None:
    from app.modules.ASR_V1.pipeline.config import ASR_MODEL

    assert ASR_MODEL == "whisper-modal"


def test_gemini_failover_only_with_flag(monkeypatch: pytest.MonkeyPatch) -> None:
    assert isinstance(get_asr_connector(), WhisperModalConnector)
    monkeypatch.setenv("ASR_GEMINI_FALLBACK", "false")
    assert isinstance(get_asr_connector(), WhisperModalConnector)
    monkeypatch.setenv("ASR_GEMINI_FALLBACK", "true")
    assert isinstance(get_asr_connector(), FailoverASRConnector)


def test_fallback_tiers_default_and_flag(monkeypatch: pytest.MonkeyPatch) -> None:
    assert resolve_tier_models() == ("whisper-modal",)
    fb = _registry_asr.ASR_CONNECTORS["asr-fallback"]()
    assert fb._tier_model_names == ["whisper-modal"]

    monkeypatch.setenv("ASR_GEMINI_FALLBACK", "true")
    fb = _registry_asr.ASR_CONNECTORS["asr-fallback"]()
    assert fb._tier_model_names == ["whisper-modal", "gemini-asr"]

    # 명시 tier 에 이미 gemini-asr 가 있으면 두 번 붙이지 않는다
    monkeypatch.setenv("ASR_FALLBACK_TIERS", "gemini-asr,whisper-modal")
    fb = _registry_asr.ASR_CONNECTORS["asr-fallback"]()
    assert fb._tier_model_names == ["gemini-asr", "whisper-modal"]
