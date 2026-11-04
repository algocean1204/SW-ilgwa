"""교재 OCR 파이프라인(ai_connectors 경로).

순서: PDF 래스터화 → OCR 커넥터 → Kanana-2 후처리 → 구조화 출력.
OCR 커넥터와 후처리 커넥터는 밖에서 넣어 준다. 결과는 OCRPipelineResult 하나다.
교재 OCR 본 경로(Marker + MinerU)는 app/modules/OCR_v1 에 있다.
"""
from __future__ import annotations

import time

from ai_connectors.ocr.pdf_rasterizer import rasterize_pdf
from ai_connectors.pipeline_schemas import OCRPipelineResult

from ._pipeline_helpers import aggregate_plain_text
from ._pipeline_runners import run_ocr_pages, run_postproc
from ._postproc_protocol import PostprocConnector


class TextbookOCRPipeline:
    """교재 OCR 파이프라인.

    ocr_connector: OCRConnector. registry 가 고른 OCR 커넥터
    postproc_connector: PostprocConnector | None. 후처리 커넥터가 없으면 None
    """

    def __init__(
        self,
        ocr_connector: object,
        postproc_connector: PostprocConnector | None = None,
    ) -> None:
        self._ocr = ocr_connector
        self._postproc = postproc_connector

    async def run(self, pdf_bytes: bytes) -> OCRPipelineResult:
        """PDF 바이트를 받아 OCRPipelineResult 를 반환한다."""
        timings: dict[str, float] = {}

        t0 = time.perf_counter()
        png_pages, rasterizer_backend = rasterize_pdf(pdf_bytes, dpi=300)
        timings["raster_ms"] = (time.perf_counter() - t0) * 1000.0

        t1 = time.perf_counter()
        page_results, ocr_model = await run_ocr_pages(self._ocr, png_pages)
        timings["ocr_ms"] = (time.perf_counter() - t1) * 1000.0

        raw_plain = aggregate_plain_text(page_results)

        t2 = time.perf_counter()
        plain_text, corrections, postproc_model = await run_postproc(
            self._postproc, raw_plain
        )
        timings["postproc_ms"] = (time.perf_counter() - t2) * 1000.0

        return OCRPipelineResult(
            plain_text=plain_text,
            pages=page_results,
            corrections=corrections,
            timings=timings,
            rasterizer_backend=rasterizer_backend,
            ocr_model=ocr_model,
            postproc_model=postproc_model,
        )
