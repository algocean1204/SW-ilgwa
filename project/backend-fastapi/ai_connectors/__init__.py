"""AI 커넥터 공개 API.

외부 모듈은 base/schemas/registry/errors 만 참조한다.
벤더별 구현은 하위 파일에 두고 registry로만 노출한다.

스키마 파일 구성:
  - schemas.py         : AIRequest/AIResponse, ASR, Denoise, TTS, OCR 공통 + Correction
  - pipeline_schemas.py: PageResult, OCRPipelineResult (파이프라인 전용)
  - postproc_schemas.py: PostprocRequest, PostprocResponse (후처리 LLM 전용)

`from ai_connectors import OCRPipelineResult` 같은 기존 import가 깨지지 않게
여기서 모두 재수출한다.
"""
from .base import (
    AIConnector,
    ASRConnector,
    DenoiseConnector,
    OCRConnector,
    PostprocConnector,
    TTSConnector,
)
from .errors import (
    AIConnectorError,
    AuthError,
    ConnectorError,
    ContextLengthExceeded,
    InferenceError,
    ModelLoadError,
    ModelNotFoundError,
    RateLimitError,
    TimeoutError,
)
from .pipeline_schemas import OCRPipelineResult, PageResult
from .postproc_schemas import PostprocRequest, PostprocResponse
from .schemas import (
    AIRequest,
    AIResponse,
    ASRRequest,
    ASRResponse,
    Correction,
    DenoiseRequest,
    DenoiseResponse,
    OCRDetection,
    OCRRequest,
    OCRResponse,
)
from .text_schemas import ChapterAIRequest, ChapterAIResponse
from .tts_schemas import TTSRequest, TTSResponse

__all__ = [
    # 인터페이스
    "AIConnector",
    "ASRConnector",
    "DenoiseConnector",
    "OCRConnector",
    "PostprocConnector",
    "TTSConnector",
    # 에러
    "AIConnectorError",
    "AuthError",
    "ConnectorError",
    "ContextLengthExceeded",
    "InferenceError",
    "ModelLoadError",
    "ModelNotFoundError",
    "RateLimitError",
    "TimeoutError",
    # 공통 스키마
    "AIRequest",
    "AIResponse",
    "ASRRequest",
    "ASRResponse",
    "ChapterAIRequest",
    "ChapterAIResponse",
    "Correction",
    "DenoiseRequest",
    "DenoiseResponse",
    "OCRDetection",
    "OCRRequest",
    "OCRResponse",
    "TTSRequest",
    "TTSResponse",
    # 파이프라인 스키마
    "OCRPipelineResult",
    "PageResult",
    # 후처리 스키마
    "PostprocRequest",
    "PostprocResponse",
]
