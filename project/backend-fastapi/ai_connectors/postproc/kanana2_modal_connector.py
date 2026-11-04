"""Modal GPU 배포용 OCR 후처리 LLM 커넥터 스텁.

Modal 에 올린 Kanana 가중치로 후처리할 자리다. 아직 NotImplementedError 만 낸다.
registry 에 등록은 되고, 호출하면 로컬 MLX 경로를 쓰라는 메시지가 나온다.
교재 OCR 의 Kanana-2 교정은 app/modules/OCR_v1 이 따로 한다.
"""
from __future__ import annotations

from ..postproc_schemas import PostprocRequest, PostprocResponse


class Kanana2ModalConnector:
    """Modal GPU 에서 후처리 LLM 을 호출하는 커넥터 (미구현)."""

    name: str = "kanana2-modal"

    async def refine(self, request: PostprocRequest) -> PostprocResponse:
        """아직 구현하지 않았다."""
        raise NotImplementedError(
            "Modal 배포 커넥터는 배포 Phase 에서 구현 예정입니다. "
            "로컬 개발에서는 AI_MODEL_POSTPROC=kanana2-mlx 를 사용하세요.",
        )

    def supports(self, feature: str) -> bool:
        """미구현이라 어떤 기능도 지원한다고 알리지 않는다."""
        return False
