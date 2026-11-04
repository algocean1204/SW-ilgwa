"""BGE-M3 원격 임베더. deploy/modal_embed_app.py의 BGEM3Server.embed를 부른다.

반환 모양은 BGEM3Embedder와 같다. OCR_V1_EMBED_BACKEND=modal(기본)일 때 registry가 고른다.
"""
from __future__ import annotations

from ..config import embedding_batch_size, modal_embed_app_name

SERVER_CLASS = "BGEM3Server"


class BGEM3ModalEmbedder:
    """Modal GPU에 배포한 BGE-M3로 Dense+Sparse 벡터를 만든다."""

    name: str = "bge-m3-modal"

    def __init__(self) -> None:
        import modal

        self._server = modal.Cls.from_name(modal_embed_app_name(), SERVER_CLASS)()

    async def embed(self, texts: list[str]) -> list[dict]:
        """텍스트 목록을 [{"dense": [...], "sparse": {"indices": [...], "values": [...]}}]로 바꾼다."""
        if not texts:
            return []
        # 한 번에 보내는 양을 로컬 배치 크기와 맞춘다
        size = embedding_batch_size()
        results: list[dict] = []
        for i in range(0, len(texts), size):
            results.extend(await self._server.embed.remote.aio(texts[i : i + size]))
        return results
