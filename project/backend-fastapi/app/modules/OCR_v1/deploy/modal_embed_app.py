"""OCR_v1 BGE-M3 임베딩과 BGE 리랭커를 Modal GPU에서 돌리는 앱.

배포 순서:
    modal run app/modules/OCR_v1/deploy/modal_embed_app.py::download_model
    modal deploy app/modules/OCR_v1/deploy/modal_embed_app.py

FastAPI 쪽은 embedders/bge_m3_modal_embedder.py, rerankers/bge_modal_reranker.py가
modal.Cls.from_name(APP_NAME, "BGEM3Server")로 이 앱을 부른다.
모델 id와 설정은 OCR_v1/config.py 기본값과 같다. 컨테이너에는 이 파일만 올라가서 config를 import하지 않는다.
"""
from __future__ import annotations

import os

import modal

APP_NAME = os.environ.get("OCR_V1_MODAL_EMBED_APP_NAME", "ocr-bge-m3")
EMBED_MODEL_ID = os.environ.get("OCR_V1_EMBEDDING_MODEL", "BAAI/bge-m3")
RERANK_MODEL_ID = os.environ.get("OCR_V1_RERANKER_MODEL", "BAAI/bge-reranker-v2-m3")
BATCH_SIZE = os.environ.get("OCR_V1_EMBEDDING_BATCH_SIZE", "32")
GPU = os.environ.get("OCR_V1_MODAL_EMBED_GPU", "L4")
MINUTES = 60

app = modal.App(APP_NAME)

hf_cache = modal.Volume.from_name(f"{APP_NAME}-hf-cache", create_if_missing=True)

image = (
    modal.Image.debian_slim(python_version="3.12")
    .uv_pip_install(
        "FlagEmbedding==1.3.5",
        "torch==2.7.1",
        "transformers==4.51.3",
        "huggingface-hub[hf-transfer]==0.36.0",
    )
    .env(
        {
            "HF_HUB_ENABLE_HF_TRANSFER": "1",
            "OCR_V1_MODAL_EMBED_APP_NAME": APP_NAME,
            "OCR_V1_EMBEDDING_MODEL": EMBED_MODEL_ID,
            "OCR_V1_RERANKER_MODEL": RERANK_MODEL_ID,
            "OCR_V1_EMBEDDING_BATCH_SIZE": BATCH_SIZE,
        }
    )
)


@app.function(image=image, volumes={"/root/.cache/huggingface": hf_cache}, timeout=30 * MINUTES)
def download_model() -> None:
    """두 모델 가중치를 HF 캐시 Volume에 미리 받아 둔다.

    BGE-M3의 sparse_linear.pt는 sparse 벡터에 필요해서 .pt는 거르지 않는다. onnx 사본만 뺀다.
    """
    from huggingface_hub import snapshot_download

    for model_id in (EMBED_MODEL_ID, RERANK_MODEL_ID):
        snapshot_download(model_id, ignore_patterns=["onnx/*", "*.onnx", "*.onnx_data"])
    hf_cache.commit()


@app.cls(
    image=image,
    gpu=GPU,
    volumes={"/root/.cache/huggingface": hf_cache},
    timeout=10 * MINUTES,
    # 요청이 끝나면 GPU를 빨리 돌려준다(기본 30초).
    scaledown_window=int(os.environ.get("OCR_V1_MODAL_EMBED_SCALEDOWN_SEC", "30")),
    # 모델을 올린 뒤 메모리를 스냅샷으로 떠서 콜드 스타트 때 그대로 복원한다.
    enable_memory_snapshot=True,
    experimental_options={"enable_gpu_snapshot": True},
)
@modal.concurrent(max_inputs=4)
class BGEM3Server:
    """BGE-M3(dense+sparse)와 bge-reranker-v2-m3를 한 컨테이너에 올린다."""

    @modal.enter(snap=True)
    def load(self) -> None:
        from FlagEmbedding import BGEM3FlagModel, FlagReranker

        # 로컬 구현과 같은 설정이다. 리랭커는 리눅스라 fp16을 켠다.
        self.embedder = BGEM3FlagModel(EMBED_MODEL_ID, use_fp16=True)
        self.reranker = FlagReranker(RERANK_MODEL_ID, use_fp16=True)

    @modal.method()
    def embed(self, texts: list[str]) -> list[dict]:
        """bge_m3_embedder._embed_batch_sync와 같은 모양을 돌려준다.

        [{"dense": list[float], "sparse": {"indices": list[int], "values": list[float]}}, ...]
        """
        if not texts:
            return []
        output = self.embedder.encode(
            texts,
            batch_size=int(BATCH_SIZE),
            return_dense=True,
            return_sparse=True,
            return_colbert_vecs=False,
        )
        results: list[dict] = []
        for dense, weights in zip(output["dense_vecs"], output["lexical_weights"]):
            results.append({
                "dense": dense.tolist(),
                "sparse": {
                    "indices": [int(k) for k in weights],
                    "values": [float(v) for v in weights.values()],
                },
            })
        return results

    @modal.method()
    def rerank(self, query: str, passages: list[str]) -> list[float]:
        """passages 순서 그대로 0~1 점수를 돌려준다. 정렬은 호출하는 쪽에서 한다."""
        if not passages:
            return []
        scores = self.reranker.compute_score([[query, p] for p in passages], normalize=True)
        # 쌍이 하나면 FlagReranker가 float 하나를 돌려준다
        if isinstance(scores, float):
            scores = [scores]
        return [float(s) for s in scores]
