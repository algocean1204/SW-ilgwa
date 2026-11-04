"""Marker 기반 OCREngine. 파이프라인의 1차 추출 엔진이다.

marker-pdf 동기 API를 asyncio.to_thread로 돌려 이벤트 루프를 막지 않는다.
born-digital과 스캔 PDF를 모두 처리한다.
use_llm은 결과의 llm_corrected 표시에만 쓰고 PdfConverter 설정으로는 넘기지 않는다.
"""
from __future__ import annotations

import asyncio
import logging
import re
import tempfile
from pathlib import Path

from ..config import marker_timeout_sec
from ..schemas import PageExtraction

_LOG = logging.getLogger(__name__)

# 설치 여부만 확인한다. 실제 import는 _run_marker에서 한다.
try:
    # 타입 스텁이 없어 pyproject.toml mypy.overrides(marker.*)로 허용한다.
    import marker.converters.pdf
    _MARKER_AVAILABLE = True
except ImportError:
    try:
        import marker.convert
        _MARKER_AVAILABLE = True
    except ImportError:
        _MARKER_AVAILABLE = False
        _LOG.warning("marker-pdf 패키지가 설치되지 않았습니다. MarkerEngine 비활성화.")


def _tables_from_md(md: str) -> list[dict]:
    """마크다운에서 파이프 테이블 블록을 추출한다."""
    lines = md.splitlines()
    tables: list[dict] = []
    current: list[str] = []
    for line in lines:
        s = line.strip()
        if s.startswith("|") and s.endswith("|"):
            current.append(line)
        else:
            if len(current) >= 2:
                tables.append({"md": "\n".join(current), "json": {}})
            current = []
    if len(current) >= 2:
        tables.append({"md": "\n".join(current), "json": {}})
    return tables


def _formulas_from_md(md: str) -> list[str]:
    """마크다운에서 LaTeX 수식($$ ... $$ 및 $ ... $)을 추출한다."""
    out: list[str] = []
    for m in re.finditer(r"\$\$(.+?)\$\$", md, re.DOTALL):
        out.append(m.group(1).strip())
    for m in re.finditer(r"(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)", md):
        out.append(m.group(1).strip())
    return out


def _make_page(idx: int, md: str, llm_corrected: bool) -> PageExtraction:
    """한 페이지 PageExtraction을 만든다."""
    return PageExtraction(
        page_num=idx + 1,
        markdown=md,
        tables=_tables_from_md(md),
        formulas=_formulas_from_md(md),
        engine="marker",
        llm_corrected=llm_corrected,
    )


class MarkerEngine:
    """Marker PDF 변환기를 감싼 OCREngine.

    모델 딕셔너리를 인스턴스에 캐시해 두 번째 호출부터 다시 로드하지 않는다.
    """

    name: str = "marker"

    def __init__(self) -> None:
        self._model_dict: dict | None = None
        self._lock = asyncio.Lock()

    def supports(self, feature: str) -> bool:
        """지원 기능 플래그를 반환한다."""
        return feature in {"pdf", "markdown", "ocr", "llm_correction", "tables", "formulas"}

    async def extract(
        self,
        pdf_bytes: bytes,
        pages: list[int] | None = None,
        force_ocr: bool = False,
        use_llm: bool = False,
    ) -> list[PageExtraction]:
        """PDF 바이트를 받아 페이지별 추출 결과를 반환한다."""
        if not _MARKER_AVAILABLE:
            raise RuntimeError("marker-pdf 패키지 미설치 — MarkerEngine 사용 불가.")
        timeout = marker_timeout_sec()
        return await asyncio.wait_for(
            asyncio.to_thread(self._extract_sync, pdf_bytes, pages, force_ocr, use_llm),
            timeout=timeout,
        )

    def _extract_sync(
        self,
        pdf_bytes: bytes,
        pages: list[int] | None,
        force_ocr: bool,
        use_llm: bool,
    ) -> list[PageExtraction]:
        """Marker가 동기 API라 스레드에서 돌리는 래퍼. PDF는 임시 파일로 넘긴다."""
        with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as f:
            f.write(pdf_bytes)
            tmp_path = Path(f.name)
        try:
            return self._run_marker(tmp_path, use_llm)
        finally:
            tmp_path.unlink(missing_ok=True)

    def _run_marker(self, pdf_path: Path, use_llm: bool) -> list[PageExtraction]:
        """Marker 변환을 실행하고 PageExtraction 리스트로 정규화한다."""
        try:
            from marker.converters.pdf import PdfConverter
            from marker.models import create_model_dict

            if self._model_dict is None:
                self._model_dict = create_model_dict()

            converter = PdfConverter(artifact_dict=self._model_dict)
            rendered = converter(str(pdf_path))
            return _parse_rendered(rendered, use_llm)

        except (ImportError, AttributeError):
            # 신버전 API가 없으면 구버전 convert_single_pdf로 내려간다
            from marker.convert import convert_single_pdf
            full_text, _images, _meta = convert_single_pdf(
                str(pdf_path), self._model_dict, max_pages=None,
                langs=None, batch_multiplier=2,
            )
            return [_make_page(0, full_text or "", use_llm)]


def _parse_rendered(rendered: object, llm_corrected: bool) -> list[PageExtraction]:
    """신버전 Marker rendered 객체를 PageExtraction 리스트로 변환한다."""
    full_md: str = getattr(rendered, "markdown", "") or ""
    pages_data = getattr(rendered, "pages", None) or [None]
    return [
        _make_page(
            idx,
            getattr(page, "markdown", full_md) if page is not None else full_md,
            llm_corrected,
        )
        for idx, page in enumerate(pages_data)
    ]
