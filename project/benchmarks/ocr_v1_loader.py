# 벤치마크가 서비스 코드와 같은 OCR_v1 후처리·품질 게이트·청커를 쓰도록 패키지 초기화(FastAPI 라우터 로드) 없이 불러온다.
import pathlib
import sys
import types

OCR_V1_DIR = pathlib.Path(__file__).resolve().parents[1] / "backend-fastapi" / "app" / "modules" / "OCR_v1"


def load():
    if "ocr_v1" not in sys.modules:
        pkg = types.ModuleType("ocr_v1")
        pkg.__path__ = [str(OCR_V1_DIR)]
        sys.modules["ocr_v1"] = pkg
    return sys.modules["ocr_v1"]
