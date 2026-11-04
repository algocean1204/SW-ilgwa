"""pypdfium2로 텍스트 레이어와 이미지 면적 비율을 재서 PDF 페이지 타입을 나눈다.

텍스트가 충분하면 born-digital, 이미지가 대부분이면 scanned, 그 사이는 mixed다.
"""
from __future__ import annotations

import logging

import pypdfium2 as pdfium

from ..schemas.state import PageClassification

_LOG = logging.getLogger(__name__)

# 이미지가 페이지 면적의 이 비율 이상이면 scanned 후보로 본다
_IMAGE_DOMINANT_RATIO = 0.6
# 텍스트가 이 글자 수 이상이어야 텍스트 레이어가 있다고 본다
_TEXT_CHAR_MIN = 50


def _classify_single(
    page: pdfium.PdfPage,
    page_num: int,
    page_width: float,
    page_height: float,
) -> PageClassification:
    """pdfium 페이지 하나를 분류한다."""
    page_area = page_width * page_height

    textpage = page.get_textpage()
    text = textpage.get_text_range()
    has_text = len(text.strip()) >= _TEXT_CHAR_MIN

    # 이미지 객체 면적을 더해 페이지 대비 비율을 구한다
    image_area = 0.0
    for obj in page.get_objects():
        if obj.type == pdfium.raw.FPDF_PAGEOBJ_IMAGE:
            left, bottom, right, top = obj.get_pos()
            image_area += abs(right - left) * abs(top - bottom)

    image_ratio = min(image_area / page_area, 1.0) if page_area > 0 else 0.0

    if has_text and image_ratio < _IMAGE_DOMINANT_RATIO:
        page_type = "born-digital"
        confidence = 1.0 - image_ratio
    elif not has_text and image_ratio >= _IMAGE_DOMINANT_RATIO:
        page_type = "scanned"
        confidence = image_ratio
    else:
        page_type = "mixed"
        confidence = 0.5

    return PageClassification(
        page_num=page_num,
        page_type=page_type,
        has_text_layer=has_text,
        image_ratio=round(image_ratio, 4),
        confidence=round(confidence, 4),
    )


def classify_pages(pdf_bytes: bytes) -> list[PageClassification]:
    """PDF 전체 페이지를 분류해 PageClassification 목록을 반환한다.

    pypdfium2가 스레드 안전하지 않아 동기 함수로 둔다.
    LangGraph 노드는 asyncio.to_thread로 부른다.
    """
    results: list[PageClassification] = []
    doc = pdfium.PdfDocument(pdf_bytes)
    try:
        for idx in range(len(doc)):
            page = doc[idx]
            w, h = page.get_width(), page.get_height()
            classification = _classify_single(page, idx + 1, w, h)
            results.append(classification)
            _LOG.debug("페이지 %d 분류 완료: %s", idx + 1, classification["page_type"])
    except Exception:
        _LOG.exception("PDF 페이지 분류 중 오류 발생")
        raise
    finally:
        doc.close()
    return results
