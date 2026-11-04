"""Modal GPU에서 Whisper large-v3-turbo 로 전사하는 ASR 커넥터.

배포 앱은 app/modules/ASR_V1/deploy/modal_whisper_app.py 이다.
앱 이름은 ASR_WHISPER_MODAL_APP_NAME(기본 asr-whisper-v3-turbo)으로 바꿀 수 있다.
"""
from __future__ import annotations

import asyncio
import os
import time
from typing import Any

import modal

from ..errors import AuthError, InferenceError, ModelNotFoundError
from ..errors import TimeoutError as ConnectorTimeoutError
from ..schemas import ASRRequest, ASRResponse

_SERVER_CLASS = "WhisperServer"
_DEFAULT_APP_NAME = "asr-whisper-v3-turbo"
# 콜드 스타트(스냅샷 복원 + GPU 이동)까지 포함한 상한
_DEFAULT_TIMEOUT_SEC = 300.0


class WhisperModalConnector:
    """배포된 WhisperServer.transcribe 를 remote.aio 로 호출한다."""

    name: str = "whisper-modal"

    def __init__(self, app_name: str | None = None, timeout_sec: float | None = None) -> None:
        self._app_name = app_name or os.getenv("ASR_WHISPER_MODAL_APP_NAME", _DEFAULT_APP_NAME)
        self._timeout_sec = timeout_sec or float(
            os.getenv("ASR_WHISPER_MODAL_TIMEOUT_SEC", str(_DEFAULT_TIMEOUT_SEC))
        )
        self._server: Any = None

    def _get_server(self) -> Any:
        """배포 클래스 핸들은 처음 호출할 때 만든다."""
        if self._server is None:
            self._server = modal.Cls.from_name(self._app_name, _SERVER_CLASS)()
        return self._server

    async def generate(self, request: ASRRequest) -> ASRResponse:
        """오디오 바이트를 Modal Whisper 로 전사한다."""
        language = request.language or "ko"
        started_at = time.perf_counter()
        try:
            result = await asyncio.wait_for(
                self._get_server().transcribe.remote.aio(
                    audio_bytes=request.audio_bytes, language=language
                ),
                timeout=self._timeout_sec,
            )
        except asyncio.TimeoutError as exc:
            raise ConnectorTimeoutError("Modal Whisper 호출 시간이 초과되었습니다.") from exc
        except modal.exception.NotFoundError as exc:
            raise ModelNotFoundError(
                f"Modal Whisper 앱을 찾을 수 없습니다(app={self._app_name}). 먼저 modal deploy 하세요."
            ) from exc
        except modal.exception.AuthError as exc:
            raise AuthError("Modal 인증에 실패했습니다. Modal 토큰을 확인하세요.") from exc
        except modal.exception.Error as exc:
            raise InferenceError(f"Modal Whisper 호출 실패: {exc}") from exc
        latency_ms = (time.perf_counter() - started_at) * 1000.0
        try:
            return ASRResponse(
                text=str(result["text"]).strip(),
                language=language,
                duration_sec=float(result["duration_sec"]),
                latency_ms=latency_ms,
                model=self.name,
            )
        except (KeyError, TypeError, ValueError) as exc:
            raise InferenceError(f"Modal Whisper 응답 처리 실패: {exc}") from exc

    def supports(self, feature: str) -> bool:
        """Whisper ASR 기능 플래그를 반환한다."""
        return feature in {"basic", "transcription", "korean"}
