"""LangGraph 폴백 노드. 품질 게이트에 걸린 페이지만 MinerU로 다시 뽑는다.

다시 뽑은 페이지만 Kanana-2로 교정한 뒤 extractions 의 해당 페이지를 바꾼다.
Kanana-2 교정이 실패하면 같은 프롬프트로 Claude가 교정하고, Claude도 실패하면 MinerU 결과를 그대로 쓴다.
게이트를 통과한 페이지는 교정하지 않는다. 전 페이지에 적용하면 원문에 없는 문장이 붙어 CER이 오른다.
"""
from __future__ import annotations

import logging
import time

from ... import config as cfg
from ...registry import get_fallback_engine
from ...schemas.state import OCRPipelineState, PageExtraction

_LOG = logging.getLogger(__name__)


def _build_page_index(extractions: list[PageExtraction]) -> dict[int, int]:
    """page_num 에서 extractions 인덱스로 가는 맵을 만든다."""
    return {page.get("page_num", 0): idx for idx, page in enumerate(extractions)}


def _get_polish_connector() -> object | None:
    """ChapterStudio의 Kanana-2 교정 커넥터를 가져온다. 쓸 수 없으면 None."""
    try:
        from app.modules.ChapterStudio_V1.ai_connectors.registry import get_polish_connector

        return get_polish_connector()
    except Exception as exc:
        _LOG.warning("Kanana-2 교정 커넥터 사용 불가: %s", exc)
        return None


def _get_claude_connector() -> object | None:
    """ChapterStudio의 Claude Sonnet 커넥터를 가져온다. 키가 없거나 쓸 수 없으면 None."""
    try:
        from app.modules.ChapterStudio_V1.ai_connectors.registry import get_connector

        return get_connector("claude_sonnet")
    except Exception as exc:
        _LOG.warning("Claude 교정 커넥터 사용 불가: %s", exc)
        return None


async def _claude_polish(connector: object, markdown: str) -> str:
    """Kanana-2와 같은 교정 지시문과 출력 정리로 Claude에게 교정을 맡긴다."""
    from app.modules.ChapterStudio_V1.ai_connectors.kanana2_connector import _clean_output, _instruction
    from app.modules.ChapterStudio_V1.ai_connectors.schemas import ChapterAIRequest

    req = ChapterAIRequest(
        system=_instruction(""),
        user=markdown,
        # Kanana-2 앱과 같은 길이 상한
        max_tokens=max(256, min(4096, len(markdown) + 512)),
        temperature=0.0,
    )
    res = await connector.generate(req)  # type: ignore[attr-defined]
    return _clean_output(res.text)


async def _polish_one(kanana: object | None, claude: object | None, markdown: str, page_num: int) -> str:
    """Kanana-2, Claude 순서로 교정한다. 둘 다 실패하면 빈 문자열을 돌려준다."""
    if kanana is not None:
        try:
            fixed = await kanana.polish(markdown)  # type: ignore[attr-defined]
        except Exception as exc:
            _LOG.warning("페이지 %d Kanana-2 교정 실패: %s", page_num, exc)
            fixed = ""
        # Kanana2Connector는 실패하면 예외 대신 원문을 돌려줘서 실패와 고칠 것 없음을 구분할 수 없다.
        # 원문과 같으면 실패로 보고 Claude에 넘긴다. 커넥터가 실패를 알려 주게 바뀌면 이 비교를 뺀다.
        if fixed and fixed != markdown:
            return fixed
    if claude is None:
        return ""
    try:
        return await _claude_polish(claude, markdown)
    except Exception as exc:
        _LOG.warning("페이지 %d Claude 교정 실패, MinerU 결과 유지: %s", page_num, exc)
        return ""


async def _polish_pages(
    pages: list[PageExtraction], failed_pages: list[int]
) -> list[PageExtraction]:
    """failed_pages 에 든 페이지의 markdown 만 Kanana-2(실패 시 Claude)로 교정한다."""
    if not cfg.fallback_kanana_polish():
        return pages
    kanana = _get_polish_connector()
    claude = _get_claude_connector() if cfg.fallback_claude_polish() else None
    targets = set(failed_pages)
    polished: list[PageExtraction] = []
    for page in pages:
        markdown = page.get("markdown", "")
        page_num = page.get("page_num", 0)
        if page_num not in targets or not markdown:
            polished.append(page)
            continue
        fixed = await _polish_one(kanana, claude, markdown, page_num)
        if fixed:
            polished.append({**page, "markdown": fixed, "llm_corrected": True})
        else:
            polished.append(page)
    return polished


async def fallback(state: OCRPipelineState) -> dict:
    """실패 페이지를 MinerU로 다시 뽑아 교정하고 extractions 에 합친다.

    failed_pages 가 없으면 그대로 통과한다.
    fallback_extractions 에는 교정 전 MinerU 결과를 따로 남긴다.
    """
    # 앞 단계가 실패했으면 아무것도 하지 않는다
    if state.get("pipeline_status") == "error":
        return {}
    t0 = time.perf_counter()
    failed_pages = state.get("failed_pages", [])

    if not failed_pages:
        _LOG.info("Stage 5: 실패 페이지 없음 — 폴백 건너뜀")
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "fallback_extractions": [],
            "current_stage": "fallback",
            "timings": {**state.get("timings", {}), "fallback": elapsed},
        }

    _LOG.info("Stage 5: 폴백 재추출 시작 — 대상 페이지=%s", failed_pages)

    try:
        fallback_engine = get_fallback_engine()
        _LOG.info("폴백 엔진: %s", fallback_engine.name)

        # 엔진은 0-based 인덱스를 받는다
        page_indices = [pn - 1 for pn in failed_pages]
        fallback_results: list[PageExtraction] = await fallback_engine.extract(
            state.get("pdf_bytes", b""), pages=page_indices
        )
    except Exception as exc:
        _LOG.warning("폴백 재추출 실패 — 기존 추출 결과 유지 (partial): %s", exc)
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "fallback_extractions": [],
            "pipeline_status": "partial",
            "error_message": f"폴백 재추출 실패: {exc}",
            "current_stage": "fallback",
            "timings": {**state.get("timings", {}), "fallback": elapsed},
        }

    # 요청은 했는데 결과가 0건이면 품질 저하를 상태에 남긴다
    if not fallback_results:
        _LOG.warning(
            "Stage 5: %d 페이지 폴백 요청했으나 재추출 결과 0건",
            len(failed_pages),
        )
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "fallback_extractions": [],
            "current_stage": "fallback",
            "pipeline_status": "partial",
            "error_message": f"폴백 재추출 결과 0건 — 실패 페이지 {len(failed_pages)}건 원본 유지",
            "timings": {**state.get("timings", {}), "fallback": elapsed},
        }

    polished_results = await _polish_pages(fallback_results, failed_pages)

    merged = list(state.get("extractions", []))
    page_index_map = _build_page_index(merged)

    for fb_page in polished_results:
        pn = fb_page.get("page_num", 0)
        if pn in page_index_map:
            merged[page_index_map[pn]] = fb_page

    elapsed = round(time.perf_counter() - t0, 3)
    _LOG.info(
        "Stage 5 완료: %d 페이지 재추출, %.3fs 소요",
        len(fallback_results),
        elapsed,
    )

    return {
        "extractions": merged,
        "fallback_extractions": fallback_results,
        "current_stage": "fallback",
        "timings": {**state.get("timings", {}), "fallback": elapsed},
    }
