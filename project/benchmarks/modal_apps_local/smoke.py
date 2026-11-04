# Modal 앱(ASR_V1 Whisper, OCR_v1 BGE-M3)과 같은 모델·정밀도·파이프라인 설정을 로컬 MPS에서 돌려 본다.
# 사용: python smoke.py <오디오 폴더> <결과 json>
import json, sys, time
import numpy as np, torch

out = {"device": "mps", "torch": torch.__version__}

# 1) Whisper: modal_whisper_app.py 의 load / to_gpu / transcribe 와 같은 설정
from transformers import AutoModelForSpeechSeq2Seq, AutoProcessor, pipeline
from transformers.pipelines.audio_utils import ffmpeg_read
MID = "openai/whisper-large-v3-turbo"
t0 = time.perf_counter()
proc = AutoProcessor.from_pretrained(MID)
model = AutoModelForSpeechSeq2Seq.from_pretrained(MID, torch_dtype=torch.float16, low_cpu_mem_usage=True).to("mps")
pipe = pipeline("automatic-speech-recognition", model=model, tokenizer=proc.tokenizer,
                feature_extractor=proc.feature_extractor, device="mps")
out["whisper_load_s"] = round(time.perf_counter() - t0, 1)
for name in ("short16k.wav", "lecture16k.wav"):
    audio = ffmpeg_read(open(f"{sys.argv[1]}/{name}", "rb").read(), 16000)
    s = time.perf_counter()
    r = pipe({"raw": audio, "sampling_rate": 16000}, chunk_length_s=30, batch_size=8,
             generate_kwargs={"language": "ko", "task": "transcribe"})
    out[name] = {"text": str(r["text"]).strip(), "duration_sec": round(len(audio) / 16000, 1),
                 "latency_ms": round((time.perf_counter() - s) * 1000)}
del pipe, model; torch.mps.empty_cache()

# 2) BGE-M3·reranker: modal_embed_app.py 는 GPU fp16. MPS fp16 결과를 CPU fp32 와 비교한다.
from FlagEmbedding import BGEM3FlagModel, FlagReranker
texts = ["분모가 같은 분수는 분자끼리 더한다.", "운영체제는 프로세스를 스케줄링한다.", "Rust의 소유권은 메모리 안전성을 보장한다."]
query, passages = "분수 덧셈은 어떻게 하나요?", texts
def run(device, fp16):
    e = BGEM3FlagModel("BAAI/bge-m3", use_fp16=fp16, devices=device)
    o = e.encode(texts, batch_size=32, max_length=8192, return_dense=True, return_sparse=True, return_colbert_vecs=False)
    rr = FlagReranker("BAAI/bge-reranker-v2-m3", use_fp16=fp16, devices=device)
    sc = rr.compute_score([[query, p] for p in passages], normalize=True)
    return np.asarray(o["dense_vecs"], dtype=np.float32), o["lexical_weights"], [float(x) for x in sc]
d_m, s_m, r_m = run("mps", True)
d_c, s_c, r_c = run("cpu", False)
cos = [float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b))) for a, b in zip(d_m, d_c)]
out["bge"] = {"dense_dim": int(d_m.shape[1]), "cosine_mps_fp16_vs_cpu_fp32": [round(c, 5) for c in cos],
              "sparse_same_tokens": [set(a) == set(b) for a, b in zip(s_m, s_c)],
              "rerank_mps": [round(x, 4) for x in r_m], "rerank_cpu": [round(x, 4) for x in r_c],
              "rerank_top1_same": int(np.argmax(r_m)) == int(np.argmax(r_c))}
json.dump(out, open(sys.argv[2], "w"), ensure_ascii=False, indent=1)
print(json.dumps(out, ensure_ascii=False, indent=1))
