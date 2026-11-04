"""Modal GPU용 Whisper large-v3-turbo 전사 서버.

배포 순서(backend-fastapi/ 에서 실행):
1. `modal run app/modules/ASR_V1/deploy/modal_whisper_app.py::download_model` 로 가중치를 Volume 에 받는다.
2. `modal deploy app/modules/ASR_V1/deploy/modal_whisper_app.py` 로 배포한다.
커넥터는 ai_connectors/asr/whisper_modal_connector.py 이다.
"""
from __future__ import annotations

import os
import time
from typing import Any

import modal

APP_NAME = os.environ.get("ASR_WHISPER_MODAL_APP_NAME", "asr-whisper-v3-turbo")
MODEL_ID = os.environ.get("ASR_WHISPER_MODAL_MODEL_ID", "openai/whisper-large-v3-turbo")
GPU = os.environ.get("ASR_WHISPER_MODAL_GPU", "L4")
TARGET_SR = 16000
MINUTES = 60

app = modal.App(APP_NAME)
hf_cache = modal.Volume.from_name(f"{APP_NAME}-hf-cache", create_if_missing=True)

image = (
    modal.Image.debian_slim(python_version="3.12")
    # 업로드 오디오(wav, mp3, webm 등)를 transformers ffmpeg_read 로 디코딩한다.
    .apt_install("ffmpeg")
    .uv_pip_install(
        "torch==2.8.0",
        "transformers==4.57.1",
        "accelerate==1.10.1",
        "huggingface-hub[hf-transfer]==0.36.0",
    )
    .env(
        {
            "HF_HUB_ENABLE_HF_TRANSFER": "1",
            "ASR_WHISPER_MODAL_MODEL_ID": MODEL_ID,
        }
    )
)


@app.function(image=image, volumes={"/root/.cache/huggingface": hf_cache}, timeout=30 * MINUTES)
def download_model() -> None:
    """가중치를 HF 캐시 Volume 에 미리 받아 둔다. 컨테이너가 뜰 때 Hub 에서 다시 받지 않는다."""
    from huggingface_hub import snapshot_download

    snapshot_download(MODEL_ID, ignore_patterns=["*.pt", "*.bin", "*.msgpack", "*.h5"])
    hf_cache.commit()


@app.cls(
    image=image,
    gpu=GPU,
    volumes={"/root/.cache/huggingface": hf_cache},
    timeout=10 * MINUTES,
    # idle GPU 과금을 줄이려고 요청이 끝나면 컨테이너를 빨리 회수한다(기본 30초).
    scaledown_window=int(os.environ.get("ASR_WHISPER_MODAL_SCALEDOWN_SEC", "30")),
    # CPU 에 올린 가중치를 스냅샷으로 떠서 콜드 스타트 때 디스크 로드를 건너뛴다.
    enable_memory_snapshot=True,
)
@modal.concurrent(max_inputs=4)
class WhisperServer:
    """Whisper large-v3-turbo 를 올려 한국어 전사를 제공한다."""

    @modal.enter(snap=True)
    def load(self) -> None:
        """스냅샷에 들어갈 단계라 GPU 없이 CPU 에 가중치만 올린다."""
        import torch
        from transformers import AutoModelForSpeechSeq2Seq, AutoProcessor

        self.processor = AutoProcessor.from_pretrained(MODEL_ID)
        self.model = AutoModelForSpeechSeq2Seq.from_pretrained(
            MODEL_ID, torch_dtype=torch.float16, low_cpu_mem_usage=True
        )

    @modal.enter(snap=False)
    def to_gpu(self) -> None:
        """복원된 컨테이너에서 모델을 GPU 로 옮기고 파이프라인을 만든다."""
        from transformers import pipeline

        self.model.to("cuda")
        self.pipe = pipeline(
            "automatic-speech-recognition",
            model=self.model,
            tokenizer=self.processor.tokenizer,
            feature_extractor=self.processor.feature_extractor,
            device="cuda:0",
        )

    @modal.method()
    def healthz(self) -> dict[str, str]:
        return {"status": "ok", "model": MODEL_ID, "gpu": GPU}

    @modal.method()
    def transcribe(self, audio_bytes: bytes, language: str = "ko") -> dict[str, Any]:
        """오디오 바이트를 전사해 text, duration_sec, latency_ms 를 돌려준다."""
        from transformers.pipelines.audio_utils import ffmpeg_read

        started_at = time.perf_counter()
        audio = ffmpeg_read(audio_bytes, TARGET_SR)
        # 30초가 넘는 강의 음성도 받도록 30초 청크로 나눠 묶음 처리한다.
        result = self.pipe(
            {"raw": audio, "sampling_rate": TARGET_SR},
            chunk_length_s=30,
            batch_size=8,
            generate_kwargs={"language": language, "task": "transcribe"},
        )
        return {
            "text": str(result["text"]).strip(),
            "duration_sec": float(len(audio)) / TARGET_SR,
            "latency_ms": (time.perf_counter() - started_at) * 1000.0,
            "model": MODEL_ID,
        }
