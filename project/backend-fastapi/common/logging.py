"""로거 팩토리.

모듈들은 logging.getLogger() 대신 get_logger(name)을 쓴다.
포맷과 레벨을 여기서만 정해 로그 모양이 모듈마다 달라지지 않게 한다.
"""
from __future__ import annotations

import logging
import os
import sys

# 루트 로거를 두 번 초기화하지 않게 상태를 dict 에 둔다
_LOG_STATE: dict[str, bool] = {"initialized": False}


def _ensure_root_logger() -> None:
    """루트 로거에 stdout 핸들러를 한 번만 부착한다."""
    if _LOG_STATE["initialized"]:
        return
    level_name = os.getenv("LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    # uvicorn 등이 이미 핸들러를 달았으면 로그가 두 번 찍히지 않게 건너뛴다
    root = logging.getLogger()
    root.setLevel(level)
    if not root.handlers:
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(
            logging.Formatter(
                fmt="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
                datefmt="%Y-%m-%d %H:%M:%S",
            )
        )
        root.addHandler(handler)
    _LOG_STATE["initialized"] = True


def get_logger(name: str) -> logging.Logger:
    """이름 있는 로거를 반환한다. 루트 로거는 처음 호출할 때 초기화한다."""
    _ensure_root_logger()
    return logging.getLogger(name)
