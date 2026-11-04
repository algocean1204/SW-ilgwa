"""프로젝트 루트 conftest. pytest 수집 전에 외부 의존성 더미를 등록한다.

llama_cpp·jinja2가 없는 환경(로컬 단위 테스트, CI)에서도 config·validator·fallback
테스트가 돌도록 sys.modules에 더미 모듈을 넣는다. 테스트 수집 때만 적용된다.
"""
import importlib
import importlib.machinery
import os
import sys
import types
from pathlib import Path

_ROOT = Path(__file__).resolve().parent
if not sys.path or sys.path[0] != str(_ROOT):
    # ChapterStudio_V1/ai_connectors 가 루트 ai_connectors 를 가리지 않게 한다.
    sys.path.insert(0, str(_ROOT))
importlib.import_module("ai_connectors")

# 테스트 API 키. 통합 테스트는 인증을 끄지 않고 이 키로 ApiKeyMiddleware 를 통과한다.
_TEST_API_KEY = "test-api-key-for-pytest"
os.environ.setdefault("FASTAPI_API_KEY", _TEST_API_KEY)


def _make_dummy(name: str) -> types.ModuleType:
    """지정된 이름의 빈 더미 모듈을 생성한다."""
    return types.ModuleType(name)


# llama_cpp 더미. model_loader.py 의 최상위 import 가 실패하지 않게 한다.
if "llama_cpp" not in sys.modules:
    _llama_cpp = _make_dummy("llama_cpp")
    # model_loader 가 타입 힌트와 인스턴스로 쓰는 Llama 클래스
    _llama_cpp.Llama = type("Llama", (), {})
    sys.modules["llama_cpp"] = _llama_cpp

# jinja2 는 pyproject.toml 의존성이라 보통 실제 패키지를 쓴다.
# 더미로 바꾸면 jinja2.__spec__ 이 None 이 되어 mlx_lm·transformers 처럼 jinja2 를 쓰는
# 패키지가 초기화에 실패한다. 그래서 jinja2 가 정말 없을 때만 더미를 넣는다.
if "jinja2" not in sys.modules:
    try:
        import jinja2  # noqa: F401  실제 패키지가 있는지 확인한다
    except ImportError:
        # CI 최소 설치처럼 jinja2 가 없을 때만 더미를 넣는다
        _jinja2 = _make_dummy("jinja2")

        class _DummyTemplate:
            """jinja2.Template 더미. test_fallback.py 는 pytest.importorskip 으로 건너뛴다."""
            def __init__(self, source: str) -> None:
                self._source = source

            def render(self, **kwargs: object) -> str:
                return self._source

        _jinja2.Template = _DummyTemplate
        # jinja2.__spec__ 을 검사하는 mlx_lm 같은 라이브러리가 동작하게 ModuleSpec 을 넣는다.
        _jinja2.__spec__ = importlib.machinery.ModuleSpec(
            name="jinja2",
            loader=None,
            origin="<dummy>",
        )
        sys.modules["jinja2"] = _jinja2
