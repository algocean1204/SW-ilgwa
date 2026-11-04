"""Modal GPU 배포용 MossFormer2 디노이즈 커넥터 스텁.

Modal GPU에서 clearvoice 를 돌릴 자리다. 아직 구현하지 않아 NotImplementedError만 낸다.
registry 에서 인스턴스는 만들 수 있다.
"""
from __future__ import annotations

from ..schemas import DenoiseRequest, DenoiseResponse


class MossFormer2ModalConnector:
    """Modal GPU에서 MossFormer2를 호출하는 커넥터 (미구현)."""

    # 등록 키와 동일
    name: str = "mossformer2-modal"

    async def denoise(self, request: DenoiseRequest) -> DenoiseResponse:
        """아직 구현하지 않았다."""
        raise NotImplementedError(
            "Modal 배포 커넥터는 배포 Phase에서 구현 예정입니다. "
            "로컬 개발에서는 AI_MODEL_DENOISE=mossformer2-se-48k를 사용하세요.",
        )

    def supports(self, feature: str) -> bool:
        """미구현이라 어떤 기능도 지원한다고 알리지 않는다."""
        return False
