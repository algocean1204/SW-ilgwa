"""AI 커넥터 공통 인터페이스.

모든 벤더 커넥터는 AIConnector(텍스트) / ASRConnector(음성→텍스트) /
DenoiseConnector(음성→음성) / TTSConnector(텍스트+레퍼런스→음성) /
OCRConnector(이미지→텍스트) / PostprocConnector(OCR 텍스트→교정 텍스트)
Protocol 중 하나를 구현한다.
호출부는 구체 커넥터를 직접 import하지 않고 registry를 통해 받는다.
"""
from __future__ import annotations

from typing import Protocol, runtime_checkable

from .postproc_schemas import PostprocRequest, PostprocResponse
from .schemas import (
    AIRequest,
    AIResponse,
    ASRRequest,
    ASRResponse,
    DenoiseRequest,
    DenoiseResponse,
    OCRRequest,
    OCRResponse,
)
from .tts_schemas import TTSRequest, TTSResponse


@runtime_checkable
class AIConnector(Protocol):
    """벤더 중립 AI 텍스트 생성 커넥터 인터페이스."""

    # 커넥터 식별자 (예: "kanana", "gemini-mac", "qwen3-tts-modal")
    name: str

    async def generate(self, request: AIRequest) -> AIResponse:
        """주어진 요청으로 AI 모델 호출 후 공통 응답 스키마로 반환."""
        ...

    def supports(self, feature: str) -> bool:
        """capability 플래그 검사 (예: 'streaming', 'tool_calling', 'vision')."""
        ...


@runtime_checkable
class ASRConnector(Protocol):
    """벤더 중립 ASR(음성→텍스트) 커넥터 인터페이스.

    AIConnector와 입력·출력 스키마가 달라 별도 Protocol로 둔다.
    """

    name: str

    async def generate(self, request: ASRRequest) -> ASRResponse:
        """오디오 요청을 받아 트랜스크립트 응답으로 반환."""
        ...

    def supports(self, feature: str) -> bool:
        """capability 플래그 검사 (예: 'transcription', 'language_hint')."""
        ...


@runtime_checkable
class DenoiseConnector(Protocol):
    """벤더 중립 Denoise(음성→음성) 커넥터 인터페이스.

    입력·출력이 모두 오디오 바이트라 generate 대신 denoise라는 이름을 쓴다.
    """

    name: str

    async def denoise(self, request: DenoiseRequest) -> DenoiseResponse:
        """지저분한 오디오를 깨끗한 WAV 바이트로 정제해 반환."""
        ...

    def supports(self, feature: str) -> bool:
        """capability 플래그 검사 (예: 'super_resolution', 'batch')."""
        ...


@runtime_checkable
class TTSConnector(Protocol):
    """벤더 중립 TTS(텍스트+레퍼런스→음성) 커넥터 인터페이스.

    입력은 합성할 텍스트와 레퍼런스 오디오/텍스트 쌍(보이스 클로닝 ICL 모드),
    출력은 WAV 바이트다. 메서드 이름은 synthesize다.
    """

    name: str

    async def synthesize(self, request: TTSRequest) -> TTSResponse:
        """텍스트 + 레퍼런스 음성으로 합성된 WAV 바이트를 반환한다."""
        ...

    def supports(self, feature: str) -> bool:
        """capability 플래그 검사 (예: 'voice_cloning', 'language_hint')."""
        ...


@runtime_checkable
class OCRConnector(Protocol):
    """벤더 중립 OCR(이미지→텍스트) 커넥터 인터페이스.

    Gemini OCR, Nemotron-OCR-v2, PP-OCRv4 구현을 바꿔 끼울 수 있게 Protocol 하나로 둔다.
    """

    name: str

    async def recognize(self, req: OCRRequest) -> OCRResponse:
        """이미지 바이트를 받아 텍스트 인식 결과를 반환한다."""
        ...

    def supports(self, feature: str) -> bool:
        """capability 플래그 검사 (예: 'korean', 'layout_analysis', 'pdf')."""
        ...


@runtime_checkable
class PostprocConnector(Protocol):
    """벤더 중립 OCR 후처리 LLM 커넥터 인터페이스.

    OCR 원문 출력의 반복 붕괴, 오타, 띄어쓰기 오류를 한국어·수식 문맥으로 교정한다.
    텍스트를 받아 텍스트를 내는 점은 AIConnector와 같지만, refine은 교정 항목 목록이
    담긴 구조화 응답을 돌려줘서 별도 Protocol로 둔다.

    등록된 구현은 kanana2-modal 이며 아직 스텁이다.
    """

    name: str

    async def refine(self, req: PostprocRequest) -> PostprocResponse:
        """OCR 텍스트를 받아 교정 결과와 교정 항목 목록을 반환한다."""
        ...

    def supports(self, feature: str) -> bool:
        """capability 플래그 검사 (예: 'korean', 'latex_preserve', 'mlx')."""
        ...
