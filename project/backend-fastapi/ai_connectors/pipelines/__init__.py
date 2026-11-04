"""AI 파이프라인 패키지.

OCR, 후처리 같은 커넥터를 순서대로 묶는 파이프라인 모듈을 둔다.
"""
from .textbook_ocr_pipeline import TextbookOCRPipeline

__all__ = ["TextbookOCRPipeline"]
