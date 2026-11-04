"""OCR 후처리 LLM 커넥터 서브 패키지.

Modal GPU 커넥터 자리를 둔다.
등록은 `ai_connectors/_registry_postproc.py` 의 POSTPROC_CONNECTORS 에서 한다.
"""

from .kanana2_modal_connector import Kanana2ModalConnector

__all__ = ["Kanana2ModalConnector"]
