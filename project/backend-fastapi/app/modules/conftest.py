"""app/modules 공통 conftest.

모듈 테스트가 TestClient(app)를 그대로 만들면 X-API-Key 헤더가 없어 401이 난다.
FASTAPI_API_KEY가 있으면 TestClient 기본 headers에 넣어 준다.
"""
from __future__ import annotations

import os
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient


# 루트 conftest.py가 정한 FASTAPI_API_KEY를 TestClient headers에 넣는다.
# 테스트 파일마다 TestClient(app)를 고치지 않아도 된다.

_ORIGINAL_TESTCLIENT_INIT = TestClient.__init__


def _patched_testclient_init(
    self: TestClient,
    app: object,
    *args: object,
    headers: dict[str, str] | None = None,
    **kwargs: object,
) -> None:
    """TestClient 생성 시 X-API-Key 헤더를 자동 삽입한다."""
    api_key = os.environ.get("FASTAPI_API_KEY", "")
    if api_key:
        merged_headers: dict[str, str] = {"X-API-Key": api_key}
        if headers:
            merged_headers.update(headers)
        headers = merged_headers
    _ORIGINAL_TESTCLIENT_INIT(self, app, *args, headers=headers, **kwargs)


@pytest.fixture(autouse=True)
def _auto_inject_api_key_header(monkeypatch: pytest.MonkeyPatch) -> None:
    """모든 테스트에서 TestClient 생성 시 X-API-Key 헤더를 자동 삽입한다."""
    monkeypatch.setattr(TestClient, "__init__", _patched_testclient_init)
