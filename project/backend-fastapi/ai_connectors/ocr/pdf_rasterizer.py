"""PDF 래스터화 모듈.

pypdfium2 로 PDF 를 페이지별 PNG 로 렌더링한다.
(Apache 2.0 / BSD. AGPL-3.0 인 PyMuPDF 는 쓰지 않는다.)
"""
from __future__ import annotations

import io
import logging
from typing import Literal

# 어떤 백엔드를 썼는지 INFO 로 남긴다
_LOG = logging.getLogger(__name__)

# 래스터화 백엔드 이름. 라우터가 X-Rasterizer-Backend 헤더로 내보낸다.
RasterizerBackend = Literal["pypdfium2"]


def _rasterize_with_pypdfium2(pdf_bytes: bytes, dpi: int) -> list[bytes]:
    """pypdfium2 로 PDF 래스터화.

    pypdfium2 는 Apache 2.0 / BSD 라이선스라 상업 이용이 된다.
    """
    import pypdfium2 as pdfium

    scale = dpi / 72.0  # pypdfium2 는 72 DPI 기준 배율로 렌더링

    doc = pdfium.PdfDocument(pdf_bytes)
    result: list[bytes] = []
    for page_index in range(len(doc)):
        page = doc[page_index]
        bitmap = page.render(scale=scale, rotation=0)
        pil_image = bitmap.to_pil()
        buf = io.BytesIO()
        pil_image.save(buf, format="PNG")
        result.append(buf.getvalue())

    return result


def rasterize_pdf(pdf_bytes: bytes, dpi: int = 300) -> tuple[list[bytes], RasterizerBackend]:
    """PDF 바이트를 페이지별 PNG 바이트 목록으로 래스터화한다.

    반환값:
        (png_pages, backend)
        - png_pages: 페이지별 PNG 바이트 목록
        - backend: 사용된 백엔드 식별자 ('pypdfium2')

    라우터가 backend 값을 X-Rasterizer-Backend 응답 헤더로 노출한다.
    """
    if not pdf_bytes:
        raise ValueError("PDF 바이트가 비어있습니다.")

    pages = _rasterize_with_pypdfium2(pdf_bytes, dpi)
    _LOG.info(
        "PDF 래스터화: pypdfium2 경로 사용 (dpi=%d, pages=%d)",
        dpi,
        len(pages),
    )
    return pages, "pypdfium2"
