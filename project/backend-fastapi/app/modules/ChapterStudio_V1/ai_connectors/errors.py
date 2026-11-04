# 벤더별 예외를 공통 예외 5종으로 바꾼다.
# 노드는 벤더를 몰라도 ConnectorError 하위 클래스만 처리하면 된다.


class ConnectorError(Exception):
    """커넥터 기본 예외. 노드에서 이 클래스 하나로 catch 가능."""


class RateLimitError(ConnectorError):
    """벤더 API 요청 한도 초과."""


class AuthError(ConnectorError):
    """API 키 오류 또는 인증 실패."""


class ModelNotFoundError(ConnectorError):
    """registry에 등록되지 않은 모델 이름."""


class TimeoutError(ConnectorError):
    """요청 타임아웃 또는 Modal 연결 끊김."""


class ContextLengthExceeded(ConnectorError):
    """입력 토큰이 모델 최대 컨텍스트를 초과."""
