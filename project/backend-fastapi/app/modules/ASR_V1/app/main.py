"""ASR V1 FastAPI 앱.

실행: uv run uvicorn app.modules.ASR_V1.app.main:app --port 8011
lifespan에서 Silero VAD를 미리 로드해 첫 WS 연결이 늦어지지 않게 한다.
"""
from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path
from collections.abc import AsyncGenerator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.modules.ASR_V1.app.routers.asr_v1 import router
from app.modules.ASR_V1.pipeline.vad import SileroVAD

# ASR_V1/static/
_STATIC_DIR = Path(__file__).resolve().parent.parent / "static"


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncGenerator[None, None]:
    """앱 시작 시 Silero VAD 모델을 미리 로드한다(torch.hub 캐시 사용)."""
    SileroVAD._ensure_model()
    yield
    # torch 모델은 GC가 정리하므로 종료 처리는 없다.


app = FastAPI(
    title="ASR V1 — 실시간 대화형 ASR 파이프라인",
    version="1.0.0",
    description="Silero VAD + 배치 ASR 기반 pseudo-streaming 전사 API",
    lifespan=lifespan,
)

# 브라우저 테스트 HTML에서 접근하도록 CORS를 모두 연다
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 테스트 HTML 서빙. static 디렉터리가 없으면 마운트하지 않는다
if _STATIC_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(_STATIC_DIR)), name="static")

app.include_router(router)
