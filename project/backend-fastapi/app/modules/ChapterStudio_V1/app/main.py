from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import asyncpg
from fastapi import FastAPI, Response, status
from fastapi.middleware.cors import CORSMiddleware

from app.modules.ChapterStudio_V1.app.db import (
    DbPool,
    close_pool,
    create_pool,
    ping,
)
from app.modules.ChapterStudio_V1.ai_connectors.registry import close_all as close_connectors
from app.modules.ChapterStudio_V1.common.logging import logger

_STARTUP_ERRORS = (asyncpg.PostgresError, OSError)
_PING_ERRORS = (asyncpg.PostgresError, asyncpg.InterfaceError, OSError)


def _db_pool(app: FastAPI) -> DbPool | None:
    """FastAPI state에서 DB 풀을 꺼내고, 없거나 타입이 다르면 None을 준다."""
    pool = getattr(app.state, "db_pool", None)
    if isinstance(pool, DbPool):
        return pool
    return None


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """앱 시작과 종료 시 DB 풀 생명주기를 관리한다."""
    try:
        app.state.db_pool = await create_pool()
    except _STARTUP_ERRORS as exc:
        logger.exception("DB pool 생성 실패: {}", exc)
        app.state.db_pool = None
    try:
        yield
    finally:
        await close_connectors()
        await close_pool(_db_pool(app))


app = FastAPI(title="ChapterStudio_V1", lifespan=lifespan)

# CORS 전체 허용은 개발 샌드박스용이다. 프로덕션에서는 오리진을 제한해야 한다.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/healthz")
async def healthz(response: Response) -> dict[str, str]:
    pool = _db_pool(app)
    if pool is None:
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "error", "db": "down", "modal": "skipped"}

    try:
        await ping(pool)
    except _PING_ERRORS as exc:
        logger.warning("DB ping 실패: {}", exc)
        response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {"status": "error", "db": "down", "modal": "skipped"}

    return {"status": "ok", "db": "ok", "modal": "skipped"}
