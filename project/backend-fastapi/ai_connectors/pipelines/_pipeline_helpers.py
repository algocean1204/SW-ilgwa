"""파이프라인 공용 순수 함수."""
from __future__ import annotations

from ai_connectors.pipeline_schemas import PageResult
from ai_connectors.schemas import Correction, OCRResponse

# 페이지 텍스트를 이을 때 사이에 넣는 구분자
_PAGE_SEP = f"\n{'─' * 40}\n"


def build_page_result(page_num: int, ocr_resp: OCRResponse) -> PageResult:
    """OCR 단일 페이지 응답을 PageResult 스키마로 변환한다."""
    return PageResult(
        page_num=page_num,
        text=ocr_resp.text,
        detections=list(ocr_resp.detections),
    )


def aggregate_plain_text(pages: list[PageResult]) -> str:
    """페이지 목록의 텍스트를 구분자로 이어붙여 전체 텍스트를 만든다."""
    texts = [p.text for p in pages if p.text]
    return _PAGE_SEP.join(texts)


def parse_corrections(
    raw_corrections: list[Correction | dict[str, str]],
) -> list[Correction]:
    """커넥터가 반환한 Correction 인스턴스 또는 dict 목록을 Correction 목록으로 변환한다.

    PostprocResponse.corrections 는 list[Correction] 이지만 예전 dict 형식도 받는다.
    """
    result: list[Correction] = []
    for item in raw_corrections:
        if isinstance(item, Correction):
            # PostprocResponse 의 Pydantic 인스턴스는 그대로 쓴다
            if item.original and item.corrected:
                result.append(item)
        else:
            # 예전 형식이나 외부에서 넣은 dict
            original = item.get("original", "")
            corrected = item.get("corrected", "")
            if original and corrected:
                result.append(
                    Correction(
                        original=original,
                        corrected=corrected,
                        reason=item.get("reason", ""),
                    )
                )
    return result
