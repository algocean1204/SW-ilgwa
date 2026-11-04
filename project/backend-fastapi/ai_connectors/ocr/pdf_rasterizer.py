"""PDF 래스터화 모듈.

Rust 구현은 lib-rust 저장소에 있고, 여기는 Python 쪽 호출부다.

우선순위:
  1. lib_rust_rasterize 휠이 있으면 Rust(PyO3) 경로를 쓴다
     (pdfium 기반, 300 DPI, 목표 페이지당 200ms 미만).
  2. 휠이 없으면 pypdfium2 를 쓴다
     (Apache 2.0 / BSD. AGPL-3.0 인 PyMuPDF 는 쓰지 않는다).
"""
from __future__ import annotations

import io
import logging
from typing import Literal

# 어떤 백엔드를 썼는지 INFO 로 남긴다
_LOG = logging.getLogger(__name__)

# 래스터화 백엔드 이름. 라우터가 X-Rasterizer-Backend 헤더로 내보낸다.
RasterizerBackend = Literal["lib-rust", "pypdfium2"]


def _rasterize_with_rust(pdf_bytes: bytes, dpi: int) -> list[bytes]:
    """lib-rust PyO3 휠 경로로 PDF 래스터화.

    lib_rust_rasterize.rasterize_pdf() 는 bytes → list[bytes] 를 반환한다.
    각 원소는 PNG 바이트 (한 페이지 = 하나의 PNG).
    휠이 없어 ImportError 가 나면 호출부가 pypdfium2 로 넘어간다.
    """
    # 휠이 없으면 여기서 ImportError 가 나서 호출부가 pypdfium2 로 넘어간다
    import lib_rust_rasterize as rust_raster

    # Rust 측 인터페이스: rasterize_pdf(pdf_bytes: bytes, dpi: int) -> list[bytes]
    return rust_raster.rasterize_pdf(pdf_bytes, dpi=dpi)


def _rasterize_with_pypdfium2(pdf_bytes: bytes, dpi: int) -> list[bytes]:
    """pypdfium2 Python fallback 경로로 PDF 래스터화.

    pypdfium2 는 Apache 2.0 / BSD 라이선스라 상업 이용이 된다.
    lib-rust 휠이 없는 로컬 환경에서 쓴다.
    """
    # 폴백에서만 쓰는 라이브러리라 여기서 import 한다
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
        - backend: 실제로 사용된 백엔드 식별자 ('lib-rust' 또는 'pypdfium2')

    라우터가 backend 값을 X-Rasterizer-Backend 응답 헤더로 노출한다.
    """
    if not pdf_bytes:
        raise ValueError("PDF 바이트가 비어있습니다.")

    # lib-rust PyO3 휠이 있으면 그쪽이 빠르다
    try:
        pages = _rasterize_with_rust(pdf_bytes, dpi)
        _LOG.info(
            "PDF 래스터화: lib-rust PyO3 경로 사용 (dpi=%d, pages=%d)",
            dpi,
            len(pages),
        )
        return pages, "lib-rust"
    except ImportError:
        # lib-rust 휠이 없으면 pypdfium2 로 넘어간다
        _LOG.info(
            "lib_rust_rasterize 미설치 — pypdfium2 fallback 으로 전환 (dpi=%d)",
            dpi,
        )

    pages = _rasterize_with_pypdfium2(pdf_bytes, dpi)
    _LOG.info(
        "PDF 래스터화: pypdfium2 경로 사용 (dpi=%d, pages=%d)",
        dpi,
        len(pages),
    )
    return pages, "pypdfium2"
