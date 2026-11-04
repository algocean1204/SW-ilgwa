# 화자 유사도 재측정. 새로 합성하지 않고, 데모 사이트의 합성 음성과 원본(참조) 음성만 비교한다.
# 방법: ECAPA-TDNN(speechbrain/spkrec-ecapa-voxceleb)으로 음성마다 화자 임베딩(192차원)을 뽑고, 두 임베딩의 코사인 유사도를 낸다.
# 같은 화자: Sp1 원본 ↔ Sp1로 합성한 데모 음성 240개. 다른 화자(대조군): 다른 참조 음성 ↔ 같은 데모 음성.
# 사용: python measure.py  (ffmpeg 필요)
import json, subprocess, sys
from pathlib import Path
import numpy as np, torch
from speechbrain.inference.speaker import EncoderClassifier

ROOT = Path(__file__).resolve().parents[2]
DEMO = ROOT / "portfolio-showcase/public/audio"
VOICES = ROOT / "backend-fastapi/app/modules/TTS_V2"
SAME = ("Sp1 원본", DEMO / "reference/sp1_ref.wav")
OTHERS = [("Sp2 (기본 튜터 2)", VOICES / "voice_samples/tutor_2_ref.wav"), ("ref_5", VOICES / "voice_samples/ref_5.m4a"),
          ("ref_7", VOICES / "voice_samples/ref_7.m4a"), ("preset_voice1", VOICES / "assets/voices/preset_voice1.wav"),
          ("preset_voice2", VOICES / "assets/voices/preset_voice2.wav")]
THRESHOLD = 0.85

def load(path: Path) -> torch.Tensor:  # ffmpeg로 16kHz mono float32로 읽는다
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", "16000", "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return torch.from_numpy(np.frombuffer(raw, dtype=np.float32).copy()).unsqueeze(0)

model = EncoderClassifier.from_hparams("speechbrain/spkrec-ecapa-voxceleb", run_opts={"device": "cpu"})
def embed(path: Path) -> np.ndarray:
    with torch.no_grad():
        e = model.encode_batch(load(path)).squeeze().numpy()
    return e / np.linalg.norm(e)

demo = sorted(p for p in DEMO.glob("*/ch*/slide_*.mp3"))
E = {p: embed(p) for p in demo}
refs = {n: embed(p) for n, p in [SAME, *OTHERS]}
def stats(v):
    v = np.array(v)
    return {"n": len(v), "mean": round(float(v.mean()), 4), "std": round(float(v.std()), 4), "min": round(float(v.min()), 4),
            "p5": round(float(np.percentile(v, 5)), 4), "median": round(float(np.median(v)), 4), "max": round(float(v.max()), 4),
            "pass_rate": round(float((v >= THRESHOLD).mean()), 4)}
same = {str(p.relative_to(DEMO)): round(float(refs[SAME[0]] @ e), 4) for p, e in E.items()}
out = {
    "측정일": "2026-11-03", "방법": "ECAPA-TDNN(speechbrain/spkrec-ecapa-voxceleb) 화자 임베딩의 코사인 유사도. 음성 전체를 16kHz mono로 읽어 임베딩 1개를 뽑는다.",
    "합성 음성": f"데모 사이트 음성 {len(demo)}개(mp3, 로컬 MLX Qwen3-TTS가 Sp1 참조 음성으로 합성)", "기준": THRESHOLD,
    "같은 화자": {"쌍": "Sp1 원본 ↔ 데모 음성", **stats(list(same.values())),
               "과목별 평균": {s: round(float(np.mean([v for k, v in same.items() if k.startswith(s + "/")])), 4) for s in ("python", "rust", "os")}},
    "다른 화자": {n: stats([float(refs[n] @ e) for e in E.values()]) for n, _ in OTHERS},
    "참조 음성끼리": {f"{a} ↔ {b}": round(float(refs[a] @ refs[b]), 4) for i, a in enumerate(refs) for b in list(refs)[i + 1:]},
    "슬라이드별": same,
}
(Path(__file__).parent / "results/summary.json").write_text(json.dumps(out, ensure_ascii=False, indent=1))
print(json.dumps({k: v for k, v in out.items() if k != "슬라이드별"}, ensure_ascii=False, indent=1))
