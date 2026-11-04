"""MinerU(magic-pdf) 기반 OCREngine.

품질 게이트에 걸린 페이지를 다시 뽑는 폴백 엔진이다.
동기 API를 asyncio.to_thread로 돌려 이벤트 루프를 막지 않는다.
"""
from __future__ import annotations

import asyncio
import logging
import tempfile
from pathlib import Path

from ..schemas import PageExtraction

_LOG = logging.getLogger(__name__)

# magic-pdf가 없어도 모듈은 불러올 수 있다
try:
    from magic_pdf.data.data_reader_writer import (
        FileBasedDataReader,
        FileBasedDataWriter,
    )
    _MINERU_AVAILABLE = True
except ImportError:
    _MINERU_AVAILABLE = False
    _LOG.warning("magic-pdf 패키지가 설치되지 않았습니다. MinerUEngine 비활성화.")


class MinerUEngine:
    """MinerU(magic-pdf) PDF 변환기를 감싼 OCREngine.

    한국어·중국어·일본어 문서에 강해 Marker의 폴백으로 쓴다.
    """

    name: str = "mineru"

    def supports(self, feature: str) -> bool:
        """지원 기능 플래그를 반환한다."""
        return feature in {"pdf", "markdown", "tables", "formulas", "cjk"}

    async def extract(
        self,
        pdf_bytes: bytes,
        pages: list[int] | None = None,
        force_ocr: bool = False,
        use_llm: bool = False,
    ) -> list[PageExtraction]:
        """PDF 바이트를 받아 페이지별 추출 결과를 반환한다."""
        if not _MINERU_AVAILABLE:
            raise RuntimeError("magic-pdf 패키지가 설치되지 않아 MinerUEngine을 사용할 수 없습니다.")

        return await asyncio.to_thread(
            self._extract_sync, pdf_bytes, pages, force_ocr, use_llm
        )

    def _extract_sync(
        self,
        pdf_bytes: bytes,
        pages: list[int] | None,
        force_ocr: bool,
        use_llm: bool,
    ) -> list[PageExtraction]:
        """MinerU가 동기 API라 스레드에서 돌리는 래퍼."""
        tmp_dir: Path | None = None
        try:
            # MinerU는 입출력 디렉터리가 필요해 임시 디렉터리를 쓴다
            import tempfile as tf
            with tf.TemporaryDirectory() as tmp_str:
                tmp_dir = Path(tmp_str)
                pdf_path = tmp_dir / "input.pdf"
                pdf_path.write_bytes(pdf_bytes)
                return self._run_mineru(pdf_path, tmp_dir, force_ocr)
        except Exception as exc:
            _LOG.exception("MinerUEngine 추출 중 오류 발생")
            raise RuntimeError(f"MinerU 추출 실패: {exc}") from exc

    def _run_mineru(
        self,
        pdf_path: Path,
        out_dir: Path,
        force_ocr: bool,
    ) -> list[PageExtraction]:
        """MinerU API를 호출하고 PageExtraction 리스트로 정규화한다.

        force_ocr=True면 OCRPipe, 아니면 UNIPipe를 쓴다. 실패하면 빈 결과를 돌려준다.
        """
        try:
            if force_ocr:
                # 타입 스텁이 없어 pyproject.toml mypy.overrides(magic_pdf.*)로 허용한다.
                from magic_pdf.pipe.OCRPipe import OCRPipe
                pipe_cls = OCRPipe
            else:
                from magic_pdf.pipe.UNIPipe import UNIPipe
                pipe_cls = UNIPipe

            reader = FileBasedDataReader("")
            writer = FileBasedDataWriter(str(out_dir))
            pdf_bytes_data = pdf_path.read_bytes()

            pipe = pipe_cls(
                pdf_bytes_data,
                {"_pdf_type": "ocr" if force_ocr else "auto", "model_list": []},
                writer,
            )
            pipe.pipe_classify()
            pipe.pipe_analyze()
            pipe.pipe_parse()
            md_content = pipe.pipe_mk_markdown(str(out_dir), drop_mode="none")

        except (ImportError, Exception) as exc:
            _LOG.warning("MinerU 파이프 실행 실패, 빈 결과 반환: %s", exc)
            md_content = ""

        return _parse_mineru_output(md_content or "")


def _parse_mineru_output(md_content: str) -> list[PageExtraction]:
    """MinerU 마크다운 출력을 PageExtraction 리스트로 변환한다.

    MinerU는 단일 마크다운을 출력하므로 페이지 경계 마커(\\n\\n---\\n\\n)로
    페이지를 분리한다. 마커가 없으면 단일 페이지로 처리한다.
    """
    # MinerU 페이지 구분자: 수평선 패턴
    page_sections = md_content.split("\n\n---\n\n")
    results: list[PageExtraction] = []

    for idx, section in enumerate(page_sections):
        section = section.strip()
        tables = _extract_tables_from_md(section)
        formulas = _extract_formulas_from_md(section)
        results.append(
            PageExtraction(
                page_num=idx + 1,
                markdown=section,
                tables=tables,
                formulas=formulas,
                engine="mineru",
                llm_corrected=False,
            )
        )

    return results if results else [
        PageExtraction(
            page_num=1,
            markdown="",
            tables=[],
            formulas=[],
            engine="mineru",
            llm_corrected=False,
        )
    ]


def _extract_tables_from_md(md: str) -> list[dict]:
    """마크다운에서 파이프 테이블 블록을 추출한다."""
    lines = md.splitlines()
    tables: list[dict] = []
    current: list[str] = []

    for line in lines:
        stripped = line.strip()
        if stripped.startswith("|") and stripped.endswith("|"):
            current.append(line)
        else:
            if len(current) >= 2:
                tables.append({"md": "\n".join(current), "json": {}})
            current = []

    if len(current) >= 2:
        tables.append({"md": "\n".join(current), "json": {}})

    return tables


def _extract_formulas_from_md(md: str) -> list[str]:
    """마크다운에서 LaTeX 수식을 추출한다."""
    import re

    formulas: list[str] = []
    for match in re.finditer(r"\$\$(.+?)\$\$", md, re.DOTALL):
        formulas.append(match.group(1).strip())
    for match in re.finditer(r"(?<!\$)\$(?!\$)(.+?)(?<!\$)\$(?!\$)", md):
        formulas.append(match.group(1).strip())
    return formulas
