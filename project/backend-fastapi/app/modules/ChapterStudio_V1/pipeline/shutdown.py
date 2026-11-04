"""강의 배치 완료 후 커넥터 shutdown 훅 호출 유틸.

[비용 통제 설계]
Modal은 running 컨테이너 시간만 과금한다. 작업 완료 후 modal_gpu_release가
`modal container stop`으로 컨테이너를 즉시 회수한다(배포 유지). scaledown_window는
보조 장치이며, un-deploy(`modal app stop`)는 decommission 전용이다.

[connector.shutdown() 계약]
- 기본(CHAPTERSTUDIO_MODAL_TEARDOWN=false): modal container stop → stop 수>0이면 True.
- teardown 스위치 ON: `modal app stop`(un-deploy) 실행 → True/False 반환.
  라이브 서비스에서 teardown=true는 다음 요청을 깨뜨리므로 쓰지 않는다.

[shutdown 미지원 커넥터]
Claude·Gemini·OpenAI 단독 커넥터는 supports("shutdown")=False라 이 유틸이 no-op(False 반환)이다.

공개 API:
    - shutdown_text_connector() : 활성 텍스트 커넥터의 shutdown 훅을 호출한다(대부분 no-op).
"""
from __future__ import annotations

import asyncio

from app.modules.ChapterStudio_V1.ai_connectors.errors import ConnectorError
from app.modules.ChapterStudio_V1.ai_connectors.registry import get_text_connector
from app.modules.ChapterStudio_V1.common.logging import logger


async def shutdown_text_connector() -> bool:
    """활성 텍스트 커넥터의 shutdown 훅을 호출한다(기본 no-op, teardown 스위치 ON 시만 un-deploy).

    scaledown_window가 이미 idle 컨테이너 비용을 0으로 만드므로, 이 함수는 실제 서비스
    흐름에서 대부분 False(no-op)를 반환한다. teardown 경로는 decommission 전용이다.
    반환값: 실제 teardown을 수행해 성공했으면 True, no-op이거나 실패면 False.
    """
    try:
        connector = get_text_connector()
    except ConnectorError as exc:
        logger.warning("shutdown: 텍스트 커넥터 조회 실패(graceful): {}", exc)
        return False
    if not _can_shutdown(connector):
        return False
    try:
        return bool(await connector.shutdown())
    except (ConnectorError, OSError, asyncio.TimeoutError, RuntimeError) as exc:
        logger.warning("shutdown: 커넥터 shutdown 훅 실패(graceful): {}", exc)
        return False


def _can_shutdown(connector: object) -> bool:
    """커넥터가 shutdown 기능을 명시 지원하고 실제 메서드를 가졌는지 확인한다."""
    supports = getattr(connector, "supports", None)
    shutdown = getattr(connector, "shutdown", None)
    if not callable(supports) or not callable(shutdown):
        return False
    return bool(supports("shutdown"))


__all__ = ["shutdown_text_connector"]
