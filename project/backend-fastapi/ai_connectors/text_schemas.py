"""텍스트 생성 전용 요청/응답 스키마 (Pydantic v2).

ChapterStudio에서 옮겨 온 스키마로, AIRequest/AIResponse 와 함께 쓴다.
도메인별 파일:
  - schemas.py           : AIRequest/AIResponse, ASR, Denoise, OCR, Correction
  - tts_schemas.py       : TTSRequest / TTSResponse
  - pipeline_schemas.py  : PageResult / OCRPipelineResult
  - postproc_schemas.py  : PostprocRequest / PostprocResponse
  - text_schemas.py (본 파일) : ChapterAIRequest / ChapterAIResponse
"""
from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class ChapterAIRequest(BaseModel):
    """텍스트 커넥터 요청.

    system/user 프롬프트를 분리해 벤더별 system 파라미터에 매핑한다.
    frozen=True 라 여러 코루틴이 같은 객체를 공유해도 바뀌지 않는다.
    """

    model_config = ConfigDict(strict=True, frozen=True)

    system: str = ""
    user: str = Field(min_length=1)
    max_tokens: int = Field(ge=1, le=32000)
    temperature: float = Field(ge=0.0, le=2.0)
    extra: dict[str, str | int | float | bool] = Field(default_factory=dict)


class ChapterAIResponse(BaseModel):
    """텍스트 커넥터 응답.

    비용 추적과 로깅에 쓰려고 input_tokens/output_tokens 를 따로 둔다.
    """

    model_config = ConfigDict(strict=True, frozen=True)

    text: str
    model: str
    input_tokens: int = Field(ge=0)
    output_tokens: int = Field(ge=0)
    finish_reason: str
