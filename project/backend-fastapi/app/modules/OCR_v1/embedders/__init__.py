"""Embedder 구현체. BGE-M3(Dense + Sparse)를 Modal GPU(기본) 또는 이 프로세스에서 돌린다."""
from __future__ import annotations

from .bge_m3_embedder import BGEM3Embedder
from .bge_m3_modal_embedder import BGEM3ModalEmbedder

__all__ = ["BGEM3Embedder", "BGEM3ModalEmbedder"]
