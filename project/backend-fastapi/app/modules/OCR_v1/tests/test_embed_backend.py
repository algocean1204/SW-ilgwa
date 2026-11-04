"""OCR_V1_EMBED_BACKEND 전환과 Modal 임베더·리랭커가 로컬과 같은 모양을 돌려주는지 확인한다.

Modal은 부르지 않는다. deploy/modal_embed_app.py는 modal을 가짜로 바꿔 불러오고,
가짜 모델을 로컬 구현과 원격 앱에 똑같이 넣어 결과를 비교한다.
"""
from __future__ import annotations

import asyncio
import importlib.util
import pathlib
import sys
import types
from unittest.mock import MagicMock

import pytest

np = pytest.importorskip("numpy")
pytest.importorskip("pypdfium2", reason="OCR_v1 패키지 import에 pypdfium2가 필요하다")

from app.modules.OCR_v1 import registry  # noqa: E402
from app.modules.OCR_v1.embedders.bge_m3_embedder import BGEM3Embedder, _embed_batch_sync  # noqa: E402
from app.modules.OCR_v1.embedders.bge_m3_modal_embedder import BGEM3ModalEmbedder  # noqa: E402
from app.modules.OCR_v1.rerankers.bge_modal_reranker import BGEModalReranker  # noqa: E402
from app.modules.OCR_v1.rerankers.bge_reranker import BGEReranker, _rerank_sync  # noqa: E402

APP_FILE = pathlib.Path(__file__).resolve().parents[1] / "deploy" / "modal_embed_app.py"
TEXTS = ["가나다", "라마바", "사아자"]


class _FakeM3:
    """BGEM3FlagModel.encode 출력 모양을 흉내 낸다."""

    def encode(self, texts, **_kw):
        dense = np.array([[float(len(t)), float(i), 0.5, -1.0] for i, t in enumerate(texts)], dtype=np.float32)
        weights = [{"12": np.float32(0.25 + i), "7": np.float32(0.125)} if i % 2 == 0 else {} for i in range(len(texts))]
        return {"dense_vecs": dense, "lexical_weights": weights}


class _FakeReranker:
    def compute_score(self, pairs, normalize=True):
        return [1.0 / (i + 1) if i != 1 else 0.9 for i in range(len(pairs))]


def _load_app_with_fake_modal(monkeypatch: pytest.MonkeyPatch):
    """modal 데코레이터를 그대로 통과시키는 가짜 modal로 배포 파일을 불러온다."""
    fake = MagicMock()
    passthrough = lambda obj: obj  # noqa: E731
    fake.App.return_value.cls.return_value = passthrough
    fake.App.return_value.function.return_value = passthrough
    fake.concurrent.return_value = passthrough
    fake.enter.return_value = passthrough
    fake.method.return_value = passthrough
    monkeypatch.setitem(sys.modules, "modal", fake)
    spec = importlib.util.spec_from_file_location("ocr_modal_embed_app_test", APP_FILE)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    server = mod.BGEM3Server()
    server.embedder = _FakeM3()
    server.reranker = _FakeReranker()
    return server


def _patch_from_name(monkeypatch: pytest.MonkeyPatch, server) -> list:
    """modal.Cls.from_name(...)()가 server를 .remote.aio로 부르게 만든다."""
    calls: list = []

    def remote(fn):
        async def aio(*args):
            calls.append(args)
            return fn(*args)
        return types.SimpleNamespace(remote=types.SimpleNamespace(aio=aio))

    handle = types.SimpleNamespace(embed=remote(server.embed), rerank=remote(server.rerank))
    fake_modal = types.SimpleNamespace(Cls=types.SimpleNamespace(from_name=lambda app, cls: (lambda: handle)))
    monkeypatch.setitem(sys.modules, "modal", fake_modal)
    return calls


def test_backend_switch(monkeypatch: pytest.MonkeyPatch) -> None:
    _patch_from_name(monkeypatch, types.SimpleNamespace(embed=None, rerank=None))

    monkeypatch.delenv("OCR_V1_EMBED_BACKEND", raising=False)
    assert isinstance(registry.get_embedder(), BGEM3ModalEmbedder)
    assert isinstance(registry.get_reranker(), BGEModalReranker)

    monkeypatch.setenv("OCR_V1_EMBED_BACKEND", "local")
    assert isinstance(registry.get_embedder(), BGEM3Embedder)
    assert isinstance(registry.get_reranker(), BGEReranker)


def test_modal_embedder_matches_local_shape(monkeypatch: pytest.MonkeyPatch) -> None:
    server = _load_app_with_fake_modal(monkeypatch)
    calls = _patch_from_name(monkeypatch, server)
    monkeypatch.setenv("OCR_V1_EMBEDDING_BATCH_SIZE", "2")

    remote = asyncio.run(BGEM3ModalEmbedder().embed(TEXTS))
    local = _embed_batch_sync(_FakeM3(), TEXTS)

    # 배치 크기 2로 나눠 두 번 부른다. 가짜 모델의 dense는 배치 안 순번을 쓰므로 비교는 배치별로 한다.
    assert [len(c[0]) for c in calls] == [2, 1]
    assert remote[:2] == _embed_batch_sync(_FakeM3(), TEXTS[:2])
    assert remote[2:] == _embed_batch_sync(_FakeM3(), TEXTS[2:])
    assert [sorted(r) for r in remote] == [sorted(r) for r in local] == [["dense", "sparse"]] * 3
    assert all(type(v) is float for r in remote for v in r["dense"] + r["sparse"]["values"])
    assert all(type(i) is int for r in remote for i in r["sparse"]["indices"])


def test_modal_reranker_matches_local(monkeypatch: pytest.MonkeyPatch) -> None:
    server = _load_app_with_fake_modal(monkeypatch)
    _patch_from_name(monkeypatch, server)

    remote = asyncio.run(BGEModalReranker().rerank("질문", TEXTS, top_k=2))
    local = _rerank_sync(_FakeReranker(), "질문", TEXTS, 2)

    assert remote == local
    assert [r["index"] for r in remote] == [0, 1]
