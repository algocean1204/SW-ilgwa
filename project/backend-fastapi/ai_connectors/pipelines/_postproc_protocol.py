"""후처리 커넥터 Protocol 재수출 모듈.

PostprocConnector 정의는 base.py 에 있다.
기존 import 경로(from ai_connectors.pipelines._postproc_protocol import PostprocConnector)가
깨지지 않게 재수출만 한다.
"""
from ai_connectors.base import PostprocConnector

__all__ = ["PostprocConnector"]
