"""파이프라인 단계(OCR, 후처리)별 비동기 실행 함수."""
from __future__ import annotations

from ai_connectors.postproc_schemas import PostprocRequest, PostprocResponse
from ai_connectors.schemas import Correction, OCRRequest, OCRResponse

from ._pipeline_helpers import build_page_result, parse_corrections
from ._postproc_protocol import PostprocConnector


async def run_ocr_pages(
    ocr_connector: object, png_pages: list[bytes]
) -> tuple[list, str]:
    """페이지별로 OCR 을 돌려 (PageResult 목록, 모델명)을 돌려준다.

    모델명은 마지막 페이지 응답의 model_version 이다.
    """
    results = []
    ocr_model = ""
    for idx, page_png in enumerate(png_pages):
        resp: OCRResponse = await ocr_connector.recognize(
            OCRRequest(image_bytes=page_png)
        )
        ocr_model = resp.model_version
        results.append(build_page_result(page_num=idx + 1, ocr_resp=resp))
    return results, ocr_model


async def run_postproc(
    postproc_connector: PostprocConnector | None,
    raw_text: str,
    *,
    preserve_latex: bool = True,
) -> tuple[str, list[Correction], str]:
    """Kanana-2 후처리를 돌린다.

    반환: (refined_text, corrections, postproc_model_name)
    커넥터가 없으면 원문과 'postproc-not-available' 모델명을 돌려준다.
    """
    if postproc_connector is None:
        # 후처리 커넥터가 없으면 건너뛴다
        return raw_text, [], "postproc-not-available"
    request = PostprocRequest(ocr_text=raw_text, preserve_latex=preserve_latex)
    response: PostprocResponse = await postproc_connector.refine(request)
    refined_text: str = response.refined_text
    corrections: list[Correction] = parse_corrections(response.corrections)
    model_name: str = getattr(postproc_connector, "name", "kanana2-modal")
    return refined_text, corrections, model_name
