"""Nemotron-OCR-v2 Modal 엔드포인트 HTTP 클라이언트.

HTTP POST 를 보내 응답 dict 를 돌려주고, 벤더 오류를 공통 예외로 바꾼다.
OCRResponse 조립은 커넥터(`nemotron_ocr_v2_connector.py`)가 한다.
"""
from __future__ import annotations

import base64
import os
from typing import Any

import httpx

from ..errors import (
    AuthError,
    InferenceError,
    ModelLoadError,
    ModelNotFoundError,
    RateLimitError,
)
from ..errors import TimeoutError as ConnectorTimeoutError

# HTTP 설정 기본값(.env 로 바꿀 수 있다)
_DEFAULT_TIMEOUT_SEC = 60.0
_DEFAULT_RETRIES = 1


def resolve_endpoint() -> str:
    """Modal 엔드포인트 URL 을 .env 에서 읽어 반환한다.

    `NEMOTRON_OCR_V2_MODAL_URL` 이 비어 있으면 ModelNotFoundError 를 낸다.
    """
    url = os.getenv("NEMOTRON_OCR_V2_MODAL_URL", "").strip()
    if not url:
        raise ModelNotFoundError(
            "NEMOTRON_OCR_V2_MODAL_URL 이 설정되지 않았습니다. "
            "`modal deploy modal/nemotron_ocr_v2_modal_app.py` 실행 후 "
            "출력된 URL 을 .env 에 주입하세요.",
        )
    return url


def resolve_timeout() -> float:
    """타임아웃(초). NEMOTRON_OCR_TIMEOUT_SEC, 없으면 기본값."""
    raw = os.getenv("NEMOTRON_OCR_TIMEOUT_SEC", "").strip()
    if not raw:
        return _DEFAULT_TIMEOUT_SEC
    try:
        return float(raw)
    except ValueError:
        return _DEFAULT_TIMEOUT_SEC


def resolve_retries() -> int:
    """재시도 횟수. NEMOTRON_OCR_RETRIES, 없으면 1."""
    raw = os.getenv("NEMOTRON_OCR_RETRIES", "").strip()
    if not raw:
        return _DEFAULT_RETRIES
    try:
        return max(0, int(raw))
    except ValueError:
        return _DEFAULT_RETRIES


def build_payload(image_bytes: bytes) -> dict[str, str]:
    """이미지 바이트를 Modal 엔드포인트 payload 로 변환한다.

    base64 로 인코딩해 JSON body 에 넣는다. Modal fastapi_endpoint 는 multipart 보다
    JSON POST 가 잘 맞는다.
    """
    return {"image_b64": base64.b64encode(image_bytes).decode("ascii")}


def normalize_http_error(exc: httpx.HTTPStatusError) -> Exception:
    """httpx HTTPStatusError 를 공통 예외로 바꾼다."""
    status = exc.response.status_code
    detail = _extract_detail(exc.response)
    if status == 401 or status == 403:
        return AuthError(f"Nemotron-OCR Modal 인증 실패(status={status}): {detail}")
    if status == 404:
        return ModelNotFoundError(f"Modal 엔드포인트 미발견(status=404): {detail}")
    if status == 429:
        return RateLimitError(f"Modal 요청 한도 초과(status=429): {detail}")
    if status == 503:
        return ModelLoadError(f"Modal 모델 로드 대기(status=503): {detail}")
    return InferenceError(f"Modal 추론 실패(status={status}): {detail}")


def _extract_detail(response: httpx.Response) -> str:
    """httpx Response 본문에서 사람이 읽을 수 있는 오류 메시지를 추출한다."""
    try:
        payload = response.json()
        if isinstance(payload, dict):
            detail = payload.get("detail")
            if isinstance(detail, str):
                return detail
            return str(payload)
        return str(payload)
    except (ValueError, UnicodeDecodeError):
        # JSON 이 아니면 원문 텍스트를 쓴다
        try:
            return response.text[:200]
        except UnicodeDecodeError:
            return "<binary body>"


async def post_recognize(image_bytes: bytes) -> dict[str, Any]:
    """Modal 엔드포인트에 POST 하고 vendor JSON 응답을 dict 로 반환한다.

    네트워크·HTTP 오류는 공통 예외로 바꿔 올리므로 호출부는 AIConnectorError 만 잡으면 된다.
    """
    url = resolve_endpoint()
    timeout = resolve_timeout()
    retries = resolve_retries()
    payload = build_payload(image_bytes)

    last_exc: Exception | None = None
    # Modal 웹 엔드포인트의 303 리다이렉트를 따라간다
    async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
        for attempt in range(retries + 1):
            try:
                resp = await client.post(url, json=payload)
                resp.raise_for_status()
                data = resp.json()
                if not isinstance(data, dict):
                    raise InferenceError(
                        f"Nemotron-OCR Modal 응답 형식이 dict 가 아님: {type(data).__name__}",
                    )
                return data
            except httpx.TimeoutException as exc:
                last_exc = ConnectorTimeoutError(f"Modal 응답 타임아웃(attempt={attempt + 1}): {exc}")
            except httpx.HTTPStatusError as exc:
                normalized = normalize_http_error(exc)
                # 4xx 는 다시 해도 같아서 바로 올린다
                if isinstance(normalized, (AuthError, ModelNotFoundError)):
                    raise normalized from exc
                last_exc = normalized
            except httpx.HTTPError as exc:
                last_exc = InferenceError(f"Modal 네트워크 오류(attempt={attempt + 1}): {exc}")
    # 재시도를 다 썼다
    assert last_exc is not None  # 루프가 한 번은 돌아서 None 일 수 없다
    raise last_exc
