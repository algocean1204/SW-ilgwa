"""OCR v1 단독 실행용 FastAPI 앱.

라우터와 테스트 HTML(static/)을 붙인다. uvicorn으로 직접 띄울 때 쓴다.
"""
from __future__ import annotations

import pathlib

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse
from fastapi.staticfiles import StaticFiles

from ..config import server_port
from .routers import health_router, ingest_router, search_router

# app/main.py 기준 상위 폴더의 static/
_STATIC_DIR = pathlib.Path(__file__).parent.parent / "static"

app = FastAPI(
    title="OCR v1 파이프라인",
    description=(
        "PDF 문서를 OCR로 추출·품질검증·청킹·임베딩·벡터저장까지 처리하는 "
        "LangGraph 기반 8단계 RAG 인제스트 파이프라인입니다.\n\n"
        "- **인제스트**: PDF 업로드 → 추출 → 품질 게이트 → 청킹 → 임베딩 저장\n"
        "- **검색**: Qdrant 하이브리드 검색 + BGE-Reranker v2 리랭킹"
    ),
    version="1.0.0",
)

# 단독 테스트용 앱이라 CORS를 모두 연다
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router, prefix="/api/ocr/v1", tags=["운영"])
app.include_router(ingest_router, prefix="/api/ocr/v1", tags=["인제스트"])
app.include_router(search_router, prefix="/api/ocr/v1", tags=["검색"])

# 테스트 HTML
if _STATIC_DIR.exists():
    app.mount("/static", StaticFiles(directory=str(_STATIC_DIR)), name="static")


@app.get("/", include_in_schema=False)
async def root() -> RedirectResponse:
    """루트 접속 시 테스트 페이지로 리다이렉트한다."""
    return RedirectResponse(url="/static/ocr_v1.html")


@app.get("/health", tags=["운영"], summary="헬스체크")
async def health() -> dict[str, str]:
    """서버 정상 동작 여부를 확인한다."""
    return {"status": "ok", "service": "ocr-v1-pipeline"}


if __name__ == "__main__":
    # uv run python -m app.modules.OCR_v1.app.main 으로 실행한다
    uvicorn.run(
        "app.modules.OCR_v1.app.main:app",
        host="0.0.0.0",
        port=server_port(),
        reload=False,
    )
