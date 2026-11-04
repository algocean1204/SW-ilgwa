"""Chat_V1 테스트 공통 설정. 실제 앱(main.app)으로 TestClient 를 만든다."""
from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)
