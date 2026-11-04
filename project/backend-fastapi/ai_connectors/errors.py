"""AI 커넥터 공통 예외 계층.

벤더별 커넥터는 SDK 예외를 여기 클래스로 바꿔 올린다.
그래야 핸들러 한 벌로 HTTP 상태코드를 매핑할 수 있다.
"""
from __future__ import annotations


class AIConnectorError(Exception):
    """AI 커넥터 공통 최상위 예외."""


class AuthError(AIConnectorError):
    """API 키 누락·무효(401)."""


class RateLimitError(AIConnectorError):
    """요청 한도 초과(429)."""


class ModelNotFoundError(AIConnectorError):
    """모델 미등록·오타(503)."""


class ModelLoadError(AIConnectorError):
    """모델 가중치 로드 실패(503).

    파일 누락, bf16 역직렬화 실패, MLX 메모리 부족 등.
    """


class InferenceError(AIConnectorError):
    """추론 중 실패(502).

    모델은 로드되었으나 generate() 호출에서 벤더 SDK가 오류를 던진 경우.
    """


class ServiceUnavailableError(InferenceError):
    """벤더 일시 과부하·사용 불가(503, UNAVAILABLE, overloaded).

    재시도·모델 폴백 대상을 구분하려고 따로 두었다.
    InferenceError를 잡는 기존 핸들러가 그대로 받도록 하위 클래스로 둔다.
    """


class TimeoutError(AIConnectorError):
    """벤더 응답 타임아웃(504).

    참고: 파이썬 내장 TimeoutError와 이름 충돌하므로 import 시 alias 권장
    (예: `from ai_connectors.errors import TimeoutError as ConnectorTimeoutError`).
    """


# 텍스트 커넥터 공통 예외 (ChapterStudio 스키마 호환)

# ChapterStudio 코드가 쓰는 import 이름을 그대로 쓰려고 둔 AIConnectorError 별칭이다.
ConnectorError = AIConnectorError


class ContextLengthExceeded(AIConnectorError):
    """입력 토큰이 모델 최대 컨텍스트를 넘었다(413)."""
